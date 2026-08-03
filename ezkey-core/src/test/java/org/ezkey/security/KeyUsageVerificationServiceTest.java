/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeyUsageVerificationServiceTest {

  @Mock private ReencryptionBatchCreationService batchCreationService;
  @Mock private ReencryptionTargetQueryService targetQueryService;
  @Mock private ReencryptionBatchRepository batchRepository;

  private KeyUsageVerificationService service;

  private final ReencryptionBatchCreationService.Target t1 =
      new ReencryptionBatchCreationService.Target("ezkey_enrollment", "integration_private_key");
  private final ReencryptionBatchCreationService.Target t2 =
      new ReencryptionBatchCreationService.Target("ezkey_enrollment", "enrollment_proof_token");

  @BeforeEach
  void setUp() {
    service =
        new KeyUsageVerificationService(batchCreationService, targetQueryService, batchRepository);
  }

  @Test
  @DisplayName("PRIMARY key yields tracked prefix totals and PRIMARY_USAGE verification")
  void primaryKey() {
    EncryptionKey key =
        new EncryptionKey(1L, KeyStatus.PRIMARY, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    when(batchCreationService.discoverReencryptableTargets()).thenReturn(List.of(t1, t2));
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "integration_private_key", 1L))
        .thenReturn(10);
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "enrollment_proof_token", 1L))
        .thenReturn(5);
    when(batchRepository.countByOldKey_KeyIdAndStatusNot(1L, BatchStatus.COMPLETED)).thenReturn(0L);

    KeyUsageVerificationService.KeyUsageSnapshot s = service.computeSnapshot(key);
    assertEquals(KeyUsageVerificationService.LIFECYCLE_PRIMARY, s.lifecycleStage());
    assertEquals(15L, s.remainingRecords());
    assertEquals(2, s.remainingTargets());
    assertEquals(KeyUsageVerificationService.VERIFICATION_PRIMARY_USAGE, s.verificationState());
    assertFalse(s.decommissionEligible());
    assertFalse(s.incompleteMigrationBatches());
  }

  @Test
  @DisplayName("ENABLED key with remaining rows is ENABLED_IN_USE")
  void enabledInUse() {
    EncryptionKey key =
        new EncryptionKey(2L, KeyStatus.ENABLED, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    when(batchCreationService.discoverReencryptableTargets()).thenReturn(List.of(t1, t2));
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "integration_private_key", 2L))
        .thenReturn(3);
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "enrollment_proof_token", 2L))
        .thenReturn(0);
    when(batchRepository.countByOldKey_KeyIdAndStatusNot(2L, BatchStatus.COMPLETED)).thenReturn(0L);

    KeyUsageVerificationService.KeyUsageSnapshot s = service.computeSnapshot(key);
    assertEquals(KeyUsageVerificationService.LIFECYCLE_ENABLED_IN_USE, s.lifecycleStage());
    assertEquals(3L, s.remainingRecords());
    assertEquals(1, s.remainingTargets());
    assertEquals(KeyUsageVerificationService.VERIFICATION_REMAINS_IN_USE, s.verificationState());
    assertFalse(s.decommissionEligible());
  }

  @Test
  @DisplayName("ENABLED key with zero rows but incomplete batches is MIGRATION_IN_PROGRESS")
  void enabledMigrationInProgress() {
    EncryptionKey key =
        new EncryptionKey(3L, KeyStatus.ENABLED, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    when(batchCreationService.discoverReencryptableTargets()).thenReturn(List.of(t1));
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "integration_private_key", 3L))
        .thenReturn(0);
    when(batchRepository.countByOldKey_KeyIdAndStatusNot(3L, BatchStatus.COMPLETED)).thenReturn(1L);

    KeyUsageVerificationService.KeyUsageSnapshot s = service.computeSnapshot(key);
    assertEquals(KeyUsageVerificationService.LIFECYCLE_ENABLED_IN_USE, s.lifecycleStage());
    assertEquals(0L, s.remainingRecords());
    assertEquals(
        KeyUsageVerificationService.VERIFICATION_MIGRATION_IN_PROGRESS, s.verificationState());
    assertTrue(s.incompleteMigrationBatches());
    assertFalse(s.decommissionEligible());
  }

  @Test
  @DisplayName("ENABLED key with zero rows and no incomplete batches is DRAINED")
  void drained() {
    EncryptionKey key =
        new EncryptionKey(4L, KeyStatus.ENABLED, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    when(batchCreationService.discoverReencryptableTargets()).thenReturn(List.of(t1, t2));
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "integration_private_key", 4L))
        .thenReturn(0);
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "enrollment_proof_token", 4L))
        .thenReturn(0);
    when(batchRepository.countByOldKey_KeyIdAndStatusNot(4L, BatchStatus.COMPLETED)).thenReturn(0L);
    when(batchRepository.findByOldKey_KeyIdAndStatus(4L, BatchStatus.COMPLETED))
        .thenReturn(List.of());

    KeyUsageVerificationService.KeyUsageSnapshot s = service.computeSnapshot(key);
    assertEquals(KeyUsageVerificationService.LIFECYCLE_DRAINED, s.lifecycleStage());
    assertEquals(0L, s.remainingRecords());
    assertEquals(KeyUsageVerificationService.VERIFICATION_VERIFIED_ZERO, s.verificationState());
    assertTrue(s.decommissionEligible());
    assertFalse(s.incompleteMigrationBatches());
    assertNull(s.reencryptionWallClockSeconds());
  }

  @Test
  @DisplayName(
      "DRAINED key computes wall clock by merging overlapping batch time windows, not by"
          + " assuming all shard siblings ran fully in parallel")
  void drainedComputesWallClockFromCompletedBatches() {
    EncryptionKey key =
        new EncryptionKey(5L, KeyStatus.ENABLED, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    EncryptionKey newKey =
        new EncryptionKey(1L, KeyStatus.PRIMARY, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    when(batchCreationService.discoverReencryptableTargets()).thenReturn(List.of(t1, t2));
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "integration_private_key", 5L))
        .thenReturn(0);
    when(targetQueryService.countRecordsEncryptedWithKey(
            "ezkey_enrollment", "enrollment_proof_token", 5L))
        .thenReturn(0);
    when(batchRepository.countByOldKey_KeyIdAndStatusNot(5L, BatchStatus.COMPLETED)).thenReturn(0L);

    // Mirrors an observed production pattern: 3 of 4 shards run fully in parallel from t0 (10s
    // each), but the 4th shard queues behind worker-pool contention and only starts once the
    // others finish, running sequentially afterward (10s more). Two short non-sharded batches run
    // fully within the first parallel window. A naive "max per shard group" estimate would report
    // 10s (plus the 3s non-sharded sum = 13s); merging actual time windows correctly reports 20s.
    OffsetDateTime t0 = OffsetDateTime.now();
    List<ReencryptionBatch> completedBatches =
        List.of(
            shardBatch("ezkey_auth_attempt", "auth_attempt_proof_token", key, newKey, 4, t0, 10),
            shardBatch("ezkey_auth_attempt", "auth_attempt_proof_token", key, newKey, 4, t0, 10),
            shardBatch("ezkey_auth_attempt", "auth_attempt_proof_token", key, newKey, 4, t0, 10),
            shardBatch(
                "ezkey_auth_attempt",
                "auth_attempt_proof_token",
                key,
                newKey,
                4,
                t0.plusSeconds(10),
                10),
            nonShardedBatch("ezkey_enrollment", "integration_private_key", key, newKey, t0, 2),
            nonShardedBatch("ezkey_enrollment", "enrollment_proof_token", key, newKey, t0, 1));
    when(batchRepository.findByOldKey_KeyIdAndStatus(5L, BatchStatus.COMPLETED))
        .thenReturn(completedBatches);

    KeyUsageVerificationService.KeyUsageSnapshot s = service.computeSnapshot(key);
    assertEquals(KeyUsageVerificationService.LIFECYCLE_DRAINED, s.lifecycleStage());
    assertEquals(20L, s.reencryptionWallClockSeconds());
  }

  @Test
  @DisplayName("Wall clock merge sums non-overlapping windows and collapses overlapping ones")
  void wallClockMergesOverlappingWindowsAndSumsGaps() {
    OffsetDateTime t0 = OffsetDateTime.now();
    EncryptionKey oldKey =
        new EncryptionKey(7L, KeyStatus.ENABLED, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    EncryptionKey newKey =
        new EncryptionKey(1L, KeyStatus.PRIMARY, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");

    // [t0, t0+5] and [t0+2, t0+8] overlap -> merges into [t0, t0+8] (8s).
    // [t0+20, t0+25] is a separate, non-overlapping window (5s).
    // Total: 8 + 5 = 13s.
    List<ReencryptionBatch> batches =
        List.of(
            nonShardedBatch("ezkey_enrollment", "integration_private_key", oldKey, newKey, t0, 5),
            nonShardedBatch(
                "ezkey_enrollment", "enrollment_proof_token", oldKey, newKey, t0.plusSeconds(2), 6),
            nonShardedBatch(
                "ezkey_api_key", "secret_key_hash", oldKey, newKey, t0.plusSeconds(20), 5));

    Long result = KeyUsageVerificationService.computeWallClockSeconds(batches);
    assertEquals(13L, result);
  }

  @Test
  @DisplayName("Wall clock aggregator returns null when no completed batch has timing")
  void wallClockNullWhenNoTiming() {
    EncryptionKey oldKey =
        new EncryptionKey(6L, KeyStatus.ENABLED, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    EncryptionKey newKey =
        new EncryptionKey(1L, KeyStatus.PRIMARY, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    ReencryptionBatch untimed = new ReencryptionBatch();
    untimed.setTargetTable("ezkey_enrollment");
    untimed.setTargetColumn("integration_private_key");
    untimed.setOldKey(oldKey);
    untimed.setNewKey(newKey);

    Long result = KeyUsageVerificationService.computeWallClockSeconds(List.of(untimed));
    assertNull(result);
  }

  private static ReencryptionBatch shardBatch(
      String table,
      String column,
      EncryptionKey oldKey,
      EncryptionKey newKey,
      int shardCount,
      OffsetDateTime start,
      long durationSeconds) {
    ReencryptionBatch batch = new ReencryptionBatch();
    batch.setTargetTable(table);
    batch.setTargetColumn(column);
    batch.setOldKey(oldKey);
    batch.setNewKey(newKey);
    batch.setShardCount(shardCount);
    batch.setShardIndex(0);
    batch.setStartedAt(start);
    batch.setCompletedAt(start.plusSeconds(durationSeconds));
    return batch;
  }

  private static ReencryptionBatch nonShardedBatch(
      String table,
      String column,
      EncryptionKey oldKey,
      EncryptionKey newKey,
      OffsetDateTime start,
      long durationSeconds) {
    ReencryptionBatch batch = new ReencryptionBatch();
    batch.setTargetTable(table);
    batch.setTargetColumn(column);
    batch.setOldKey(oldKey);
    batch.setNewKey(newKey);
    batch.setStartedAt(start);
    batch.setCompletedAt(start.plusSeconds(durationSeconds));
    return batch;
  }
}
