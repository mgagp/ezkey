/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AuditLogRepository
 * Description: Spring Data JPA repository for audit log persistence.
 */

package org.ezkey.audit.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Repository
public interface AuditLogRepository
    extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

  boolean existsByCreatedAtBefore(OffsetDateTime cutoffDate);

  /**
   * Allocates the next {@code audit_log_id} from the named sequence before insert.
   *
   * <p>Used by the single-INSERT HMAC seal path so the identity is known when building the
   * canonical form (no post-insert UPDATE).
   *
   * @return next sequence value
   */
  @Query(value = "SELECT nextval('ezkey_audit_log_id_seq')", nativeQuery = true)
  Long nextAuditLogId();

  /**
   * Delete audit logs older than the specified date.
   *
   * @param cutoffDate cutoff date for deletion
   * @return number of records deleted
   */
  @Modifying
  @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
  int deleteOlderThan(@Param("cutoffDate") OffsetDateTime cutoffDate);

  /**
   * Loads {@code entry_hmac} values for a half-open checkpoint window (Integrity cut 3 D5).
   *
   * <p>Same filter and sort as the former full-entity {@code findAll(Specification)} path in {@code
   * AuditChainVerificationService#computeEntriesDigest}: {@code createdAt >= windowStart} and
   * {@code createdAt < windowEnd}, ordered by {@code auditLogId} ascending.
   *
   * @param windowStart inclusive window start
   * @param windowEnd exclusive window end
   * @return projected rows (id + entry HMAC only)
   */
  @Query(
      """
      SELECT a.auditLogId AS auditLogId, a.entryHmac AS entryHmac
      FROM AuditLog a
      WHERE a.createdAt >= :windowStart AND a.createdAt < :windowEnd
      ORDER BY a.auditLogId ASC
      """)
  List<AuditLogEntryHmacView> findEntryHmacsInWindow(
      @Param("windowStart") OffsetDateTime windowStart,
      @Param("windowEnd") OffsetDateTime windowEnd);
}
