/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentVerifyService
 * Description: Specialized service for handling enrollment verification operations.
 */

package org.ezkey.enrollment.service;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyStateConflictException;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.service.EntityEligibilityService;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for handling enrollment verification operations.
 *
 * <p>This service manages the enrollment verification process where a mobile device completes the
 * enrollment by providing cryptographic proof of device ownership. It implements comprehensive
 * security validation to prevent replay attacks and ensure enrollment integrity.
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li><b>Signature Validation:</b> Verifies device cryptographic signature
 *   <li><b>Device Key Uniqueness:</b> Prevents replay attacks using same public key
 *   <li><b>Challenge Verification:</b> Validates enrollment challenge response
 *   <li><b>State Consistency:</b> Ensures enrollment state integrity
 *   <li><b>Atomic Operations:</b> Uses row-level locking for thread safety
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * to ensure data consistency during the enrollment verification process.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentVerifyRequest
 * @see EnrollmentVerifyResponse
 * @see Enrollment
 * @see SignatureService
 */
@Service
@Transactional
public class EnrollmentVerifyService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentVerifyService.class);

  private final EnrollmentRepository enrollmentRepository;
  private final EzkeyAdminRepository ezkeyAdminRepository;
  private final EntityEligibilityService eligibilityService;
  private final SignatureService signatureService;
  private final EnrollmentTxHelper enrollmentTxHelper;

  /**
   * Constructs the verify service with required dependencies.
   *
   * @param enrollmentRepository the JPA repository for enrollment operations
   * @param ezkeyAdminRepository the admin repository for admin-linked enrollment checks
   * @param eligibilityService centralized eligibility checks for admin-linked enrollments
   * @param signatureService the signature service for cryptographic operations
   * @param enrollmentTxHelper the transactional helper for marking expired and emitting audit in a
   *     separate transaction
   */
  public EnrollmentVerifyService(
      EnrollmentRepository enrollmentRepository,
      EzkeyAdminRepository ezkeyAdminRepository,
      EntityEligibilityService eligibilityService,
      SignatureService signatureService,
      EnrollmentTxHelper enrollmentTxHelper) {
    this.enrollmentRepository = enrollmentRepository;
    this.ezkeyAdminRepository = ezkeyAdminRepository;
    this.eligibilityService = eligibilityService;
    this.signatureService = signatureService;
    this.enrollmentTxHelper = enrollmentTxHelper;
  }

  /**
   * Verifies an enrollment with comprehensive security validation.
   *
   * <p>This method handles the enrollment confirmation process with multiple layers of security
   * validation including signature verification, device public key uniqueness validation, and
   * challenge verification. It implements the same security principles as AuthAttemptService to
   * prevent replay attacks and ensure enrollment integrity.
   *
   * @param request the verify request containing device keys and signatures
   * @return the verify response confirming successful enrollment
   * @throws EnrollmentVerifyFailedException if validation fails (signature, uniqueness, or
   *     challenge)
   * @throws EnrollmentVerifyStateConflictException if enrollment is in invalid state or already
   *     processed
   */
  public EnrollmentVerifyResponse verify(EnrollmentVerifyRequest request) {
    logger.info(
        "Starting enrollment verify process for enrollment ID: {}", request.getEnrollmentId());

    // Step 1: Validate enrollment state
    Enrollment enrollment = validateEnrollmentState(request);

    // Step 2: Validate signature
    validateSignature(request, enrollment);

    // Step 3: Validate device public key uniqueness
    validateDeviceKeyUniqueness(request);

    // Step 4: Validate challenge response
    validateChallengeResponse(request, enrollment);

    // Step 5: Acquire lock and final validation
    Enrollment lockedEnrollment = acquireLockAndValidate(request, enrollment);

    // Step 6: Validate uniqueness - check for existing VERIFIED enrollment
    validateUniqueness(lockedEnrollment);

    // Step 7: Mark as verified and activate
    markAsVerified(lockedEnrollment, request);

    // Step 7: Build and return response
    return buildVerifyResponse(lockedEnrollment);
  }

  /**
   * Validates the enrollment state and ensures it's ready for verification.
   *
   * <p>This method performs read-only pre-checks to ensure the enrollment exists and is in the
   * correct state for verification.
   *
   * @param request the verify request
   * @return the validated enrollment
   * @throws EnrollmentVerifyStateConflictException if enrollment is not found or in invalid state
   * @throws EnrollmentVerifyFailedException if invitation expired
   */
  private Enrollment validateEnrollmentState(EnrollmentVerifyRequest request) {
    logger.debug(
        "Step 1: Performing read-only pre-checks for enrollment ID: {}", request.getEnrollmentId());

    Enrollment enrollment =
        enrollmentRepository
            .findById(request.getEnrollmentId())
            .orElseThrow(
                () -> {
                  logger.warn(
                      "Validation failed: Enrollment not found for ID: {}",
                      request.getEnrollmentId());
                  return new EnrollmentVerifyStateConflictException(
                      "Enrollment not available for verification");
                });

    logger.debug(
        "Enrollment found: ID={}, Status={}", enrollment.getEnrollmentId(), enrollment.getStatus());

    if (enrollment.getStatus() == EnrollmentStatus.VERIFIED) {
      logger.warn(
          "Validation failed: Enrollment already verified - ID: {}, Status: {}",
          enrollment.getEnrollmentId(),
          enrollment.getStatus());
      throw new EnrollmentVerifyStateConflictException("Enrollment verification failed");
    }

    logger.debug("Enrollment status validation passed: Status is not VERIFIED");

    if (enrollment.getStatus() != EnrollmentStatus.BOUND) {
      String message = resolveNotBoundMessage(enrollment.getStatus());
      logger.warn(
          "Validation failed: Enrollment not in BOUND state - ID: {}, Status: {}",
          enrollment.getEnrollmentId(),
          enrollment.getStatus());
      enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
      throw new EnrollmentVerifyStateConflictException(message);
    }

    // Reject if pending enrollment has expired (expires_at in the past)
    OffsetDateTime now = OffsetDateTime.now();
    if (enrollment.isExpired(now)) {
      logger.warn(
          "Validation failed: Enrollment invitation expired - ID: {}, expiresAt: {}",
          enrollment.getEnrollmentId(),
          enrollment.getExpiresAt());
      try {
        enrollmentTxHelper.markExpiredAndEmitAudit(
            enrollment.getEnrollmentId(),
            enrollment.getIntegrationId(),
            enrollment.getExpiresAt(),
            "enrollment_expired_verify_rejected");
      } catch (DataAccessException | IllegalArgumentException | IllegalStateException e) {
        logger.warn(
            "Failed to mark enrollment {} as EXPIRED and emit audit (client will still get 400):"
                + " {}",
            enrollment.getEnrollmentId(),
            e.getMessage());
      }
      throw new EnrollmentVerifyFailedException("Enrollment invitation has expired");
    }

    validateAdminLinkedEnrollmentEligibility(enrollment);

    logger.debug("Enrollment status validation passed: Status is BOUND");
    return enrollment;
  }

  private void validateAdminLinkedEnrollmentEligibility(Enrollment enrollment) {
    ezkeyAdminRepository
        .findByEnrollmentId(enrollment.getEnrollmentId())
        .ifPresent(
            admin -> {
              String ineligibilityReason =
                  eligibilityService.adminLinkedEnrollmentIneligibilityReason(admin);
              if (ineligibilityReason != null) {
                logger.warn(
                    "Validation failed: Admin-linked enrollment is not eligible for verify -"
                        + " enrollmentId: {}, adminId: {}, lifecycleStatus: {}, active: {},"
                        + " reason: {}",
                    enrollment.getEnrollmentId(),
                    admin.getAdminId(),
                    admin.getLifecycleStatus(),
                    admin.getActive(),
                    ineligibilityReason);
                throw new EnrollmentVerifyFailedException(
                    "Enrollment verification failed: " + ineligibilityReason);
              }
            });
  }

  /**
   * Validates the device signature for the enrollment.
   *
   * <p>This method ensures that the device signature is valid and corresponds to the enrollment
   * proof token.
   *
   * @param request the verify request containing device signature
   * @param enrollment the enrollment containing proof token
   * @throws EnrollmentVerifyFailedException if signature validation fails
   */
  private void validateSignature(EnrollmentVerifyRequest request, Enrollment enrollment) {
    logger.debug("Step 2: Validating signature for enrollment ID: {}", request.getEnrollmentId());

    String signedPayload =
        EnrollmentSignaturePayload.buildVerifyDevicePayload(
            enrollment.getEnrollmentProofToken(),
            request.getEnrollmentId(),
            request.getChallengeResponse(),
            request.getDevicePublicKey());
    boolean valid =
        signatureService.validateSignature(
            signedPayload, request.getEnrollmentProofTokenSigned(), request.getDevicePublicKey());

    if (!valid) {
      logger.warn(
          "Validation failed: Invalid bind proof token signature for enrollment ID: {}",
          request.getEnrollmentId());
      enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
      throw new EnrollmentVerifyFailedException("Invalid bind proof token signature");
    }

    logger.debug("Signature validation passed for enrollment ID: {}", request.getEnrollmentId());
  }

  /**
   * Validates the device public key uniqueness to prevent replay attacks.
   *
   * <p>This method ensures that the device public key has not been used for any other verified
   * enrollment to prevent enrollment hijacking. Uses SHA-256 hash for validation to ensure
   * uniqueness independent of encryption format.
   *
   * @param request the verify request containing device public key
   * @throws EnrollmentVerifyFailedException if device public key is already used
   */
  private void validateDeviceKeyUniqueness(EnrollmentVerifyRequest request) {
    logger.debug(
        "Step 3: Validating device public key uniqueness for enrollment ID: {}",
        request.getEnrollmentId());

    // Calculate hash of device public key for uniqueness validation
    String devicePublicKeyHash = SensitiveDataHasher.sha256Hex(request.getDevicePublicKey());

    if (enrollmentRepository.existsByDevicePublicKeyHash(devicePublicKeyHash)) {
      logger.warn(
          "Validation failed: Device public key already used for verified enrollment - ID: {},"
              + " DevicePublicKeyHash: {}",
          request.getEnrollmentId(),
          devicePublicKeyHash);
      enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
      throw new EnrollmentVerifyFailedException("Enrollment verification failed");
    }

    logger.debug(
        "Device public key uniqueness validation passed for enrollment ID: {}",
        request.getEnrollmentId());
  }

  /**
   * Validates the challenge response for the enrollment.
   *
   * <p>This method ensures that the challenge response matches the expected value to complete the
   * enrollment verification process.
   *
   * @param request the verify request containing challenge response
   * @param enrollment the enrollment containing expected challenge
   * @throws EnrollmentVerifyFailedException if challenge response is invalid
   */
  private void validateChallengeResponse(EnrollmentVerifyRequest request, Enrollment enrollment) {
    logger.debug(
        "Step 4: Validating challenge response for enrollment ID: {}", request.getEnrollmentId());

    if (!request.getChallengeResponse().equals(enrollment.getEnrollmentChallenge())) {
      logger.warn(
          "Validation failed: Invalid challenge response for enrollment ID: {} - Expected: {},"
              + " Received: {}",
          request.getEnrollmentId(),
          enrollment.getEnrollmentChallenge(),
          request.getChallengeResponse());
      enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
      throw new EnrollmentVerifyFailedException("Invalid challenge response");
    }

    logger.debug(
        "Challenge response validation passed for enrollment ID: {}", request.getEnrollmentId());
  }

  /**
   * Acquires a lock on the enrollment and performs final validation.
   *
   * <p>This method implements the read-once guarantee by atomically locking and validating the
   * enrollment state.
   *
   * @param request the verify request
   * @param snapshot the enrollment snapshot from pre-checks
   * @return the locked enrollment
   * @throws EnrollmentVerifyStateConflictException if enrollment is not found or in invalid state
   */
  private Enrollment acquireLockAndValidate(EnrollmentVerifyRequest request, Enrollment snapshot) {
    logger.debug("Step 5: Acquiring lock for enrollment ID: {}", request.getEnrollmentId());

    Enrollment enrollment =
        enrollmentRepository.findAndLockBoundById(request.getEnrollmentId()).orElse(null);

    if (enrollment == null) {
      logger.warn(
          "Validation failed: Enrollment not found or already verified after lock acquisition for"
              + " ID: {}",
          request.getEnrollmentId());
      throw new EnrollmentVerifyStateConflictException("Enrollment already verified");
    }

    logger.debug("Lock acquired successfully for enrollment ID: {}", enrollment.getEnrollmentId());

    if (enrollment.getStatus() != EnrollmentStatus.BOUND) {
      String message = resolveNotBoundMessage(enrollment.getStatus());
      logger.warn(
          "Validation failed: Enrollment not in BOUND state after lock - ID: {}, Status: {}",
          enrollment.getEnrollmentId(),
          enrollment.getStatus());
      enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
      throw new EnrollmentVerifyStateConflictException(message);
    }

    logger.debug("Post-lock status validation passed: Status is BOUND");

    // Guard against state changes between snapshot and lock
    logger.debug(
        "Step 6: Validating state consistency between snapshot and locked enrollment for ID: {}",
        request.getEnrollmentId());
    if (!snapshot.getEnrollmentProofToken().equals(enrollment.getEnrollmentProofToken())
        || !snapshot.getEnrollmentChallenge().equals(enrollment.getEnrollmentChallenge())) {
      logger.warn(
          "Validation failed: Enrollment state changed between snapshot and lock - ID: {}",
          request.getEnrollmentId());
      enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
      throw new EnrollmentVerifyStateConflictException("Enrollment state changed");
    }

    logger.debug(
        "State consistency validation passed for enrollment ID: {}", request.getEnrollmentId());
    return enrollment;
  }

  /**
   * Validates that no other VERIFIED enrollment exists with the same integration and name.
   *
   * <p>This method ensures uniqueness constraint at the application level before database
   * constraint violations occur. It provides clear error messages directing users to the recovery
   * process if a duplicate VERIFIED enrollment exists.
   *
   * @param enrollment the enrollment being verified
   * @throws EnrollmentVerifyStateConflictException if a VERIFIED enrollment already exists with the
   *     same integration and name
   */
  private void validateUniqueness(Enrollment enrollment) {
    logger.debug(
        "Step 6: Validating uniqueness for enrollment ID: {}", enrollment.getEnrollmentId());

    // Check for existing VERIFIED enrollments with same integration and name
    List<Enrollment> existingVerifiedEnrollments =
        enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
            enrollment.getIntegrationId(),
            enrollment.getEnrollmentName(),
            EnrollmentStatus.VERIFIED,
            enrollment.getEnrollmentId());

    if (!existingVerifiedEnrollments.isEmpty()) {
      Enrollment existing = existingVerifiedEnrollments.get(0);
      logger.warn(
          "Enrollment verification rejected: VERIFIED enrollment {} (ID: {}) already exists for"
              + " integration {} and name '{}'. Use recovery process (/api/v1/admin/auth/recover +"
              + " /api/v1/admin/enrollments/reset) to replace enrollment.",
          existing.getEnrollmentName(),
          existing.getEnrollmentId(),
          enrollment.getIntegrationId(),
          enrollment.getEnrollmentName());
      throw new EnrollmentVerifyStateConflictException(
          "A verified enrollment with the same name already exists for this integration. To replace"
              + " an enrollment, use the recovery process: POST /api/v1/admin/auth/recover with a"
              + " recovery code, then POST /api/v1/admin/enrollments/reset to reset the existing"
              + " enrollment.");
    }

    logger.debug(
        "Uniqueness validation passed for enrollment ID: {}", enrollment.getEnrollmentId());
  }

  /**
   * Marks the enrollment as verified and activates it.
   *
   * <p>This method updates the enrollment status to VERIFIED and sets it as active, completing the
   * enrollment process. Also calculates and stores the SHA-256 hash of the device public key for
   * uniqueness validation.
   *
   * @param enrollment the enrollment to mark as verified
   * @param request the verify request containing device public key
   */
  private void markAsVerified(Enrollment enrollment, EnrollmentVerifyRequest request) {
    logger.info("Step 7: Marking enrollment as VERIFIED - ID: {}", enrollment.getEnrollmentId());

    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);
    enrollment.setVerifiedAt(OffsetDateTime.now());
    enrollment.setDevicePublicKey(request.getDevicePublicKey());

    // Calculate and store SHA-256 hash of device public key for uniqueness
    // validation
    String devicePublicKeyHash = SensitiveDataHasher.sha256Hex(request.getDevicePublicKey());
    enrollment.setDevicePublicKeyHash(devicePublicKeyHash);
    enrollment.setDevicePrivateKeyStorageTier(request.getDevicePrivateKeyStorageTier());

    enrollmentRepository.saveAndFlush(enrollment);

    logger.info(
        "Enrollment successfully verified - ID: {}, Status: VERIFIED, Active: true,"
            + " DevicePublicKeyHash: {}",
        enrollment.getEnrollmentId(),
        devicePublicKeyHash);
  }

  /**
   * Resolves the user-facing error message when enrollment is not in BOUND state.
   *
   * <p>Differentiates between CREATED (never bound), INVALID (invalidated by failed verification),
   * REVOKED, and EXPIRED so the client receives an accurate message.
   *
   * @param status the current enrollment status
   * @return the appropriate error message for the status
   */
  private static String resolveNotBoundMessage(EnrollmentStatus status) {
    return switch (status) {
      case CREATED -> "Enrollment must be bound before verification";
      case INVALID ->
          "Enrollment verification failed. The enrollment was invalidated due to a previous failed"
              + " verification attempt.";
      case REVOKED -> "Enrollment has been revoked and cannot be verified.";
      case EXPIRED -> "Enrollment invitation has expired.";
      default -> "Enrollment is not available for verification";
    };
  }

  /**
   * Builds the verification response.
   *
   * <p>This method creates a response confirming that the enrollment has been successfully verified
   * and activated.
   *
   * @return the verification response
   */
  private EnrollmentVerifyResponse buildVerifyResponse(Enrollment enrollment) {
    logger.debug("Step 8: Building verify response");

    String message = EnrollmentSignaturePayload.defaultVerifySuccessMessage();
    String resultPayload =
        EnrollmentSignaturePayload.buildVerifyResultPayload(
            enrollment.getEnrollmentProofToken(),
            enrollment.getEnrollmentId(),
            EnrollmentSignaturePayload.EnrollmentVerificationOutcome.VERIFIED,
            message);
    String resultSignature =
        signatureService.signIntegrationPayload(
            resultPayload, enrollment.getIntegrationPrivateKey());
    String normalizedIntegrationPublicKey =
        signatureService.normalizeIntegrationPublicKeyToBase64(
            enrollment.getIntegrationPublicKey());
    if (!signatureService.verifyIntegrationSignature(
        resultPayload, resultSignature, normalizedIntegrationPublicKey)) {
      logger.error(
          "Verify response integration signature self-verification failed for enrollment ID: {}",
          enrollment.getEnrollmentId());
    }

    EnrollmentVerifyResponse response = new EnrollmentVerifyResponse();
    response.setActive(true);
    response.setEnrollmentVerifyMessage(message);
    response.setEnrollmentVerifyPayloadSignedByIntegration(resultSignature);

    logger.info("Enrollment verify process completed successfully");
    return response;
  }
}
