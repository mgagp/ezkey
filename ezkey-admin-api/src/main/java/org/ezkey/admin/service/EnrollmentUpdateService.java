/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentUpdateService
 * Description: Business logic for partial update of enrollment metadata (PATCH).
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.dto.request.EnrollmentUpdateRequestDto;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for partial update of enrollment metadata.
 *
 * <p>Implements PATCH semantics for enrollment metadata: enrollmentName, contactEmail, expiresAt,
 * authAttemptChallengeRequired. Only active, non-revoked VERIFIED enrollments can be updated.
 *
 * <p><b>Constraints:</b>
 *
 * <ul>
 *   <li>Enrollment must be VERIFIED and active (not revoked, not deactivated)
 *   <li>enrollmentName: must be unique per (integration_id, name) for VERIFIED status
 *   <li>expiresAt: must be in the future if provided; null = no expiration
 * </ul>
 *
 * <p><b>Optimistic Locking:</b> Uses {@code version} from the request to detect concurrent
 * modifications. On mismatch, throws {@link ObjectOptimisticLockingFailureException} (409
 * Conflict).
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class EnrollmentUpdateService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentUpdateService.class);

  private final EnrollmentRepository enrollmentRepository;

  /**
   * Constructs the EnrollmentUpdateService with required dependencies.
   *
   * @param enrollmentRepository repository for enrollment persistence
   */
  public EnrollmentUpdateService(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  /**
   * Partially updates an enrollment's metadata.
   *
   * <p>Only non-null fields in the request are applied. The enrollment must be VERIFIED and active.
   *
   * @param enrollmentId the enrollment ID to update
   * @param request the partial update request
   * @return the updated enrollment entity
   * @throws ResourceNotFoundException if enrollment not found
   * @throws IllegalArgumentException if enrollment is not updatable (revoked, inactive) or
   *     validation fails (name uniqueness, expiresAt in past)
   * @throws ObjectOptimisticLockingFailureException if version mismatch (stale)
   */
  @Transactional
  public Enrollment updateEnrollment(Integer enrollmentId, EnrollmentUpdateRequestDto request) {
    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

    // Only active, non-revoked VERIFIED enrollments can be updated
    if (enrollment.getStatus() != EnrollmentStatus.VERIFIED) {
      throw new IllegalArgumentException(
          "Enrollment cannot be updated: status is "
              + enrollment.getStatus()
              + ". Only VERIFIED enrollments can be updated.");
    }
    if (!Boolean.TRUE.equals(enrollment.getActive())) {
      throw new IllegalArgumentException(
          "Enrollment cannot be updated: enrollment is inactive. Reactivate before updating.");
    }
    if (enrollment.getRevokedAt() != null) {
      throw new IllegalArgumentException(
          "Enrollment cannot be updated: enrollment has been permanently revoked.");
    }

    // Optimistic lock check
    if (request.version() != null && !request.version().equals(enrollment.getVersion())) {
      throw new ObjectOptimisticLockingFailureException(Enrollment.class, enrollmentId);
    }

    // Validate and apply enrollmentName (uniqueness per integration for VERIFIED)
    if (request.enrollmentName() != null) {
      List<Enrollment> existing =
          enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
              enrollment.getIntegrationId(),
              request.enrollmentName(),
              EnrollmentStatus.VERIFIED,
              enrollmentId);
      if (!existing.isEmpty()) {
        throw new IllegalArgumentException(
            "Enrollment name '"
                + request.enrollmentName()
                + "' already exists for this integration. Choose a different name.");
      }
      enrollment.setEnrollmentName(request.enrollmentName());
    }

    // Validate and apply contactEmail
    if (request.contactEmail() != null) {
      enrollment.setContactEmail(request.contactEmail());
    }

    // Validate and apply expiresAt (must be in future if provided)
    if (request.expiresAt() != null) {
      OffsetDateTime now = OffsetDateTime.now();
      if (!request.expiresAt().isAfter(now)) {
        throw new IllegalArgumentException(
            "expiresAt must be in the future. Got: " + request.expiresAt());
      }
      enrollment.setExpiresAt(request.expiresAt());
    }

    // Apply authAttemptChallengeRequired
    if (request.authAttemptChallengeRequired() != null) {
      enrollment.setAuthAttemptChallengeRequired(request.authAttemptChallengeRequired());
    }

    enrollment = enrollmentRepository.save(enrollment);
    logger.info(
        "Enrollment {} metadata updated (integrationId: {})",
        enrollmentId,
        enrollment.getIntegrationId());
    return enrollment;
  }
}
