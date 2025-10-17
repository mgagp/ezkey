/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AuditLogRepository
 * Description: Spring Data JPA repository for audit log persistence.
 */

package org.ezkey.audit.domain.repository;

import java.time.OffsetDateTime;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for audit log persistence.
 *
 * <p>Provides CRUD operations and custom queries for audit log entries supporting security
 * monitoring and compliance reporting.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

  /**
   * Find audit logs with optional filters and pagination.
   *
   * @param eventType optional event type filter
   * @param eventStatus optional event status filter
   * @param apiName optional API name filter
   * @param enrollmentId optional enrollment ID filter
   * @param adminId optional admin ID filter
   * @param pageable pagination parameters
   * @return page of audit logs matching criteria
   */
  @Query(
      "SELECT a FROM AuditLog a WHERE "
          + "(:eventType IS NULL OR a.eventType = :eventType) AND "
          + "(:eventStatus IS NULL OR a.eventStatus = :eventStatus) AND "
          + "(:apiName IS NULL OR a.apiName = :apiName) AND "
          + "(:enrollmentId IS NULL OR a.enrollmentId = :enrollmentId) AND "
          + "(:adminId IS NULL OR a.adminId = :adminId) "
          + "ORDER BY a.createdAt DESC")
  Page<AuditLog> findByFilters(
      @Param("eventType") EventType eventType,
      @Param("eventStatus") EventStatus eventStatus,
      @Param("apiName") ApiName apiName,
      @Param("enrollmentId") Integer enrollmentId,
      @Param("adminId") Integer adminId,
      Pageable pageable);

  /**
   * Delete audit logs older than the specified date.
   *
   * @param cutoffDate cutoff date for deletion
   * @return number of records deleted
   */
  @Modifying
  @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
  int deleteOlderThan(@Param("cutoffDate") OffsetDateTime cutoffDate);
}
