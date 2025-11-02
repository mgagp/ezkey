/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptPendingService
 * Description: Specialized service for handling pending authentication requests.
 */

package org.ezkey.authattempt.service;

import java.time.OffsetDateTime;
import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.NoPendingAuthAttemptException;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for processing pending authentication attempts.
 *
 * <p>This service handles the mobile device polling for pending authentication requests. It
 * implements the read-once guarantee security principle and validates device signatures and
 * enrollment proof tokens.
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li><b>Read-Once Guarantee:</b> Each authentication attempt can only be read once
 *   <li><b>Enrollment Proof Token Validation:</b> Prevents enumeration attacks
 *   <li><b>Device Signature Validation:</b> Ensures legitimate device access
 *   <li><b>Anti-Replay Protection:</b> Prevents reuse of device proof tokens
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * to ensure data consistency during the authentication attempt claiming process.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptPendingRequest
 * @see AuthAttemptPendingResponse
 * @see AuthAttempt
 * @see Enrollment
 * @see SignatureService
 */
@Service
@Transactional
public class AuthAttemptPendingService {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptPendingService.class);

  private final AuthAttemptRepository authAttemptRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final SignatureService signatureService;

  /**
   * Constructs the pending service with required dependencies.
   *
   * @param authAttemptRepository the JPA repository for authentication attempts
   * @param enrollmentRepository the JPA repository for enrollments
   * @param signatureService the signature service for cryptographic operations
   */
  public AuthAttemptPendingService(
      AuthAttemptRepository authAttemptRepository,
      EnrollmentRepository enrollmentRepository,
      SignatureService signatureService) {
    this.authAttemptRepository = authAttemptRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.signatureService = signatureService;
  }

  /**
   * Process pending authentication attempt request using secure enrollment proof token.
   *
   * <p>This method implements the fundamental Ezkey security principle of read-once guarantee. It
   * validates the request completely before locking the authentication attempt to ensure that each
   * authentication attempt can only be read once by a legitimate device.
   *
   * @param request the pending request with enrollment proof token
   * @return the pending authentication response with proof token
   * @throws IllegalArgumentException if enrollment proof token is invalid
   * @throws IllegalStateException if the authentication attempt is already processed
   * @throws NoPendingAuthAttemptException if no pending authentication attempt is found
   */
  public AuthAttemptPendingResponse pending(final AuthAttemptPendingRequest request) {
    // Step 1: Validate enrollment
    Enrollment enrollment = validateEnrollment(request);

    // Step 2: Validate device signature
    validateDeviceSignature(request, enrollment);

    // Step 3: Claim the pending attempt
    AuthAttempt authAttempt = claimPendingAttempt(request);

    // Step 4: Build and return response
    return buildPendingResponse(authAttempt, enrollment);
  }

  /**
   * Validates the enrollment using the proof token and enrollment ID.
   *
   * <p>This method ensures that the enrollment exists, is active, and that the provided enrollment
   * ID matches the proof token to prevent enumeration attacks.
   *
   * @param request the pending request containing enrollment proof token and ID
   * @return the validated enrollment
   * @throws IllegalArgumentException if enrollment validation fails
   */
  private Enrollment validateEnrollment(AuthAttemptPendingRequest request) {
    // Find enrollment by proof token instead of ID
    Enrollment enrollment =
        enrollmentRepository
            .findByEnrollmentProofTokenAndActive(request.getEnrollmentProofToken(), true)
            .orElseThrow(
                () -> {
                  logger.warn("Invalid enrollment proof token provided");
                  return new IllegalArgumentException("Authentication request failed");
                });

    // Validate that the provided enrollment ID matches the proof token
    if (!enrollment.getEnrollmentId().equals(request.getEnrollmentId())) {
      logger.warn(
          "Enrollment ID mismatch with proof token for enrollment: {}",
          enrollment.getEnrollmentId());
      throw new IllegalArgumentException("Authentication request failed");
    }

    return enrollment;
  }

  /**
   * Validates the device signature and proof token uniqueness.
   *
   * <p>This method ensures that the device signature is valid and that the device proof token has
   * not been used before to prevent replay attacks.
   *
   * @param request the pending request containing device signature and proof token
   * @param enrollment the validated enrollment containing device public key
   * @throws IllegalArgumentException if signature validation fails
   * @throws IllegalStateException if device public key is missing
   */
  private void validateDeviceSignature(AuthAttemptPendingRequest request, Enrollment enrollment) {
    // Validate device public key
    String devicePublicKey = enrollment.getDevicePublicKey();
    if (devicePublicKey == null) {
      logger.warn("Device public key missing for enrollment: {}", request.getEnrollmentId());
      throw new IllegalStateException("Authentication request failed");
    }

    // Validate signature
    boolean isValid =
        signatureService.validateSignature(
            request.getDeviceProofToken(), request.getDeviceProofTokenSigned(), devicePublicKey);
    if (!isValid) {
      logger.warn("Invalid signature for enrollment: {}", request.getEnrollmentId());
      throw new IllegalArgumentException("Authentication request failed");
    }

    // Check device proof token uniqueness
    if (authAttemptRepository.existsByDeviceProofToken(request.getDeviceProofToken())) {
      logger.warn("Device proof token already used for enrollment: {}", request.getEnrollmentId());
      throw new IllegalArgumentException("Authentication request failed");
    }
  }

  /**
   * Claims the pending authentication attempt and marks it as read.
   *
   * <p>This method implements the read-once guarantee by atomically locking and marking the
   * authentication attempt as processed.
   *
   * @param request the pending request
   * @return the claimed authentication attempt
   * @throws NoPendingAuthAttemptException if no pending attempt is found
   * @throws IllegalStateException if the attempt is already processed
   */
  private AuthAttempt claimPendingAttempt(AuthAttemptPendingRequest request) {
    // Find valid (non-expired) pending auth attempt
    OffsetDateTime now = OffsetDateTime.now();
    AuthAttempt authAttempt =
        authAttemptRepository
            .findAndLockMostRecentValidByEnrollmentIdAndStatus(
                request.getEnrollmentId(), AuthAttemptStatus.PENDING.name(), now)
            .orElse(null);

    if (authAttempt == null) {
      throw new NoPendingAuthAttemptException("No pending authentication request");
    }

    // Double-check if already processed (protection against race condition)
    if (authAttempt.getAuthAttemptStatus() != AuthAttemptStatus.PENDING) {
      logger.warn(
          "Auth attempt already processed: {} with status {}",
          authAttempt.getAuthAttemptId(),
          authAttempt.getAuthAttemptStatus());
      throw new IllegalStateException("Authentication request failed");
    }

    // Record the device proof token to ensure unicity and update status to READ
    authAttempt.setDeviceProofToken(request.getDeviceProofToken());
    authAttempt.setAuthAttemptStatus(AuthAttemptStatus.READ);
    authAttemptRepository.save(authAttempt);

    return authAttempt;
  }

  /**
   * Builds the pending response with signed proof token and challenge information.
   *
   * <p>This method creates a complete response containing the authentication attempt ID, signed
   * proof token, and challenge requirements for the mobile device.
   *
   * @param authAttempt the claimed authentication attempt
   * @param enrollment the enrollment containing integration private key
   * @return the complete pending response
   */
  private AuthAttemptPendingResponse buildPendingResponse(
      AuthAttempt authAttempt, Enrollment enrollment) {
    AuthAttemptPendingResponse response = new AuthAttemptPendingResponse();
    response.setAuthAttemptId(authAttempt.getAuthAttemptId());
    response.setAuthAttemptProofToken(authAttempt.getAuthAttemptProofToken());
    response.setAuthAttemptProofTokenSignedByIntegration(
        signatureService.generateSignature(
            authAttempt.getAuthAttemptProofToken(), enrollment.getIntegrationPrivateKey()));

    // Determine challenge requirements
    if (authAttempt.getAuthAttemptChallenge() != null) {
      response.setAuthAttemptChallengeRequired(true);
    } else {
      response.setAuthAttemptChallengeRequired(enrollment.getAuthAttemptChallengeRequired());
    }

    return response;
  }
}
