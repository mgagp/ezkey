/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditEntityFkResolver
 * Description: Resolves whether client-supplied IDs may be stored on audit rows that reference
 *     database foreign keys.
 */
package org.ezkey.audit.support;

import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.springframework.stereotype.Service;

/**
 * Supplies audit log foreign-key columns only when the referenced row exists.
 *
 * <p>Admin access control may allow global administrators to pass arbitrary numeric IDs in requests
 * before domain validation runs. Audit logging must not copy those values into {@code
 * ezkey_audit_log.integration_id} or {@code enrollment_id} when no matching row exists, or inserts
 * fail with FK violations (and failure audits are lost when errors are swallowed in {@code
 * AuditLogService}).
 *
 * <p>When this resolver returns {@code null}, callers should still record the requested identifier
 * in {@code error_message} or {@code event_details}.
 *
 * @since 2026
 */
@Service
public class AuditEntityFkResolver {

  private final IntegrationRepository integrationRepository;
  private final EnrollmentRepository enrollmentRepository;

  /**
   * Constructs the resolver.
   *
   * @param integrationRepository integration persistence
   * @param enrollmentRepository enrollment persistence
   */
  public AuditEntityFkResolver(
      IntegrationRepository integrationRepository, EnrollmentRepository enrollmentRepository) {
    this.integrationRepository = integrationRepository;
    this.enrollmentRepository = enrollmentRepository;
  }

  /**
   * Returns {@code integrationId} for audit persistence only if a row exists; otherwise {@code
   * null}.
   *
   * @param integrationId requested integration id (may be null)
   * @return id safe for {@code ezkey_audit_log.integration_id}, or null
   */
  public Integer integrationIdForAuditOrNull(Integer integrationId) {
    if (integrationId == null) {
      return null;
    }
    return integrationRepository.existsById(integrationId) ? integrationId : null;
  }

  /**
   * Returns {@code enrollmentId} for audit persistence only if a row exists; otherwise {@code
   * null}.
   *
   * @param enrollmentId requested enrollment id (may be null)
   * @return id safe for {@code ezkey_audit_log.enrollment_id}, or null
   */
  public Integer enrollmentIdForAuditOrNull(Integer enrollmentId) {
    if (enrollmentId == null) {
      return null;
    }
    return enrollmentRepository.existsById(enrollmentId) ? enrollmentId : null;
  }
}
