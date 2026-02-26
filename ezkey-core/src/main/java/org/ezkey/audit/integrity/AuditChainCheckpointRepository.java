/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AuditChainCheckpointRepository
 * Description: Spring Data JPA repository for audit chain checkpoint persistence.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for audit chain checkpoint persistence.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Repository
public interface AuditChainCheckpointRepository extends JpaRepository<AuditChainCheckpoint, Long> {

  /**
   * Checks whether a checkpoint already exists for the given time window.
   *
   * @param windowStart start of the window
   * @param windowEnd end of the window
   * @return true if a checkpoint exists
   */
  boolean existsByWindowStartAndWindowEnd(OffsetDateTime windowStart, OffsetDateTime windowEnd);

  /**
   * Finds the most recent checkpoint by window_start descending.
   *
   * @return the latest checkpoint, or empty if no checkpoints exist
   */
  @Query("SELECT c FROM AuditChainCheckpoint c ORDER BY c.windowStart DESC LIMIT 1")
  Optional<AuditChainCheckpoint> findLatest();

  /**
   * Finds all checkpoints whose window_start falls within a range, ordered ascending.
   *
   * @param from start boundary (inclusive)
   * @param to end boundary (exclusive)
   * @return list of checkpoints in chronological order
   */
  @Query(
      "SELECT c FROM AuditChainCheckpoint c "
          + "WHERE c.windowStart >= :from AND c.windowStart < :to "
          + "ORDER BY c.windowStart ASC")
  List<AuditChainCheckpoint> findByWindowRange(
      @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
