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

import java.util.List;
import java.util.Optional;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.entity.Alert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

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
   * Returns alerts matching the given status, ordered by creation time descending. Used by the
   * dashboard "recent open alerts" widget.
   *
   * @param status status to filter on
   * @param pageable pagination/sort parameters
   * @return alerts in the requested page
   */
  List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status, Pageable pageable);
}
