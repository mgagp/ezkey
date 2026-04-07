/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import java.time.OffsetDateTime;
import org.ezkey.security.domain.entity.EncryptionKey;
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
              LIFECYCLE_PENDING, null, null, verifiedAt, VERIFICATION_NOT_APPLICABLE, false, false);
      case PRIMARY ->
          new KeyUsageSnapshot(
              LIFECYCLE_PRIMARY, null, null, verifiedAt, VERIFICATION_NOT_APPLICABLE, false, false);
      case DISABLED ->
          new KeyUsageSnapshot(
              LIFECYCLE_DISABLED,
              null,
              null,
              verifiedAt,
              VERIFICATION_NOT_APPLICABLE,
              false,
              false);
      case ENABLED -> computeEnabledSnapshot(key, verifiedAt);
    };
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
          incompleteMigration);
    }
    if (incompleteMigration) {
      return new KeyUsageSnapshot(
          LIFECYCLE_ENABLED_IN_USE,
          0L,
          0,
          verifiedAt,
          VERIFICATION_MIGRATION_IN_PROGRESS,
          false,
          true);
    }
    return new KeyUsageSnapshot(
        LIFECYCLE_DRAINED, 0L, 0, verifiedAt, VERIFICATION_VERIFIED_ZERO, true, false);
  }

  /**
   * Point-in-time lifecycle evidence for one encryption key.
   *
   * @param lifecycleStage derived operator-facing stage (see constants on {@link
   *     KeyUsageVerificationService})
   * @param remainingRecords sum of ciphertext units (tracked row/column ENC: prefix counts) still
   *     using this key; null when not applicable (non-ENABLED keys)
   * @param remainingTargets number of targets with count &gt; 0; null when not applicable
   * @param lastVerifiedAt when this snapshot was computed
   * @param verificationState machine-readable verification outcome
   * @param decommissionEligible true when the key is drained and ready for a future decommission
   *     workflow
   * @param incompleteMigrationBatches true when non-{@link BatchStatus#COMPLETED} batches exist for
   *     this old key
   */
  public record KeyUsageSnapshot(
      String lifecycleStage,
      Long remainingRecords,
      Integer remainingTargets,
      OffsetDateTime lastVerifiedAt,
      String verificationState,
      boolean decommissionEligible,
      boolean incompleteMigrationBatches) {}
}
