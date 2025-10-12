/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditService
 * Description: Service for recording and managing audit log events
 */

package org.ezkey.audit.service;

import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for managing audit logs across the Ezkey system.
 * <p>
 * This service provides methods for recording security-relevant events including
 * authentication, enrollment operations, and administrative actions. All audit
 * operations are transactional to ensure data integrity.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record an audit event.
     * Uses REQUIRES_NEW propagation to ensure audit logs are saved even if the
     * main transaction fails.
     *
     * @param builder the audit log builder with event details
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditLogBuilder builder) {
        try {
            AuditLog auditLog = builder.build();
            auditLogRepository.save(auditLog);
            logger.debug("Audit event logged: {} - {} - {}", 
                auditLog.getEventType(), auditLog.getEventAction(), auditLog.getEventStatus());
        } catch (Exception e) {
            // Never let audit logging fail the main operation
            logger.error("Failed to log audit event: {}", e.getMessage(), e);
        }
    }

    /**
     * Query audit logs with pagination.
     *
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * Query audit logs by event type.
     *
     * @param eventType the event type
     * @param pageable  pagination parameters
     * @return page of audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByEventType(String eventType, Pageable pageable) {
        return auditLogRepository.findByEventTypeOrderByCreatedAtDesc(eventType, pageable);
    }

    /**
     * Query audit logs by event status.
     *
     * @param eventStatus the event status
     * @param pageable    pagination parameters
     * @return page of audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByEventStatus(String eventStatus, Pageable pageable) {
        return auditLogRepository.findByEventStatusOrderByCreatedAtDesc(eventStatus, pageable);
    }

    /**
     * Query audit logs by API name.
     *
     * @param apiName  the API name
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByApiName(String apiName, Pageable pageable) {
        return auditLogRepository.findByApiNameOrderByCreatedAtDesc(apiName, pageable);
    }

    /**
     * Query audit logs by enrollment ID.
     *
     * @param enrollmentId the enrollment ID
     * @param pageable     pagination parameters
     * @return page of audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByEnrollmentId(Integer enrollmentId, Pageable pageable) {
        return auditLogRepository.findByEnrollmentIdOrderByCreatedAtDesc(enrollmentId, pageable);
    }

    /**
     * Query audit logs by admin ID.
     *
     * @param adminId  the admin ID
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByAdminId(Integer adminId, Pageable pageable) {
        return auditLogRepository.findByAdminIdOrderByCreatedAtDesc(adminId, pageable);
    }

    /**
     * Delete old audit logs based on retention policy.
     *
     * @param retentionDays number of days to retain logs
     * @return number of deleted records
     */
    @Transactional
    public int cleanupOldLogs(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        long count = auditLogRepository.countOldLogs(cutoffDate);
        
        if (count > 0) {
            logger.info("Deleting {} audit logs older than {} days", count, retentionDays);
            int deleted = auditLogRepository.deleteOldLogs(cutoffDate);
            logger.info("Successfully deleted {} audit logs", deleted);
            return deleted;
        }
        
        logger.debug("No audit logs to clean up (retention: {} days)", retentionDays);
        return 0;
    }

    /**
     * Builder class for creating audit log entries.
     */
    public static class AuditLogBuilder {
        private final AuditLog auditLog;

        public AuditLogBuilder() {
            this.auditLog = new AuditLog();
        }

        public AuditLogBuilder eventType(String eventType) {
            auditLog.setEventType(eventType);
            return this;
        }

        public AuditLogBuilder eventAction(String eventAction) {
            auditLog.setEventAction(eventAction);
            return this;
        }

        public AuditLogBuilder eventStatus(String eventStatus) {
            auditLog.setEventStatus(eventStatus);
            return this;
        }

        public AuditLogBuilder apiName(String apiName) {
            auditLog.setApiName(apiName);
            return this;
        }

        public AuditLogBuilder endpointPath(String endpointPath) {
            auditLog.setEndpointPath(endpointPath);
            return this;
        }

        public AuditLogBuilder httpMethod(String httpMethod) {
            auditLog.setHttpMethod(httpMethod);
            return this;
        }

        public AuditLogBuilder ipAddress(String ipAddress) {
            auditLog.setIpAddress(ipAddress);
            return this;
        }

        public AuditLogBuilder userAgent(String userAgent) {
            auditLog.setUserAgent(userAgent);
            return this;
        }

        public AuditLogBuilder adminId(Integer adminId) {
            auditLog.setAdminId(adminId);
            return this;
        }

        public AuditLogBuilder integrationId(Integer integrationId) {
            auditLog.setIntegrationId(integrationId);
            return this;
        }

        public AuditLogBuilder enrollmentId(Integer enrollmentId) {
            auditLog.setEnrollmentId(enrollmentId);
            return this;
        }

        public AuditLogBuilder authAttemptId(Integer authAttemptId) {
            auditLog.setAuthAttemptId(authAttemptId);
            return this;
        }

        public AuditLogBuilder tenantId(Integer tenantId) {
            auditLog.setTenantId(tenantId);
            return this;
        }

        public AuditLogBuilder eventDetails(String eventDetails) {
            auditLog.setEventDetails(eventDetails);
            return this;
        }

        public AuditLogBuilder errorMessage(String errorMessage) {
            auditLog.setErrorMessage(errorMessage);
            return this;
        }

        public AuditLog build() {
            return auditLog;
        }
    }
}
