/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AuditChainCheckpointRepository
 * Description: Spring Data JPA repository for audit chain checkpoint persistence.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for audit chain checkpoint persistence.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Repository
public interface AuditChainCheckpointRepository
    extends JpaRepository<AuditChainCheckpoint, Long>,
        JpaSpecificationExecutor<AuditChainCheckpoint> {

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
   * Finds the earliest checkpoint by window_start ascending.
   *
   * <p>Used by the scheduler to clamp the lookback window so that checkpoints are never created
   * before the first checkpoint ever created (avoids empty "past" windows on bootstrap).
   *
   * @return the earliest checkpoint, or empty if no checkpoints exist
   */
  @Query("SELECT c FROM AuditChainCheckpoint c ORDER BY c.windowStart ASC LIMIT 1")
  Optional<AuditChainCheckpoint> findEarliest();

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

  /**
   * Finds all checkpoints whose checkpoint_id falls within [idFrom, idTo] (both inclusive), ordered
   * by checkpoint_id ascending.
   *
   * <p>Used by the archive seal operation when the period is identified by checkpoint IDs rather
   * than timestamps. This is the ergonomic mode for admins who look at the database directly.
   *
   * @param idFrom lower bound (inclusive)
   * @param idTo upper bound (inclusive)
   * @return list of checkpoints in ID order
   */
  @Query(
      "SELECT c FROM AuditChainCheckpoint c "
          + "WHERE c.checkpointId >= :idFrom AND c.checkpointId <= :idTo "
          + "ORDER BY c.checkpointId ASC")
  List<AuditChainCheckpoint> findByIdRange(@Param("idFrom") Long idFrom, @Param("idTo") Long idTo);

  /**
   * Counts checkpoints of any type whose window_start falls within the given range.
   *
   * <p>Used by gap declaration to validate that no regular checkpoints already exist in the
   * declared gap period before creating the GAP_DECLARATION checkpoint.
   *
   * @param from start boundary (inclusive)
   * @param to end boundary (exclusive)
   * @return number of checkpoints in the range
   */
  @Query(
      "SELECT COUNT(c) FROM AuditChainCheckpoint c "
          + "WHERE c.windowStart >= :from AND c.windowStart < :to")
  long countByWindowRange(@Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);

  /**
   * Finds the checkpoint immediately preceding the given start time (the latest checkpoint with
   * window_start strictly before {@code before}).
   *
   * <p>Used by gap declaration to find the chain anchor for the new GAP_DECLARATION checkpoint.
   *
   * @param before exclusive upper boundary
   * @return the most recent checkpoint before the boundary, or empty if none
   */
  @Query(
      "SELECT c FROM AuditChainCheckpoint c "
          + "WHERE c.windowStart < :before ORDER BY c.windowStart DESC LIMIT 1")
  Optional<AuditChainCheckpoint> findLatestBefore(@Param("before") OffsetDateTime before);

  /**
   * Finds the earliest checkpoint whose window_start is strictly after {@code after}, ordered
   * ascending.
   *
   * <p>Used by gap declaration to auto-derive {@code gapEnd} when it is not explicitly provided:
   * {@code gapEnd = firstCheckpointAfter(anchor.window_end).window_start}. This places the gap
   * boundary exactly at the start of the first checkpoint the scheduler created post-restart,
   * avoiding any overlap between the GAP_DECLARATION and regular checkpoints.
   *
   * @param after exclusive lower boundary
   * @return the earliest checkpoint after the boundary, or empty if none exist
   */
  @Query(
      "SELECT c FROM AuditChainCheckpoint c "
          + "WHERE c.windowStart > :after ORDER BY c.windowStart ASC LIMIT 1")
  Optional<AuditChainCheckpoint> findFirstAfter(@Param("after") OffsetDateTime after);

  /**
   * Finds all checkpoints whose window_start is at or after the given boundary, ordered ascending.
   *
   * <p>Used by gap declaration to re-chain checkpoints that already exist after the gap (created by
   * the scheduler before the gap was declared). Their prev_chain_hmac must be updated to link to
   * the new GAP_DECLARATION checkpoint and each other.
   *
   * @param from start boundary (inclusive)
   * @return list of checkpoints in chronological order
   */
  @Query(
      "SELECT c FROM AuditChainCheckpoint c "
          + "WHERE c.windowStart >= :from ORDER BY c.windowStart ASC")
  List<AuditChainCheckpoint> findAllWithWindowStartAtOrAfter(@Param("from") OffsetDateTime from);

  List<AuditChainCheckpoint> findByLifecycleStateAndCheckpointTypeOrderByWindowStartAsc(
      CheckpointLifecycleState lifecycleState, String checkpointType);

  List<AuditChainCheckpoint>
      findByLifecycleStateAndCheckpointTypeAndWindowStartBeforeOrderByWindowStartAsc(
          CheckpointLifecycleState lifecycleState, String checkpointType, OffsetDateTime before);

  @Query(
      "SELECT COUNT(c) FROM AuditChainCheckpoint c "
          + "WHERE c.windowStart < :before AND c.lifecycleState NOT IN :allowedStates")
  long countByWindowStartBeforeAndLifecycleStateNotIn(
      @Param("before") OffsetDateTime before,
      @Param("allowedStates") Collection<CheckpointLifecycleState> allowedStates);

  long countByWindowStartBeforeAndLifecycleState(
      OffsetDateTime before, CheckpointLifecycleState lifecycleState);
}
