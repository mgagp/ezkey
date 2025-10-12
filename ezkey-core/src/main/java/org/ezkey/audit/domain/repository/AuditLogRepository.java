/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AuditLogRepository
 * Description: Data access layer for audit log entities
 */

package org.ezkey.audit.domain.repository;

import org.ezkey.audit.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository interface for accessing audit log records.
 * <p>
 * Provides methods for querying audit logs with filtering by event type,
 * date ranges, and entity references. Also supports retention cleanup operations.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs with optional filtering.
     *
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find audit logs by event type.
     *
     * @param eventType the event type to filter by
     * @param pageable  pagination parameters
     * @return page of audit logs matching the event type
     */
    Page<AuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    /**
     * Find audit logs by event status.
     *
     * @param eventStatus the event status to filter by
     * @param pageable    pagination parameters
     * @return page of audit logs matching the event status
     */
    Page<AuditLog> findByEventStatusOrderByCreatedAtDesc(String eventStatus, Pageable pageable);

    /**
     * Find audit logs by API name.
     *
     * @param apiName  the API name to filter by
     * @param pageable pagination parameters
     * @return page of audit logs from the specified API
     */
    Page<AuditLog> findByApiNameOrderByCreatedAtDesc(String apiName, Pageable pageable);

    /**
     * Find audit logs by enrollment ID.
     *
     * @param enrollmentId the enrollment ID
     * @param pageable     pagination parameters
     * @return page of audit logs for the specified enrollment
     */
    Page<AuditLog> findByEnrollmentIdOrderByCreatedAtDesc(Integer enrollmentId, Pageable pageable);

    /**
     * Find audit logs by admin ID.
     *
     * @param adminId  the admin ID
     * @param pageable pagination parameters
     * @return page of audit logs for the specified admin
     */
    Page<AuditLog> findByAdminIdOrderByCreatedAtDesc(Integer adminId, Pageable pageable);

    /**
     * Find audit logs created after a specific date.
     *
     * @param createdAt the date to filter from
     * @param pageable  pagination parameters
     * @return page of audit logs created after the specified date
     */
    Page<AuditLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime createdAt, Pageable pageable);

    /**
     * Find audit logs by multiple criteria (event type and status).
     *
     * @param eventType   the event type
     * @param eventStatus the event status
     * @param pageable    pagination parameters
     * @return page of audit logs matching both criteria
     */
    Page<AuditLog> findByEventTypeAndEventStatusOrderByCreatedAtDesc(
            String eventType, String eventStatus, Pageable pageable);

    /**
     * Delete audit logs older than the specified date (for retention policy).
     *
     * @param cutoffDate the date before which logs should be deleted
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    int deleteOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count audit logs older than the specified date.
     *
     * @param cutoffDate the date to count from
     * @return number of logs older than the cutoff date
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    long countOldLogs(@Param("cutoffDate") LocalDateTime cutoffDate);
}
