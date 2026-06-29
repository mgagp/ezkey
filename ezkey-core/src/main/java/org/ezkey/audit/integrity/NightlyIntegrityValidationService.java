/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: NightlyIntegrityValidationService
 * Description: Retroactive integrity validation over a completed time window.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs retroactive integrity validation over a completed window: per-audit HMAC checks plus
 * checkpoint chain verification via {@link AuditChainVerificationService}.
 *
 * @since 2026
 */
@Service
public class NightlyIntegrityValidationService {

  private static final Logger logger =
      LoggerFactory.getLogger(NightlyIntegrityValidationService.class);

  private static final String INTEGRITY_RUPTURE_DEDUPE_PREFIX = "AUDIT_INTEGRITY_RUPTURE:";

  private final NightlyIntegrityProperties nightlyProperties;
  private final AuditChainVerificationService chainVerificationService;
  private final AuditHmacService auditHmacService;
  private final AuditLogRepository auditLogRepository;
  private final AlertService alertService;
  private final AlertRepository alertRepository;
  private final AuditLogService auditLogService;

  /**
   * Constructs the service.
   *
   * @param nightlyProperties nightly batch configuration
   * @param chainVerificationService checkpoint chain verification
   * @param auditHmacService per-entry HMAC verification
   * @param auditLogRepository audit log access
   * @param alertService alert raise/touch
   * @param alertRepository alert existence checks
   * @param auditLogService audit log for batch completion events
   */
  public NightlyIntegrityValidationService(
      NightlyIntegrityProperties nightlyProperties,
      AuditChainVerificationService chainVerificationService,
      AuditHmacService auditHmacService,
      AuditLogRepository auditLogRepository,
      AlertService alertService,
      AlertRepository alertRepository,
      AuditLogService auditLogService) {
    this.nightlyProperties = nightlyProperties;
    this.chainVerificationService = chainVerificationService;
    this.auditHmacService = auditHmacService;
    this.auditLogRepository = auditLogRepository;
    this.alertService = alertService;
    this.alertRepository = alertRepository;
    this.auditLogService = auditLogService;
  }

  /**
   * Validates the retroactive window ending at {@code windowEndExclusive}.
   *
   * @param windowEndExclusive end of the validation window (exclusive), typically batch start time
   * @return structured result for registry and audit logging
   */
  @Transactional
  public NightlyIntegrityValidationResult validateWindow(OffsetDateTime windowEndExclusive) {
    OffsetDateTime windowStart = windowEndExclusive.minusHours(nightlyProperties.getWindowHours());
    String scope = formatScope(windowStart, windowEndExclusive);

    if (!auditHmacService.isActive()) {
      return NightlyIntegrityValidationResult.skipped(scope, "HMAC signing is not active");
    }

    List<String> entryHmacViolations = verifyEntryHmacs(windowStart, windowEndExclusive);
    AuditChainVerificationService.ChainVerificationReport chainReport =
        chainVerificationService.verifyChain(windowStart, windowEndExclusive);

    boolean entryHmacIntact = entryHmacViolations.isEmpty();
    boolean chainIntact = chainReport.intact();
    boolean integrityFailure = !entryHmacIntact || !chainIntact;

    boolean alertRaised = false;
    if (integrityFailure && shouldRaiseIntegrityAlert(chainReport, entryHmacIntact)) {
      raiseIntegrityRuptureAlert(windowStart, windowEndExclusive, chainReport, entryHmacViolations);
      alertRaised = true;
    }

    emitCompletionAudit(
        windowStart, windowEndExclusive, chainReport, entryHmacViolations, integrityFailure);

    logger.info(
        "Nightly integrity validation completed: window=[{} to {}), intact={}, alertRaised={},"
            + " chainStatus={}, entryViolations={}",
        windowStart,
        windowEndExclusive,
        !integrityFailure,
        alertRaised,
        chainReport.status(),
        entryHmacViolations.size());

    return new NightlyIntegrityValidationResult(
        windowStart,
        windowEndExclusive,
        scope,
        !integrityFailure,
        alertRaised,
        chainReport.status(),
        entryHmacViolations.size(),
        chainReport.violations().size());
  }

  private List<String> verifyEntryHmacs(OffsetDateTime from, OffsetDateTime to) {
    Specification<AuditLog> spec =
        (root, query, cb) ->
            cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThan(root.get("createdAt"), to));

    List<AuditLog> entries = auditLogRepository.findAll(spec, Sort.by("auditLogId").ascending());
    List<String> violations = new ArrayList<>();
    for (AuditLog entry : entries) {
      if (entry.getEntryHmac() == null) {
        violations.add("Missing entry_hmac on auditLogId=" + entry.getAuditLogId());
        continue;
      }
      if (!auditHmacService.verifyHmac(entry)) {
        violations.add("Entry HMAC mismatch on auditLogId=" + entry.getAuditLogId());
      }
    }
    return violations;
  }

  /**
   * C8-6: when the only chain finding is undeclared gaps and heartbeat is already surfaced, defer
   * duplicate integrity alert.
   */
  private boolean shouldRaiseIntegrityAlert(
      AuditChainVerificationService.ChainVerificationReport chainReport, boolean entryHmacIntact) {
    if (!entryHmacIntact) {
      return true;
    }
    if (chainReport.invalidCheckpoints() > 0) {
      return true;
    }
    if (chainReport.intact()) {
      return false;
    }
    if (chainReport.undeclaredGaps().isEmpty()) {
      return true;
    }
    return !hasOpenHeartbeatStaleAlert();
  }

  private boolean hasOpenHeartbeatStaleAlert() {
    return alertRepository
        .findByDedupeKeyAndStatus(
            AuditChainHeartbeatGuardService.HEARTBEAT_STALE_ALERT_DEDUPE_KEY, AlertStatus.OPEN)
        .isPresent();
  }

  private void raiseIntegrityRuptureAlert(
      OffsetDateTime windowStart,
      OffsetDateTime windowEnd,
      AuditChainVerificationService.ChainVerificationReport chainReport,
      List<String> entryHmacViolations) {
    String dedupeKey = INTEGRITY_RUPTURE_DEDUPE_PREFIX + windowStart.toInstant().toEpochMilli();
    String payload =
        AuditDetailsBuilder.builder()
            .custom("windowStart", windowStart.toString())
            .custom("windowEnd", windowEnd.toString())
            .custom("chainStatus", chainReport.status())
            .custom("violationCount", chainReport.violations().size())
            .custom("entryHmacViolationCount", entryHmacViolations.size())
            .custom(
                "failBoundary",
                chainReport.coverageStart() != null ? chainReport.coverageStart().toString() : null)
            .custom(
                "resumeBoundary",
                chainReport.coverageEnd() != null ? chainReport.coverageEnd().toString() : null)
            .custom(
                "message",
                "Nightly integrity validation detected a rupture in the retroactive window")
            .toJson();

    Alert alert =
        alertService.raiseOrTouch(
            AlertType.AUDIT_INTEGRITY_RUPTURE, AlertSeverity.CRITICAL, dedupeKey, payload);
    logger.warn(
        "Raised/touched AUDIT_INTEGRITY_RUPTURE alert id={} for window [{} to {})",
        alert.getAlertId(),
        windowStart,
        windowEnd);
  }

  private void emitCompletionAudit(
      OffsetDateTime windowStart,
      OffsetDateTime windowEnd,
      AuditChainVerificationService.ChainVerificationReport chainReport,
      List<String> entryHmacViolations,
      boolean integrityFailure) {
    String details =
        AuditDetailsBuilder.builder()
            .custom("windowStart", windowStart.toString())
            .custom("windowEnd", windowEnd.toString())
            .custom("intact", !integrityFailure)
            .custom("chainStatus", chainReport.status())
            .custom("entryHmacViolationCount", entryHmacViolations.size())
            .custom("chainViolationCount", chainReport.violations().size())
            .toJson();

    AuditLog entry =
        AuditLog.builder()
            .eventType(EventType.NIGHTLY_INTEGRITY_VALIDATION_COMPLETED)
            .eventAction("nightly-integrity-validation")
            .eventStatus(integrityFailure ? EventStatus.FAILURE : EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .eventDetails(details)
            .build();
    auditLogService.log(entry);
  }

  private String formatScope(OffsetDateTime start, OffsetDateTime end) {
    long hours = java.time.Duration.between(start, end).toHours();
    return "Validated " + hours + " h ending " + end.withOffsetSameInstant(ZoneOffset.UTC);
  }

  /**
   * Result of a nightly integrity validation run.
   *
   * @param windowStart inclusive start of validated window
   * @param windowEnd exclusive end of validated window
   * @param scope human-readable scope for registry
   * @param intact true when no integrity failures were detected
   * @param alertRaised true when an integrity rupture alert was raised/touched
   * @param chainStatus status string from chain verification
   * @param entryHmacViolationCount count of per-entry HMAC failures
   * @param chainViolationCount count of chain-level violation messages
   */
  public record NightlyIntegrityValidationResult(
      OffsetDateTime windowStart,
      OffsetDateTime windowEnd,
      String scope,
      boolean intact,
      boolean alertRaised,
      String chainStatus,
      int entryHmacViolationCount,
      int chainViolationCount) {

    static NightlyIntegrityValidationResult skipped(String scope, String reason) {
      return new NightlyIntegrityValidationResult(null, null, scope, true, false, reason, 0, 0);
    }
  }
}
