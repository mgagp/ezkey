/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditLogService
 * Description: Service for audit log operations with transaction safety.
 */

package org.ezkey.audit.service;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.EventTypeFamily;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.integrity.AuditChainCheckpointRepository;
import org.ezkey.audit.integrity.AuditHmacService;
import org.ezkey.audit.integrity.CheckpointLifecycleState;
import org.ezkey.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AuditLogService {

  /** Bounded neighborhood of audit events around a single anchor log. */
  public static final class AuditLogContextSlice {
    private final List<AuditLog> items;
    private final Long anchorAuditLogId;
    private final boolean hasMoreBefore;
    private final boolean hasMoreAfter;

    public AuditLogContextSlice(
        List<AuditLog> items, Long anchorAuditLogId, boolean hasMoreBefore, boolean hasMoreAfter) {
      this.items = items;
      this.anchorAuditLogId = anchorAuditLogId;
      this.hasMoreBefore = hasMoreBefore;
      this.hasMoreAfter = hasMoreAfter;
    }

    public List<AuditLog> getItems() {
      return items;
    }

    public Long getAnchorAuditLogId() {
      return anchorAuditLogId;
    }

    public boolean isHasMoreBefore() {
      return hasMoreBefore;
    }

    public boolean isHasMoreAfter() {
      return hasMoreAfter;
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository auditLogRepository;
  private final AuditChainCheckpointRepository checkpointRepository;
  private final AuditHmacService auditHmacService;
  private final TransactionTemplate requiresNewTx;

  public AuditLogService(
      AuditLogRepository auditLogRepository,
      AuditChainCheckpointRepository checkpointRepository,
      AuditHmacService auditHmacService,
      PlatformTransactionManager transactionManager) {
    this.auditLogRepository = auditLogRepository;
    this.checkpointRepository = checkpointRepository;
    this.auditHmacService = auditHmacService;
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.requiresNewTx = tx;
  }

  /**
   * Log an audit event in a dedicated new transaction with optional HMAC signing.
   *
   * <p>Uses a programmatic {@link TransactionTemplate} with {@code REQUIRES_NEW} propagation
   * instead of the declarative {@code @Transactional(REQUIRES_NEW)} annotation. This is
   * intentional: with the declarative approach, if Hibernate marks the inner JPA session as
   * rollback-only (e.g. due to a schema mismatch or constraint violation caught internally), Spring
   * throws {@code UnexpectedRollbackException} <em>after</em> the method body returns — escaping
   * the internal {@code catch} block and propagating to the caller. With a programmatic template
   * the commit/rollback is fully controlled here, so audit failures are always silently absorbed
   * and never disrupt the calling business operation.
   *
   * <p><b>Single-INSERT HMAC seal:</b> {@code audit_log_id} is pre-allocated from the named
   * sequence {@code ezkey_audit_log_id_seq} ({@code nextval}) before signing. The application owns
   * {@code created_at} truncated to microseconds so the signed value matches PostgreSQL {@code
   * TIMESTAMPTZ} precision without a post-insert refresh. The HMAC is computed with id + timestamp
   * already set, then a single {@code save()} (Persistable INSERT) persists id, {@code created_at},
   * and {@code entry_hmac} together — no UPDATE grant required for peripheral roles.
   *
   * @param auditLog the audit log to save
   */
  public void log(AuditLog auditLog) {
    try {
      requiresNewTx.execute(
          _ -> {
            if (auditLog.getInstanceId() == null) {
              auditLog.setInstanceId(auditHmacService.getInstanceId());
            }

            // Application owns created_at at microsecond precision (signed == stored).
            OffsetDateTime createdAt =
                auditLog.getCreatedAt() != null ? auditLog.getCreatedAt() : OffsetDateTime.now();
            auditLog.setCreatedAt(createdAt.truncatedTo(ChronoUnit.MICROS));

            // Pre-allocate identity so the canonical form can include audit_log_id before INSERT.
            if (auditLog.getAuditLogId() == null) {
              auditLog.setAuditLogId(auditLogRepository.nextAuditLogId());
            }

            if (auditLog.getEntryHmac() == null && auditHmacService.isActive()) {
              auditLog.setIntegrationIdHmacSnapshot(auditLog.getIntegrationId());
              auditLog.setEnrollmentIdHmacSnapshot(auditLog.getEnrollmentId());
              auditLog.setEntryHmac(auditHmacService.computeHmac(auditLog));
            }

            auditLogRepository.save(auditLog);
            return null;
          });
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
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
   * @param eventType optional single event type filter (mutually exclusive with {@code
   *     eventTypeFamily})
   * @param eventTypeFamily optional filter for all event types in a family ({@code IN (...)} ;
   *     mutually exclusive with {@code eventType})
   * @param eventStatus optional event status filter
   * @param apiName optional API name filter
   * @param enrollmentId optional enrollment ID filter
   * @param authAttemptId optional auth attempt ID filter
   * @param integrationId optional integration ID filter
   * @param adminId optional admin ID filter (actor who performed the action)
   * @param targetAdminId optional target admin ID filter (admin who is the subject of the event,
   *     e.g. created, deactivated, or activated)
   * @param requesterTenantId the tenant ID of the requesting admin; {@code null} for Global Admin
   *     (no tenant restriction), non-null for Tenant Admin (strict tenant filtering)
   * @param filterTenantId optional explicit tenant filter for Global Admin; ignored when {@code
   *     requesterTenantId} is non-null (Tenant Admin scope takes precedence)
   * @param createdAfter optional start of date range filter (inclusive)
   * @param createdBefore optional end of date range filter (inclusive)
   * @param pageable pagination and sorting parameters
   * @return page of audit logs matching criteria and tenant scope
   */
  @Transactional(readOnly = true)
  public Page<AuditLog> findByFilters(
      EventType eventType,
      EventTypeFamily eventTypeFamily,
      EventStatus eventStatus,
      ApiName apiName,
      Integer enrollmentId,
      Integer authAttemptId,
      Integer integrationId,
      Integer adminId,
      Integer targetAdminId,
      Integer requesterTenantId,
      Integer filterTenantId,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
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

          if (eventTypeFamily != null) {
            predicates.add(root.get("eventType").in(eventTypeFamily.getMemberEventTypes()));
          } else if (eventType != null) {
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

          if (authAttemptId != null) {
            predicates.add(cb.equal(root.get("authAttemptId"), authAttemptId));
          }

          if (integrationId != null) {
            predicates.add(cb.equal(root.get("integrationId"), integrationId));
          }

          if (adminId != null) {
            predicates.add(cb.equal(root.get("adminId"), adminId));
          }

          if (targetAdminId != null) {
            predicates.add(cb.equal(root.get("targetAdminId"), targetAdminId));
          }

          if (createdAfter != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
          }
          if (createdBefore != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
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
   * Find a bounded neighborhood of audit events around a single anchor log.
   *
   * <p>The result is intentionally opinionated and bounded: the anchor event is returned together
   * with a limited number of older and newer events that are visible to the requesting admin under
   * the same tenant-scoping rules as the standard audit search.
   *
   * @param anchorAuditLogId anchor audit log identifier
   * @param beforeCount number of older events to include before the anchor in chronological order
   * @param afterCount number of newer events to include after the anchor in chronological order
   * @param requesterTenantId tenant scope enforced for tenant admins; {@code null} for global
   * @param filterTenantId optional explicit tenant filter for global admins
   * @return bounded audit-log neighborhood around the anchor
   * @throws ResourceNotFoundException if the anchor does not exist or is outside the caller scope
   */
  @Transactional(readOnly = true)
  public AuditLogContextSlice findContextAround(
      Long anchorAuditLogId,
      int beforeCount,
      int afterCount,
      Integer requesterTenantId,
      Integer filterTenantId) {

    AuditLog anchor =
        auditLogRepository
            .findById(anchorAuditLogId)
            .orElseThrow(() -> new ResourceNotFoundException("AuditLog", anchorAuditLogId));

    if (!isVisibleToRequester(anchor, requesterTenantId, filterTenantId)) {
      throw new ResourceNotFoundException("AuditLog", anchorAuditLogId);
    }

    List<AuditLog> before =
        fetchOlderContext(anchor, beforeCount + 1, requesterTenantId, filterTenantId);
    boolean hasMoreBefore = before.size() > beforeCount;
    if (hasMoreBefore) {
      before = new ArrayList<>(before.subList(0, beforeCount));
    }
    Collections.reverse(before);

    List<AuditLog> after =
        fetchNewerContext(anchor, afterCount + 1, requesterTenantId, filterTenantId);
    boolean hasMoreAfter = after.size() > afterCount;
    if (hasMoreAfter) {
      after = new ArrayList<>(after.subList(0, afterCount));
    }

    List<AuditLog> items = new ArrayList<>(before.size() + 1 + after.size());
    items.addAll(before);
    items.add(anchor);
    items.addAll(after);

    return new AuditLogContextSlice(items, anchor.getAuditLogId(), hasMoreBefore, hasMoreAfter);
  }

  private List<AuditLog> fetchOlderContext(
      AuditLog anchor, int limit, Integer requesterTenantId, Integer filterTenantId) {
    if (limit <= 0) {
      return List.of();
    }

    Specification<AuditLog> spec =
        visibleToRequester(requesterTenantId, filterTenantId)
            .and(olderThan(anchor.getCreatedAt(), anchor.getAuditLogId()));

    return auditLogRepository
        .findAll(
            spec,
            PageRequest.of(
                0, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("auditLogId"))))
        .getContent();
  }

  private List<AuditLog> fetchNewerContext(
      AuditLog anchor, int limit, Integer requesterTenantId, Integer filterTenantId) {
    if (limit <= 0) {
      return List.of();
    }

    Specification<AuditLog> spec =
        visibleToRequester(requesterTenantId, filterTenantId)
            .and(newerThan(anchor.getCreatedAt(), anchor.getAuditLogId()));

    return auditLogRepository
        .findAll(
            spec,
            PageRequest.of(
                0, limit, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("auditLogId"))))
        .getContent();
  }

  private Specification<AuditLog> visibleToRequester(
      Integer requesterTenantId, Integer filterTenantId) {
    return (root, _, cb) -> {
      if (requesterTenantId != null) {
        return cb.equal(root.get("tenantId"), requesterTenantId);
      }
      if (filterTenantId != null) {
        return cb.equal(root.get("tenantId"), filterTenantId);
      }
      return cb.conjunction();
    };
  }

  private Specification<AuditLog> olderThan(OffsetDateTime createdAt, Long auditLogId) {
    return (root, _, cb) ->
        cb.or(
            cb.lessThan(root.get("createdAt"), createdAt),
            cb.and(
                cb.equal(root.get("createdAt"), createdAt),
                cb.lessThan(root.get("auditLogId"), auditLogId)));
  }

  private Specification<AuditLog> newerThan(OffsetDateTime createdAt, Long auditLogId) {
    return (root, _, cb) ->
        cb.or(
            cb.greaterThan(root.get("createdAt"), createdAt),
            cb.and(
                cb.equal(root.get("createdAt"), createdAt),
                cb.greaterThan(root.get("auditLogId"), auditLogId)));
  }

  private boolean isVisibleToRequester(
      AuditLog auditLog, Integer requesterTenantId, Integer filterTenantId) {
    if (requesterTenantId != null) {
      return requesterTenantId.equals(auditLog.getTenantId());
    }
    if (filterTenantId != null) {
      return filterTenantId.equals(auditLog.getTenantId());
    }
    return true;
  }

  /**
   * Purge audit logs whose checkpoint windows are lifecycle-authorized for physical deletion.
   *
   * <p>Physical deletion is permitted only after checkpoint lifecycle progression reaches {@code
   * PURGEABLE}. This method intentionally has no chain-off or age-only fallback path.
   *
   * @param purgeCutoff exclusive upper bound for rows and checkpoint windows considered for purge
   * @return number of records deleted
   */
  @Transactional
  public int purgeLifecycleEligibleLogs(OffsetDateTime purgeCutoff) {
    EnumSet<CheckpointLifecycleState> deletableStates =
        EnumSet.of(CheckpointLifecycleState.PURGEABLE, CheckpointLifecycleState.PURGED);
    long blockedCheckpointCount =
        checkpointRepository.countByWindowStartBeforeAndLifecycleStateNotIn(
            purgeCutoff, deletableStates);
    long purgeableCheckpointCount =
        checkpointRepository.countByWindowStartBeforeAndLifecycleState(
            purgeCutoff, CheckpointLifecycleState.PURGEABLE);
    boolean hasOldLogs = auditLogRepository.existsByCreatedAtBefore(purgeCutoff);

    if (blockedCheckpointCount > 0 || (hasOldLogs && purgeableCheckpointCount == 0)) {
      logger.warn(
          "Skipping audit log purge: target overlaps checkpoint ranges that are not PURGEABLE.");
      return 0;
    }

    int deleted = auditLogRepository.deleteOlderThan(purgeCutoff);
    logger.info("Purged {} audit logs before {}", deleted, purgeCutoff);
    return deleted;
  }
}
