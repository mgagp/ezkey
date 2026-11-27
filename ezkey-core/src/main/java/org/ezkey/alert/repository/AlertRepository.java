/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AlertRepository
 * Description: Spring Data JPA repository for the ezkey_alert table.
 */

package org.ezkey.alert.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.entity.Alert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link Alert} rows.
 *
 * <p>Combines a standard CRUD interface with {@link JpaSpecificationExecutor} so the search
 * endpoint can compose dynamic predicates the same way the audit-log search does.
 *
 * @since 2026
 */
public interface AlertRepository
    extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

  /**
   * Returns the open alert with the given dedupe key, if any. Backed by the partial unique index
   * {@code uq_alert_dedupe_key_open}, which guarantees at most one row.
   *
   * @param dedupeKey producer-defined deduplication key
   * @return the open alert with that dedupe key, or empty when none exists
   */
  Optional<Alert> findByDedupeKeyAndStatus(String dedupeKey, AlertStatus status);

  /**
   * Inserts a new OPEN alert unless an OPEN row with the same dedupe key already exists.
   *
   * <p>The race between two first raises is settled by PostgreSQL ({@code ON CONFLICT ... DO
   * NOTHING} on {@code uq_alert_dedupe_key_open}), so the losing caller gets no row back instead of
   * a unique violation that would abort its transaction (#1267, ADR-0020). When the other insert is
   * still uncommitted, this statement waits for it. PostgreSQL only: H2 rejects this syntax, so
   * tests that reach it run on the Testcontainers tier.
   *
   * @param alertType alert type name
   * @param severity severity name
   * @param dedupeKey producer-defined deduplication key
   * @param payload optional JSON payload
   * @param now creation and last-seen timestamp
   * @return the new alert id, or empty when an OPEN row already holds the dedupe key
   */
  @Transactional
  @Query(
      value =
          "INSERT INTO ezkey_alert (alert_type, severity, status, dedupe_key, payload,"
              + " occurrence_count, created_at, last_seen_at)"
              + " VALUES (:alertType, :severity, 'OPEN', :dedupeKey, :payload, 1, :now, :now)"
              + " ON CONFLICT (dedupe_key) WHERE status = 'OPEN' DO NOTHING"
              + " RETURNING alert_id",
      nativeQuery = true)
  Optional<Long> insertOpenIfAbsent(
      @Param("alertType") String alertType,
      @Param("severity") String severity,
      @Param("dedupeKey") String dedupeKey,
      @Param("payload") String payload,
      @Param("now") OffsetDateTime now);

  /**
   * Returns the alert with the given id and status.
   *
   * @param alertId alert identifier
   * @param status status to match
   * @return matching alert when present
   */
  Optional<Alert> findByAlertIdAndStatus(Long alertId, AlertStatus status);

  /**
   * Returns alerts matching the given status, ordered by creation time descending. Used by the
   * dashboard "recent open alerts" widget.
   *
   * @param status status to filter on
   * @param pageable pagination/sort parameters
   * @return alerts in the requested page
   */
  List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);

  /**
   * Returns the number of alerts with the given status.
   *
   * @param status status to count
   * @return count of matching alerts
   */
  long countByStatus(AlertStatus status);
}
