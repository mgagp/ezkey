/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: RetroactiveIntegrityValidationService
 * Description: Retroactive integrity validation over a completed time window.
 */

package org.ezkey.audit.integrity;

import java.time.Duration;
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
 * <p>Used by the nightly scheduler and by Global Admin {@code POST …/integrity-validation/run}.
 *
 * @since 2026
 */
@Service
public class RetroactiveIntegrityValidationService {

  private static final Logger logger =
      LoggerFactory.getLogger(RetroactiveIntegrityValidationService.class);

  private final NightlyIntegrityProperties nightlyProperties;
  private final RetroactiveIntegrityProperties retroactiveProperties;
  private final AuditChainVerificationService chainVerificationService;
  private final AuditHmacService auditHmacService;
  private final AuditLogRepository auditLogRepository;
  private final AlertService alertService;
  private final AlertRepository alertRepository;
  private final AuditLogService auditLogService;
  private final EntryIntegrityViolationClassifier entryIntegrityViolationClassifier;

  /**
   * Constructs the service.
   *
   * @param nightlyProperties nightly batch window configuration
   * @param retroactiveProperties operator-trigger limits
   * @param chainVerificationService checkpoint chain verification
   * @param auditHmacService per-entry HMAC verification
   * @param auditLogRepository audit log access
   * @param alertService alert raise/touch
   * @param alertRepository alert existence checks
   * @param auditLogService audit log for batch completion events
   * @param entryIntegrityViolationClassifier reporting vs alert-eligible entry classifier
   */
  public RetroactiveIntegrityValidationService(
      NightlyIntegrityProperties nightlyProperties,
      RetroactiveIntegrityProperties retroactiveProperties,
      AuditChainVerificationService chainVerificationService,
      AuditHmacService auditHmacService,
      AuditLogRepository auditLogRepository,
      AlertService alertService,
      AlertRepository alertRepository,
      AuditLogService auditLogService,
      EntryIntegrityViolationClassifier entryIntegrityViolationClassifier) {
    this.nightlyProperties = nightlyProperties;
    this.retroactiveProperties = retroactiveProperties;
    this.chainVerificationService = chainVerificationService;
    this.auditHmacService = auditHmacService;
    this.auditLogRepository = auditLogRepository;
    this.alertService = alertService;
    this.alertRepository = alertRepository;
    this.auditLogService = auditLogService;
    this.entryIntegrityViolationClassifier = entryIntegrityViolationClassifier;
  }

  /**
   * Validates the retroactive window ending at {@code windowEndExclusive} using the configured
   * nightly window length.
   *
   * @param windowEndExclusive end of the validation window (exclusive), typically batch start time
   * @return structured result for registry and audit logging
   */
  @Transactional
  public RetroactiveIntegrityValidationResult validateWindow(OffsetDateTime windowEndExclusive) {
    OffsetDateTime windowStart = windowEndExclusive.minusHours(nightlyProperties.getWindowHours());
    return runValidation(
        windowStart, windowEndExclusive, RetroactiveIntegrityValidationOptions.scheduled());
  }

  /**
   * Validates audit integrity over {@code [from, to)} with explicit bounds and run options.
   *
   * @param from inclusive window start
   * @param to exclusive window end
   * @param options per-run alert and trigger metadata
   * @return structured result for API, registry, and audit logging
   */
  @Transactional
  public RetroactiveIntegrityValidationResult runValidation(
      OffsetDateTime from, OffsetDateTime to, RetroactiveIntegrityValidationOptions options) {
    String scope = formatScope(from, to);

    if (!auditHmacService.isActive()) {
      return RetroactiveIntegrityValidationResult.skipped(
          scope, "HMAC signing is not active", options.triggerSource());
    }

    EntryIntegrityViolationClassifier.ViolationCollection entryViolationCollection =
        entryIntegrityViolationClassifier.collectRangeViolations(auditLogRepository, from, to);
    List<EntryIntegrityViolation> entryViolations = entryViolationCollection.reportingViolations();
    List<EntryIntegrityViolation> alertEligibleEntryViolations =
        entryViolationCollection.alertEligibleViolations();
    AuditChainVerificationService.ChainVerificationReport chainReport =
        chainVerificationService.verifyChain(from, to);

    boolean entryHmacIntact = entryViolations.isEmpty();
    boolean chainIntact = chainReport.intact();
    boolean integrityFailure = !entryHmacIntact || !chainIntact;
    boolean hasOpenHeartbeatStale = hasOpenHeartbeatStaleAlert();

    boolean alertRaised = false;
    Long alertId = null;
    if (options.raiseAlert()
        && integrityFailure
        && shouldRaiseIntegrityAlert(
            chainReport, alertEligibleEntryViolations, hasOpenHeartbeatStale)) {
      alertId =
          raiseIntegrityRuptureAlert(
              from,
              to,
              chainReport,
              entryViolations,
              alertEligibleEntryViolations,
              hasOpenHeartbeatStale,
              options.triggerSource());
      alertRaised = true;
    }

    emitCompletionAudit(
        from,
        to,
        chainReport,
        entryViolations,
        integrityFailure,
        options.triggerSource(),
        options.requestedByAdminId());

    logger.info(
        "Retroactive integrity validation completed: trigger={}, window=[{} to {}), intact={},"
            + " alertRaised={}, chainStatus={}, entryViolations={}",
        options.triggerSource(),
        from,
        to,
        !integrityFailure,
        alertRaised,
        chainReport.status(),
        entryViolations.size());

    return new RetroactiveIntegrityValidationResult(
        from,
        to,
        scope,
        false,
        null,
        !integrityFailure,
        alertRaised,
        alertId,
        chainReport.status(),
        entryViolations.size(),
        alertEligibleEntryViolations.size(),
        chainReport.violations().size(),
        options.triggerSource());
  }

  /**
   * Validates operator-selected bounds before {@link #runValidation}.
   *
   * @param from inclusive window start
   * @param to exclusive window end
   * @throws IllegalArgumentException when bounds are invalid or exceed the configured cap
   */
  public void validateOperatorWindow(OffsetDateTime from, OffsetDateTime to) {
    if (from == null || to == null) {
      throw new IllegalArgumentException(
          "Date range is required. Provide from (inclusive) and to (exclusive) as ISO-8601.");
    }
    if (!to.isAfter(from)) {
      throw new IllegalArgumentException(
          "Invalid date range: to must be after from (exclusive end, inclusive start).");
    }
    int maxHours = resolveOperatorMaxWindowHours();
    Duration duration = Duration.between(from, to);
    if (duration.compareTo(Duration.ofHours(maxHours)) > 0) {
      throw new IllegalArgumentException(
          "Validation window exceeds maximum of " + maxHours + " hours.");
    }
  }

  private int resolveOperatorMaxWindowHours() {
    Integer configured = retroactiveProperties.getOperatorMaxWindowHours();
    if (configured != null) {
      return configured;
    }
    return nightlyProperties.getWindowHours();
  }

  private Long raiseIntegrityRuptureAlert(
      OffsetDateTime windowStart,
      OffsetDateTime windowEnd,
      AuditChainVerificationService.ChainVerificationReport chainReport,
      List<EntryIntegrityViolation> entryViolations,
      List<EntryIntegrityViolation> alertEligibleEntryViolations,
      boolean hasOpenHeartbeatStale,
      RetroactiveIntegrityValidationTriggerSource triggerSource) {
    IntegrityViolationCappedList<EntryIntegrityViolation> cappedEntries =
        IntegrityViolationCappedList.of(
            entryViolations, IntegrityViolationCappedList.ALERT_ENTRY_CAP);
    boolean chainAlertEligible =
        IntegrityRuptureIncidentFingerprint.isChainAlertEligible(
            chainReport, hasOpenHeartbeatStale);
    List<ChainIntegrityViolation> alertEligibleChains =
        IntegrityRuptureIncidentFingerprint.alertEligibleChainViolations(
            chainReport, chainAlertEligible);
    IntegrityRuptureIncidentFingerprint.BoundaryPair boundaries =
        IntegrityRuptureIncidentFingerprint.fingerprintBoundaries(chainReport, chainAlertEligible);
    IntegrityViolationCappedList<ChainIntegrityViolation> cappedChains =
        IntegrityViolationCappedList.of(
            chainReport.chainViolations(), IntegrityViolationCappedList.ALERT_CHAIN_CAP);

    String dedupeKey =
        IntegrityRuptureIncidentFingerprint.computeDedupeKey(
            boundaries.failBoundary(),
            boundaries.resumeBoundary(),
            alertEligibleEntryViolations,
            alertEligibleChains);
    String message =
        triggerSource == RetroactiveIntegrityValidationTriggerSource.OPERATOR
            ? "Operator retroactive integrity validation detected a rupture in the window"
            : "Retroactive integrity validation detected a rupture in the window";
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
                boundaries.failBoundary() != null ? boundaries.failBoundary().toString() : null)
            .custom(
                "resumeBoundary",
                boundaries.resumeBoundary() != null ? boundaries.resumeBoundary().toString() : null)
            .custom("message", message)
            .toJson();

    Alert alert =
        alertService.raiseOrTouch(
            AlertType.AUDIT_INTEGRITY_RUPTURE, AlertSeverity.CRITICAL, dedupeKey, payload);
    logger.warn(
        "Raised/touched AUDIT_INTEGRITY_RUPTURE alert id={} for window [{} to {})",
        alert.getAlertId(),
        windowStart,
        windowEnd);
    return alert.getAlertId();
  }

  private void emitCompletionAudit(
      OffsetDateTime windowStart,
      OffsetDateTime windowEnd,
      AuditChainVerificationService.ChainVerificationReport chainReport,
      List<EntryIntegrityViolation> entryViolations,
      boolean integrityFailure,
      RetroactiveIntegrityValidationTriggerSource triggerSource,
      Integer requestedByAdminId) {
    IntegrityViolationCappedList<EntryIntegrityViolation> cappedEntries =
        IntegrityViolationCappedList.of(
            entryViolations, IntegrityViolationCappedList.EVENT_ENTRY_CAP);
    IntegrityViolationCappedList<ChainIntegrityViolation> cappedChains =
        IntegrityViolationCappedList.of(
            chainReport.chainViolations(), IntegrityViolationCappedList.EVENT_CHAIN_CAP);

    AuditDetailsBuilder builder =
        AuditDetailsBuilder.builder()
            .custom("windowStart", windowStart.toString())
            .custom("windowEnd", windowEnd.toString())
            .custom("intact", !integrityFailure)
            .custom("chainStatus", chainReport.status())
            .custom("entryHmacViolationCount", entryViolations.size())
            .custom("chainViolationCount", chainReport.violations().size())
            .custom("entryViolations", cappedListToMap(cappedEntries))
            .custom("chainViolations", cappedListToMap(cappedChains))
            .custom("triggerSource", triggerSource.name());
    if (requestedByAdminId != null) {
      builder.custom("requestedByAdminId", requestedByAdminId);
    }
    String details = builder.toJson();

    AuditLog entry =
        AuditLog.builder()
            .eventType(EventType.NIGHTLY_INTEGRITY_VALIDATION_COMPLETED)
            .eventAction("retroactive-integrity-validation")
            .eventStatus(integrityFailure ? EventStatus.FAILURE : EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .eventDetails(details)
            .build();
    auditLogService.log(entry);
  }

  private String formatScope(OffsetDateTime start, OffsetDateTime end) {
    long hours = Duration.between(start, end).toHours();
    return "Validated " + hours + " h ending " + end.withOffsetSameInstant(ZoneOffset.UTC);
  }

  /**
   * C8-6: when the only chain finding is undeclared gaps and heartbeat is already surfaced, defer
   * duplicate integrity alert.
   */
  private boolean shouldRaiseIntegrityAlert(
      AuditChainVerificationService.ChainVerificationReport chainReport,
      List<EntryIntegrityViolation> alertEligibleEntryViolations,
      boolean hasOpenHeartbeatStale) {
    if (!alertEligibleEntryViolations.isEmpty()) {
      return true;
    }
    return IntegrityRuptureIncidentFingerprint.isChainAlertEligible(
        chainReport, hasOpenHeartbeatStale);
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
   * Result of a retroactive integrity validation run.
   *
   * @param windowStart inclusive start of validated window
   * @param windowEnd exclusive end of validated window
   * @param scope human-readable scope for registry
   * @param skipped true when validation did not run
   * @param skipReason reason when skipped
   * @param intact true when no integrity failures were detected
   * @param alertRaised true when an integrity rupture alert was raised/touched
   * @param alertId alert id when raised/touched
   * @param chainStatus status string from chain verification
   * @param entryHmacViolationCount count of per-entry HMAC failures (reporting set)
   * @param entryAlertEligibleCount count of alert-eligible entry violations
   * @param chainViolationCount count of chain-level violation messages
   * @param triggerSource scheduled or operator origin
   */
  public record RetroactiveIntegrityValidationResult(
      OffsetDateTime windowStart,
      OffsetDateTime windowEnd,
      String scope,
      boolean skipped,
      String skipReason,
      boolean intact,
      boolean alertRaised,
      Long alertId,
      String chainStatus,
      int entryHmacViolationCount,
      int entryAlertEligibleCount,
      int chainViolationCount,
      RetroactiveIntegrityValidationTriggerSource triggerSource) {

    static RetroactiveIntegrityValidationResult skipped(
        String scope, String reason, RetroactiveIntegrityValidationTriggerSource triggerSource) {
      return new RetroactiveIntegrityValidationResult(
          null, null, scope, true, reason, true, false, null, reason, 0, 0, 0, triggerSource);
    }
  }
}
