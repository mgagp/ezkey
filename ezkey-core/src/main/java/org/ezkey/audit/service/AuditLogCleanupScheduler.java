/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduler: AuditLogCleanupScheduler
 * Description: Scheduled job for audit log retention management.
 */

package org.ezkey.audit.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.audit.dto.ArchiveSealRequest;
import org.ezkey.audit.dto.ArchiveSealResult;
import org.ezkey.audit.integrity.AuditChainProperties;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for audit log retention management.
 *
 * <p>When the audit chain is enabled ({@code ezkey.audit.chain.enabled=true}), the scheduler
 * performs a <b>chain seal</b> on the period older than the retention window instead of deleting
 * entries. Sealing marks checkpoints as {@code ARCHIVE_SEAL}, preserves the cryptographic chain
 * linkage, and allows a DBA to safely {@code DROP} the partition without corrupting chain
 * integrity.
 *
 * <p>When the chain is disabled (default), the scheduler falls back to direct deletion of entries
 * older than the retention period.
 *
 * <p><b>Default Schedule:</b> Daily at 2 AM (configurable via cron expression)
 *
 * <p><b>Default Retention:</b> 90 days (SOC2-ready, configurable)
 *
 * <p><b>Configuration Properties:</b>
 *
 * <ul>
 *   <li>ezkey.audit.retention-days - Days to retain audit logs (default: 90)
 *   <li>ezkey.audit.cleanup.enabled - Enable/disable cleanup (default: true)
 *   <li>ezkey.audit.cleanup.cron - Cron expression for schedule (default: 0 0 2 * * ?)
 *   <li>ezkey.audit.chain.enabled - When true, sealing is used instead of deletion (default: true)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.audit.cleanup.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuditLogCleanupScheduler {

  private static final Logger logger = LoggerFactory.getLogger(AuditLogCleanupScheduler.class);

  /**
   * Broad lower bound for the seal period. Earlier than any audit log could exist in the system, so
   * all checkpoints older than the retention cutoff are included in the seal.
   */
  private static final OffsetDateTime EPOCH_LOWER_BOUND =
      OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  private final AuditLogService auditLogService;
  private final AuditLifecycleService auditLifecycleService;
  private final AuditChainProperties chainProperties;

  @Value("${ezkey.audit.retention-days:90}")
  private int retentionDays;

  /**
   * Constructs the cleanup scheduler with its required dependencies.
   *
   * @param auditLogService audit log service used for direct deletion (chain-disabled path)
   * @param auditLifecycleService lifecycle service used for chain sealing (chain-enabled path)
   * @param chainProperties chain configuration; determines which retention strategy is applied
   */
  public AuditLogCleanupScheduler(
      AuditLogService auditLogService,
      AuditLifecycleService auditLifecycleService,
      AuditChainProperties chainProperties) {
    this.auditLogService = auditLogService;
    this.auditLifecycleService = auditLifecycleService;
    this.chainProperties = chainProperties;
  }

  /**
   * Scheduled retention job for audit logs.
   *
   * <p>Runs daily at 2 AM by default (configurable via {@code ezkey.audit.cleanup.cron}).
   *
   * <ul>
   *   <li><b>Chain enabled:</b> Seals the period older than the retention window via {@link
   *       AuditLifecycleService#sealArchive(ArchiveSealRequest)}. Checkpoints are marked {@code
   *       ARCHIVE_SEAL} so that future chain verification skips entries_digest re-computation for
   *       the sealed windows. The DBA may then safely {@code DROP} the corresponding partition. No
   *       entries are deleted by this job.
   *   <li><b>Chain disabled:</b> Directly deletes entries older than the retention period.
   * </ul>
   *
   * <p><b>HA Safety:</b> Uses distributed locking to ensure only one instance executes this job at
   * a time.
   */
  @Scheduled(cron = "${ezkey.audit.cleanup.cron:0 0 2 * * ?}")
  @SchedulerLock(name = "AUDIT_CLEANUP", lockAtMostFor = "PT10M")
  public void cleanupOldAuditLogs() {
    try {
      logger.info("Starting audit log cleanup (retention: {} days)", retentionDays);
      if (chainProperties.isEnabled()) {
        sealOldAuditLogPeriod();
      } else {
        int deleted = auditLogService.deleteOldLogs(retentionDays);
        logger.info("Audit log cleanup completed. Deleted {} records", deleted);
      }
    } catch (Exception e) {
      logger.error("Audit log cleanup failed: {}", e.getMessage(), e);
    }
  }

  /**
   * Seals the audit chain period older than the retention window.
   *
   * <p>Covers all checkpoints from {@link #EPOCH_LOWER_BOUND} up to (exclusive) the current
   * retention cutoff date. Sealed checkpoints can no longer be verified against live database
   * entries (entries may be dropped by the DBA), but the chain HMAC linkage remains intact.
   */
  private void sealOldAuditLogPeriod() {
    OffsetDateTime periodEnd = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
    String justification =
        "Automated retention-based archive seal. Audit log entries older than "
            + retentionDays
            + " days are sealed to allow safe partition archiving per retention policy.";

    ArchiveSealRequest request =
        new ArchiveSealRequest(EPOCH_LOWER_BOUND, periodEnd, null, null, justification);
    ArchiveSealResult result = auditLifecycleService.sealArchive(request);

    logger.info(
        "Audit log seal completed. Sealed {} checkpoint(s) in period [{} to {}) for archiving."
            + " Seal chain HMAC: {}",
        result.checkpointsSealed(),
        result.periodStart(),
        result.periodEnd(),
        result.sealChainHmac() != null
            ? result.sealChainHmac().substring(0, 12) + "..."
            : "none (no checkpoints in period)");
  }
}
