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
import org.ezkey.audit.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

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
