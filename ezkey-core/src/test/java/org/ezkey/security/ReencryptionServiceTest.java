/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ReencryptionServiceTest
 * Description: Critical unit tests for ReencryptionService with mocks.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.TinkProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Critical unit tests for ReencryptionService.
 *
 * <p>This test class provides comprehensive coverage of the ReencryptionService focusing on
 * security-critical re-encryption operations. Tests use mocks to isolate the service logic and
 * verify correct behavior without requiring database or encryption infrastructure.
 *
 * <p><b>Critical Test Coverage (Priority 1):</b>
 *
 * <ul>
 *   <li>reencryptRecord() - Core re-encryption logic with all edge cases
 *   <li>processBatch() - Complete batch processing with error handling
 *   <li>countRecordsEncryptedWithKey() - Accurate record counting
 * </ul>
 *
 * <p><b>Security Focus:</b> These tests validate the critical security aspects of re-encryption
 * including data integrity, error handling, and proper encryption/decryption flow to prevent data
 * corruption.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ReencryptionService Critical Unit Tests")
class ReencryptionServiceTest {

  @Mock private EncryptionOperations encryptionOperations;
  @Mock private KeyManagementOperations keyManagementOperations;
  @Mock private EncryptionKeyRepository keyRepository;
  @Mock private ReencryptionBatchRepository batchRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private TinkProperties properties;
  @Mock private AuditLogService auditLogService;
  @Mock private EntityManager entityManager;
  @Mock private ObjectProvider<MeterRegistry> meterRegistryProvider;
  @Mock private org.ezkey.audit.integrity.ScheduledJobLastRunService jobLastRunService;

  private ReencryptionRecordCipher recordCipher;
  private ReencryptionTargetQueryService targetQueryService;
  private ReencryptionRowPersistenceService rowPersistence;
  private ReencryptionBatchProcessingService batchProcessingService;
  private ReencryptionBatchParallelRunner parallelRunner;
  private ReencryptionBatchCreationService batchCreationService;
  private ThreadPoolTaskExecutor reencryptionBatchExecutor;

  private ReencryptionService service;

  private EncryptionKey oldKey;
  private EncryptionKey newKey;
  private ReencryptionBatch batch;
  private TinkProperties.Reencryption reencryptionConfig;

  @BeforeEach
  void setUp() {
    lenient().when(meterRegistryProvider.getIfAvailable()).thenReturn(null);

    recordCipher = new ReencryptionRecordCipher(encryptionOperations);
    targetQueryService =
        new ReencryptionTargetQueryService(enrollmentRepository, authAttemptRepository);
    rowPersistence =
        new ReencryptionRowPersistenceService(
            enrollmentRepository, authAttemptRepository, entityManager, recordCipher);
    batchProcessingService =
        new ReencryptionBatchProcessingService(
            batchRepository,
            keyRepository,
            properties,
            auditLogService,
            targetQueryService,
            rowPersistence,
            recordCipher,
            meterRegistryProvider);
    reencryptionBatchExecutor = new ThreadPoolTaskExecutor();
    reencryptionBatchExecutor.setCorePoolSize(1);
    reencryptionBatchExecutor.setMaxPoolSize(1);
    reencryptionBatchExecutor.setQueueCapacity(10);
    reencryptionBatchExecutor.setThreadNamePrefix("reenc-test-");
    reencryptionBatchExecutor.initialize();
    batchCreationService =
        new ReencryptionBatchCreationService(
            keyManagementOperations,
            keyRepository,
            batchRepository,
            auditLogService,
            targetQueryService,
            properties);
    parallelRunner =
        new ReencryptionBatchParallelRunner(
            reencryptionBatchExecutor, batchProcessingService, properties);
    service =
        new ReencryptionService(
            encryptionOperations,
            keyRepository,
            batchRepository,
            properties,
            auditLogService,
            batchCreationService,
            batchProcessingService,
            parallelRunner,
            jobLastRunService);

    // Setup encryption keys
    oldKey = new EncryptionKey();
    oldKey.setKeyId(1111111111L);
    oldKey.setKeyStatus(KeyStatus.ENABLED);
    oldKey.setRecordsReencrypted(0L);

    newKey = new EncryptionKey();
    newKey.setKeyId(2222222222L);
    newKey.setKeyStatus(KeyStatus.PRIMARY);

    // Setup batch
    batch = new ReencryptionBatch();
    batch.setBatchId(1);
    batch.setTargetTable("ezkey_enrollment");
    batch.setTargetColumn("integration_private_key");
    batch.setOldKey(oldKey);
    batch.setNewKey(newKey);
    batch.setRecordsTotal(100);
    batch.setRecordsDone(0);
    batch.setRecordsFailed(0);
    batch.setRecordsSkipped(0);
    batch.setStatus(BatchStatus.PENDING);
    batch.setProgressPct(BigDecimal.ZERO);

    // Setup reencryption configuration
    reencryptionConfig = new TinkProperties.Reencryption();
    reencryptionConfig.setBatchSize(10);
    reencryptionConfig.setThrottleMs(0); // No throttle in tests
    reencryptionConfig.setMaxBatchesPerRun(100);
    reencryptionConfig.setMaxDurationMinutes(60);
    reencryptionConfig.setEnabled(true);
    reencryptionConfig.setParallelBatchWorkers(1);
    reencryptionConfig.setAuthAttemptShardCount(1);

    // Use lenient() for stubbings that may not be used by all tests
    lenient().when(properties.getReencryption()).thenReturn(reencryptionConfig);
    lenient().when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    lenient().when(keyManagementOperations.isInitialized()).thenReturn(true);
    lenient().when(keyManagementOperations.getCurrentPrimaryKeyId()).thenReturn(2222222222L);
    lenient().when(keyRepository.findById(2222222222L)).thenReturn(java.util.Optional.of(newKey));
    lenient().when(batchRepository.findById(1)).thenReturn(Optional.of(batch));
  }

  // ===== PRIORITY 1: reencryptRecord() Tests =====

  @Test
  @DisplayName("reencryptRecord() - Should successfully re-encrypt Enrollment record")
  void reencryptRecord_ShouldSuccessfullyReencryptEnrollment() {
    // Arrange
    Enrollment enrollment = createMockEnrollment(123, "ENC:1111111111:encrypted-data");
    when(encryptionOperations.decrypt("ENC:1111111111:encrypted-data"))
        .thenReturn("plaintext-data");
    when(encryptionOperations.encrypt("plaintext-data"))
        .thenReturn("ENC:2222222222:reencrypted-data");

    // Act
    ReencryptionRecordCipher.ReencryptResult result = invokeReencryptRecord(batch, enrollment);

    // Assert
    assertTrue(result.reencrypted());
    assertNotNull(result.modifiedRecord());
    verify(encryptionOperations).decrypt("ENC:1111111111:encrypted-data");
    verify(encryptionOperations).encrypt("plaintext-data");
    // Note: save() is no longer called here, records are batch saved in processBatch()
    // Note: setEncryptedField is called internally, verified by the save() call
  }

  @Test
  @DisplayName("reencryptRecord() - Should successfully re-encrypt AuthAttempt record")
  void reencryptRecord_ShouldSuccessfullyReencryptAuthAttempt() {
    // Arrange
    AuthAttempt authAttempt = createMockAuthAttempt(456, "ENC:1111111111:encrypted-token");
    when(encryptionOperations.decrypt("ENC:1111111111:encrypted-token"))
        .thenReturn("plaintext-token");
    when(encryptionOperations.encrypt("plaintext-token"))
        .thenReturn("ENC:2222222222:reencrypted-token");

    // Act
    ReencryptionBatch authBatch = createBatch("ezkey_auth_attempt", "auth_attempt_proof_token");
    ReencryptionRecordCipher.ReencryptResult result = invokeReencryptRecord(authBatch, authAttempt);

    // Assert
    assertTrue(result.reencrypted());
    assertNotNull(result.modifiedRecord());
    verify(encryptionOperations).decrypt("ENC:1111111111:encrypted-token");
    verify(encryptionOperations).encrypt("plaintext-token");
    // Note: save() is no longer called here, records are batch saved in processBatch()
  }

  @Test
  @DisplayName("reencryptRecord() - Should skip if already encrypted with new key")
  void reencryptRecord_ShouldSkipIfAlreadyEncryptedWithNewKey() {
    // Arrange
    Enrollment enrollment = createMockEnrollment(123, "ENC:2222222222:already-reencrypted");

    // Act
    ReencryptionRecordCipher.ReencryptResult result = invokeReencryptRecord(batch, enrollment);

    // Assert
    assertFalse(result.reencrypted());
    assertNull(result.modifiedRecord());
    verify(encryptionOperations, never()).decrypt(anyString());
    verify(encryptionOperations, never()).encrypt(anyString());
  }

  @Test
  @DisplayName("reencryptRecord() - Should skip if not encrypted with old key")
  void reencryptRecord_ShouldSkipIfNotEncryptedWithOldKey() {
    // Arrange
    Enrollment enrollment = createMockEnrollment(123, "ENC:9999999999:other-key-data");

    // Act
    ReencryptionRecordCipher.ReencryptResult result = invokeReencryptRecord(batch, enrollment);

    // Assert
    assertFalse(result.reencrypted());
    assertNull(result.modifiedRecord());
    verify(encryptionOperations, never()).decrypt(anyString());
    verify(encryptionOperations, never()).encrypt(anyString());
  }

  @Test
  @DisplayName("reencryptRecord() - Should skip if encrypted value is null")
  void reencryptRecord_ShouldSkipIfEncryptedValueIsNull() {
    // Arrange
    Enrollment enrollment = createMockEnrollment(123, null);

    // Act
    ReencryptionRecordCipher.ReencryptResult result = invokeReencryptRecord(batch, enrollment);

    // Assert
    assertFalse(result.reencrypted());
    assertNull(result.modifiedRecord());
    verify(encryptionOperations, never()).decrypt(anyString());
    verify(encryptionOperations, never()).encrypt(anyString());
  }

  @Test
  @DisplayName("reencryptRecord() - Should handle decryption error gracefully")
  void reencryptRecord_ShouldHandleDecryptionError() {
    // Arrange
    Enrollment enrollment = createMockEnrollment(123, "ENC:1111111111:corrupted-data");
    when(encryptionOperations.decrypt("ENC:1111111111:corrupted-data"))
        .thenThrow(new RuntimeException("Decryption failed"));

    // Act & Assert
    assertThrows(RuntimeException.class, () -> invokeReencryptRecord(batch, enrollment));
    verify(encryptionOperations).decrypt("ENC:1111111111:corrupted-data");
    verify(encryptionOperations, never()).encrypt(anyString());
    // Note: save() is no longer called here, records are batch saved in processBatch()
  }

  @Test
  @DisplayName("reencryptRecord() - Should handle encryption error gracefully")
  void reencryptRecord_ShouldHandleEncryptionError() {
    // Arrange
    Enrollment enrollment = createMockEnrollment(123, "ENC:1111111111:encrypted-data");
    when(encryptionOperations.decrypt("ENC:1111111111:encrypted-data"))
        .thenReturn("plaintext-data");
    when(encryptionOperations.encrypt("plaintext-data"))
        .thenThrow(new RuntimeException("Encryption failed"));

    // Act & Assert
    assertThrows(RuntimeException.class, () -> invokeReencryptRecord(batch, enrollment));
    verify(encryptionOperations).decrypt("ENC:1111111111:encrypted-data");
    verify(encryptionOperations).encrypt("plaintext-data");
    // Note: save() is no longer called here, records are batch saved in processBatch()
  }

  // ===== PRIORITY 1: processBatch() Tests =====

  @Test
  @DisplayName("processBatch() - Should skip batch if already COMPLETED")
  void processBatch_ShouldSkipIfAlreadyCompleted() {
    // Arrange
    batch.setStatus(BatchStatus.COMPLETED);

    // Act
    service.processBatch(batch);

    // Assert
    verify(batchRepository, never()).save(any(ReencryptionBatch.class));
    verify(encryptionOperations, never()).decrypt(anyString());
  }

  @Test
  @DisplayName("processBatch() - Should transition PENDING to IN_PROGRESS")
  void processBatch_ShouldTransitionPendingToInProgress() {
    // Arrange
    batch.setStatus(BatchStatus.PENDING);
    batch.setRecordsTotal(1);
    when(batchRepository.save(any(ReencryptionBatch.class))).thenReturn(batch);
    when(enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(anyString(), any(), anyInt()))
        .thenReturn(List.of());
    // Count says old-key ciphertext still exists, but fetch returned nothing: do not reconcile away
    // the gap or the batch would complete in one shot (reconciliation + completion).
    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike("ENC:1111111111:%"))
        .thenReturn(1);

    // Act
    service.processBatch(batch);

    // Assert
    assertEquals(BatchStatus.IN_PROGRESS, batch.getStatus());
    assertNotNull(batch.getStartedAt());
    verify(batchRepository, atLeast(1)).save(batch);
  }

  @Test
  @DisplayName("processBatch() - Should process records and update progress")
  void processBatch_ShouldProcessRecordsAndUpdateProgress() {
    // Arrange
    batch.setStatus(BatchStatus.PENDING);
    batch.setRecordsTotal(2);

    Enrollment enrollment1 = createMockEnrollment(1, "ENC:1111111111:data1");
    Enrollment enrollment2 = createMockEnrollment(2, "ENC:1111111111:data2");

    when(batchRepository.save(any(ReencryptionBatch.class))).thenReturn(batch);
    when(enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(anyString(), any(), anyInt()))
        .thenReturn(List.of(enrollment1, enrollment2))
        .thenReturn(List.of());

    when(encryptionOperations.decrypt(anyString())).thenReturn("plaintext");
    when(encryptionOperations.encrypt("plaintext")).thenReturn("ENC:2222222222:reencrypted");
    when(enrollmentRepository.findByIdForReencryptionUpdate(1))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(1, "ENC:1111111111:data1")));
    when(enrollmentRepository.findByIdForReencryptionUpdate(2))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(2, "ENC:1111111111:data2")));
    when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.processBatch(batch);

    // Assert
    assertEquals(2, batch.getRecordsDone());
    assertEquals(0, batch.getRecordsFailed());
    assertEquals(0, batch.getRecordsSkipped());
    assertEquals(new BigDecimal("100.00"), batch.getProgressPct());
    verify(batchRepository, atLeast(1)).save(batch);
    verify(enrollmentRepository, times(2)).save(any(Enrollment.class));
  }

  @Test
  @DisplayName("processBatch() - Should mark batch as COMPLETED when all records processed")
  void processBatch_ShouldMarkBatchAsCompleted() {
    // Arrange
    batch.setStatus(BatchStatus.PENDING);
    batch.setRecordsTotal(1);

    Enrollment enrollment = createMockEnrollment(1, "ENC:1111111111:data");

    when(batchRepository.save(any(ReencryptionBatch.class))).thenReturn(batch);
    when(enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(anyString(), any(), anyInt()))
        .thenReturn(List.of(enrollment))
        .thenReturn(List.of());

    when(encryptionOperations.decrypt(anyString())).thenReturn("plaintext");
    when(encryptionOperations.encrypt("plaintext")).thenReturn("ENC:2222222222:reencrypted");
    when(enrollmentRepository.findByIdForReencryptionUpdate(1))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(1, "ENC:1111111111:data")));
    when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(keyRepository.save(any(EncryptionKey.class))).thenReturn(oldKey);

    // Act
    service.processBatch(batch);

    // Assert
    assertEquals(BatchStatus.COMPLETED, batch.getStatus());
    assertNotNull(batch.getCompletedAt());
    assertEquals(1, batch.getRecordsDone());
    verify(keyRepository).save(oldKey); // Statistics updated
    verify(auditLogService).log(any()); // Audit log emitted
  }

  @Test
  @DisplayName(
      "processBatch() - Should complete when fetch empty but no ciphertext left on old key"
          + " (reconcile)")
  void processBatch_ShouldCompleteWhenExternallyMigrated_Reconciliation() {
    batch.setStatus(BatchStatus.PENDING);
    batch.setRecordsTotal(5);
    batch.setRecordsDone(0);
    batch.setRecordsFailed(0);
    batch.setRecordsSkipped(0);

    when(batchRepository.save(any(ReencryptionBatch.class))).thenReturn(batch);
    when(enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(anyString(), any(), anyInt()))
        .thenReturn(List.of());
    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike("ENC:1111111111:%"))
        .thenReturn(0);
    when(keyRepository.save(any(EncryptionKey.class))).thenReturn(oldKey);

    service.processBatch(batch);

    assertEquals(BatchStatus.COMPLETED, batch.getStatus());
    assertEquals(0, batch.getRecordsDone());
    assertEquals(5, batch.getRecordsSkipped());
    // Progress percentage reflects recordsDone only (skipped rows do not raise pct).
    assertEquals(new BigDecimal("0.00"), batch.getProgressPct());
    verify(keyRepository).save(oldKey);
    verify(auditLogService).log(any());
  }

  @Test
  @DisplayName("processBatch() - Should handle record processing errors")
  void processBatch_ShouldHandleRecordProcessingErrors() {
    // Arrange
    batch.setStatus(BatchStatus.PENDING);
    batch.setRecordsTotal(2);

    Enrollment enrollment1 = createMockEnrollment(1, "ENC:1111111111:data1");
    Enrollment enrollment2 = createMockEnrollment(2, "ENC:1111111111:data2");

    when(batchRepository.save(any(ReencryptionBatch.class))).thenReturn(batch);
    when(enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(anyString(), any(), anyInt()))
        .thenReturn(List.of(enrollment1, enrollment2))
        .thenReturn(List.of());

    when(encryptionOperations.decrypt("ENC:1111111111:data1")).thenReturn("plaintext1");
    when(encryptionOperations.encrypt("plaintext1")).thenReturn("ENC:2222222222:reencrypted1");
    when(encryptionOperations.decrypt("ENC:1111111111:data2"))
        .thenThrow(new RuntimeException("Decryption failed"));
    when(enrollmentRepository.findByIdForReencryptionUpdate(1))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(1, "ENC:1111111111:data1")));
    when(enrollmentRepository.findByIdForReencryptionUpdate(2))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(2, "ENC:1111111111:data2")));
    // Note: save() may not be called if no records are successfully re-encrypted
    lenient()
        .when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.processBatch(batch);

    // Assert
    assertEquals(1, batch.getRecordsDone());
    assertEquals(1, batch.getRecordsFailed());
    assertEquals(0, batch.getRecordsSkipped());
    verify(batchRepository, atLeast(1)).save(batch);
  }

  @Test
  @DisplayName("processBatch() - Should update lastRecordId correctly")
  void processBatch_ShouldUpdateLastRecordId() {
    // Arrange
    batch.setStatus(BatchStatus.PENDING);
    batch.setRecordsTotal(2);
    batch.setLastRecordId(null);

    Enrollment enrollment1 = createMockEnrollment(10, "ENC:1111111111:data1");
    Enrollment enrollment2 = createMockEnrollment(20, "ENC:1111111111:data2");

    when(batchRepository.save(any(ReencryptionBatch.class))).thenReturn(batch);
    when(enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(anyString(), any(), anyInt()))
        .thenReturn(List.of(enrollment1, enrollment2))
        .thenReturn(List.of());

    when(encryptionOperations.decrypt(anyString())).thenReturn("plaintext");
    when(encryptionOperations.encrypt("plaintext")).thenReturn("ENC:2222222222:reencrypted");
    when(enrollmentRepository.findByIdForReencryptionUpdate(10))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(10, "ENC:1111111111:data1")));
    when(enrollmentRepository.findByIdForReencryptionUpdate(20))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(20, "ENC:1111111111:data2")));
    when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.processBatch(batch);

    // Assert
    assertEquals(Long.valueOf(20), batch.getLastRecordId()); // Should be last processed ID
  }

  @Test
  @DisplayName("processBatch() - Should skip records already encrypted with new key")
  void processBatch_ShouldSkipRecordsAlreadyEncryptedWithNewKey() {
    // Arrange
    batch.setStatus(BatchStatus.PENDING);
    batch.setRecordsTotal(2);

    Enrollment enrollment1 = createMockEnrollment(1, "ENC:1111111111:data1");
    Enrollment enrollment2 = createMockEnrollment(2, "ENC:2222222222:already-reencrypted");

    when(batchRepository.save(any(ReencryptionBatch.class))).thenReturn(batch);
    when(enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(anyString(), any(), anyInt()))
        .thenReturn(List.of(enrollment1, enrollment2))
        .thenReturn(List.of());

    when(encryptionOperations.decrypt("ENC:1111111111:data1")).thenReturn("plaintext1");
    when(encryptionOperations.encrypt("plaintext1")).thenReturn("ENC:2222222222:reencrypted1");
    when(enrollmentRepository.findByIdForReencryptionUpdate(1))
        .thenAnswer(inv -> Optional.of(createMockEnrollment(1, "ENC:1111111111:data1")));
    lenient()
        .when(enrollmentRepository.save(any(Enrollment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    service.processBatch(batch);

    // Assert
    assertEquals(1, batch.getRecordsDone());
    assertEquals(0, batch.getRecordsFailed());
    assertEquals(1, batch.getRecordsSkipped());
  }

  // ===== PRIORITY 1: countRecordsEncryptedWithKey() Tests =====

  @Test
  @DisplayName("countRecordsEncryptedWithKey() - Should count Enrollment integration_private_key")
  void countRecordsEncryptedWithKey_ShouldCountEnrollmentIntegrationPrivateKey() {
    // Arrange
    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike("ENC:1111111111:%"))
        .thenReturn(42);

    // Act
    int count =
        invokeCountRecordsEncryptedWithKey(
            "ezkey_enrollment", "integration_private_key", 1111111111L);

    // Assert
    assertEquals(42, count);
    verify(enrollmentRepository).countByEncryptedIntegrationPrivateKeyLike("ENC:1111111111:%");
  }

  @Test
  @DisplayName("countRecordsEncryptedWithKey() - Should count Enrollment enrollment_proof_token")
  void countRecordsEncryptedWithKey_ShouldCountEnrollmentProofToken() {
    // Arrange
    when(enrollmentRepository.countByEncryptedEnrollmentProofTokenLike("ENC:1111111111:%"))
        .thenReturn(15);

    // Act
    int count =
        invokeCountRecordsEncryptedWithKey(
            "ezkey_enrollment", "enrollment_proof_token", 1111111111L);

    // Assert
    assertEquals(15, count);
    verify(enrollmentRepository).countByEncryptedEnrollmentProofTokenLike("ENC:1111111111:%");
  }

  @Test
  @DisplayName("countRecordsEncryptedWithKey() - Should count AuthAttempt auth_attempt_proof_token")
  void countRecordsEncryptedWithKey_ShouldCountAuthAttemptProofToken() {
    // Arrange
    when(authAttemptRepository.countByEncryptedAuthAttemptProofTokenLike(
            eq("ENC:1111111111:%"), isNull(), isNull()))
        .thenReturn(33);

    // Act
    int count =
        invokeCountRecordsEncryptedWithKey(
            "ezkey_auth_attempt", "auth_attempt_proof_token", 1111111111L);

    // Assert
    assertEquals(33, count);
    verify(authAttemptRepository)
        .countByEncryptedAuthAttemptProofTokenLike(eq("ENC:1111111111:%"), isNull(), isNull());
  }

  @Test
  @DisplayName("countRecordsEncryptedWithKey() - Should count AuthAttempt device_proof_token")
  void countRecordsEncryptedWithKey_ShouldCountAuthAttemptDeviceProofToken() {
    // Arrange
    when(authAttemptRepository.countByEncryptedDeviceProofTokenLike(
            eq("ENC:1111111111:%"), isNull(), isNull()))
        .thenReturn(7);

    // Act
    int count =
        invokeCountRecordsEncryptedWithKey("ezkey_auth_attempt", "device_proof_token", 1111111111L);

    // Assert
    assertEquals(7, count);
    verify(authAttemptRepository)
        .countByEncryptedDeviceProofTokenLike(eq("ENC:1111111111:%"), isNull(), isNull());
  }

  @Test
  @DisplayName("countRecordsEncryptedWithKey() - Should return 0 for unknown table")
  void countRecordsEncryptedWithKey_ShouldReturnZeroForUnknownTable() {
    // Act
    int count = invokeCountRecordsEncryptedWithKey("unknown_table", "some_column", 1111111111L);

    // Assert
    assertEquals(0, count);
    verify(enrollmentRepository, never()).countByEncryptedIntegrationPrivateKeyLike(anyString());
    verify(authAttemptRepository, never())
        .countByEncryptedAuthAttemptProofTokenLike(anyString(), any(), any());
  }

  @Test
  @DisplayName("countRecordsEncryptedWithKey() - Should return 0 for unknown column")
  void countRecordsEncryptedWithKey_ShouldReturnZeroForUnknownColumn() {
    // Act
    int count =
        invokeCountRecordsEncryptedWithKey("ezkey_enrollment", "unknown_column", 1111111111L);

    // Assert
    assertEquals(0, count);
  }

  @Test
  @DisplayName("countRecordsEncryptedWithKey() - Should use correct prefix format")
  void countRecordsEncryptedWithKey_ShouldUseCorrectPrefixFormat() {
    // Arrange
    ArgumentCaptor<String> prefixCaptor = ArgumentCaptor.forClass(String.class);
    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike(prefixCaptor.capture()))
        .thenReturn(5);

    // Act
    invokeCountRecordsEncryptedWithKey("ezkey_enrollment", "integration_private_key", 12345L);

    // Assert
    assertEquals("ENC:12345:%", prefixCaptor.getValue());
  }

  // ===== PRIORITY 1: discoverReencryptableTargets() Tests =====

  @Test
  @DisplayName("discoverReencryptableTargets() - Should discover all Enrollment encrypted fields")
  void discoverReencryptableTargets_ShouldDiscoverEnrollmentFields() {
    // Act
    List<ReencryptionBatchCreationService.Target> targets = invokeDiscoverReencryptableTargets();

    // Assert
    assertTrue(
        targets.contains(
            new ReencryptionBatchCreationService.Target(
                "ezkey_enrollment", "integration_private_key")),
        "Should discover integration_private_key");
    assertTrue(
        targets.contains(
            new ReencryptionBatchCreationService.Target(
                "ezkey_enrollment", "enrollment_proof_token")),
        "Should discover enrollment_proof_token");
  }

  @Test
  @DisplayName("discoverReencryptableTargets() - Should discover all AuthAttempt encrypted fields")
  void discoverReencryptableTargets_ShouldDiscoverAuthAttemptFields() {
    // Act
    List<ReencryptionBatchCreationService.Target> targets = invokeDiscoverReencryptableTargets();

    // Assert
    assertTrue(
        targets.contains(
            new ReencryptionBatchCreationService.Target(
                "ezkey_auth_attempt", "auth_attempt_proof_token")),
        "Should discover auth_attempt_proof_token");
    assertTrue(
        targets.contains(
            new ReencryptionBatchCreationService.Target(
                "ezkey_auth_attempt", "device_proof_token")),
        "Should discover device_proof_token");
  }

  @Test
  @DisplayName("discoverReencryptableTargets() - Should discover all 4 encrypted fields")
  void discoverReencryptableTargets_ShouldDiscoverAllFields() {
    // Act
    List<ReencryptionBatchCreationService.Target> targets = invokeDiscoverReencryptableTargets();

    // Assert
    assertEquals(4, targets.size(), "Should discover exactly 4 encrypted fields");
  }

  @Test
  @DisplayName("discoverReencryptableTargets() - Should eliminate duplicates")
  void discoverReencryptableTargets_ShouldEliminateDuplicates() {
    // Act
    List<ReencryptionBatchCreationService.Target> targets = invokeDiscoverReencryptableTargets();

    // Assert - Check that each target appears only once
    long enrollmentPrivateKeyCount =
        targets.stream()
            .filter(
                t ->
                    t.table().equals("ezkey_enrollment")
                        && t.column().equals("integration_private_key"))
            .count();
    assertEquals(1, enrollmentPrivateKeyCount, "Should have no duplicates");
  }

  @Test
  @DisplayName("discoverReencryptableTargets() - Should return consistent order")
  void discoverReencryptableTargets_ShouldReturnConsistentOrder() {
    // Act - Call multiple times
    List<ReencryptionBatchCreationService.Target> targets1 = invokeDiscoverReencryptableTargets();
    List<ReencryptionBatchCreationService.Target> targets2 = invokeDiscoverReencryptableTargets();

    // Assert
    assertEquals(targets1, targets2, "Should return same order on multiple calls");
  }

  @Test
  @DisplayName(
      "discoverReencryptableTargets() - Should use getEncryptedFields() as source of truth")
  void discoverReencryptableTargets_ShouldUseGetEncryptedFieldsAsSourceOfTruth() {
    // Arrange - Create enrollment with encrypted fields to verify getEncryptedFields() works
    Enrollment enrollment = new Enrollment();
    enrollment.setEncryptedField("integration_private_key", "ENC:1:test1");
    enrollment.setEncryptedField("enrollment_proof_token", "ENC:1:test2");

    // Verify getEncryptedFields() returns the fields
    Map<String, String> enrollmentFields = enrollment.getEncryptedFields();
    assertEquals(2, enrollmentFields.size(), "getEncryptedFields() should return 2 fields");
    assertTrue(enrollmentFields.containsKey("integration_private_key"));
    assertTrue(enrollmentFields.containsKey("enrollment_proof_token"));

    // Act
    List<ReencryptionBatchCreationService.Target> targets = invokeDiscoverReencryptableTargets();

    // Assert - Verify that what's in getEncryptedFields() is discovered
    for (String column : enrollmentFields.keySet()) {
      assertTrue(
          targets.contains(new ReencryptionBatchCreationService.Target("ezkey_enrollment", column)),
          "Should discover column: " + column);
    }
  }

  // ===== Helper Methods =====

  private Enrollment createMockEnrollment(Integer enrollmentId, String encryptedValue) {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(enrollmentId);
    if (encryptedValue != null) {
      enrollment.setEncryptedField("integration_private_key", encryptedValue);
    }
    return enrollment;
  }

  private AuthAttempt createMockAuthAttempt(Integer authAttemptId, String encryptedValue) {
    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(authAttemptId);
    if (encryptedValue != null) {
      authAttempt.setEncryptedField("auth_attempt_proof_token", encryptedValue);
    }
    return authAttempt;
  }

  private ReencryptionBatch createBatch(String table, String column) {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable(table);
    b.setTargetColumn(column);
    b.setOldKey(oldKey);
    b.setNewKey(newKey);
    b.setStatus(BatchStatus.PENDING);
    return b;
  }

  private ReencryptionRecordCipher.ReencryptResult invokeReencryptRecord(
      ReencryptionBatch batch, Reencryptable record) {
    return recordCipher.reencryptRecord(batch, record);
  }

  private int invokeCountRecordsEncryptedWithKey(String table, String column, Long keyId) {
    return targetQueryService.countRecordsEncryptedWithKey(table, column, keyId);
  }

  private List<ReencryptionBatchCreationService.Target> invokeDiscoverReencryptableTargets() {
    return batchCreationService.discoverReencryptableTargets();
  }

  // ===== PRIORITY 1: createBatchesForOldKeys() Tests =====

  @Test
  @DisplayName(
      "createBatchesForOldKeys() - Should create separate batches for each old key even for same"
          + " table/column")
  void createBatchesForOldKeys_ShouldCreateBatchesForEachOldKey() {
    // Arrange: Setup multiple old keys (ENABLED) and one PRIMARY key
    EncryptionKey oldKey1 = new EncryptionKey();
    oldKey1.setKeyId(1111111111L);
    oldKey1.setKeyStatus(KeyStatus.ENABLED);

    EncryptionKey oldKey2 = new EncryptionKey();
    oldKey2.setKeyId(2222222222L);
    oldKey2.setKeyStatus(KeyStatus.ENABLED);

    EncryptionKey oldKey3 = new EncryptionKey();
    oldKey3.setKeyId(3333333333L);
    oldKey3.setKeyStatus(KeyStatus.ENABLED);

    EncryptionKey primaryKey = new EncryptionKey();
    primaryKey.setKeyId(9999999999L);
    primaryKey.setKeyStatus(KeyStatus.PRIMARY);

    List<EncryptionKey> enabledKeys = List.of(oldKey1, oldKey2, oldKey3);

    // Mock repository responses
    when(keyRepository.findEnabledKeys()).thenReturn(new java.util.ArrayList<>(enabledKeys));
    when(keyRepository.findById(9999999999L)).thenReturn(java.util.Optional.of(primaryKey));

    // Mock: No active batches exist for any old key (using corrected method)
    // Note: With corrected version, this checks (table, column, oldKeyId) allowing multiple
    // batches for different old keys targeting the same table/column
    when(batchRepository.findActiveBatchesByTargetAndOldKey(
            anyString(), anyString(), anyLong(), any(), any()))
        .thenReturn(List.of());

    // Mock key management to return PRIMARY key ID
    when(keyManagementOperations.isInitialized()).thenReturn(true);
    when(keyManagementOperations.getCurrentPrimaryKeyId()).thenReturn(9999999999L);

    // Mock: Each old key has records to re-encrypt
    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike("ENC:1111111111:%"))
        .thenReturn(5);
    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike("ENC:2222222222:%"))
        .thenReturn(3);
    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike("ENC:3333333333:%"))
        .thenReturn(2);

    when(enrollmentRepository.countByEncryptedEnrollmentProofTokenLike("ENC:1111111111:%"))
        .thenReturn(5);
    when(enrollmentRepository.countByEncryptedEnrollmentProofTokenLike("ENC:2222222222:%"))
        .thenReturn(3);
    when(enrollmentRepository.countByEncryptedEnrollmentProofTokenLike("ENC:3333333333:%"))
        .thenReturn(2);

    // Mock: Save batches
    ArgumentCaptor<ReencryptionBatch> batchCaptor =
        ArgumentCaptor.forClass(ReencryptionBatch.class);
    when(batchRepository.save(batchCaptor.capture()))
        .thenAnswer(
            invocation -> {
              ReencryptionBatch b = invocation.getArgument(0);
              b.setBatchId(batchCaptor.getAllValues().size() + 1);
              return b;
            });

    // Act
    service.createBatchesForOldKeys();

    // Assert: Should create 6 batches total (3 old keys × 2 columns)
    List<ReencryptionBatch> savedBatches = batchCaptor.getAllValues();
    assertEquals(6, savedBatches.size(), "Should create 6 batches (3 old keys × 2 columns)");

    // Verify batches for oldKey1
    long batchesForOldKey1 =
        savedBatches.stream().filter(b -> b.getOldKey().getKeyId().equals(1111111111L)).count();
    assertEquals(2, batchesForOldKey1, "Should create 2 batches for oldKey1 (2 columns)");

    // Verify batches for oldKey2
    long batchesForOldKey2 =
        savedBatches.stream().filter(b -> b.getOldKey().getKeyId().equals(2222222222L)).count();
    assertEquals(2, batchesForOldKey2, "Should create 2 batches for oldKey2 (2 columns)");

    // Verify batches for oldKey3
    long batchesForOldKey3 =
        savedBatches.stream().filter(b -> b.getOldKey().getKeyId().equals(3333333333L)).count();
    assertEquals(2, batchesForOldKey3, "Should create 2 batches for oldKey3 (2 columns)");

    // Verify all batches target the same table/column combinations
    long integrationPrivateKeyBatches =
        savedBatches.stream()
            .filter(b -> b.getTargetColumn().equals("integration_private_key"))
            .count();
    assertEquals(
        3, integrationPrivateKeyBatches, "Should have 3 batches for integration_private_key");

    long enrollmentProofTokenBatches =
        savedBatches.stream()
            .filter(b -> b.getTargetColumn().equals("enrollment_proof_token"))
            .count();
    assertEquals(
        3, enrollmentProofTokenBatches, "Should have 3 batches for enrollment_proof_token");

    // Verify each batch has correct oldKey and newKey
    for (ReencryptionBatch batch : savedBatches) {
      assertTrue(
          batch.getOldKey().getKeyId().equals(1111111111L)
              || batch.getOldKey().getKeyId().equals(2222222222L)
              || batch.getOldKey().getKeyId().equals(3333333333L),
          "Batch should have one of the old keys");
      assertEquals(
          9999999999L, batch.getNewKey().getKeyId(), "All batches should target PRIMARY key");
    }
  }

  @Test
  @DisplayName(
      "createBatchesForOldKeys() - Should create one shard batch per auth column when sharding"
          + " enabled")
  void createBatchesForOldKeys_ShouldCreateShardedAuthAttemptBatches() {
    reencryptionConfig.setAuthAttemptShardCount(2);

    EncryptionKey oldKey1 = new EncryptionKey();
    oldKey1.setKeyId(1111111111L);
    oldKey1.setKeyStatus(KeyStatus.ENABLED);

    EncryptionKey primaryKey = new EncryptionKey();
    primaryKey.setKeyId(9999999999L);
    primaryKey.setKeyStatus(KeyStatus.PRIMARY);

    when(keyRepository.findEnabledKeys()).thenReturn(new java.util.ArrayList<>(List.of(oldKey1)));
    when(keyRepository.findById(9999999999L)).thenReturn(java.util.Optional.of(primaryKey));

    when(batchRepository.findActiveBatchesByTargetAndOldKey(
            anyString(), anyString(), anyLong(), any(), any()))
        .thenReturn(List.of());

    when(keyManagementOperations.isInitialized()).thenReturn(true);
    when(keyManagementOperations.getCurrentPrimaryKeyId()).thenReturn(9999999999L);

    when(enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike(anyString())).thenReturn(0);
    when(enrollmentRepository.countByEncryptedEnrollmentProofTokenLike(anyString())).thenReturn(0);

    when(authAttemptRepository.countByEncryptedAuthAttemptProofTokenLike(
            eq("ENC:1111111111:%"), eq(0), eq(2)))
        .thenReturn(10);
    when(authAttemptRepository.countByEncryptedAuthAttemptProofTokenLike(
            eq("ENC:1111111111:%"), eq(1), eq(2)))
        .thenReturn(0);
    when(authAttemptRepository.countByEncryptedDeviceProofTokenLike(
            eq("ENC:1111111111:%"), eq(0), eq(2)))
        .thenReturn(7);
    when(authAttemptRepository.countByEncryptedDeviceProofTokenLike(
            eq("ENC:1111111111:%"), eq(1), eq(2)))
        .thenReturn(0);

    ArgumentCaptor<ReencryptionBatch> batchCaptor =
        ArgumentCaptor.forClass(ReencryptionBatch.class);
    when(batchRepository.save(batchCaptor.capture()))
        .thenAnswer(
            invocation -> {
              ReencryptionBatch b = invocation.getArgument(0);
              b.setBatchId(batchCaptor.getAllValues().size() + 1);
              return b;
            });

    service.createBatchesForOldKeys();

    List<ReencryptionBatch> saved = batchCaptor.getAllValues();
    assertEquals(2, saved.size());
    assertTrue(
        saved.stream()
            .allMatch(
                b ->
                    ReencryptionBatchCreationService.EZKEY_AUTH_ATTEMPT_TABLE.equals(
                        b.getTargetTable())));
    ReencryptionBatch proofBatch =
        saved.stream()
            .filter(b -> "auth_attempt_proof_token".equals(b.getTargetColumn()))
            .findFirst()
            .orElseThrow();
    ReencryptionBatch deviceBatch =
        saved.stream()
            .filter(b -> "device_proof_token".equals(b.getTargetColumn()))
            .findFirst()
            .orElseThrow();
    for (ReencryptionBatch b : List.of(proofBatch, deviceBatch)) {
      assertEquals(2, b.getShardCount().intValue());
      assertEquals(0, b.getShardIndex().intValue());
    }
    assertEquals(10, proofBatch.getRecordsTotal());
    assertEquals(7, deviceBatch.getRecordsTotal());
  }
}
