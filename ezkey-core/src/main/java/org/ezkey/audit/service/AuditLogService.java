/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditLogService
 * Description: Service for audit log operations with transaction safety.
 */

package org.ezkey.audit.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.integrity.AuditHmacService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for audit log operations.
 *
 * <p>Provides audit logging functionality with transaction safety using REQUIRES_NEW propagation to
 * ensure audit logs are saved even if main transaction fails.
 *
 * <p><b>Transaction Safety:</b> Uses REQUIRES_NEW to prevent audit logging from being rolled back
 * with the main transaction, ensuring comprehensive audit trails.
 *
 * <p><b>Error Handling:</b> Audit failures are logged but never thrown to avoid disrupting main
 * business operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AuditLogService {

  private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository auditLogRepository;
  private final AuditHmacService auditHmacService;
  private final EntityManager entityManager;

  public AuditLogService(
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService,
      EntityManager entityManager) {
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
    this.entityManager = entityManager;
  }

  /**
   * Log an audit event in a separate transaction with optional HMAC signing.
   *
   * <p>Uses REQUIRES_NEW propagation to ensure the audit log is saved even if the calling
   * transaction is rolled back.
   *
   * <p><b>HMAC signing order is critical:</b> the {@code audit_log_id} is assigned by the database
   * on the first {@code save()} call. The HMAC must therefore be computed <em>after</em> the
   * initial save so that the database-assigned ID is included in the canonical form. A second
   * {@code save()} persists the computed HMAC. This two-step pattern is intentional: the ID is an
   * immutable, database-assigned value and must be part of the cryptographic seal.
   *
   * <p><b>Timestamp alignment:</b> After the first save, the entity is refreshed from the database
   * before computing the HMAC. This ensures {@code created_at} used in the canonical form matches
   * exactly what PostgreSQL stores (microsecond precision, rounded if needed). Without this,
   * in-memory nanosecond precision could differ from DB-stored value and cause ~50% of entries to
   * fail verification.
   *
   * @param auditLog the audit log to save
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(AuditLog auditLog) {
    try {
      if (auditLog.getInstanceId() == null) {
        auditLog.setInstanceId(auditHmacService.getInstanceId());
      }
      AuditLog saved = auditLogRepository.save(auditLog);

      if (saved.getEntryHmac() == null && auditHmacService.isActive()) {
        entityManager.refresh(saved);
        saved.setEnrollmentIdHmacSnapshot(saved.getEnrollmentId());
        saved.setEntryHmac(auditHmacService.computeHmac(saved));
        auditLogRepository.save(saved);
      }
    } catch (Exception e) {
      logger.error("Failed to save audit log: {}", e.getMessage(), e);
    }
  }

  /**
   * Find audit logs with filters, tenant scoping, and pagination.
   *
   * <p>This method supports multi-criteria search with mandatory tenant-based visibility
   * enforcement. All filter parameters are optional except {@code requesterTenantId} which controls
   * tenant scoping:
   *
   * <ul>
   *   <li><b>Global Admin ({@code requesterTenantId = null}):</b> Sees all audit logs. An optional
   *       {@code filterTenantId} may be provided to narrow results to a specific tenant.
   *   <li><b>Tenant Admin ({@code requesterTenantId != null}):</b> Sees only audit logs where
   *       {@code tenant_id} matches their tenant. Audit entries with {@code tenant_id = NULL}
   *       (e.g., system-level events) are excluded.
   * </ul>
   *
   * <p>Results are ordered by creation date descending (newest first) by default.
   *
   * <p><b>Use Case:</b> Security operators monitoring audit logs, forensic analysis, and compliance
   * reporting with proper multi-tenant isolation.
   *
   * @param eventType optional event type filter
   * @param eventStatus optional event status filter
   * @param apiName optional API name filter
   * @param enrollmentId optional enrollment ID filter
   * @param adminId optional admin ID filter
   * @param requesterTenantId the tenant ID of the requesting admin; {@code null} for Global Admin
   *     (no tenant restriction), non-null for Tenant Admin (strict tenant filtering)
   * @param filterTenantId optional explicit tenant filter for Global Admin; ignored when {@code
   *     requesterTenantId} is non-null (Tenant Admin scope takes precedence)
   * @param pageable pagination and sorting parameters
   * @return page of audit logs matching criteria and tenant scope
   */
  @Transactional(readOnly = true)
  public Page<AuditLog> findByFilters(
      EventType eventType,
      EventStatus eventStatus,
      ApiName apiName,
      Integer enrollmentId,
      Integer adminId,
      Integer requesterTenantId,
      Integer filterTenantId,
      Pageable pageable) {

    Specification<AuditLog> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          // Tenant scoping: Tenant Admin sees only their tenant's audit logs
          if (requesterTenantId != null) {
            predicates.add(cb.equal(root.get("tenantId"), requesterTenantId));
          } else if (filterTenantId != null) {
            // Global Admin with explicit tenant filter
            predicates.add(cb.equal(root.get("tenantId"), filterTenantId));
          }

          if (eventType != null) {
            predicates.add(cb.equal(root.get("eventType"), eventType));
          }

          if (eventStatus != null) {
            predicates.add(cb.equal(root.get("eventStatus"), eventStatus));
          }

          if (apiName != null) {
            predicates.add(cb.equal(root.get("apiName"), apiName));
          }

          if (enrollmentId != null) {
            predicates.add(cb.equal(root.get("enrollmentId"), enrollmentId));
          }

          if (adminId != null) {
            predicates.add(cb.equal(root.get("adminId"), adminId));
          }

          // Force ordering by createdAt DESC if not specified in pageable
          if (pageable.getSort().isUnsorted()) {
            query.orderBy(cb.desc(root.get("createdAt")));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return auditLogRepository.findAll(spec, pageable);
  }

  /**
   * Delete audit logs older than retention period.
   *
   * @param retentionDays number of days to retain audit logs
   * @return number of records deleted
   */
  @Transactional
  public int deleteOldLogs(int retentionDays) {
    OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(retentionDays);
    int deleted = auditLogRepository.deleteOlderThan(cutoffDate);
    logger.info("Deleted {} audit logs older than {} days", deleted, retentionDays);
    return deleted;
  }
}
