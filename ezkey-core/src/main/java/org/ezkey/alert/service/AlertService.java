/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AlertService
 * Description: Raise/touch and resolve operator-facing alerts in the ezkey_alert table.
 */

package org.ezkey.alert.service;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application-layer entry point for the minimal alert subsystem.
 *
 * <p>Producers call {@link #raiseOrTouch(AlertType, AlertSeverity, String, String)} when they
 * detect a condition worth surfacing to the operator. The service guarantees a single OPEN row per
 * {@code dedupeKey}: an existing OPEN row is touched (last-seen timestamp + payload + occurrence
 * counter) instead of duplicated. The partial unique index {@code uq_alert_dedupe_key_open}
 * enforces this invariant at the database level.
 *
 * <p>Resolution uses {@link #resolveByDedupeKey(String, AlertResolutionReason, Integer)} and is
 * idempotent: resolving an already-resolved (or absent) alert is a no-op.
 *
 * <p>Every alert lifecycle transition (raise of a new row, resolve) is mirrored in the audit log
 * via {@link EventType#ALERT_RAISED} / {@link EventType#ALERT_RESOLVED} so the SOC 2-oriented trail
 * stays complete without polluting the audit table with the alert content itself.
 *
 * @since 2026
 */
@Service
public class AlertService {

  private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

  private final AlertRepository alertRepository;
  private final AuditLogService auditLogService;

  /**
   * Constructs the alert service.
   *
   * @param alertRepository the alert repository
   * @param auditLogService audit log service for emitting ALERT_RAISED / ALERT_RESOLVED entries
   */
  public AlertService(AlertRepository alertRepository, AuditLogService auditLogService) {
    this.alertRepository = alertRepository;
    this.auditLogService = auditLogService;
  }

  /**
   * Raises a new alert, or touches the existing OPEN one for the same {@code dedupeKey}.
   *
   * <p>Behaviour:
   *
   * <ul>
   *   <li>If an OPEN row with the given {@code dedupeKey} exists, its {@code lastSeenAt}, {@code
   *       payload} and {@code occurrenceCount} are updated. No new audit entry is emitted (the
   *       condition is still active and was already announced).
   *   <li>Otherwise a new row is inserted and an {@link EventType#ALERT_RAISED} audit entry is
   *       written.
   * </ul>
   *
   * @param alertType the alert kind
   * @param severity operator-facing severity
   * @param dedupeKey deduplication key (conventionally {@code "<ALERT_TYPE>:<discriminator>"})
   * @param payload optional JSON payload (string, opaque to this service)
   * @return the persisted alert row
   */
  @Transactional
  public Alert raiseOrTouch(
      AlertType alertType, AlertSeverity severity, String dedupeKey, String payload) {
    if (alertType == null || severity == null || dedupeKey == null || dedupeKey.isBlank()) {
      throw new IllegalArgumentException(
          "alertType, severity and a non-blank dedupeKey are required");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

    Optional<Alert> existing =
        alertRepository.findByDedupeKeyAndStatus(dedupeKey, AlertStatus.OPEN);
    if (existing.isPresent()) {
      Alert alert = existing.get();
      alert.setLastSeenAt(now);
      alert.setOccurrenceCount(alert.getOccurrenceCount() + 1);
      if (payload != null) {
        alert.setPayload(payload);
      }
      return alertRepository.save(alert);
    }

    Alert alert = new Alert();
    alert.setAlertType(alertType);
    alert.setSeverity(severity);
    alert.setStatus(AlertStatus.OPEN);
    alert.setDedupeKey(dedupeKey);
    alert.setPayload(payload);
    alert.setOccurrenceCount(1);
    alert.setCreatedAt(now);
    alert.setLastSeenAt(now);

    Alert saved;
    try {
      saved = alertRepository.saveAndFlush(alert);
    } catch (DataIntegrityViolationException e) {
      // Race: a concurrent producer inserted the OPEN row between our check and insert. The
      // partial unique index on (dedupe_key) WHERE status = 'OPEN' guarantees we can recover by
      // touching the now-present row.
      logger.debug("Concurrent insert detected for dedupeKey={}, falling back to touch", dedupeKey);
      Alert concurrent =
          alertRepository
              .findByDedupeKeyAndStatus(dedupeKey, AlertStatus.OPEN)
              .orElseThrow(() -> e);
      concurrent.setLastSeenAt(now);
      concurrent.setOccurrenceCount(concurrent.getOccurrenceCount() + 1);
      if (payload != null) {
        concurrent.setPayload(payload);
      }
      return alertRepository.save(concurrent);
    }

    emitAuditEntry(EventType.ALERT_RAISED, saved, null);
    logger.info(
        "Alert raised: id={} type={} severity={} dedupeKey={}",
        saved.getAlertId(),
        saved.getAlertType(),
        saved.getSeverity(),
        saved.getDedupeKey());
    return saved;
  }

  /**
   * Transitions the OPEN alert with the given {@code dedupeKey} to {@code RESOLVED}. Idempotent: a
   * missing or already-resolved alert is a no-op.
   *
   * @param dedupeKey deduplication key of the alert to resolve
   * @param reason structured resolution reason
   * @param adminId resolving admin id, or {@code null} for system-driven resolution
   * @return the resolved alert when one was transitioned, otherwise empty
   */
  @Transactional
  public Optional<Alert> resolveByDedupeKey(
      String dedupeKey, AlertResolutionReason reason, Integer adminId) {
    if (dedupeKey == null || dedupeKey.isBlank() || reason == null) {
      throw new IllegalArgumentException("dedupeKey and reason are required");
    }
    Optional<Alert> open = alertRepository.findByDedupeKeyAndStatus(dedupeKey, AlertStatus.OPEN);
    if (open.isEmpty()) {
      return Optional.empty();
    }
    Alert alert = open.get();
    alert.setStatus(AlertStatus.RESOLVED);
    alert.setResolvedAt(OffsetDateTime.now(ZoneOffset.UTC));
    alert.setResolutionReason(reason);
    alert.setResolvedByAdminId(adminId);
    Alert saved = alertRepository.save(alert);
    emitAuditEntry(EventType.ALERT_RESOLVED, saved, adminId);
    logger.info(
        "Alert resolved: id={} type={} dedupeKey={} reason={} adminId={}",
        saved.getAlertId(),
        saved.getAlertType(),
        saved.getDedupeKey(),
        reason,
        adminId);
    return Optional.of(saved);
  }

  /**
   * Returns a single alert by id.
   *
   * @param alertId database identifier
   * @return the alert when present, otherwise empty
   */
  @Transactional(readOnly = true)
  public Optional<Alert> findById(Long alertId) {
    if (alertId == null) {
      return Optional.empty();
    }
    return alertRepository.findById(alertId);
  }

  /**
   * Returns the most recent OPEN alerts, newest first. Used by the dashboard widget.
   *
   * @param limit maximum number of rows to return (must be positive)
   * @return list of OPEN alerts ordered by creation time descending
   */
  @Transactional(readOnly = true)
  public List<Alert> findRecentOpen(int limit) {
    if (limit <= 0) {
      return List.of();
    }
    return alertRepository.findByStatusOrderByCreatedAtDesc(
        AlertStatus.OPEN, PageRequest.of(0, limit));
  }

  /**
   * Paginated search with optional filters.
   *
   * @param status optional status filter
   * @param alertType optional alert-type filter
   * @param severity optional severity filter
   * @param dedupeKey optional exact dedupe-key match (debug aid)
   * @param createdAfter inclusive lower bound on created_at
   * @param createdBefore inclusive upper bound on created_at
   * @param pageable pagination/sort (default sort applied by the controller)
   * @return matching alerts, paginated
   */
  @Transactional(readOnly = true)
  public Page<Alert> search(
      AlertStatus status,
      AlertType alertType,
      AlertSeverity severity,
      String dedupeKey,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      Pageable pageable) {
    Specification<Alert> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }
          if (alertType != null) {
            predicates.add(cb.equal(root.get("alertType"), alertType));
          }
          if (severity != null) {
            predicates.add(cb.equal(root.get("severity"), severity));
          }
          if (dedupeKey != null && !dedupeKey.isBlank()) {
            predicates.add(cb.equal(root.get("dedupeKey"), dedupeKey));
          }
          if (createdAfter != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
          }
          if (createdBefore != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Pageable effective = pageable;
    if (pageable.getSort().isUnsorted()) {
      effective =
          PageRequest.of(
              pageable.getPageNumber(),
              pageable.getPageSize(),
              Sort.by(Sort.Direction.DESC, "createdAt"));
    }
    return alertRepository.findAll(spec, effective);
  }

  private void emitAuditEntry(EventType eventType, Alert alert, Integer adminId) {
    String details =
        "{"
            + "\"alertId\":"
            + alert.getAlertId()
            + ","
            + "\"alertType\":\""
            + alert.getAlertType().name()
            + "\","
            + "\"severity\":\""
            + alert.getSeverity().name()
            + "\","
            + "\"status\":\""
            + alert.getStatus().name()
            + "\","
            + "\"dedupeKey\":\""
            + escapeJson(alert.getDedupeKey())
            + "\""
            + (alert.getResolutionReason() != null
                ? ",\"resolutionReason\":\"" + alert.getResolutionReason().name() + "\""
                : "")
            + "}";

    AuditLog entry =
        AuditLog.builder()
            .eventType(eventType)
            .eventAction("alert-service")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .adminId(adminId)
            .eventDetails(details)
            .build();
    auditLogService.log(entry);
  }

  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
