/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ReencryptionBatchLifecyclePostgresTest
 * Description: Re-encryption batch lifecycle on real Postgres: crash rollback, early exit,
 *     interrupt, creation guard and the V32 heal (#1431).
 */

package org.ezkey.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real-Postgres checks for the re-encryption batch lifecycle (#1431). Row access ({@link
 * ReencryptionTargetQueryService}, {@link ReencryptionRowPersistenceService}, {@link
 * ReencryptionRecordCipher}) is mocked; the batch row, its transaction and the repository queries
 * are real.
 *
 * <p>No class-level {@code @Transactional}: {@code processBatchInternal} runs in {@code
 * REQUIRES_NEW} and the assertions read committed state from a separate connection.
 *
 * @since 2026
 */
@SpringBootTest(classes = ReencryptionBatchLifecyclePostgresTest.TestConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext // close the pool with this class: one more cached Postgres context slows JVM exit
@DisplayName("Re-encryption batch lifecycle on Postgres (#1431)")
class ReencryptionBatchLifecyclePostgresTest {

  private static final String TABLE = "ezkey_api_key";
  private static final String COLUMN = "secret_key_hash";
  private static final long OLD_KEY_ID = 1111111111L;
  private static final long NEW_KEY_ID = 2222222222L;

  @Container @ServiceConnection
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:19-alpine").withDatabaseName("ezkey_it");

  @DynamicPropertySource
  static void datasourceProps(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("spring.flyway.enabled", () -> "false");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = {EncryptionKey.class, ReencryptionBatch.class})
  @EnableJpaRepositories(
      basePackageClasses = {EncryptionKeyRepository.class, ReencryptionBatchRepository.class})
  @Import(ReencryptionBatchProcessingService.class)
  static class TestConfiguration {

    @Bean
    TinkProperties tinkProperties() {
      TinkProperties p = new TinkProperties();
      p.getReencryption().setThrottleMs(0);
      p.getReencryption().setBatchSize(10);
      return p;
    }
  }

  @MockitoBean private ReencryptionTargetQueryService targetQueryService;
  @MockitoBean private ReencryptionRowPersistenceService rowPersistence;
  @MockitoBean private ReencryptionRecordCipher recordCipher;
  @MockitoBean private AuditLogService auditLogService;

  @Autowired private ReencryptionBatchProcessingService processingService;
  @Autowired private ReencryptionBatchRepository batchRepository;
  @Autowired private EncryptionKeyRepository keyRepository;
  @Autowired private JdbcTemplate jdbc;

  private EncryptionKey oldKey;
  private EncryptionKey newKey;

  @BeforeEach
  void seedKeys() {
    batchRepository.deleteAll();
    keyRepository.deleteAll();
    oldKey = keyRepository.save(key(OLD_KEY_ID, KeyStatus.ENABLED));
    newKey = keyRepository.save(key(NEW_KEY_ID, KeyStatus.PRIMARY));
  }

  @AfterEach
  void clearInterruptFlag() {
    Thread.interrupted();
    reset(targetQueryService, rowPersistence, recordCipher);
  }

  @Test
  @DisplayName("T1: a running batch is invisible to other readers (PENDING, 0 done) until commit")
  void runningBatchIsInvisibleUntilCommit() {
    ReencryptionBatch batch = batchRepository.save(newBatch(3));
    AtomicReference<String> seenStatus = new AtomicReference<>();
    AtomicInteger calls = new AtomicInteger();
    when(targetQueryService.fetchRecords(any(), any(), anyInt()))
        .thenAnswer(_ -> {
          if (calls.getAndIncrement() == 0) {
            return List.of(apiKey(1), apiKey(2));
          }
          // Second slice: the first slice's progress save has run inside the batch transaction.
          seenStatus.set(statusOf(batch.getBatchId()));
          return List.of();
        });
    when(recordCipher.isCandidateForReencryption(any(), any())).thenReturn(false);
    when(targetQueryService.countRecordsEncryptedWithKey(
            anyString(), anyString(), anyLong(), isNull(), isNull()))
        .thenReturn(0);

    processingService.processBatchInternal(batch);

    assertThat(seenStatus.get()).isEqualTo("PENDING");
    assertThat(statusOf(batch.getBatchId())).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("T2: a crash mid-batch rolls the batch back to its last committed state")
  void crashRollsBack() {
    ReencryptionBatch batch = batchRepository.save(newBatch(4));
    AtomicInteger calls = new AtomicInteger();
    when(targetQueryService.fetchRecords(any(), any(), anyInt()))
        .thenAnswer(_ -> {
          if (calls.getAndIncrement() == 0) {
            return List.of(apiKey(1), apiKey(2));
          }
          throw new IllegalStateException("connection lost");
        });
    when(recordCipher.isCandidateForReencryption(any(), any())).thenReturn(false);

    assertThatThrownBy(() -> processingService.processBatchInternal(batch))
        .isInstanceOf(IllegalStateException.class);

    Map<String, Object> row = rowOf(batch.getBatchId());
    assertThat(row.get("status")).isEqualTo("PENDING");
    assertThat(row.get("records_skipped")).isEqualTo(0);
    assertThat(row.get("last_record_id")).isNull();
  }

  @Test
  @DisplayName(
      "T3: no row left to fetch while old-key rows remain -> FAILED Stalled, not IN_PROGRESS")
  void stallBecomesFailed() {
    ReencryptionBatch batch = batchRepository.save(newBatch(3));
    AtomicInteger calls = new AtomicInteger();
    when(targetQueryService.fetchRecords(any(), any(), anyInt()))
        .thenAnswer(_ -> calls.getAndIncrement() == 0 ? List.of(apiKey(1)) : List.of());
    when(recordCipher.isCandidateForReencryption(any(), any())).thenReturn(false);
    when(targetQueryService.countRecordsEncryptedWithKey(
            anyString(), anyString(), anyLong(), isNull(), isNull()))
        .thenReturn(2);

    processingService.processBatchInternal(batch);

    Map<String, Object> row = rowOf(batch.getBatchId());
    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat(row.get("error_message")).isEqualTo("Stalled");
    assertThat(row.get("retry_count")).isEqualTo(1);
    assertThat(row.get("records_skipped")).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "T4: an interrupt (shutdown) with old-key rows left -> FAILED Interrupted, no retry used")
  void interruptBecomesFailedWithoutRetry() {
    ReencryptionBatch batch = batchRepository.save(newBatch(3));
    when(targetQueryService.fetchRecords(any(), any(), anyInt()))
        .thenAnswer(_ -> {
          Thread.currentThread().interrupt();
          return List.of(apiKey(1));
        });
    when(recordCipher.isCandidateForReencryption(any(), any())).thenReturn(false);
    when(targetQueryService.countRecordsEncryptedWithKey(
            anyString(), anyString(), anyLong(), isNull(), isNull()))
        .thenReturn(2);

    processingService.processBatchInternal(batch);

    Map<String, Object> row = rowOf(batch.getBatchId());
    assertThat(row.get("status")).isEqualTo("FAILED");
    assertThat(row.get("error_message")).isEqualTo("Interrupted");
    assertThat(row.get("retry_count")).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "T6: a row another worker already migrated counts as skipped, not done; so does a key-id fix")
  void rowsNotReencryptedHereAreNotCountedDone() {
    ReencryptionBatch batch = batchRepository.save(newBatch(3));
    AtomicInteger calls = new AtomicInteger();
    when(targetQueryService.fetchRecords(any(), any(), anyInt()))
        .thenAnswer(_ ->
            calls.getAndIncrement() == 0 ? List.of(apiKey(1), apiKey(2), apiKey(3)) : List.of());
    when(recordCipher.isCandidateForReencryption(any(), any())).thenReturn(true);
    when(rowPersistence.persistApiKeyReencryption(any(), any()))
        .thenReturn(new ReencryptionRecordCipher.ReencryptResult(true, false, null))
        .thenReturn(new ReencryptionRecordCipher.ReencryptResult(false, false, null))
        .thenReturn(new ReencryptionRecordCipher.ReencryptResult(false, true, null));

    processingService.processBatchInternal(batch);

    Map<String, Object> row = rowOf(batch.getBatchId());
    assertThat(row.get("status")).isEqualTo("COMPLETED");
    assertThat(row.get("records_done")).isEqualTo(1);
    assertThat(row.get("records_skipped")).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "T12: a FAILED batch counts as unfinished for the creation guard; COMPLETED does not")
  void creationGuardSeesFailed() {
    ReencryptionBatch failed = newBatch(3);
    failed.setStatus(BatchStatus.FAILED);
    batchRepository.save(failed);
    ReencryptionBatch completed = newBatch(3);
    completed.setStatus(BatchStatus.COMPLETED);
    batchRepository.save(completed);

    List<ReencryptionBatch> unfinished =
        batchRepository.findNotCompletedBatchesByTargetAndOldKey(TABLE, COLUMN, OLD_KEY_ID);

    assertThat(unfinished)
        .extracting(ReencryptionBatch::getStatus)
        .containsExactly(BatchStatus.FAILED);
  }

  @Test
  @DisplayName("V32 heals committed IN_PROGRESS rows to FAILED Orphaned, retries untouched")
  void v32HealsOrphans() throws Exception {
    ReencryptionBatch orphan = newBatch(3);
    orphan.setStatus(BatchStatus.IN_PROGRESS);
    orphan.setRetryCount(1);
    orphan = batchRepository.save(orphan);
    ReencryptionBatch pending = batchRepository.save(newBatch(3));

    String sql =
        new ClassPathResource("db/migration/V32__reencryption_batch_heal_orphan_in_progress.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    jdbc.execute(sql);

    Map<String, Object> healed = rowOf(orphan.getBatchId());
    assertThat(healed.get("status")).isEqualTo("FAILED");
    assertThat(healed.get("error_message")).isEqualTo("Orphaned");
    assertThat(healed.get("retry_count")).isEqualTo(1);
    assertThat(healed.get("completed_at")).isNotNull();
    assertThat(statusOf(pending.getBatchId())).isEqualTo("PENDING");
  }

  private ReencryptionBatch newBatch(int total) {
    return new ReencryptionBatch(TABLE, COLUMN, oldKey, newKey, total, "TEST");
  }

  private static ApiKey apiKey(int id) {
    ApiKey k = new ApiKey();
    k.setApiKeyId(id);
    return k;
  }

  private static EncryptionKey key(long id, KeyStatus status) {
    EncryptionKey k = new EncryptionKey();
    k.setKeyId(id);
    k.setKeyStatus(status);
    k.setAlgorithm("AES256_GCM");
    k.setIntroducedAt(OffsetDateTime.now().minusDays(1));
    k.setCreatedBy("TEST");
    return k;
  }

  private String statusOf(Integer batchId) {
    return jdbc.queryForObject(
        "SELECT status FROM ezkey_reencryption_batch WHERE batch_id = ?", String.class, batchId);
  }

  private Map<String, Object> rowOf(Integer batchId) {
    return jdbc.queryForMap("SELECT * FROM ezkey_reencryption_batch WHERE batch_id = ?", batchId);
  }
}
