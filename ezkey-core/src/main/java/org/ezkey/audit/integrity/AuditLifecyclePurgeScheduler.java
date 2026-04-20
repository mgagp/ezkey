/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduler: AuditLifecyclePurgeScheduler
 * Description: Scheduled job for lifecycle-driven audit log purge execution.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.audit.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for lifecycle-driven audit log purge execution.
 *
 * <p>This job does not perform legacy age-only cleanup. It computes the current lifecycle policy
 * horizon and delegates physical deletion only to the lifecycle-aware purge guard in {@link
 * AuditLogService}.
 *
 * @since 2026
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.audit.archive.purge.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuditLifecyclePurgeScheduler {

  private static final Logger logger = LoggerFactory.getLogger(AuditLifecyclePurgeScheduler.class);

  private final AuditLogService auditLogService;
  private final AuditLifecycleService auditLifecycleService;
  private final AuditArchiveProperties archiveProperties;

  public AuditLifecyclePurgeScheduler(
      AuditLogService auditLogService,
      AuditLifecycleService auditLifecycleService,
      AuditArchiveProperties archiveProperties) {
    this.auditLogService = auditLogService;
    this.auditLifecycleService = auditLifecycleService;
    this.archiveProperties = archiveProperties;
  }

  /**
   * Scheduled purge job for audit logs whose lifecycle reached a deletable state.
   *
   * <p>The cutoff is policy-driven and conservative. Checkpoint state still remains the decisive
   * authorization for physical deletion.
   */
  @Scheduled(cron = "${ezkey.audit.archive.purge.cron:0 0 2 * * ?}")
  @SchedulerLock(name = "AUDIT_LIFECYCLE_PURGE", lockAtMostFor = "PT10M")
  public void purgeLifecycleEligibleAuditLogs() {
    try {
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      AuditLifecycleService.LifecycleAutomationResult automationResult =
          auditLifecycleService.progressLifecyclePolicy(now);
      OffsetDateTime purgeCutoff =
          now.minus(archiveProperties.getRetentionPeriod())
              .minus(archiveProperties.getSealDelay())
              .minus(archiveProperties.getPurgeDelay());
      logger.info(
          "Starting audit lifecycle purge with cutoff {} (sealed={}, purgeable={})",
          purgeCutoff,
          automationResult.sealedCount(),
          automationResult.purgeableCount());
      int deleted = auditLogService.purgeLifecycleEligibleLogs(purgeCutoff);
      logger.info("Audit lifecycle purge completed. Deleted {} records", deleted);
    } catch (Exception e) {
      logger.error("Audit lifecycle purge failed: {}", e.getMessage(), e);
    }
  }
}
