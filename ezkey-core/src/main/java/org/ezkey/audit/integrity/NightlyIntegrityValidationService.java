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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.ezkey.audit.dto.ChainIntegrityViolation;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import org.ezkey.audit.dto.IntegrityViolationCappedList;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    List<EntryIntegrityViolation> entryViolations =
        EntryHmacViolationCollector.collectAll(
            auditLogRepository, auditHmacService, windowStart, windowEndExclusive);
    AuditChainVerificationService.ChainVerificationReport chainReport =
        chainVerificationService.verifyChain(windowStart, windowEndExclusive);

    boolean entryHmacIntact = entryViolations.isEmpty();
    boolean chainIntact = chainReport.intact();
    boolean integrityFailure = !entryHmacIntact || !chainIntact;

    boolean alertRaised = false;
    if (integrityFailure && shouldRaiseIntegrityAlert(chainReport, entryHmacIntact)) {
      raiseIntegrityRuptureAlert(windowStart, windowEndExclusive, chainReport, entryViolations);
      alertRaised = true;
    }

    emitCompletionAudit(
        windowStart, windowEndExclusive, chainReport, entryViolations, integrityFailure);

    logger.info(
        "Nightly integrity validation completed: window=[{} to {}), intact={}, alertRaised={},"
            + " chainStatus={}, entryViolations={}",
        windowStart,
        windowEndExclusive,
        !integrityFailure,
        alertRaised,
        chainReport.status(),
        entryViolations.size());

    return new NightlyIntegrityValidationResult(
        windowStart,
        windowEndExclusive,
        scope,
        !integrityFailure,
        alertRaised,
        chainReport.status(),
        entryViolations.size(),
        chainReport.violations().size());
  }

  private void raiseIntegrityRuptureAlert(
      OffsetDateTime windowStart,
      OffsetDateTime windowEnd,
      AuditChainVerificationService.ChainVerificationReport chainReport,
      List<EntryIntegrityViolation> entryViolations) {
    IntegrityViolationCappedList<EntryIntegrityViolation> cappedEntries =
        IntegrityViolationCappedList.of(
            entryViolations, IntegrityViolationCappedList.ALERT_ENTRY_CAP);
    IntegrityViolationCappedList<ChainIntegrityViolation> cappedChains =
        IntegrityViolationCappedList.of(
            chainReport.chainViolations(), IntegrityViolationCappedList.ALERT_CHAIN_CAP);

    String dedupeKey = INTEGRITY_RUPTURE_DEDUPE_PREFIX + windowStart.toInstant().toEpochMilli();
    String payload =
        AuditDetailsBuilder.builder()
            .custom("windowStart", windowStart.toString())
            .custom("windowEnd", windowEnd.toString())
            .custom("chainStatus", chainReport.status())
            .custom("violationCount", chainReport.violations().size())
            .custom("entryHmacViolationCount", entryViolations.size())
            .custom("entryViolations", cappedListToMap(cappedEntries))
            .custom("chainViolations", cappedListToMap(cappedChains))
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
      List<EntryIntegrityViolation> entryViolations,
      boolean integrityFailure) {
    IntegrityViolationCappedList<EntryIntegrityViolation> cappedEntries =
        IntegrityViolationCappedList.of(
            entryViolations, IntegrityViolationCappedList.EVENT_ENTRY_CAP);
    IntegrityViolationCappedList<ChainIntegrityViolation> cappedChains =
        IntegrityViolationCappedList.of(
            chainReport.chainViolations(), IntegrityViolationCappedList.EVENT_CHAIN_CAP);

    String details =
        AuditDetailsBuilder.builder()
            .custom("windowStart", windowStart.toString())
            .custom("windowEnd", windowEnd.toString())
            .custom("intact", !integrityFailure)
            .custom("chainStatus", chainReport.status())
            .custom("entryHmacViolationCount", entryViolations.size())
            .custom("chainViolationCount", chainReport.violations().size())
            .custom("entryViolations", cappedListToMap(cappedEntries))
            .custom("chainViolations", cappedListToMap(cappedChains))
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

  private static Map<String, Object> cappedListToMap(IntegrityViolationCappedList<?> capped) {
    Map<String, Object> map = new HashMap<>();
    map.put("items", capped.items());
    map.put("totalCount", capped.totalCount());
    map.put("returnedCount", capped.returnedCount());
    map.put("truncated", capped.truncated());
    return map;
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
