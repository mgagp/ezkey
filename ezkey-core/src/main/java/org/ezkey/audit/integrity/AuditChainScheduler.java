/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduler: AuditChainScheduler
 * Description: Scheduled job for periodic audit log chain checkpoint creation.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled job that creates periodic chain checkpoints for audit log completeness proof.
 *
 * <p>Runs at a configurable interval (default: every 5 minutes) and creates checkpoint records that
 * link consecutive time windows via HMAC chains. This provides evidence that no audit entries have
 * been inserted, deleted, or reordered between checkpoints.
 *
 * <p><b>Algorithm:</b>
 *
 * <ol>
 *   <li>Detect and alert on any pre-lookback gap (undeclared downtime before the lookback window)
 *   <li>Round current time down to the nearest window boundary
 *   <li>Look back N minutes (default: 60) to find uncheckpointed windows
 *   <li>For each missing window, compute entries_digest from ordered entry_hmac values
 *   <li>Chain with previous checkpoint's chain_hmac
 *   <li>Insert checkpoint row (idempotent via UNIQUE constraint)
 * </ol>
 *
 * <p><b>Defensive gap detection:</b> Before processing the lookback window, the scheduler checks
 * whether the latest checkpoint in the database pre-dates the start of the lookback window. If so,
 * an undeclared gap exists that falls outside the scheduler's catch-up range. A WARNING is logged
 * and an {@code AUDIT_CHAIN_GAP_PENDING} alert is raised (or touched) in the {@code ezkey_alert}
 * table on every scheduler tick until an admin calls {@code POST /lifecycle/declare-gap} to
 * formally close the gap. Regular checkpoints for the lookback window are still created to ensure
 * new post-restart activity is signed.
 *
 * <p><b>HA Safety:</b> Uses ShedLock to ensure only one instance creates checkpoints at a time.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.audit.chain.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class AuditChainScheduler {

  private static final Logger logger = LoggerFactory.getLogger(AuditChainScheduler.class);

  private static final String EMPTY_WINDOW_MARKER = "EMPTY_WINDOW";
  private static final String GENESIS_MARKER = "GENESIS";
  private static final String FIELD_SEPARATOR = "|";

  private final AuditChainProperties chainProperties;
  private final AuditChainCheckpointRepository checkpointRepository;
  private final AuditLogRepository auditLogRepository;
  private final AuditHmacService auditHmacService;
  private final AlertService alertService;
  private final ScheduledJobLastRunService jobLastRunService;

  /**
   * Constructs the scheduler with required dependencies.
   *
   * @param chainProperties chain configuration (window size, lookback window)
   * @param checkpointRepository checkpoint persistence
   * @param auditLogRepository audit log entry repository
   * @param auditHmacService HMAC signing service
   * @param auditLogService audit log service for emitting checkpoint meta-entries
   * @param alertService alert subsystem entry point for raising undeclared-gap alerts
   * @param jobLastRunService scheduled job registry updates
   */
  public AuditChainScheduler(
      AuditChainProperties chainProperties,
      AuditChainCheckpointRepository checkpointRepository,
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService,
      AuditLogService auditLogService,
      AlertService alertService,
      ScheduledJobLastRunService jobLastRunService) {
    this.chainProperties = chainProperties;
    this.checkpointRepository = checkpointRepository;
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
    this.alertService = alertService;
    this.jobLastRunService = jobLastRunService;
  }

  /**
   * Scheduled job to create chain checkpoints for recent time windows.
   *
   * <p>Detects undeclared gaps before the lookback window and emits alerts, then processes all
   * uncheckpointed windows within the lookback period. Idempotent — safe to run multiple times or
   * after missed executions.
   */
  @Scheduled(cron = "${ezkey.audit.chain.cron:1 */5 * * * ?}")
  @SchedulerLock(name = "AUDIT_CHAIN_CHECKPOINT", lockAtMostFor = "PT5M")
  @Transactional
  public void createCheckpoints() {
    if (!auditHmacService.isActive()) {
      logger.debug("Skipping chain checkpoint creation -- HMAC signing not active");
      return;
    }

    try {
      int windowMinutes = chainProperties.getWindowMinutes();
      int lookbackMinutes = chainProperties.getLookbackMinutes();

      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      OffsetDateTime currentWindowStart = roundDownToWindow(now, windowMinutes);
      OffsetDateTime lookbackStart = currentWindowStart.minusMinutes(lookbackMinutes);

      // Defensive check: detect undeclared gap before the lookback window
      detectPreLookbackGap(lookbackStart);

      // Bootstrap: when no checkpoints exist, only create the last completed window (avoids
      // 60 minutes of empty "past" checkpoints). When checkpoints exist, clamp lookback so we
      // never create checkpoints before the first one ever created.
      int created = 0;
      OffsetDateTime windowStart;
      Optional<AuditChainCheckpoint> latestOpt = checkpointRepository.findLatest();
      if (latestOpt.isEmpty()) {
        windowStart = currentWindowStart.minusMinutes(windowMinutes);
      } else {
        Optional<AuditChainCheckpoint> earliestOpt = checkpointRepository.findEarliest();
        if (earliestOpt.isPresent()) {
          OffsetDateTime earliestStart = earliestOpt.get().getWindowStart();
          if (lookbackStart.isBefore(earliestStart)) {
            lookbackStart = earliestStart;
          }
        }
        windowStart = lookbackStart;
      }

      while (windowStart.isBefore(currentWindowStart)) {
        OffsetDateTime windowEnd = windowStart.plusMinutes(windowMinutes);

        if (!checkpointRepository.existsByWindowStartAndWindowEnd(windowStart, windowEnd)) {
          createCheckpoint(windowStart, windowEnd);
          created++;
        }

        windowStart = windowEnd;
      }

      if (created > 0) {
        logger.info("Created {} audit chain checkpoint(s)", created);
      } else {
        logger.debug("All chain checkpoints up to date");
      }

      jobLastRunService.recordSuccess(
          ScheduledJobKey.AUDIT_CHAIN_CHECKPOINT, "Lookback " + lookbackMinutes + " min");
    } catch (Exception e) {
      logger.error("Failed to create audit chain checkpoints: {}", e.getMessage(), e);
    }
  }

  /**
   * Detects whether the latest checkpoint in the database predates the start of the current
   * lookback window, indicating an undeclared gap that the scheduler cannot cover.
   *
   * <p>When a gap is detected, a WARNING is logged and an {@code AUDIT_CHAIN_GAP_PENDING} alert is
   * raised (or touched if already open) in the {@code ezkey_alert} table. Operators see this alert
   * in the Admin UI and must call {@code POST /lifecycle/declare-gap} to formally close the gap;
   * that workflow auto-resolves the matching alert.
   *
   * <p>Regular checkpoints for the lookback window are still created even when a gap is detected:
   * post-restart audit activity must be signed and checkpointed regardless.
   *
   * @param lookbackStart start of the scheduler's current lookback window
   */
  private void detectPreLookbackGap(OffsetDateTime lookbackStart) {
    try {
      Optional<AuditChainCheckpoint> latestOpt = checkpointRepository.findLatest();
      if (latestOpt.isEmpty()) {
        return; // No checkpoints yet — system just started, nothing to detect
      }

      AuditChainCheckpoint latest = latestOpt.get();

      // A gap exists if the latest checkpoint's window_end is before the lookback window start.
      // This means there are uncovered windows between the last checkpoint and the scheduler's
      // catch-up horizon that will never be automatically filled.
      if (latest.getWindowEnd().isBefore(lookbackStart)) {
        OffsetDateTime gapStart = latest.getWindowEnd();
        long gapMinutes = ChronoUnit.MINUTES.between(gapStart, lookbackStart);

        logger.warn(
            "AUDIT CHAIN GAP DETECTED: Last checkpoint window_end={}, lookback starts at {}."
                + " Undeclared gap of ~{} minutes. "
                + "Call POST /lifecycle/declare-gap with anchorCheckpointId={} to resolve.",
            gapStart,
            lookbackStart,
            gapMinutes,
            latest.getCheckpointId());

        String eventDetails =
            "{"
                + "\"gapStart\":\""
                + gapStart
                + "\","
                + "\"estimatedGapEnd\":\""
                + lookbackStart
                + "\","
                + "\"estimatedGapMinutes\":"
                + gapMinutes
                + ","
                + "\"anchorCheckpointId\":"
                + latest.getCheckpointId()
                + ","
                + "\"message\":\"Undeclared gap detected before scheduler lookback window."
                + " Admin action required: POST /lifecycle/declare-gap\""
                + "}";

        alertService.raiseOrTouch(
            AlertType.AUDIT_CHAIN_GAP_PENDING,
            AlertSeverity.WARNING,
            "AUDIT_CHAIN_GAP_PENDING:" + latest.getCheckpointId(),
            eventDetails);
      }
    } catch (Exception e) {
      logger.error("Failed to check for pre-lookback gap: {}", e.getMessage(), e);
    }
  }

  /**
   * Creates a single chain checkpoint for the given time window.
   *
   * @param windowStart start of the window (inclusive)
   * @param windowEnd end of the window (exclusive)
   */
  private void createCheckpoint(OffsetDateTime windowStart, OffsetDateTime windowEnd) {
    Specification<org.ezkey.audit.domain.entity.AuditLog> spec =
        (root, _, cb) ->
            cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), windowStart),
                cb.lessThan(root.get("createdAt"), windowEnd));

    List<org.ezkey.audit.domain.entity.AuditLog> entries =
        auditLogRepository.findAll(spec, Sort.by("auditLogId").ascending());

    String entriesDigest;
    Long firstId = null;
    Long lastId = null;

    if (entries.isEmpty()) {
      entriesDigest = auditHmacService.computeHmac(EMPTY_WINDOW_MARKER);
    } else {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < entries.size(); i++) {
        if (i > 0) {
          sb.append(FIELD_SEPARATOR);
        }
        String hmac = entries.get(i).getEntryHmac();
        sb.append(hmac != null ? hmac : "");
      }
      entriesDigest = auditHmacService.computeHmac(sb.toString());
      firstId = entries.get(0).getAuditLogId();
      lastId = entries.get(entries.size() - 1).getAuditLogId();
    }

    String prevChainHmac =
        checkpointRepository.findLatest().map(AuditChainCheckpoint::getChainHmac).orElse(null);

    String chainInput =
        entriesDigest + FIELD_SEPARATOR + (prevChainHmac != null ? prevChainHmac : GENESIS_MARKER);
    String chainHmac = auditHmacService.computeHmac(chainInput);

    AuditChainCheckpoint checkpoint = new AuditChainCheckpoint();
    checkpoint.setWindowStart(windowStart);
    checkpoint.setWindowEnd(windowEnd);
    checkpoint.setEntryCount(entries.size());
    checkpoint.setFirstEntryId(firstId);
    checkpoint.setLastEntryId(lastId);
    checkpoint.setEntriesDigest(entriesDigest);
    checkpoint.setPrevChainHmac(prevChainHmac);
    checkpoint.setChainHmac(chainHmac);

    checkpointRepository.save(checkpoint);
  }

  /**
   * Rounds a timestamp down to the nearest window boundary.
   *
   * <p>Public to allow reuse in {@link AuditLifecycleService} for {@code gapEnd} auto-derivation
   * without duplicating the arithmetic.
   *
   * @param time the timestamp to round
   * @param windowMinutes the window size in minutes
   * @return rounded timestamp
   */
  public static OffsetDateTime roundDownToWindow(OffsetDateTime time, int windowMinutes) {
    long minutesSinceEpoch = time.toEpochSecond() / 60;
    long roundedMinutes = (minutesSinceEpoch / windowMinutes) * windowMinutes;
    return OffsetDateTime.ofInstant(
        java.time.Instant.ofEpochSecond(roundedMinutes * 60), ZoneOffset.UTC);
  }
}
