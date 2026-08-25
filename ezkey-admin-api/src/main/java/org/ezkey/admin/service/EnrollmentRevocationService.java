/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentRevocationService
 * Description: Business logic for enrollment revocation, deactivation, and reactivation lifecycle.
 */

package org.ezkey.admin.service;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.exception.EnrollmentCannotBeDeletedException;
import org.ezkey.admin.exception.EnrollmentLinkedAsAdminException;
import org.ezkey.admin.exception.SelfRevocationNotAllowedException;
import org.ezkey.admin.exception.SystemIntegrationRevocationException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for enrollment revocation, deactivation, and reactivation lifecycle management.
 *
 * <p>This service implements the full enrollment lifecycle management for administrators, providing
 * three distinct operations with appropriate security guards, audit trails, and cascading token
 * invalidation.
 *
 * <p><b>Operations:</b>
 *
 * <ul>
 *   <li><b>Revoke:</b> Permanent, admin-initiated revocation. Sets status to {@code REVOKED},
 *       {@code active=false}, records {@code revokedAt} and {@code revokedByAdminId}. Irreversible.
 *   <li><b>Deactivate:</b> Reversible soft-disable. Sets {@code active=false}, records {@code
 *       deactivatedAt} and {@code deactivatedByAdminId}. Status remains {@code VERIFIED}.
 *   <li><b>Reactivate:</b> Reverses a deactivation. Only permitted for enrollments with status
 *       {@code VERIFIED} and {@code active=false}. Cannot reactivate {@code REVOKED} enrollments.
 * </ul>
 *
 * <p><b>Security Guards (applied to all mutating operations):</b>
 *
 * <ol>
 *   <li><b>Self-revocation guard:</b> Administrators cannot revoke or deactivate their own MFA
 *       enrollment, which would cause a self-inflicted lockout.
 *   <li><b>Bearer token invalidation:</b> When an administrator's own MFA enrollment is revoked or
 *       deactivated, all their active bearer tokens are immediately invalidated to prevent
 *       continued access with stale sessions.
 * </ol>
 *
 * <p><b>Non-Impersonation Principle:</b> Revoking an enrollment is an administrative control action
 * — it is not impersonation. Global Admins may revoke any enrollment (including regular user
 * enrollments across all integrations). Tenant Admins may revoke enrollments within their tenant.
 * This is consistent with rapid access removal (rapid access removal) and industry standards (Okta, Duo).
 *
 * <p><b>Peer Revocation:</b> Tenant Admins may revoke other Tenant Admins' enrollments within the
 * same tenant (scoping enforced by the controller via {@code AccessControlService}). A mandatory
 * {@code reason} parameter is required for revoke operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see SelfRevocationNotAllowedException
 * @see SystemIntegrationRevocationException
 */
@Service
public class EnrollmentRevocationService {

  /**
   * Internal result summary for integration-scoped bulk enrollment lifecycle operations.
   *
   * @param affectedCount number of enrollments whose state changed
   * @param skippedCount number of enrollments skipped by defensive guards
   * @param noOp whether the operation completed successfully without changing any enrollment state
   */
  public record BulkEnrollmentOperationResult(int affectedCount, int skippedCount, boolean noOp) {}

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentRevocationService.class);

  private final EnrollmentRepository enrollmentRepository;
  private final EzkeyAdminRepository adminRepository;
  private final AdminTokenRepository adminTokenRepository;
  private final IntegrationRepository integrationRepository;
  private final AuditLogService auditLogService;
  private final AuthAttemptRepository authAttemptRepository;

  /**
   * Constructs the EnrollmentRevocationService with required dependencies.
   *
   * @param enrollmentRepository repository for enrollment persistence
   * @param adminRepository repository for looking up admins by MFA enrollment
   * @param adminTokenRepository repository for bearer token invalidation
   * @param integrationRepository repository for integration metadata (system integration check)
   * @param auditLogService service for writing audit log entries
   * @param authAttemptRepository repository for checking authentication history before deletion
   */
  public EnrollmentRevocationService(
      EnrollmentRepository enrollmentRepository,
      EzkeyAdminRepository adminRepository,
      AdminTokenRepository adminTokenRepository,
      IntegrationRepository integrationRepository,
      AuditLogService auditLogService,
      AuthAttemptRepository authAttemptRepository) {
    this.enrollmentRepository = enrollmentRepository;
    this.adminRepository = adminRepository;
    this.adminTokenRepository = adminTokenRepository;
    this.integrationRepository = integrationRepository;
    this.auditLogService = auditLogService;
    this.authAttemptRepository = authAttemptRepository;
  }

  /**
   * Permanently revokes an enrollment.
   *
   * <p>Revocation is <em>irreversible</em>: the enrollment transitions to status {@code REVOKED}
   * and {@code active=false}. The associated cryptographic credentials are considered destroyed
   * from a security standpoint. If the enrollment belongs to an administrator, all their active
   * bearer tokens are immediately invalidated.
   *
   * <p><b>Self-revocation guard:</b> Throws {@link SelfRevocationNotAllowedException} if the
   * calling administrator's own MFA enrollment is the target.
   *
   * <p>Idempotent: revoking an already-revoked enrollment is a no-op (returns silently).
   *
   * @param enrollmentId the ID of the enrollment to revoke
   * @param principal the admin principal performing the revocation
   * @param reason mandatory justification for the revocation (min 10 characters)
   * @param context client context for audit logging (IP, user-agent, etc.)
   * @param tenantId tenant ID for audit log association (null for global admin operations)
   * @throws ResourceNotFoundException if the enrollment is not found
   * @throws SelfRevocationNotAllowedException if the admin attempts to revoke their own enrollment
   */
  @Transactional
  public void revoke(
      Integer enrollmentId,
      AdminPrincipal principal,
      String reason,
      ClientContext context,
      Integer tenantId) {

    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

    assertNotSelfRevocation(principal, enrollment);

    if (EnrollmentStatus.REVOKED.equals(enrollment.getStatus())) {
      logger.debug("Enrollment {} is already revoked — no-op", enrollmentId);
      return;
    }

    enrollment.setStatus(EnrollmentStatus.REVOKED);
    enrollment.setActive(false);
    enrollment.setRevokedAt(OffsetDateTime.now());
    enrollment.setRevokedByAdminId(principal.adminId());
    enrollmentRepository.save(enrollment);

    invalidateAdminTokensIfAdminEnrollment(enrollment);

    auditLogService.log(
        AuditHelper.createAdminAudit(
                context,
                EventType.ENROLLMENT_REVOKED,
                AdminAuditConstants.ENROLLMENT_REVOKED,
                tenantId)
            .eventStatus(EventStatus.SUCCESS)
            .enrollmentId(enrollmentId)
            .integrationId(enrollment.getIntegrationId())
            .reason(reason)
            .eventDetails(
                "Enrollment '"
                    + enrollment.getEnrollmentName()
                    + "' permanently revoked by admin "
                    + principal.adminId())
            .build());

    logger.info(
        "Enrollment {} ('{}') permanently revoked by admin {}",
        enrollmentId,
        enrollment.getEnrollmentName(),
        principal.adminId());
  }

  /**
   * Deactivates an enrollment (reversible soft-disable).
   *
   * <p>Deactivation sets {@code active=false} while preserving the enrollment status as {@code
   * VERIFIED}. The enrollment can later be reactivated. If the enrollment belongs to an
   * administrator, all their active bearer tokens are immediately invalidated.
   *
   * <p><b>Self-revocation guard:</b> Throws {@link SelfRevocationNotAllowedException} if the
   * calling administrator's own MFA enrollment is the target.
   *
   * <p>Idempotent: deactivating an already-inactive enrollment is a no-op (returns silently),
   * unless the enrollment is revoked (in which case deactivation is not meaningful).
   *
   * @param enrollmentId the ID of the enrollment to deactivate
   * @param principal the admin principal performing the deactivation
   * @param reason optional justification for the deactivation
   * @param context client context for audit logging
   * @param tenantId tenant ID for audit log association
   * @throws ResourceNotFoundException if the enrollment is not found
   * @throws SelfRevocationNotAllowedException if the admin attempts to deactivate their own
   *     enrollment
   * @throws IllegalStateException if the enrollment is already permanently revoked
   */
  @Transactional
  public void deactivate(
      Integer enrollmentId,
      AdminPrincipal principal,
      String reason,
      ClientContext context,
      Integer tenantId) {

    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

    assertNotSelfRevocation(principal, enrollment);

    if (EnrollmentStatus.REVOKED.equals(enrollment.getStatus())) {
      throw new IllegalStateException(
          "Enrollment " + enrollmentId + " is permanently revoked and cannot be deactivated.");
    }

    if (!Boolean.TRUE.equals(enrollment.getActive())) {
      logger.debug("Enrollment {} is already inactive — no-op", enrollmentId);
      return;
    }

    enrollment.setActive(false);
    enrollment.setDeactivatedAt(OffsetDateTime.now());
    enrollment.setDeactivatedByAdminId(principal.adminId());
    enrollmentRepository.save(enrollment);

    invalidateAdminTokensIfAdminEnrollment(enrollment);

    auditLogService.log(
        AuditHelper.createAdminAudit(
                context,
                EventType.ENROLLMENT_DEACTIVATED,
                AdminAuditConstants.ENROLLMENT_DEACTIVATED,
                tenantId)
            .eventStatus(EventStatus.SUCCESS)
            .enrollmentId(enrollmentId)
            .integrationId(enrollment.getIntegrationId())
            .reason(reason)
            .eventDetails(
                "Enrollment '"
                    + enrollment.getEnrollmentName()
                    + "' deactivated by admin "
                    + principal.adminId())
            .build());

    logger.info(
        "Enrollment {} ('{}') deactivated by admin {}",
        enrollmentId,
        enrollment.getEnrollmentName(),
        principal.adminId());
  }

  /**
   * Reactivates a previously deactivated enrollment.
   *
   * <p>Reactivation is only permitted for enrollments with status {@code VERIFIED} and {@code
   * active=false}. Permanently revoked enrollments ({@code status=REVOKED}) cannot be reactivated.
   *
   * <p>On successful reactivation, the {@code deactivatedAt} and {@code deactivatedByAdminId} audit
   * fields are cleared to reflect the current active state.
   *
   * @param enrollmentId the ID of the enrollment to reactivate
   * @param principal the admin principal performing the reactivation
   * @param reason optional justification for the reactivation
   * @param context client context for audit logging
   * @param tenantId tenant ID for audit log association
   * @throws ResourceNotFoundException if the enrollment is not found
   * @throws IllegalStateException if the enrollment is permanently revoked or already active
   */
  @Transactional
  public void reactivate(
      Integer enrollmentId,
      AdminPrincipal principal,
      String reason,
      ClientContext context,
      Integer tenantId) {

    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

    if (EnrollmentStatus.REVOKED.equals(enrollment.getStatus())) {
      throw new IllegalStateException(
          "Enrollment "
              + enrollmentId
              + " is permanently revoked and cannot be reactivated."
              + " A new enrollment must be created.");
    }

    if (!EnrollmentStatus.VERIFIED.equals(enrollment.getStatus())) {
      throw new IllegalStateException(
          "Only VERIFIED enrollments can be reactivated. Current status: "
              + enrollment.getStatus());
    }

    if (Boolean.TRUE.equals(enrollment.getActive())) {
      logger.debug("Enrollment {} is already active — no-op", enrollmentId);
      return;
    }

    enrollment.setActive(true);
    enrollment.setDeactivatedAt(null);
    enrollment.setDeactivatedByAdminId(null);
    enrollmentRepository.save(enrollment);

    auditLogService.log(
        AuditHelper.createAdminAudit(
                context,
                EventType.ENROLLMENT_REACTIVATED,
                AdminAuditConstants.ENROLLMENT_REACTIVATED,
                tenantId)
            .eventStatus(EventStatus.SUCCESS)
            .enrollmentId(enrollmentId)
            .integrationId(enrollment.getIntegrationId())
            .reason(reason)
            .eventDetails(
                "Enrollment '"
                    + enrollment.getEnrollmentName()
                    + "' reactivated by admin "
                    + principal.adminId())
            .build());

    logger.info(
        "Enrollment {} ('{}') reactivated by admin {}",
        enrollmentId,
        enrollment.getEnrollmentName(),
        principal.adminId());
  }

  /**
   * Bulk-revokes all revocable enrollments for an integration.
   *
   * <p>This operation is designed for incident response scenarios (e.g., compromised integration
   * API key) where all enrollments associated with an integration must be immediately revoked,
   * including already deactivated verified enrollments and in-flight CREATED or BOUND enrollments.
   *
   * <p><b>System integration guard:</b> Cannot be applied to system integrations ({@code
   * is_system_integration=true}), which host all administrator MFA enrollments. Applying bulk
   * revocation to a system integration would cause a complete admin lockout.
   *
   * <p>Each individual enrollment is revoked with the self-revocation guard applied — the calling
   * admin's own enrollment is skipped with a warning if encountered (defensive programming, though
   * this scenario is unlikely for non-system integrations).
   *
   * @param integrationId the ID of the integration whose enrollments should be revoked
   * @param principal the admin principal performing the bulk revocation
   * @param reason optional justification for the bulk revocation (min 10 characters when provided)
   * @param context client context for audit logging
   * @param tenantId tenant ID for audit log association
   * @throws ResourceNotFoundException if the integration is not found
   * @throws SystemIntegrationRevocationException if the integration is a system integration
   */
  @Transactional
  public BulkEnrollmentOperationResult revokeAllByIntegration(
      Integer integrationId,
      AdminPrincipal principal,
      String reason,
      ClientContext context,
      Integer tenantId) {

    Integration integration =
        integrationRepository
            .findById(integrationId)
            .orElseThrow(() -> new ResourceNotFoundException("Integration", integrationId));

    if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
      throw new SystemIntegrationRevocationException(
          "Bulk revocation cannot be applied to a system integration."
              + " Revoking all system enrollments would lock out all administrators.");
    }

    List<Enrollment> revocableEnrollments =
        enrollmentRepository.findByIntegrationIdAndStatusIn(
            integrationId,
            List.of(EnrollmentStatus.CREATED, EnrollmentStatus.BOUND, EnrollmentStatus.VERIFIED));

    int revokedCount = 0;
    int skippedCount = 0;

    for (Enrollment enrollment : revocableEnrollments) {
      if (isSelfEnrollment(principal, enrollment)) {
        logger.warn(
            "Bulk revoke skipped enrollment {} — it is the calling admin's own MFA enrollment."
                + " Use individual revoke for self-revocation prevention enforcement.",
            enrollment.getEnrollmentId());
        skippedCount++;
        continue;
      }

      enrollment.setStatus(EnrollmentStatus.REVOKED);
      enrollment.setActive(false);
      enrollment.setRevokedAt(OffsetDateTime.now());
      enrollment.setRevokedByAdminId(principal.adminId());
      enrollmentRepository.save(enrollment);

      invalidateAdminTokensIfAdminEnrollment(enrollment);
      revokedCount++;
    }

    if (revokedCount > 0) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_REVOKED,
                  AdminAuditConstants.ENROLLMENT_REVOKE_ALL,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .integrationId(integrationId)
              .reason(reason)
              .eventDetails(
                  "Bulk revocation: "
                      + revokedCount
                      + " enrollments revoked"
                      + (skippedCount > 0 ? ", " + skippedCount + " skipped (self-guard)" : "")
                      + " for integration "
                      + integrationId)
              .build());
    }

    logger.info(
        "Bulk revocation complete for integration {}: {} revoked, {} skipped by admin {}",
        integrationId,
        revokedCount,
        skippedCount,
        principal.adminId());
    return new BulkEnrollmentOperationResult(revokedCount, skippedCount, revokedCount == 0);
  }

  /**
   * Bulk-deactivates all active VERIFIED enrollments for an integration (reversible lockdown).
   *
   * <p>Use this for precautionary lockdowns (suspected but unconfirmed threat, emergency
   * maintenance). Status remains {@code VERIFIED}; only {@code active} is set to {@code false}.
   * Enrollments can be restored with {@link #reactivateAllByIntegration}.
   *
   * <p><b>System integration guard:</b> Cannot be applied to system integrations ({@code
   * is_system_integration=true}), which would lock out all administrators.
   *
   * <p>Each enrollment is deactivated with the self-revocation guard applied — the calling admin's
   * own enrollment is skipped with a warning if encountered.
   *
   * @param integrationId the ID of the integration whose enrollments should be deactivated
   * @param principal the admin principal performing the bulk deactivation
   * @param reason optional justification for the bulk deactivation
   * @param context client context for audit logging
   * @param tenantId tenant ID for audit log association
   * @throws ResourceNotFoundException if the integration is not found
   * @throws SystemIntegrationRevocationException if the integration is a system integration
   */
  @Transactional
  public BulkEnrollmentOperationResult deactivateAllByIntegration(
      Integer integrationId,
      AdminPrincipal principal,
      String reason,
      ClientContext context,
      Integer tenantId) {

    Integration integration =
        integrationRepository
            .findById(integrationId)
            .orElseThrow(() -> new ResourceNotFoundException("Integration", integrationId));

    if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
      throw new SystemIntegrationRevocationException(
          "Bulk deactivation cannot be applied to a system integration."
              + " Deactivating all system enrollments would lock out all administrators.");
    }

    List<Enrollment> activeEnrollments =
        enrollmentRepository.findByIntegrationIdAndStatusAndActive(
            integrationId, EnrollmentStatus.VERIFIED, true);

    int deactivatedCount = 0;
    int skippedCount = 0;

    for (Enrollment enrollment : activeEnrollments) {
      if (isSelfEnrollment(principal, enrollment)) {
        logger.warn(
            "Bulk deactivate skipped enrollment {} — it is the calling admin's own MFA enrollment."
                + " Use individual deactivate for self-revocation prevention enforcement.",
            enrollment.getEnrollmentId());
        skippedCount++;
        continue;
      }

      enrollment.setActive(false);
      enrollment.setDeactivatedAt(OffsetDateTime.now());
      enrollment.setDeactivatedByAdminId(principal.adminId());
      enrollmentRepository.save(enrollment);

      invalidateAdminTokensIfAdminEnrollment(enrollment);
      deactivatedCount++;
    }

    if (deactivatedCount > 0) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_DEACTIVATED,
                  AdminAuditConstants.ENROLLMENT_DEACTIVATE_ALL,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .integrationId(integrationId)
              .reason(reason)
              .eventDetails(
                  "Bulk deactivation: "
                      + deactivatedCount
                      + " enrollments deactivated"
                      + (skippedCount > 0 ? ", " + skippedCount + " skipped (self-guard)" : "")
                      + " for integration "
                      + integrationId)
              .build());
    }

    logger.info(
        "Bulk deactivation complete for integration {}: {} deactivated, {} skipped by admin {}",
        integrationId,
        deactivatedCount,
        skippedCount,
        principal.adminId());
    return new BulkEnrollmentOperationResult(deactivatedCount, skippedCount, deactivatedCount == 0);
  }

  /**
   * Bulk-reactivates all inactive VERIFIED enrollments for an integration.
   *
   * <p>Use this after a precautionary {@link #deactivateAllByIntegration} when the threat is
   * cleared or the maintenance window ends. Only enrollments with status {@code VERIFIED} and
   * {@code active=false} are reactivated; permanently revoked enrollments are not affected.
   *
   * @param integrationId the ID of the integration whose enrollments should be reactivated
   * @param principal the admin principal performing the bulk reactivation
   * @param reason optional justification for the bulk reactivation
   * @param context client context for audit logging
   * @param tenantId tenant ID for audit log association
   * @throws ResourceNotFoundException if the integration is not found
   */
  @Transactional
  public BulkEnrollmentOperationResult reactivateAllByIntegration(
      Integer integrationId,
      AdminPrincipal principal,
      String reason,
      ClientContext context,
      Integer tenantId) {

    integrationRepository
        .findById(integrationId)
        .orElseThrow(() -> new ResourceNotFoundException("Integration", integrationId));

    List<Enrollment> inactiveEnrollments =
        enrollmentRepository.findByIntegrationIdAndStatusAndActive(
            integrationId, EnrollmentStatus.VERIFIED, false);

    int reactivatedCount = 0;

    for (Enrollment enrollment : inactiveEnrollments) {
      enrollment.setActive(true);
      enrollment.setDeactivatedAt(null);
      enrollment.setDeactivatedByAdminId(null);
      enrollmentRepository.save(enrollment);
      reactivatedCount++;
    }

    if (reactivatedCount > 0) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_REACTIVATED,
                  AdminAuditConstants.ENROLLMENT_REACTIVATE_ALL,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .integrationId(integrationId)
              .reason(reason)
              .eventDetails(
                  "Bulk reactivation: "
                      + reactivatedCount
                      + " enrollments reactivated for integration "
                      + integrationId)
              .build());
    }

    logger.info(
        "Bulk reactivation complete for integration {}: {} reactivated by admin {}",
        integrationId,
        reactivatedCount,
        principal.adminId());
    return new BulkEnrollmentOperationResult(reactivatedCount, 0, reactivatedCount == 0);
  }

  /**
   * Asserts that the calling administrator is not attempting to revoke their own MFA enrollment.
   *
   * <p>This guard prevents self-inflicted lockout: if an admin revokes their own enrollment, their
   * bearer token validation would immediately fail (the token validation service checks enrollment
   * active status), and without a valid enrollment, the admin cannot log in without a recovery
   * code. The recovery flow ({@code POST /api/v1/admin/enrollments/reset}) is the correct path for
   * enrollment reset.
   *
   * @param principal the admin principal performing the operation
   * @param enrollment the target enrollment
   * @throws SelfRevocationNotAllowedException if the enrollment belongs to the calling admin
   */
  private void assertNotSelfRevocation(AdminPrincipal principal, Enrollment enrollment) {
    adminRepository
        .findByEnrollmentId(enrollment.getEnrollmentId())
        .ifPresent(
            ownerAdmin -> {
              if (ownerAdmin.getAdminId().equals(principal.adminId())) {
                throw new SelfRevocationNotAllowedException(
                    "Cannot revoke or deactivate your own enrollment. Use the recovery flow to"
                        + " reset it.");
              }
            });
  }

  /**
   * Asserts that the calling administrator is not attempting to delete their own enrollment.
   *
   * <p>Same guard as {@link #assertNotSelfRevocation} but for the delete operation, with a
   * delete-specific message. Call this before performing enrollment deletion so that the API
   * returns RFC 9457 instead of a database constraint violation.
   *
   * @param principal the admin principal performing the operation
   * @param enrollmentId the target enrollment ID to delete
   * @throws SelfRevocationNotAllowedException if the enrollment is the calling admin's enrollment
   */
  public void assertNotSelfDeletion(AdminPrincipal principal, Integer enrollmentId) {
    adminRepository
        .findByEnrollmentId(enrollmentId)
        .ifPresent(
            ownerAdmin -> {
              if (ownerAdmin.getAdminId().equals(principal.adminId())) {
                throw new SelfRevocationNotAllowedException(
                    "Cannot delete your own enrollment. Use the recovery flow to reset it.");
              }
            });
  }

  /**
   * Asserts that the enrollment is not linked as any administrator's enrollment.
   *
   * <p>Call this after {@link #assertNotSelfDeletion} when performing enrollment deletion. If this
   * enrollment is linked to any admin (including another admin), deletion would cause foreign key
   * or Hibernate transient reference errors. This guard returns RFC 9457 409 instead.
   *
   * @param enrollmentId the target enrollment ID to delete
   * @throws EnrollmentLinkedAsAdminException if the enrollment is linked as any admin's enrollment
   */
  public void assertNotLinkedAsAdmin(Integer enrollmentId) {
    if (adminRepository.findByEnrollmentId(enrollmentId).isPresent()) {
      throw new EnrollmentLinkedAsAdminException(
          "Enrollment cannot be deleted because it is linked to an administrator. Use the recovery"
              + " flow to reset that administrator enrollment first.");
    }
  }

  /**
   * Consolidates all pre-deletion guards into a single validation call.
   *
   * <p>Runs the three guards that must pass before an enrollment can be hard-deleted:
   *
   * <ol>
   *   <li>{@link #assertNotSelfDeletion} — admin cannot delete their own MFA enrollment
   *   <li>{@link #assertNotLinkedAsAdmin} — enrollment must not be linked to any admin account
   *   <li>Auth-history check — enrollment must have no authentication attempts (use revoke instead)
   * </ol>
   *
   * @param principal the admin principal performing the deletion
   * @param enrollmentId the target enrollment ID
   * @throws SelfRevocationNotAllowedException if the enrollment belongs to the calling admin
   * @throws EnrollmentLinkedAsAdminException if the enrollment is linked to any administrator
   * @throws EnrollmentCannotBeDeletedException if the enrollment has authentication history
   */
  public void validateDelete(AdminPrincipal principal, Integer enrollmentId) {
    assertNotSelfDeletion(principal, enrollmentId);
    assertNotLinkedAsAdmin(enrollmentId);
    if (authAttemptRepository.existsByEnrollmentId(enrollmentId)) {
      throw new EnrollmentCannotBeDeletedException(
          "Enrollment cannot be deleted because it has authentication history. Revoke the"
              + " enrollment instead.");
    }
  }

  /**
   * Checks whether the given enrollment is the calling admin's own enrollment.
   *
   * <p>Used by the bulk revocation operation to skip the calling admin's own enrollment rather than
   * throwing an exception (bulk operations should be as complete as possible).
   *
   * @param principal the admin principal performing the operation
   * @param enrollment the enrollment to check
   * @return {@code true} if this is the calling admin's own enrollment, {@code false} otherwise
   */
  private boolean isSelfEnrollment(AdminPrincipal principal, Enrollment enrollment) {
    return adminRepository
        .findByEnrollmentId(enrollment.getEnrollmentId())
        .map(ownerAdmin -> ownerAdmin.getAdminId().equals(principal.adminId()))
        .orElse(false);
  }

  /**
   * Invalidates all active bearer tokens for the administrator who owns the given enrollment.
   *
   * <p>When an enrollment is revoked or deactivated, the {@link AdminTokenValidationService} checks
   * enrollment active status on every request. However, immediately revoking all tokens provides
   * defence-in-depth: no window exists where a revoked enrollment's sessions remain active, even
   * briefly.
   *
   * <p>This method is a no-op if the enrollment does not belong to any administrator (i.e., it is a
   * regular user enrollment for a non-system integration).
   *
   * @param enrollment the enrollment that was just revoked or deactivated
   */
  private void invalidateAdminTokensIfAdminEnrollment(Enrollment enrollment) {
    adminRepository
        .findByEnrollmentId(enrollment.getEnrollmentId())
        .ifPresent(
            ownerAdmin -> {
              int revokedTokenCount =
                  adminTokenRepository.deactivateAllTokensForAdmin(ownerAdmin.getAdminId());
              if (revokedTokenCount > 0) {
                logger.info(
                    "Invalidated {} active bearer token(s) for admin {} (enrollment {} revoked"
                        + " or deactivated)",
                    revokedTokenCount,
                    ownerAdmin.getAdminId(),
                    enrollment.getEnrollmentId());
              }
            });
  }
}
