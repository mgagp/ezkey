/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptRespondService
 * Description: Specialized service for processing authentication responses.
 */

package org.ezkey.authattempt.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptRequestFailedException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for processing authentication responses from mobile devices.
 *
 * <p>This service handles the completion of authentication attempts when mobile devices submit
 * their responses (approve/deny) with cryptographic proof. It validates device signatures, checks
 * challenge responses if required, and updates authentication attempt status accordingly.
 *
 * <p><b>Security Validations:</b>
 *
 * <ul>
 *   <li><b>Signature Verification:</b> Validates device signature using enrollment public key
 *   <li><b>Challenge Validation:</b> Verifies challenge response if required by enrollment
 *   <li><b>Status Validation:</b> Ensures authentication attempt is in READ status
 *   <li><b>Proof Token Validation:</b> Verifies authentication attempt proof token signature
 *   <li><b>Supersession Check:</b> Ensures no newer authentication attempt exists
 *   <li><b>Expiration Check:</b> Validates authentication attempt has not expired
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * to ensure data consistency during the authentication response processing.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptRespondRequest
 * @see AuthAttemptRespondResponse
 * @see AuthAttempt
 * @see Enrollment
 * @see SignatureService
 * @see AuthenticationResult
 */
@Service
@Transactional
public class AuthAttemptRespondService {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptRespondService.class);

  /**
   * Client-visible message for FAILED results. Must not echo internal exception messages (IDs,
   * signature hints).
   */
  private static final String RESPOND_FAILURE_CLIENT_MESSAGE =
      "The response could not be processed.";

  private final AuthAttemptRepository authAttemptRepository;

  private final EnrollmentRepository enrollmentRepository;

  private final SignatureService signatureService;

  private final AuthAttemptTxHelper authAttemptTxHelper;

  /**
   * Constructs the respond service with required dependencies.
   *
   * @param authAttemptRepository the JPA repository for authentication attempts
   * @param enrollmentRepository the JPA repository for enrollments
   * @param signatureService the signature service for cryptographic operations
   * @param authAttemptTxHelper the transaction helper for independent status commits
   */
  public AuthAttemptRespondService(
      AuthAttemptRepository authAttemptRepository,
      EnrollmentRepository enrollmentRepository,
      SignatureService signatureService,
      AuthAttemptTxHelper authAttemptTxHelper) {
    this.authAttemptRepository = authAttemptRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.authAttemptTxHelper = authAttemptTxHelper;
    this.signatureService = signatureService;
  }

  /**
   * Processes an authentication response from a mobile device.
   *
   * <p>This method handles the completion of an authentication attempt when a mobile device submits
   * its response (approve/deny) with cryptographic proof. It validates the device signature, checks
   * challenge responses if required, and updates the authentication attempt status accordingly.
   *
   * @param request the authentication response request from mobile device
   * @return the authentication response result with status and message
   */
  public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
    AuthAttempt authAttempt = null;
    try {
      // Step 1: Validate and get the authentication attempt
      authAttempt = validateAndGetAttempt(request);

      // Step 2: Validate enrollment
      Enrollment enrollment = validateEnrollment(authAttempt);

      // Step 3: Validate device signature
      validateDeviceSignature(request, authAttempt, enrollment);

      // Step 4: Validate challenge if required
      validateChallenge(request, authAttempt, enrollment);

      // Step 5: Update attempt status
      updateAttemptStatus(authAttempt, enrollment, request);

      // Step 6: Build and return response (integration-signed result payload)
      return buildResponse(request, authAttempt, enrollment);
    } catch (IllegalArgumentException e) {
      return buildFailedResponse(request, authAttempt, e);
    }
  }

  /**
   * Validates and retrieves the authentication attempt.
   *
   * <p>This method ensures the authentication attempt exists and is in the correct state for
   * processing a response.
   *
   * @param request the authentication response request
   * @return the validated authentication attempt
   */
  private AuthAttempt validateAndGetAttempt(AuthAttemptRespondRequest request) {
    Optional<AuthAttempt> authAttemptOpt =
        authAttemptRepository.findById(request.getAuthAttemptId());
    if (authAttemptOpt.isEmpty()) {
      throw new AuthAttemptRequestFailedException("Auth attempt record not found");
    }
    AuthAttempt authAttempt = authAttemptOpt.get();

    // Check if in READ status (device has claimed the attempt)
    if (authAttempt.getAuthAttemptStatus() != AuthAttemptStatus.READ) {
      throw new AuthAttemptRequestFailedException("Auth attempt not read by device");
    }
    // Check if superseded by a newer authentication attempt for the same enrollment
    Optional<AuthAttempt> newerAttempt =
        authAttemptRepository.findNewerAttemptByEnrollmentId(
            authAttempt.getEnrollmentId(), authAttempt.getCreatedAt());
    if (newerAttempt.isPresent()) {
      logger.info(
          "Auth attempt {} superseded by newer attempt {} for enrollment {}",
          authAttempt.getAuthAttemptId(),
          newerAttempt.get().getAuthAttemptId(),
          authAttempt.getEnrollmentId());
      throw new AuthAttemptStateConflictException(
          "Authentication attempt superseded by newer request");
    }
    // Check if expired
    OffsetDateTime now = OffsetDateTime.now();
    if (authAttempt.getExpiresAt() != null && now.isAfter(authAttempt.getExpiresAt())) {
      throw new AuthAttemptStateConflictException("Authentication attempt expired");
    }
    return authAttempt;
  }

  /**
   * Validates the enrollment for the authentication attempt.
   *
   * <p>This method ensures the enrollment exists and contains the necessary cryptographic keys for
   * validation.
   *
   * @param authAttempt the authentication attempt
   * @return the validated enrollment
   */
  private Enrollment validateEnrollment(AuthAttempt authAttempt) {
    Enrollment enrollment =
        enrollmentRepository
            .findById(authAttempt.getEnrollmentId())
            .orElseThrow(
                () -> new AuthAttemptRequestFailedException("Enrollment record not found"));

    // Validate device public key
    String devicePublicKey = enrollment.getDevicePublicKey();
    if (devicePublicKey == null) {
      // Use TxHelper to commit INVALID status before throwing exception
      authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());
      throw new AuthAttemptRequestFailedException("Device public key not found");
    }
    return enrollment;
  }

  /**
   * Validates the device signature for the authentication attempt.
   *
   * <p>The device signs the canonical payload {@code proofToken|accepted} (UTF-8) so that
   * authAttemptAccepted cannot be flipped by a MITM. See {@link AuthAttemptSignaturePayload} and
   * docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md.
   *
   * @param request the authentication response request
   * @param authAttempt the authentication attempt
   * @param enrollment the enrollment containing device public key
   */
  private void validateDeviceSignature(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
    boolean accepted = Boolean.TRUE.equals(request.getAuthAttemptAccepted());
    String payload =
        AuthAttemptSignaturePayload.buildRespondPayload(
            authAttempt.getAuthAttemptProofToken(), accepted);
    boolean isValid =
        signatureService.validateSignature(
            payload,
            request.getAuthAttemptProofTokenSignedByDevice(),
            enrollment.getDevicePublicKey());
    if (!isValid) {
      authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());
      throw new AuthAttemptRequestFailedException("Invalid signature for auth attempt code");
    }
  }

  /**
   * Validates the challenge response if required by the enrollment or auth attempt.
   *
   * <p>This method ensures the challenge response matches the expected value if challenge
   * validation is required. Challenge can be required either at enrollment level (permanent) or at
   * auth attempt level (per-request).
   *
   * <p><b>Security:</b> If an auth attempt has a challenge code (authAttemptChallenge != null), the
   * challenge MUST be validated regardless of enrollment settings. This prevents bypassing
   * challenge verification when it was explicitly requested.
   *
   * <p><b>Reject Handling:</b> Challenge validation is skipped if the user rejected the
   * authentication request ({@code authAttemptAccepted = false}). Since the device signature is
   * already validated, we can trust that a reject is legitimate and there's no security risk in
   * skipping challenge validation for rejects. This ensures rejects are properly marked as REJECTED
   * rather than INVALID.
   *
   * @param request the authentication response request
   * @param authAttempt the authentication attempt
   * @param enrollment the enrollment containing challenge requirements
   */
  private void validateChallenge(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
    // Skip challenge validation if user rejected - no need to validate challenge for rejects
    // This ensures rejects are properly marked as REJECTED rather than INVALID
    if (Boolean.FALSE.equals(request.getAuthAttemptAccepted())) {
      logger.debug(
          "Skipping challenge validation for rejected auth attempt: {}",
          authAttempt.getAuthAttemptId());
      return;
    }

    // Validate challenge if this auth attempt has a challenge code
    // Challenge can be required at enrollment level OR per-request (authAttemptChallenge != null)
    boolean challengeRequired =
        Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())
            || authAttempt.getAuthAttemptChallenge() != null;
    if (challengeRequired) {
      if (request.getAuthAttemptChallengeResponse() == null
          || !request
              .getAuthAttemptChallengeResponse()
              .equals(authAttempt.getAuthAttemptChallenge())) {
        // Use TxHelper to commit INVALID status before throwing exception
        // This prevents rollback and ensures audit trail is preserved
        authAttemptTxHelper.markAsInvalid(authAttempt.getAuthAttemptId());

        logger.warn(
            "❌ Challenge validation failed for authAttemptId: {} (expected: {}, got: {})",
            authAttempt.getAuthAttemptId(),
            authAttempt.getAuthAttemptChallenge(),
            request.getAuthAttemptChallengeResponse());
        throw new AuthAttemptRequestFailedException("Challenge value mismatch");
      }
      logger.debug(
          "✅ Challenge validated successfully for authAttemptId: {}",
          authAttempt.getAuthAttemptId());
    }
  }

  /**
   * Updates the authentication attempt status based on the user's decision.
   *
   * <p>This method sets the final status of the authentication attempt based on whether the user
   * approved or denied the authentication request.
   *
   * @param authAttempt the authentication attempt to update
   * @param request the authentication response request
   */
  private void updateAttemptStatus(
      AuthAttempt authAttempt, Enrollment enrollment, AuthAttemptRespondRequest request) {
    // Update authorization attempt based on user decision
    if (Boolean.TRUE.equals(request.getAuthAttemptAccepted())) {
      authAttempt.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);
      enrollment.setLastUsedAt(OffsetDateTime.now());
      enrollmentRepository.save(enrollment);
    } else {
      authAttempt.setAuthAttemptStatus(AuthAttemptStatus.REJECTED);
    }
    authAttemptRepository.save(authAttempt);
  }

  /**
   * Builds the authentication response with the appropriate result and integration signature over
   * the canonical Respond result payload.
   *
   * @param request the authentication response request
   * @param authAttempt the updated attempt
   * @param enrollment enrollment (integration private key for signing)
   * @return the authentication response with result, message, and signature
   */
  private AuthAttemptRespondResponse buildResponse(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
    AuthAttemptRespondResponse response = new AuthAttemptRespondResponse();

    if (Boolean.TRUE.equals(request.getAuthAttemptAccepted())) {
      response.setResult(AuthenticationResult.APPROVED);
    } else {
      response.setResult(AuthenticationResult.DENIED);
    }
    response.setMessage("Auth attempt completed");
    response.setAuthAttemptId(authAttempt.getAuthAttemptId());
    response.setCreatedAt(authAttempt.getCreatedAt());
    attachRespondResultSignature(response, enrollment, authAttempt.getAuthAttemptProofToken());
    return response;
  }

  /**
   * Builds a FAILED result for validation errors and signs when the integration key can be resolved
   * from the attempt's enrollment.
   */
  private AuthAttemptRespondResponse buildFailedResponse(
      AuthAttemptRespondRequest request, AuthAttempt authAttempt, IllegalArgumentException e) {
    AuthAttemptRespondResponse response =
        new AuthAttemptRespondResponse(AuthenticationResult.FAILED, RESPOND_FAILURE_CLIENT_MESSAGE);
    if (authAttempt != null) {
      response.setAuthAttemptId(authAttempt.getAuthAttemptId());
      response.setCreatedAt(authAttempt.getCreatedAt());
      String proofToken =
          authAttempt.getAuthAttemptProofToken() != null
              ? authAttempt.getAuthAttemptProofToken()
              : "";
      enrollmentRepository
          .findById(authAttempt.getEnrollmentId())
          .ifPresent(enrollment -> attachRespondResultSignature(response, enrollment, proofToken));
    } else {
      response.setAuthAttemptId(request.getAuthAttemptId());
    }
    return response;
  }

  /**
   * Signs {@code proofToken|authAttemptId|result|message} with the integration private key. Skips
   * signing if the key is missing.
   */
  private void attachRespondResultSignature(
      AuthAttemptRespondResponse response, Enrollment enrollment, String proofToken) {
    if (enrollment == null) {
      return;
    }
    String privateKey = enrollment.getIntegrationPrivateKey();
    if (privateKey == null || privateKey.isBlank()) {
      return;
    }
    String payload =
        AuthAttemptSignaturePayload.buildRespondResultPayload(
            proofToken, response.getAuthAttemptId(), response.getResult(), response.getMessage());
    String signature = signatureService.signIntegrationPayload(payload, privateKey);
    response.setAuthAttemptProofTokenResultSignedByIntegration(signature);
  }
}
