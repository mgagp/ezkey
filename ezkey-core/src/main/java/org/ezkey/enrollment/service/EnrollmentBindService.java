/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentBindService
 * Description: Specialized service for handling enrollment binding operations.
 */

package org.ezkey.enrollment.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.EnrollmentAlreadyBoundException;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.ezkey.exception.auth.EnrollmentIntegrationNotFoundException;
import org.ezkey.exception.auth.EnrollmentInvitationExpiredException;
import org.ezkey.exception.auth.EnrollmentNotAvailableAfterLockException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.ezkey.service.EntityEligibilityService;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for handling enrollment binding operations.
 *
 * <p>This service manages the enrollment binding process where a mobile device establishes a secure
 * connection with an integration. It implements the read-once guarantee security principle and
 * validates enrollment proof tokens.
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li><b>Read-Once Guarantee:</b> Each enrollment can only be bound once
 *   <li><b>Proof Token Validation:</b> Prevents unauthorized enrollment access
 *   <li><b>State Validation:</b> Ensures enrollment is in correct state for binding
 *   <li><b>Atomic Operations:</b> Uses row-level locking for thread safety
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * to ensure data consistency during the enrollment binding process.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentBindRequest
 * @see EnrollmentBindResponse
 * @see Enrollment
 * @see Integration
 */
@Service
@Transactional
public class EnrollmentBindService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentBindService.class);

  private final EnrollmentRepository enrollmentRepository;
  private final IntegrationRepository integrationRepository;
  private final EzkeyAdminRepository ezkeyAdminRepository;
  private final EntityEligibilityService eligibilityService;
  private final EnrollmentTxHelper enrollmentTxHelper;
  private final SignatureService signatureService;

  /**
   * Constructs the bind service with required dependencies.
   *
   * @param enrollmentRepository the JPA repository for enrollment operations
   * @param integrationRepository the JPA repository for integration operations
   * @param ezkeyAdminRepository the JPA repository for admin lookup (admin MFA enrollments)
   * @param eligibilityService centralized eligibility checks for admin-linked enrollments
   * @param enrollmentTxHelper the transactional helper for marking expired and emitting audit in a
   *     separate transaction
   * @param signatureService the signature service for normalizing integration public key to
   *     SubjectPublicKeyInfo (so bind response matches what demo device and mobile expect)
   */
  public EnrollmentBindService(
      EnrollmentRepository enrollmentRepository,
      IntegrationRepository integrationRepository,
      EzkeyAdminRepository ezkeyAdminRepository,
      EntityEligibilityService eligibilityService,
      EnrollmentTxHelper enrollmentTxHelper,
      SignatureService signatureService) {
    this.enrollmentRepository = enrollmentRepository;
    this.integrationRepository = integrationRepository;
    this.ezkeyAdminRepository = ezkeyAdminRepository;
    this.eligibilityService = eligibilityService;
    this.enrollmentTxHelper = enrollmentTxHelper;
    this.signatureService = signatureService;
  }

  /**
   * Binds an enrollment to a device.
   *
   * <p>This method validates the request completely before locking the enrollment to ensure the
   * read-once guarantee is maintained. The method follows the security principle of validation
   * before modification to prevent transaction rollbacks that could compromise the read-once
   * guarantee.
   *
   * @param request the bind request
   * @return the bind response
   * @throws EnrollmentBindingFailedException if validation fails (secure messages; not exposed to
   *     clients verbatim)
   * @throws EnrollmentAlreadyBoundException if the enrollment is already processed
   */
  public EnrollmentBindResponse bind(EnrollmentBindRequest request) {
    logger.info(
        "Starting enrollment bind process for enrollment ID: {}", request.getEnrollmentId());

    // Step 1: Validate enrollment and proof token
    Enrollment enrollment = validateEnrollment(request);

    // Step 2: Load integration
    Integration integration = loadIntegration(enrollment.getIntegrationId());
    String integrationName = integration.getName();
    String integrationDescription = integration.getDescription();

    // Step 3: Acquire lock and validate state
    Enrollment lockedEnrollment = acquireLockAndValidate(request);

    // Step 4: Mark as BOUND (read-once guarantee)
    markAsBound(lockedEnrollment);

    // Step 5: Build and return response (uses locked row for integration signing material)
    return buildBindResponse(
        lockedEnrollment, integration, integrationName, integrationDescription);
  }

  /**
   * Validates the enrollment using the proof token and enrollment ID.
   *
   * <p>This method ensures that the enrollment exists, is active, and that the provided enrollment
   * ID matches the proof token to prevent unauthorized access.
   *
   * @param request the bind request containing enrollment proof token and ID
   * @return the validated enrollment
   * @throws EnrollmentBindingFailedException if enrollment validation fails
   * @throws EnrollmentAlreadyBoundException if enrollment is not in CREATED state
   * @throws EnrollmentInvitationExpiredException if the invitation has expired
   */
  private Enrollment validateEnrollment(EnrollmentBindRequest request) {
    logger.debug(
        "Step 1: Performing read-only pre-checks with proof token validation for enrollment ID: {}",
        request.getEnrollmentId());

    String proofTokenHash = SensitiveDataHasher.sha256Hex(request.getEnrollmentProofToken());

    if (proofTokenHash == null) {
      logger.warn(
          "Validation failed: Missing or blank enrollment proof token for ID: {}",
          request.getEnrollmentId());
      throw new EnrollmentBindingFailedException("Enrollment binding failed");
    }

    Enrollment enrollment =
        enrollmentRepository
            .findByEnrollmentIdAndEnrollmentProofTokenHash(
                request.getEnrollmentId(), proofTokenHash)
            .orElse(null);

    if (enrollment == null) {
      logger.warn(
          "Validation failed: Enrollment not found or invalid proof token for ID: {}",
          request.getEnrollmentId());
      throw new EnrollmentBindingFailedException("Enrollment binding failed");
    }

    logger.debug(
        "Enrollment found with valid proof token: ID={}, Status={}, IntegrationId={}",
        enrollment.getEnrollmentId(),
        enrollment.getStatus(),
        enrollment.getIntegrationId());

    // Short-circuit: if already processed, avoid acquiring a lock
    if (enrollment.getStatus() != EnrollmentStatus.CREATED) {
      logger.warn(
          "Validation failed: Enrollment already processed - ID: {}, Status: {}",
          enrollment.getEnrollmentId(),
          enrollment.getStatus());
      throw new EnrollmentAlreadyBoundException("Enrollment already bound by a device");
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
            "enrollment_expired_bind_rejected");
      } catch (Exception e) {
        logger.warn(
            "Failed to mark enrollment {} as EXPIRED and emit audit (client will still get 400):"
                + " {}",
            enrollment.getEnrollmentId(),
            e.getMessage());
      }
      throw new EnrollmentInvitationExpiredException("Enrollment invitation has expired");
    }

    validateAdminLinkedEnrollmentEligibility(enrollment);

    logger.debug("Enrollment status validation passed: Status is CREATED");
    return enrollment;
  }

  private void validateAdminLinkedEnrollmentEligibility(Enrollment enrollment) {
    ezkeyAdminRepository
        .findByEnrollmentId(enrollment.getEnrollmentId())
        .ifPresent(
            admin -> {
              if (!eligibilityService.isAdminLinkedEnrollmentEligible(admin)) {
                logger.warn(
                    "Validation failed: Admin-linked enrollment is not eligible for bind -"
                        + " enrollmentId: {}, adminId: {}, lifecycleStatus: {}, active: {}",
                    enrollment.getEnrollmentId(),
                    admin.getAdminId(),
                    admin.getLifecycleStatus(),
                    admin.getActive());
                throw new EnrollmentBindingFailedException("Enrollment binding failed");
              }
            });
  }

  /**
   * Loads the integration for the enrollment.
   *
   * @param integrationId the integration ID
   * @return the integration entity
   * @throws EnrollmentIntegrationNotFoundException if integration is not found
   */
  private Integration loadIntegration(Integer integrationId) {
    logger.debug("Step 2: Loading integration for ID: {}", integrationId);

    Optional<Integration> integrationOpt = integrationRepository.findById(integrationId);
    if (integrationOpt.isEmpty()) {
      logger.warn("Validation failed: Integration not found for integration ID: {}", integrationId);
      throw new EnrollmentIntegrationNotFoundException("Integration not found for enrollment");
    }

    Integration integration = integrationOpt.get();
    logger.debug("Integration loaded successfully: ID={}", integration.getId());
    return integration;
  }

  /**
   * Acquires a lock on the enrollment and validates its state.
   *
   * <p>This method implements the read-once guarantee by atomically locking and validating the
   * enrollment state.
   *
   * @param request the bind request
   * @return the locked enrollment
   * @throws EnrollmentNotAvailableAfterLockException if enrollment is not found or already bound
   * @throws EnrollmentBindingFailedException if proof token mismatch after lock
   * @throws EnrollmentAlreadyBoundException if enrollment is in invalid state
   */
  private Enrollment acquireLockAndValidate(EnrollmentBindRequest request) {
    logger.debug("Step 4: Acquiring lock for enrollment ID: {}", request.getEnrollmentId());

    Enrollment enrollment =
        enrollmentRepository.findAndLockUnreadById(request.getEnrollmentId()).orElse(null);

    if (enrollment == null) {
      logger.warn(
          "Validation failed: Enrollment not found or already bound after lock acquisition for ID:"
              + " {}",
          request.getEnrollmentId());
      throw new EnrollmentNotAvailableAfterLockException("Enrollment not found or already bound");
    }

    // Additional validation: ensure the proof token still matches after lock
    if (!enrollment.getEnrollmentProofToken().equals(request.getEnrollmentProofToken())) {
      logger.warn(
          "Validation failed: Proof token mismatch after lock acquisition for ID: {}",
          request.getEnrollmentId());
      throw new EnrollmentBindingFailedException("Enrollment binding failed");
    }

    logger.debug("Lock acquired successfully for enrollment ID: {}", enrollment.getEnrollmentId());

    if (enrollment.getStatus() != EnrollmentStatus.CREATED) {
      logger.warn(
          "Validation failed: Enrollment already processed after lock - ID: {}, Status: {}",
          enrollment.getEnrollmentId(),
          enrollment.getStatus());
      throw new EnrollmentAlreadyBoundException("Enrollment already bound by a device");
    }

    logger.debug("Post-lock status validation passed: Status is CREATED");
    return enrollment;
  }

  /**
   * Marks the enrollment as BOUND to implement the read-once guarantee.
   *
   * <p>This method updates the enrollment status to BOUND, ensuring that the enrollment can only be
   * bound once.
   *
   * @param enrollment the enrollment to mark as bound
   */
  private void markAsBound(Enrollment enrollment) {
    logger.info("Step 5: Marking enrollment as BOUND - ID: {}", enrollment.getEnrollmentId());
    enrollment.setStatus(EnrollmentStatus.BOUND);
    enrollmentRepository.save(enrollment);
    logger.info(
        "Enrollment successfully bound - ID: {}, Status: BOUND", enrollment.getEnrollmentId());
  }

  /**
   * Builds the bind response with integration information.
   *
   * <p>This method creates a complete response containing the enrollment data, integration
   * information, and proof token for the mobile device.
   *
   * @param enrollment the enrollment data
   * @param integration the integration data
   * @param integrationName the resolved integration name
   * @param integrationDescription the resolved integration description
   * @return the complete bind response
   */
  private EnrollmentBindResponse buildBindResponse(
      Enrollment enrollment,
      Integration integration,
      String integrationName,
      String integrationDescription) {
    logger.debug(
        "Step 5: Building bind response for enrollment ID: {}", enrollment.getEnrollmentId());

    EnrollmentBindResponse response = new EnrollmentBindResponse();
    response.setEnrollmentId(enrollment.getEnrollmentId());
    response.setEnrollmentName(enrollment.getEnrollmentName());
    String normalizedIntegrationPublicKey =
        signatureService.normalizeIntegrationPublicKeyToBase64(
            enrollment.getIntegrationPublicKey());
    response.setIntegrationPublicKey(normalizedIntegrationPublicKey);
    response.setIntegrationKeyAlgorithm("ed25519");
    response.setEnrollmentProofToken(enrollment.getEnrollmentProofToken());
    response.setIntegrationName(integrationName);
    response.setIntegrationDescription(integrationDescription);

    // Tenant resolution: use scalar queries only to avoid loading Tenant entity and its
    // administrators collection (prevents "Found shared references to collection:
    // Tenant.administrators").
    // - For system integrations (administrator enrollment): try admin's tenant first; if empty
    //   (global admin),
    //   use integration's tenant via scalar query.
    // - For non-system integrations: use integration's tenant via scalar query.
    if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
      Optional<Object[]> tenantInfo =
          ezkeyAdminRepository.findTenantInfoByAdminEnrollmentId(enrollment.getEnrollmentId());
      if (tenantInfo.isPresent()) {
        applyTenantInfoFromRow(response, tenantInfo.get());
      } else {
        // Global admin (no tenant): use integration's tenant (system tenant) via scalar query
        integrationRepository
            .findTenantInfoByIntegrationId(integration.getId())
            .ifPresent(row -> applyTenantInfoFromRow(response, row));
      }
    } else {
      integrationRepository
          .findTenantInfoByIntegrationId(integration.getId())
          .ifPresent(row -> applyTenantInfoFromRow(response, row));
    }

    String bindPayload =
        EnrollmentSignaturePayload.buildBindPayload(
            response.getEnrollmentProofToken(),
            response.getEnrollmentId(),
            response.getIntegrationPublicKey(),
            response.getIntegrationKeyAlgorithm(),
            response.getIntegrationName(),
            response.getIntegrationDescription(),
            response.getEnrollmentName(),
            response.getTenantId(),
            response.getTenantName(),
            response.getTenantDescription());
    String bindSignature =
        signatureService.signIntegrationPayload(bindPayload, enrollment.getIntegrationPrivateKey());
    response.setEnrollmentBindPayloadSignedByIntegration(bindSignature);
    if (!signatureService.verifyIntegrationSignature(
        bindPayload, bindSignature, response.getIntegrationPublicKey())) {
      logger.error(
          "Bind response integration signature self-verification failed for enrollment ID: {}",
          enrollment.getEnrollmentId());
    }

    logger.info(
        "Enrollment bind process completed successfully for ID: {}", enrollment.getEnrollmentId());
    return response;
  }

  /**
   * Applies tenant info from a native query row [tenantId, tenantName, tenantDescription].
   *
   * <p>Uses safe extraction for tenantId since PostgreSQL JDBC may return Integer, Long, or
   * BigDecimal depending on column type and driver version.
   */
  private void applyTenantInfoFromRow(EnrollmentBindResponse response, Object[] row) {
    if (row == null || row.length == 0) {
      return;
    }
    Object tenantIdVal = row[0];
    Integer tenantId = tenantIdVal instanceof Number n ? n.intValue() : null;
    response.setTenantId(tenantId);
    response.setTenantName(row.length > 1 ? (String) row[1] : null);
    response.setTenantDescription(row.length > 2 ? (String) row[2] : null);
  }
}
