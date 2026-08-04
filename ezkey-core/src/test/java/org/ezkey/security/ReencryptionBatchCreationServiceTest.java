/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ReencryptionBatchCreationService effective auth-attempt sharding")
class ReencryptionBatchCreationServiceTest {

  private KeyManagementOperations keyManagementOperations;
  private EncryptionKeyRepository keyRepository;
  private ReencryptionBatchRepository batchRepository;
  private AuditLogService auditLogService;
  private ReencryptionTargetQueryService targetQueryService;
  private TinkProperties tinkProperties;
  private ReencryptionBatchCreationService service;

  private EncryptionKey oldKey;
  private EncryptionKey primaryKey;

  @BeforeEach
  void setUp() {
    keyManagementOperations = mock(KeyManagementOperations.class);
    keyRepository = mock(EncryptionKeyRepository.class);
    batchRepository = mock(ReencryptionBatchRepository.class);
    auditLogService = mock(AuditLogService.class);
    targetQueryService = mock(ReencryptionTargetQueryService.class);
    tinkProperties = new TinkProperties();
    tinkProperties.getReencryption().setAuthAttemptShardCount(4);

    service =
        new ReencryptionBatchCreationService(
            keyManagementOperations,
            keyRepository,
            batchRepository,
            auditLogService,
            targetQueryService,
            tinkProperties);

    oldKey = new EncryptionKey(100L, KeyStatus.ENABLED, "AES256_GCM", OffsetDateTime.now(), "TEST");
    primaryKey =
        new EncryptionKey(200L, KeyStatus.PRIMARY, "AES256_GCM", OffsetDateTime.now(), "TEST");

    when(batchRepository.findAnyActiveBatchesByTargetAndOldKey(any(), any(), any()))
        .thenReturn(List.of());
    when(batchRepository.save(any(ReencryptionBatch.class)))
        .thenAnswer(
            invocation -> {
              ReencryptionBatch batch = invocation.getArgument(0);
              if (batch.getBatchId() == null) {
                batch.setBatchId(1);
              }
              return batch;
            });
  }

  @Test
  @DisplayName("Small backlog uses effective shard count min(configured, totalRecords)")
  void createBatchesForTarget_usesEffectiveShardCountForSmallBacklog() {
    String table = ReencryptionBatchCreationService.EZKEY_AUTH_ATTEMPT_TABLE;
    String column = "auth_attempt_proof_token";

    when(targetQueryService.countRecordsEncryptedWithKey(table, column, oldKey.getKeyId()))
        .thenReturn(2);
    when(targetQueryService.countRecordsEncryptedWithKey(
            eq(table), eq(column), eq(oldKey.getKeyId()), eq(0), eq(2)))
        .thenReturn(1);
    when(targetQueryService.countRecordsEncryptedWithKey(
            eq(table), eq(column), eq(oldKey.getKeyId()), eq(1), eq(2)))
        .thenReturn(1);

    List<ReencryptionBatch> created =
        service.createBatchesForTarget(
            oldKey, primaryKey, new ReencryptionBatchCreationService.Target(table, column), "TEST");

    assertThat(created).hasSize(2);
    ArgumentCaptor<ReencryptionBatch> captor = ArgumentCaptor.forClass(ReencryptionBatch.class);
    verify(batchRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .allMatch(b -> Integer.valueOf(2).equals(b.getShardCount()))
        .extracting(ReencryptionBatch::getShardIndex)
        .containsExactlyInAnyOrder(0, 1);
    verify(targetQueryService, never())
        .countRecordsEncryptedWithKey(eq(table), eq(column), eq(oldKey.getKeyId()), any(), eq(4));
  }

  @Test
  @DisplayName("Single non-empty residue class falls back to a non-sharded batch")
  void createBatchesForTarget_fallsBackToNonShardedWhenOnlyOneResidueHasRows() {
    String table = ReencryptionBatchCreationService.EZKEY_AUTH_ATTEMPT_TABLE;
    String column = "auth_attempt_proof_token";

    when(targetQueryService.countRecordsEncryptedWithKey(table, column, oldKey.getKeyId()))
        .thenReturn(2);
    when(targetQueryService.countRecordsEncryptedWithKey(
            eq(table), eq(column), eq(oldKey.getKeyId()), eq(0), eq(2)))
        .thenReturn(2);
    when(targetQueryService.countRecordsEncryptedWithKey(
            eq(table), eq(column), eq(oldKey.getKeyId()), eq(1), eq(2)))
        .thenReturn(0);

    List<ReencryptionBatch> created =
        service.createBatchesForTarget(
            oldKey, primaryKey, new ReencryptionBatchCreationService.Target(table, column), "TEST");

    assertThat(created).hasSize(1);
    ReencryptionBatch batch = created.get(0);
    assertThat(batch.getShardCount()).isNull();
    assertThat(batch.getShardIndex()).isNull();
    assertThat(batch.getRecordsTotal()).isEqualTo(2);
  }

  @Test
  @DisplayName("One total record never shards even when configured shard count is 4")
  void createBatchesForTarget_singleRecordCreatesNonShardedBatch() {
    String table = ReencryptionBatchCreationService.EZKEY_AUTH_ATTEMPT_TABLE;
    String column = "auth_attempt_proof_token";

    when(targetQueryService.countRecordsEncryptedWithKey(table, column, oldKey.getKeyId()))
        .thenReturn(1);

    List<ReencryptionBatch> created =
        service.createBatchesForTarget(
            oldKey, primaryKey, new ReencryptionBatchCreationService.Target(table, column), "TEST");

    assertThat(created).hasSize(1);
    assertThat(created.get(0).getShardCount()).isNull();
    verify(targetQueryService, never())
        .countRecordsEncryptedWithKey(eq(table), eq(column), eq(oldKey.getKeyId()), any(), any());
  }

  @Test
  @DisplayName("Enrollment targets remain non-sharded regardless of auth-attempt shard config")
  void createBatchesForTarget_enrollmentNeverSharded() {
    String table = "ezkey_enrollment";
    String column = "enrollment_proof_token";

    when(targetQueryService.countRecordsEncryptedWithKey(table, column, oldKey.getKeyId()))
        .thenReturn(10);

    List<ReencryptionBatch> created =
        service.createBatchesForTarget(
            oldKey, primaryKey, new ReencryptionBatchCreationService.Target(table, column), "TEST");

    assertThat(created).hasSize(1);
    assertThat(created.get(0).getShardCount()).isNull();
    verify(targetQueryService, never())
        .countRecordsEncryptedWithKey(eq(table), eq(column), eq(oldKey.getKeyId()), any(), any());
  }

  @Test
  @DisplayName("Active batch for the same target and old key blocks creation")
  void createBatchesForTarget_skipsWhenAnyActiveBatchExists() {
    String table = ReencryptionBatchCreationService.EZKEY_AUTH_ATTEMPT_TABLE;
    String column = "auth_attempt_proof_token";
    when(batchRepository.findAnyActiveBatchesByTargetAndOldKey(table, column, oldKey.getKeyId()))
        .thenReturn(List.of(new ReencryptionBatch()));

    List<ReencryptionBatch> created =
        service.createBatchesForTarget(
            oldKey, primaryKey, new ReencryptionBatchCreationService.Target(table, column), "TEST");

    assertThat(created).isEmpty();
    verify(targetQueryService, never())
        .countRecordsEncryptedWithKey(any(), any(), any(), any(), any());
    verify(targetQueryService, never()).countRecordsEncryptedWithKey(any(), any(), any());
    verify(batchRepository, never()).save(any());
  }

  @Test
  @DisplayName("Large backlog keeps configured shard count when every residue class has rows")
  void createBatchesForTarget_keepsConfiguredShardsForLargeBacklog() {
    String table = ReencryptionBatchCreationService.EZKEY_AUTH_ATTEMPT_TABLE;
    String column = "auth_attempt_proof_token";

    when(targetQueryService.countRecordsEncryptedWithKey(table, column, oldKey.getKeyId()))
        .thenReturn(100);
    for (int i = 0; i < 4; i++) {
      when(targetQueryService.countRecordsEncryptedWithKey(
              eq(table), eq(column), eq(oldKey.getKeyId()), eq(i), eq(4)))
          .thenReturn(25);
    }

    List<ReencryptionBatch> created =
        service.createBatchesForTarget(
            oldKey, primaryKey, new ReencryptionBatchCreationService.Target(table, column), "TEST");

    assertThat(created).hasSize(4);
    ArgumentCaptor<ReencryptionBatch> captor = ArgumentCaptor.forClass(ReencryptionBatch.class);
    verify(batchRepository, times(4)).save(captor.capture());
    assertThat(captor.getAllValues())
        .allMatch(b -> Integer.valueOf(4).equals(b.getShardCount()))
        .extracting(ReencryptionBatch::getShardIndex)
        .containsExactlyInAnyOrder(0, 1, 2, 3);
  }
}
