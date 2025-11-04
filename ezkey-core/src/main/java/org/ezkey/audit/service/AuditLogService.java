/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditLogService
 * Description: Service for audit log operations with transaction safety.
 */

package org.ezkey.audit.service;

import java.time.OffsetDateTime;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for audit log operations.
 *
 * <p>Provides audit logging functionality with transaction safety using REQUIRES_NEW propagation to
 * ensure audit logs are saved even if main transaction fails.
 *
 * <p><b>Transaction Safety:</b> Uses REQUIRES_NEW to prevent audit logging from being rolled back
 * with the main transaction, ensuring comprehensive audit trails.
 *
 * <p><b>Error Handling:</b> Audit failures are logged but never thrown to avoid disrupting main
 * business operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AuditLogService {

  private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

  private final AuditLogRepository auditLogRepository;

  public AuditLogService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  /**
   * Log an audit event in a separate transaction.
   *
   * <p>Uses REQUIRES_NEW propagation to ensure the audit log is saved even if the calling
   * transaction is rolled back.
   *
   * @param auditLog the audit log to save
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void log(AuditLog auditLog) {
    try {
      auditLogRepository.save(auditLog);
    } catch (Exception e) {
      // Log error but don't throw to avoid disrupting main operation
      logger.error("Failed to save audit log: {}", e.getMessage(), e);
    }
  }

  /**
   * Find audit logs with filters and pagination.
   *
   * @param eventType optional event type filter
   * @param eventStatus optional event status filter
   * @param apiName optional API name filter
   * @param enrollmentId optional enrollment ID filter
   * @param adminId optional admin ID filter
   * @param pageable pagination parameters
   * @return page of audit logs
   */
  @Transactional(readOnly = true)
  public Page<AuditLog> findByFilters(
      EventType eventType,
      EventStatus eventStatus,
      ApiName apiName,
      Integer enrollmentId,
      Integer adminId,
      Pageable pageable) {
    return auditLogRepository.findByFilters(
        eventType, eventStatus, apiName, enrollmentId, adminId, pageable);
  }

  /**
   * Delete audit logs older than retention period.
   *
   * @param retentionDays number of days to retain audit logs
   * @return number of records deleted
   */
  @Transactional
  public int deleteOldLogs(int retentionDays) {
    OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(retentionDays);
    int deleted = auditLogRepository.deleteOlderThan(cutoffDate);
    logger.info("Deleted {} audit logs older than {} days", deleted, retentionDays);
    return deleted;
  }
}
