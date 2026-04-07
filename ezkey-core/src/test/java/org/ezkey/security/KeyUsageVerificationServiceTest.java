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
  @DisplayName("PRIMARY key yields NOT_APPLICABLE verification")
  void primaryKey() {
    EncryptionKey key =
        new EncryptionKey(1L, KeyStatus.PRIMARY, "AES256_GCM", OffsetDateTime.now(), "SYSTEM");
    KeyUsageVerificationService.KeyUsageSnapshot s = service.computeSnapshot(key);
    assertEquals(KeyUsageVerificationService.LIFECYCLE_PRIMARY, s.lifecycleStage());
    assertNull(s.remainingRecords());
    assertEquals(KeyUsageVerificationService.VERIFICATION_NOT_APPLICABLE, s.verificationState());
    assertFalse(s.decommissionEligible());
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

    KeyUsageVerificationService.KeyUsageSnapshot s = service.computeSnapshot(key);
    assertEquals(KeyUsageVerificationService.LIFECYCLE_DRAINED, s.lifecycleStage());
    assertEquals(0L, s.remainingRecords());
    assertEquals(KeyUsageVerificationService.VERIFICATION_VERIFIED_ZERO, s.verificationState());
    assertTrue(s.decommissionEligible());
    assertFalse(s.incompleteMigrationBatches());
  }
}
