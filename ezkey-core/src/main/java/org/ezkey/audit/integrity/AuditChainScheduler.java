/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.audit.domain.repository.AuditLogRepository;
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
 *   <li>Round current time down to the nearest window boundary
 *   <li>Look back N minutes (default: 60) to find uncheckpointed windows
 *   <li>For each missing window, compute entries_digest from ordered entry_hmac values
 *   <li>Chain with previous checkpoint's chain_hmac
 *   <li>Insert checkpoint row (idempotent via UNIQUE constraint)
 * </ol>
 *
 * <p><b>HA Safety:</b> Uses ShedLock to ensure only one instance creates checkpoints at a time.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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

  public AuditChainScheduler(
      AuditChainProperties chainProperties,
      AuditChainCheckpointRepository checkpointRepository,
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService) {
    this.chainProperties = chainProperties;
    this.checkpointRepository = checkpointRepository;
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
  }

  /**
   * Scheduled job to create chain checkpoints for recent time windows.
   *
   * <p>Processes all uncheckpointed windows within the lookback period, creating chain links in
   * chronological order. Idempotent -- safe to run multiple times or after missed executions.
   */
  @Scheduled(cron = "${ezkey.audit.chain.cron:0 */5 * * * ?}")
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

      int created = 0;
      OffsetDateTime windowStart = lookbackStart;

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
    } catch (Exception e) {
      logger.error("Failed to create audit chain checkpoints: {}", e.getMessage(), e);
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
        (root, query, cb) ->
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
   * @param time the timestamp to round
   * @param windowMinutes the window size in minutes
   * @return rounded timestamp
   */
  static OffsetDateTime roundDownToWindow(OffsetDateTime time, int windowMinutes) {
    long minutesSinceEpoch = time.toEpochSecond() / 60;
    long roundedMinutes = (minutesSinceEpoch / windowMinutes) * windowMinutes;
    return OffsetDateTime.ofInstant(
        java.time.Instant.ofEpochSecond(roundedMinutes * 60), ZoneOffset.UTC);
  }
}
