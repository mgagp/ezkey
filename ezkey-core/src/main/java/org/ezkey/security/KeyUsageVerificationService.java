/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.springframework.stereotype.Service;

/**
 * Computes derived encryption key lifecycle fields for Admin API responses.
 *
 * <p>Uses the same target inventory as {@link ReencryptionBatchCreationService} and prefix counts
 * from {@link ReencryptionTargetQueryService} so "remaining usage" matches batch creation logic.
 *
 * @since 2025
 */
@Service
public class KeyUsageVerificationService {

  /** Technical key status aligned with {@link EncryptionKey.KeyStatus} for non-derived rows. */
  public static final String LIFECYCLE_PENDING = "PENDING";

  public static final String LIFECYCLE_PRIMARY = "PRIMARY";
  public static final String LIFECYCLE_DISABLED = "DISABLED";

  /** Old key still has ciphertext rows and/or migration batches are not all completed. */
  public static final String LIFECYCLE_ENABLED_IN_USE = "ENABLED_IN_USE";

  /** Verified zero remaining rows across tracked targets and no non-completed migration batches. */
  public static final String LIFECYCLE_DRAINED = "DRAINED";

  public static final String VERIFICATION_NOT_APPLICABLE = "NOT_APPLICABLE";
  public static final String VERIFICATION_REMAINS_IN_USE = "REMAINS_IN_USE";
  public static final String VERIFICATION_MIGRATION_IN_PROGRESS = "MIGRATION_IN_PROGRESS";
  public static final String VERIFICATION_VERIFIED_ZERO = "VERIFIED_ZERO";

  /**
   * PRIMARY key: prefix-scan totals across the same tracked targets (live ciphertext using this
   * key). Not a migration backlog; operator observability only.
   */
  public static final String VERIFICATION_PRIMARY_USAGE = "PRIMARY_USAGE";

  private final ReencryptionBatchCreationService batchCreationService;
  private final ReencryptionTargetQueryService targetQueryService;
  private final ReencryptionBatchRepository batchRepository;

  public KeyUsageVerificationService(
      ReencryptionBatchCreationService batchCreationService,
      ReencryptionTargetQueryService targetQueryService,
      ReencryptionBatchRepository batchRepository) {
    this.batchCreationService = batchCreationService;
    this.targetQueryService = targetQueryService;
    this.batchRepository = batchRepository;
  }

  /**
   * Builds a point-in-time snapshot for API exposure (list and detail).
   *
   * @param key encryption key row
   * @return snapshot with lifecycle stage and usage evidence
   */
  public KeyUsageSnapshot computeSnapshot(EncryptionKey key) {
    OffsetDateTime verifiedAt = OffsetDateTime.now();
    return switch (key.getKeyStatus()) {
      case PENDING ->
          new KeyUsageSnapshot(
              LIFECYCLE_PENDING,
              null,
              null,
              verifiedAt,
              VERIFICATION_NOT_APPLICABLE,
              false,
              false,
              null);
      case PRIMARY -> computePrimarySnapshot(key, verifiedAt);
      case DISABLED ->
          new KeyUsageSnapshot(
              LIFECYCLE_DISABLED,
              null,
              null,
              verifiedAt,
              VERIFICATION_NOT_APPLICABLE,
              false,
              false,
              null);
      case ENABLED -> computeEnabledSnapshot(key, verifiedAt);
    };
  }

  /**
   * Snapshot for the current PRIMARY: same prefix counts as migration discovery, exposing how many
   * ciphertext units currently reference this key (observability; unexpected growth may indicate
   * load, misconfiguration, or stalled migration elsewhere).
   */
  private KeyUsageSnapshot computePrimarySnapshot(EncryptionKey key, OffsetDateTime verifiedAt) {
    long totalOnKey = 0L;
    int targetsWithRows = 0;
    for (ReencryptionBatchCreationService.Target target :
        batchCreationService.discoverReencryptableTargets()) {
      int count =
          targetQueryService.countRecordsEncryptedWithKey(
              target.table(), target.column(), key.getKeyId());
      totalOnKey += count;
      if (count > 0) {
        targetsWithRows++;
      }
    }
    long nonCompletedBatches =
        batchRepository.countByOldKey_KeyIdAndStatusNot(key.getKeyId(), BatchStatus.COMPLETED);
    boolean incompleteMigration = nonCompletedBatches > 0;
    return new KeyUsageSnapshot(
        LIFECYCLE_PRIMARY,
        totalOnKey,
        targetsWithRows,
        verifiedAt,
        VERIFICATION_PRIMARY_USAGE,
        false,
        incompleteMigration,
        null);
  }

  private KeyUsageSnapshot computeEnabledSnapshot(EncryptionKey key, OffsetDateTime verifiedAt) {
    long totalRemaining = 0L;
    int targetsWithRows = 0;
    for (ReencryptionBatchCreationService.Target target :
        batchCreationService.discoverReencryptableTargets()) {
      int count =
          targetQueryService.countRecordsEncryptedWithKey(
              target.table(), target.column(), key.getKeyId());
      totalRemaining += count;
      if (count > 0) {
        targetsWithRows++;
      }
    }

    long nonCompletedBatches =
        batchRepository.countByOldKey_KeyIdAndStatusNot(key.getKeyId(), BatchStatus.COMPLETED);
    boolean incompleteMigration = nonCompletedBatches > 0;

    if (totalRemaining > 0) {
      return new KeyUsageSnapshot(
          LIFECYCLE_ENABLED_IN_USE,
          totalRemaining,
          targetsWithRows,
          verifiedAt,
          VERIFICATION_REMAINS_IN_USE,
          false,
          incompleteMigration,
          null);
    }
    if (incompleteMigration) {
      return new KeyUsageSnapshot(
          LIFECYCLE_ENABLED_IN_USE,
          0L,
          0,
          verifiedAt,
          VERIFICATION_MIGRATION_IN_PROGRESS,
          false,
          true,
          null);
    }
    return new KeyUsageSnapshot(
        LIFECYCLE_DRAINED,
        0L,
        0,
        verifiedAt,
        VERIFICATION_VERIFIED_ZERO,
        true,
        false,
        computeDrainedWallClockSeconds(key.getKeyId()));
  }

  /**
   * Retrospective, parallel-aware wall-clock duration (whole seconds) for the completed migration
   * off {@code oldKeyId}.
   *
   * @param oldKeyId the old (now drained) encryption key id
   * @return total estimated seconds, or {@code null} when no completed batch has both a start and
   *     completion timestamp
   */
  private Long computeDrainedWallClockSeconds(Long oldKeyId) {
    List<ReencryptionBatch> completedBatches =
        batchRepository.findByOldKey_KeyIdAndStatus(oldKeyId, BatchStatus.COMPLETED);
    return computeWallClockSeconds(completedBatches);
  }

  /**
   * Aggregates completed batch wall-clock durations into a single estimate.
   *
   * <p>Sharded batches for the same target (same table, column, and destination key) run in
   * parallel, so their group contributes the {@code max} duration observed among shard siblings.
   * Non-sharded batches run serially relative to each other (single-stream target), so they
   * contribute a {@code sum}. This intentionally mirrors {@code ReencryptionBatchParallelRunner}'s
   * per-shard mutex model (see {@code docs/REENCRYPTION_OPERATIONS.md}), not raw CPU time.
   *
   * @param completedBatches completed batches for one old key (any target/column mix)
   * @return total estimated wall-clock seconds, or {@code null} when no batch has both a start and
   *     a completion timestamp
   */
  static Long computeWallClockSeconds(List<ReencryptionBatch> completedBatches) {
    Map<String, Long> shardGroupMaxSeconds = new LinkedHashMap<>();
    long nonShardedSumSeconds = 0L;
    boolean anyTimed = false;

    for (ReencryptionBatch batch : completedBatches) {
      OffsetDateTime started = batch.getStartedAt();
      OffsetDateTime completed = batch.getCompletedAt();
      if (started == null || completed == null) {
        continue;
      }
      long seconds = Duration.between(started, completed).getSeconds();
      if (seconds < 0) {
        continue;
      }
      anyTimed = true;
      Integer shardCount = batch.getShardCount();
      if (shardCount != null && shardCount > 1) {
        String shardGroupKey =
            batch.getTargetTable()
                + "|"
                + batch.getTargetColumn()
                + "|"
                + batch.getNewKey().getKeyId();
        shardGroupMaxSeconds.merge(shardGroupKey, seconds, Math::max);
      } else {
        nonShardedSumSeconds += seconds;
      }
    }

    if (!anyTimed) {
      return null;
    }
    long total = nonShardedSumSeconds;
    for (long maxSeconds : shardGroupMaxSeconds.values()) {
      total += maxSeconds;
    }
    return total;
  }

  /**
   * Point-in-time lifecycle evidence for one encryption key.
   *
   * @param lifecycleStage derived operator-facing stage (see constants on {@link
   *     KeyUsageVerificationService})
   * @param remainingRecords sum of ciphertext units (tracked row/column ENC: prefix counts) for
   *     this key: migration backlog for ENABLED; live volume on PRIMARY; null for PENDING /
   *     DISABLED
   * @param remainingTargets number of targets with count &gt; 0; null when not applicable
   * @param lastVerifiedAt when this snapshot was computed
   * @param verificationState machine-readable verification outcome
   * @param decommissionEligible true when the key is drained and ready for a future decommission
   *     workflow
   * @param incompleteMigrationBatches true when non-{@link BatchStatus#COMPLETED} batches exist for
   *     this old key
   * @param reencryptionWallClockSeconds retrospective, parallel-aware wall-clock duration in
   *     seconds for a fully drained migration (max duration across each shard group plus the sum of
   *     other completed batches); {@code null} unless {@code lifecycleStage} is {@link
   *     #LIFECYCLE_DRAINED} and at least one completed batch has both a start and completion
   *     timestamp
   */
  public record KeyUsageSnapshot(
      String lifecycleStage,
      Long remainingRecords,
      Integer remainingTargets,
      OffsetDateTime lastVerifiedAt,
      String verificationState,
      boolean decommissionEligible,
      boolean incompleteMigrationBatches,
      Long reencryptionWallClockSeconds) {}
}
