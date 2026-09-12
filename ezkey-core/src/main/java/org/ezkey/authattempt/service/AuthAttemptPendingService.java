/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptPendingService
 * Description: Specialized service for handling pending authentication requests.
 */

package org.ezkey.authattempt.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.NoPendingAuthAttemptException;
import org.ezkey.exception.auth.AuthAttemptRequestFailedException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.security.SensitiveDataHasher;
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
 *   <li><b>Device Proof Token Uniqueness (on claim):</b> Once a {@code PENDING} attempt is
 *       successfully claimed, the device proof token hash is persisted on {@link AuthAttempt} and
 *       must not be reused on a later claim. If there is no pending attempt (HTTP 204 from the
 *       API), no row is updated and the same signed device proof token may be used on subsequent
 *       polls—this is authenticated polling without a state change, not a gap in cryptographic
 *       verification.
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * to ensure data consistency during the authentication attempt claiming process.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
   * <p>If {@link NoPendingAuthAttemptException} is thrown (no {@code PENDING} row), the device
   * proof token is not persisted; the client may repeat the same signed {@code deviceProofToken} on
   * later polls until an attempt is claimed.
   *
   * @param request the pending request with enrollment proof token
   * @return the pending authentication response with proof token
   * @throws AuthAttemptRequestFailedException if enrollment proof token is invalid
   * @throws AuthAttemptStateConflictException if the authentication attempt is already processed
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
   * @throws AuthAttemptRequestFailedException if enrollment validation fails
   */
  private Enrollment validateEnrollment(AuthAttemptPendingRequest request) {
    // Find enrollment by proof token instead of ID
    String proofTokenHash = SensitiveDataHasher.sha256Hex(request.getEnrollmentProofToken());

    if (proofTokenHash == null) {
      logger.warn("Missing enrollment proof token");
      throw new AuthAttemptRequestFailedException("Authentication request failed");
    }

    Enrollment enrollment =
        enrollmentRepository
            .findByEnrollmentProofTokenHashAndActive(proofTokenHash, true)
            .orElseThrow(
                () -> {
                  logger.warn("Invalid enrollment proof token provided");
                  return new AuthAttemptRequestFailedException("Authentication request failed");
                });

    // Validate that the provided enrollment ID matches the proof token
    if (!enrollment.getEnrollmentId().equals(request.getEnrollmentId())) {
      logger.warn(
          "Enrollment ID mismatch with proof token for enrollment: {}",
          enrollment.getEnrollmentId());
      throw new AuthAttemptRequestFailedException("Authentication request failed");
    }

    return enrollment;
  }

  /**
   * Validates the device signature and checks device proof token uniqueness against persisted
   * attempts.
   *
   * <p>The signature proves possession of the device key. The uniqueness check consults only hashes
   * already stored on {@link AuthAttempt} rows after a prior successful claim; it does not apply to
   * empty polls (no pending attempt), where nothing is persisted.
   *
   * @param request the pending request containing device signature and proof token
   * @param enrollment the validated enrollment containing device public key
   * @throws AuthAttemptRequestFailedException if signature validation fails
   * @throws AuthAttemptStateConflictException if device public key is missing
   */
  private void validateDeviceSignature(AuthAttemptPendingRequest request, Enrollment enrollment) {
    // Validate device public key
    String devicePublicKey = enrollment.getDevicePublicKey();
    if (devicePublicKey == null) {
      logger.warn("Device public key missing for enrollment: {}", request.getEnrollmentId());
      throw new AuthAttemptStateConflictException("Authentication request failed");
    }

    // Validate signature
    boolean isValid =
        signatureService.validateSignature(
            request.getDeviceProofToken(), request.getDeviceProofTokenSigned(), devicePublicKey);
    if (!isValid) {
      logger.warn("Invalid signature for enrollment: {}", request.getEnrollmentId());
      throw new AuthAttemptRequestFailedException("Authentication request failed");
    }

    // Check device proof token uniqueness
    String deviceProofTokenHash = SensitiveDataHasher.sha256Hex(request.getDeviceProofToken());

    if (deviceProofTokenHash != null
        && authAttemptRepository.existsByDeviceProofTokenHash(deviceProofTokenHash)) {
      logger.warn("Device proof token already used for enrollment: {}", request.getEnrollmentId());
      throw new AuthAttemptRequestFailedException("Authentication request failed");
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
   * @throws AuthAttemptStateConflictException if the attempt is already processed
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
      throw new AuthAttemptStateConflictException("Authentication request failed");
    }

    // Record the device proof token hash to ensure unicity and update status to READ
    String deviceProofTokenHash = SensitiveDataHasher.sha256Hex(request.getDeviceProofToken());
    authAttempt.setDeviceProofTokenHash(deviceProofTokenHash);
    authAttempt.setAuthAttemptStatus(AuthAttemptStatus.READ);
    authAttemptRepository.save(authAttempt);

    return authAttempt;
  }

  /**
   * Builds the pending response with signed proof token and challenge information.
   *
   * <p>The integration signs the canonical payload {@code proofToken|challengeRequired|contextTitle
   * |contextMessage} (NFC-normalized text, UTF-8) so that context and challenge cannot be tampered
   * by a MITM. See {@link AuthAttemptSignaturePayload} and docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md.
   *
   * @param authAttempt the claimed authentication attempt
   * @param enrollment the enrollment containing integration private key
   * @return the complete pending response
   */
  private AuthAttemptPendingResponse buildPendingResponse(
      AuthAttempt authAttempt, Enrollment enrollment) {
    AuthAttemptPendingResponse response = new AuthAttemptPendingResponse();
    response.setAuthAttemptId(authAttempt.getAuthAttemptId());
    response.setCreatedAt(authAttempt.getCreatedAt()); // Required for FK to partitioned table
    response.setAuthAttemptProofToken(authAttempt.getAuthAttemptProofToken());

    // Determine challenge requirements (same logic as response fields)
    boolean challengeRequired =
        authAttempt.getAuthAttemptChallenge() != null
            || Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired());
    response.setAuthAttemptChallengeRequired(challengeRequired);

    // Forward optional contextual authentication fields to the mobile device
    response.setContextTitle(authAttempt.getContextTitle());
    response.setContextMessage(authAttempt.getContextMessage());

    // Sign canonical payload (proofToken|challengeRequired|contextTitle|contextMessage) with NFC
    String payload =
        AuthAttemptSignaturePayload.buildPendingPayload(
            authAttempt.getAuthAttemptProofToken(),
            challengeRequired,
            authAttempt.getContextTitle(),
            authAttempt.getContextMessage());
    // Diagnostic: SHA-256 (hex) of UTF-8 bytes — must match mobile pendingPayload
    // (buildPendingPayload). DEBUG only: avoid noisy INFO on every poll (see
    // docs/PENDING_PAYLOAD_DIAGNOSTIC.md).
    logger.debug(
        "PENDING_PAYLOAD_DIAG enrollmentId={} authAttemptId={} payloadLen={}"
            + " payloadSha256Utf8Hex={}",
        enrollment.getEnrollmentId(),
        authAttempt.getAuthAttemptId(),
        payload.length(),
        sha256HexUtf8(payload));
    String signature =
        signatureService.signIntegrationPayload(payload, enrollment.getIntegrationPrivateKey());
    response.setAuthAttemptProofTokenSignedByIntegration(signature);

    // Same string the client receives in JSON (Base64URL Ed25519) — hash UTF-8 bytes for
    // bit-equality
    // check.
    logger.debug(
        "PENDING_SIGNATURE_DIAG enrollmentId={} authAttemptId={} signatureLen={}"
            + " signatureSha256Utf8Hex={}",
        enrollment.getEnrollmentId(),
        authAttempt.getAuthAttemptId(),
        signature != null ? signature.length() : 0,
        sha256HexUtf8(signature));

    // Exact public key material used for JCA verify (normalized SPKI / cert extraction).
    String normalizedPublicKey =
        signatureService.normalizeIntegrationPublicKeyToBase64(
            enrollment.getIntegrationPublicKey());
    logger.debug(
        "PENDING_INTEGRATION_PUBLIC_KEY_DIAG enrollmentId={} authAttemptId={} keyLen={}"
            + " integrationPublicKeySha256Utf8Hex={}",
        enrollment.getEnrollmentId(),
        authAttempt.getAuthAttemptId(),
        normalizedPublicKey != null ? normalizedPublicKey.length() : 0,
        sha256HexUtf8(normalizedPublicKey));

    // Same verification path as mobile (Ed25519). If this fails, the mobile app will also fail.
    if (!signatureService.verifyIntegrationSignature(payload, signature, normalizedPublicKey)) {
      logger.error(
          "Pending integration signature failed self-verification (Ed25519). "
              + "enrollmentId={} authAttemptId={}",
          enrollment.getEnrollmentId(),
          authAttempt.getAuthAttemptId());
    }

    return response;
  }

  /** SHA-256 over UTF-8 bytes, lowercase hex — same convention as mobile {@code sha256HexUtf8}. */
  private static String sha256HexUtf8(String input) {
    if (input == null) {
      return "";
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return "sha256_error";
    }
  }
}
