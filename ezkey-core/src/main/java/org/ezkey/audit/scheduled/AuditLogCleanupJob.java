/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduled Job: AuditLogCleanupJob
 * Description: Scheduled job for cleaning up old audit logs based on retention policy
 */

package org.ezkey.audit.scheduled;

import org.ezkey.audit.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for cleaning up old audit logs based on configurable retention policy.
 * <p>
 * This job runs daily at 2 AM by default and deletes audit logs older than the
 * configured retention period. The retention period is configurable via application
 * properties (default is 90 days for SOC2 compliance).
 * </p>
 * 
 * <p>
 * <b>Configuration:</b>
 * <ul>
 * <li>ezkey.audit.retention-days - Number of days to retain audit logs (default: 90)</li>
 * <li>ezkey.audit.cleanup.enabled - Enable/disable the cleanup job (default: true)</li>
 * </ul>
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConditionalOnProperty(name = "ezkey.audit.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class AuditLogCleanupJob {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogCleanupJob.class);

    private final AuditService auditService;

    @Value("${ezkey.audit.retention-days:90}")
    private int retentionDays;

    public AuditLogCleanupJob(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Scheduled job that runs daily at 2 AM to clean up old audit logs.
     * <p>
     * The cron expression "0 0 2 * * ?" means:
     * - Second: 0
     * - Minute: 0
     * - Hour: 2 (2 AM)
     * - Day of month: * (every day)
     * - Month: * (every month)
     * - Day of week: ? (any)
     * </p>
     */
    @Scheduled(cron = "${ezkey.audit.cleanup.cron:0 0 2 * * ?}")
    public void cleanupOldAuditLogs() {
        logger.info("Starting audit log cleanup job (retention: {} days)", retentionDays);
        
        try {
            int deletedCount = auditService.cleanupOldLogs(retentionDays);
            
            if (deletedCount > 0) {
                logger.info("Audit log cleanup completed successfully - Deleted {} records", deletedCount);
            } else {
                logger.debug("Audit log cleanup completed - No records to delete");
            }
        } catch (Exception e) {
            logger.error("Error during audit log cleanup: {}", e.getMessage(), e);
        }
    }
}
