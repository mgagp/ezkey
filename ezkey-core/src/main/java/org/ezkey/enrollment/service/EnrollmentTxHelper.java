/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentTxHelper
 * Description: Transactional helper service for enrollment state management and cleanup operations.
 */

package org.ezkey.enrollment.service;

import java.time.OffsetDateTime;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional helper service for enrollment state management and cleanup operations.
 *
 * <p>This service provides specialized transactional methods for managing enrollment lifecycle and
 * cleanup operations that require separate transaction boundaries. It handles critical enrollment
 * state transitions and ensures data consistency during enrollment invalidation and cleanup
 * processes.
 *
 * <p><b>Transaction Management:</b> This service uses <code>Propagation.REQUIRES_NEW</code> to
 * ensure that cleanup operations are executed in separate transaction boundaries, preventing
 * rollback of cleanup operations if the calling transaction fails. This is essential for
 * maintaining data integrity and preventing orphaned enrollment data.
 *
 * <p><b>Key Responsibilities:</b>
 *
 * <ul>
 *   <li><b>Enrollment Invalidation:</b> Mark enrollments as invalid and clear sensitive data
 *   <li><b>State Cleanup:</b> Remove challenge data and deactivate enrollments
 *   <li><b>Transaction Isolation:</b> Ensure cleanup operations are not affected by parent
 *       transaction failures
 *   <li><b>Data Consistency:</b> Maintain enrollment state consistency across transaction
 *       boundaries
 * </ul>
 *
 * <p><b>Security Considerations:</b>
 *
 * <ul>
 *   <li>Clears sensitive enrollment challenge data during invalidation
 *   <li>Prevents access to invalidated enrollments by setting active flag to false
 *   <li>Ensures cleanup operations are atomic and consistent
 *   <li>Protects against data leakage through proper state management
 * </ul>
 *
 * <p><b>Usage Context:</b> This helper is typically used by enrollment services when handling
 * enrollment failures, timeout scenarios, or cleanup operations that need to be isolated from the
 * main transaction flow. It ensures that enrollment cleanup is performed reliably regardless of the
 * outcome of the primary enrollment operation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Transaction Level:</b> Service-level transactional operations with isolation boundaries
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentService
 * @see EnrollmentStatus
 * @see Enrollment
 * @see EnrollmentRepository
 * @see org.springframework.transaction.annotation.Transactional
 * @see org.springframework.transaction.annotation.Propagation
 */
@Service
public class EnrollmentTxHelper {

  private final EnrollmentRepository enrollmentRepository;
  private final AuditLogService auditLogService;

  /**
   * Constructs the enrollment transactional helper with the required dependencies.
   *
   * @param enrollmentRepository the JPA repository for enrollment entity operations
   * @param auditLogService the audit log service for ENROLLMENT_EXPIRED events
   */
  public EnrollmentTxHelper(
      EnrollmentRepository enrollmentRepository, AuditLogService auditLogService) {
    this.enrollmentRepository = enrollmentRepository;
    this.auditLogService = auditLogService;
  }

  /**
   * Marks an enrollment as invalid and clears sensitive data in a separate transaction.
   *
   * <p>This method performs critical enrollment cleanup operations that must be executed in
   * isolation from the calling transaction. It ensures that enrollment invalidation and data
   * cleanup are performed atomically, regardless of the outcome of the parent transaction that
   * triggered the cleanup.
   *
   * <p><b>Transaction Behavior:</b> Uses <code>Propagation.REQUIRES_NEW</code> to create a new
   * transaction boundary, ensuring that cleanup operations are committed independently of any
   * parent transaction. This prevents rollback of cleanup operations if the calling transaction
   * fails.
   *
   * <p><b>Security Operations:</b>
   *
   * <ol>
   *   <li>Retrieves the enrollment by ID
   *   <li>Validates enrollment exists and is not already verified
   *   <li>Sets enrollment status to <code>INVALID</code>
   *   <li>Clears sensitive enrollment challenge data
   *   <li>Deactivates the enrollment by setting active flag to false
   *   <li>Persists the changes to the database
   * </ol>
   *
   * <p><b>Safety Checks:</b>
   *
   * <ul>
   *   <li>Returns silently if enrollment ID is null or enrollment not found
   *   <li>Skips invalidation if enrollment is already in <code>VERIFIED</code> status
   *   <li>Ensures atomic operation with proper transaction boundaries
   * </ul>
   *
   * <p><b>Usage Scenarios:</b>
   *
   * <ul>
   *   <li>Enrollment timeout handling
   *   <li>Failed enrollment verification cleanup
   *   <li>Security-related enrollment invalidation
   *   <li>Manual enrollment cleanup operations
   * </ul>
   *
   * @param enrollmentId the unique identifier of the enrollment to invalidate and clear
   * @throws RuntimeException if database operation fails during cleanup
   * @see EnrollmentStatus#INVALID
   * @see EnrollmentStatus#VERIFIED
   * @see org.springframework.transaction.annotation.Propagation#REQUIRES_NEW
   * @since 2025
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markInvalidAndClear(Integer enrollmentId) {
    Enrollment e = enrollmentRepository.findById(enrollmentId).orElse(null);
    if (e == null) {
      return;
    }
    if (e.getStatus() == EnrollmentStatus.VERIFIED) {
      return;
    }
    e.setStatus(EnrollmentStatus.INVALID);
    e.setEnrollmentChallenge(null);
    e.setActive(false);
    enrollmentRepository.save(e);
  }

  /**
   * Marks a pending enrollment as EXPIRED and emits ENROLLMENT_EXPIRED in a separate transaction.
   *
   * <p>Used when bind or verify rejects an expired enrollment. Runs in REQUIRES_NEW so the update
   * and audit commit even when the caller's transaction rolls back, allowing the API to return 400
   * without a follow-up 500 from rollback.
   *
   * @param enrollmentId enrollment ID
   * @param integrationId integration ID (for audit)
   * @param expiresAt expiration timestamp (for audit details)
   * @param eventAction e.g. enrollment_expired_bind_rejected, enrollment_expired_verify_rejected
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markExpiredAndEmitAudit(
      Integer enrollmentId, Integer integrationId, OffsetDateTime expiresAt, String eventAction) {
    Enrollment e = enrollmentRepository.findById(enrollmentId).orElse(null);
    if (e == null) {
      return;
    }
    e.setStatus(EnrollmentStatus.EXPIRED);
    enrollmentRepository.save(e);
    String details =
        AuditDetailsBuilder.builder()
            .custom("enrollmentId", enrollmentId)
            .custom("expiresAt", expiresAt != null ? expiresAt.toString() : null)
            .toJson();
    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.ENROLLMENT_EXPIRED)
            .eventAction(eventAction)
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.AUTH_API)
            .enrollmentId(enrollmentId)
            .integrationId(integrationId)
            .eventDetails(details)
            .build());
  }
}
