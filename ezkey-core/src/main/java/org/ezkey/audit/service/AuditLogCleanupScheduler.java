/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduler: AuditLogCleanupScheduler
 * Description: Scheduled job for audit log retention management.
 */

package org.ezkey.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for audit log cleanup.
 *
 * <p>Automatically deletes audit logs older than the configured retention period to maintain
 * database performance and comply with data retention policies.
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
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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

  private static final Logger log = LoggerFactory.getLogger(AuditLogCleanupScheduler.class);

  private final AuditLogService auditLogService;

  @Value("${ezkey.audit.retention-days:90}")
  private int retentionDays;

  public AuditLogCleanupScheduler(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  /**
   * Scheduled cleanup job for audit logs.
   *
   * <p>Runs daily at 2 AM by default (configurable via ezkey.audit.cleanup.cron). Deletes audit
   * logs older than the retention period.
   */
  @Scheduled(cron = "${ezkey.audit.cleanup.cron:0 0 2 * * ?}")
  public void cleanupOldAuditLogs() {
    try {
      log.info("Starting audit log cleanup (retention: {} days)", retentionDays);
      int deleted = auditLogService.deleteOldLogs(retentionDays);
      log.info("Audit log cleanup completed. Deleted {} records", deleted);
    } catch (Exception e) {
      log.error("Audit log cleanup failed: {}", e.getMessage(), e);
    }
  }
}
