/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.config.TinkProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes a single {@link ReencryptionBatch} in {@link Propagation#REQUIRES_NEW} (fresh
 * persistence context per batch).
 */
@Service
public class ReencryptionBatchProcessingService {

  private static final Logger logger =
      LoggerFactory.getLogger(ReencryptionBatchProcessingService.class);

  private final ReencryptionBatchRepository batchRepository;
  private final EncryptionKeyRepository keyRepository;
  private final TinkProperties properties;
  private final AuditLogService auditLogService;
  private final ReencryptionTargetQueryService targetQueryService;
  private final ReencryptionRowPersistenceService rowPersistence;
  private final ReencryptionRecordCipher recordCipher;
  private final Optional<MeterRegistry> meterRegistry;

  public ReencryptionBatchProcessingService(
      ReencryptionBatchRepository batchRepository,
      EncryptionKeyRepository keyRepository,
      TinkProperties properties,
      AuditLogService auditLogService,
      ReencryptionTargetQueryService targetQueryService,
      ReencryptionRowPersistenceService rowPersistence,
      ReencryptionRecordCipher recordCipher,
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this.batchRepository = batchRepository;
    this.keyRepository = keyRepository;
    this.properties = properties;
    this.auditLogService = auditLogService;
    this.targetQueryService = targetQueryService;
    this.rowPersistence = rowPersistence;
    this.recordCipher = recordCipher;
    this.meterRegistry = Optional.ofNullable(meterRegistryProvider.getIfAvailable());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void processBatchInternal(ReencryptionBatch batchRef) {
    long t0 = System.nanoTime();
    ReencryptionBatch batch =
        batchRepository
            .findById(batchRef.getBatchId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Re-encryption batch not found: " + batchRef.getBatchId()));

    logger.info(
        "Processing re-encryption batch {} ({}.{})",
        batch.getBatchId(),
        batch.getTargetTable(),
        batch.getTargetColumn());

    if (batch.getStatus() == BatchStatus.COMPLETED) {
      logger.debug("Batch {} already completed, skipping", batch.getBatchId());
      return;
    }

    if (batch.getStatus() == BatchStatus.PENDING) {
      batch.setStatus(BatchStatus.IN_PROGRESS);
      batch.setStartedAt(OffsetDateTime.now());
      batchRepository.save(batch);
    }

    int recordsDone = batch.getRecordsDone();
    int recordsFailed = batch.getRecordsFailed();
    int recordsSkipped = batch.getRecordsSkipped();
    Long lastRecordId = batch.getLastRecordId();

    OffsetDateTime batchStartTime = OffsetDateTime.now();

    while (recordsDone + recordsFailed + recordsSkipped < batch.getRecordsTotal()) {
      int sliceSize = resolveEffectiveBatchSize(lastRecordId);
      List<? extends Reencryptable> records =
          targetQueryService.fetchRecords(batch, lastRecordId, sliceSize);
      if (records.isEmpty()) {
        break;
      }

      List<Integer> pendingEnrollmentIds = new ArrayList<>();
      List<Integer> pendingAuthAttemptIds = new ArrayList<>();

      for (Reencryptable record : records) {
        try {
          lastRecordId = record.getEntityId();

          if (!recordCipher.isCandidateForReencryption(batch, record)) {
            recordsSkipped++;
            continue;
          }
          if (record instanceof Enrollment e) {
            if (e.getEnrollmentId() != null) {
              pendingEnrollmentIds.add(e.getEnrollmentId());
            }
          } else if (record instanceof AuthAttempt a) {
            if (a.getAuthAttemptId() != null) {
              pendingAuthAttemptIds.add(a.getAuthAttemptId());
            }
          }
        } catch (Exception ex) {
          logger.warn(
              "Failed to classify record in batch {}: {}", batch.getBatchId(), ex.getMessage());
          recordsFailed++;
          lastRecordId = record.getEntityId();
        }
      }

      for (Integer enrollmentId : pendingEnrollmentIds) {
        try {
          rowPersistence.persistEnrollmentReencryption(batch, enrollmentId);
          recordsDone++;
          recordRowMetric(batch);
        } catch (Exception ex) {
          logger.warn(
              "Failed to persist re-encrypted enrollment {} in batch {}: {}",
              enrollmentId,
              batch.getBatchId(),
              ex.getMessage());
          recordsFailed++;
        }
      }
      for (Integer authAttemptId : pendingAuthAttemptIds) {
        try {
          rowPersistence.persistAuthAttemptReencryption(batch, authAttemptId);
          recordsDone++;
          recordRowMetric(batch);
        } catch (Exception ex) {
          logger.warn(
              "Failed to persist re-encrypted auth attempt {} in batch {}: {}",
              authAttemptId,
              batch.getBatchId(),
              ex.getMessage());
          recordsFailed++;
        }
      }

      batch.updateProgress(recordsDone, recordsFailed, recordsSkipped);
      batch.setLastRecordId(lastRecordId);
      batch.setLastBatchAt(OffsetDateTime.now());
      batchRepository.save(batch);

      try {
        Thread.sleep(properties.getReencryption().getThrottleMs());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    int accounted = recordsDone + recordsFailed + recordsSkipped;
    if (accounted < batch.getRecordsTotal()) {
      int stillWithOldKey =
          targetQueryService.countRecordsEncryptedWithKey(
              batch.getTargetTable(),
              batch.getTargetColumn(),
              batch.getOldKey().getKeyId(),
              batch.getShardIndex(),
              batch.getShardCount());
      if (stillWithOldKey == 0) {
        int gap = batch.getRecordsTotal() - accounted;
        recordsSkipped += gap;
        logger.info(
            "Re-encryption batch {} reconciliation: {} record(s) already migrated outside this"
                + " batch (accounted {} of {}); counted as skipped",
            batch.getBatchId(),
            gap,
            accounted,
            batch.getRecordsTotal());
        batch.updateProgress(recordsDone, recordsFailed, recordsSkipped);
        batch.setLastBatchAt(OffsetDateTime.now());
        batchRepository.save(batch);
      } else {
        logger.warn(
            "Re-encryption batch {} may be stalled: fetch returned no rows but {} row(s) still"
                + " encrypted with old key (accounted {} of {})",
            batch.getBatchId(),
            stillWithOldKey,
            accounted,
            batch.getRecordsTotal());
      }
    }

    if (recordsDone + recordsFailed + recordsSkipped >= batch.getRecordsTotal()) {
      batch.setStatus(BatchStatus.COMPLETED);
      batch.setCompletedAt(OffsetDateTime.now());
      batchRepository.save(batch);

      updateKeyStatistics(batch, recordsDone);

      logger.info(
          "✅ Batch {} completed. Done: {}, Failed: {}, Skipped: {}",
          batch.getBatchId(),
          recordsDone,
          recordsFailed,
          recordsSkipped);

      long durationMs = java.time.Duration.between(batchStartTime, OffsetDateTime.now()).toMillis();
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_COMPLETED)
              .eventAction("complete_reencryption_batch")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .reencryptionBatchId(batch.getBatchId())
                      .targetTable(batch.getTargetTable())
                      .targetColumn(batch.getTargetColumn())
                      .oldKeyId(batch.getOldKey().getKeyId())
                      .newKeyId(batch.getNewKey().getKeyId())
                      .recordsTotal(batch.getRecordsTotal())
                      .recordsDone(recordsDone)
                      .recordsFailed(recordsFailed)
                      .custom("records_skipped", recordsSkipped)
                      .progressPct(batch.getProgressPct().doubleValue())
                      .durationMs(durationMs)
                      .toJson())
              .build());
    }

    recordBatchTimer(batch, System.nanoTime() - t0);
  }

  /**
   * First slice uses a smaller chunk when temporal sizing is enabled; subsequent slices use a
   * larger chunk (heuristic for lower conflict risk on the leading edge).
   */
  int resolveEffectiveBatchSize(Long lastRecordId) {
    var r = properties.getReencryption();
    if (!r.isTemporalBatchSizingEnabled()) {
      return r.getBatchSize();
    }
    if (lastRecordId == null) {
      return r.getRecentDataChunkSize();
    }
    return r.getStaleDataChunkSize();
  }

  void markBatchFailed(ReencryptionBatch batch, String errorMessage) {
    ReencryptionBatch managed = batchRepository.findById(batch.getBatchId()).orElse(batch);
    managed.setStatus(BatchStatus.FAILED);
    managed.setErrorMessage(errorMessage);
    managed.setErrorCount(managed.getErrorCount() + 1);
    managed.setRetryCount(managed.getRetryCount() + 1);
    managed.setCompletedAt(OffsetDateTime.now());
    batchRepository.save(managed);

    meterRegistry.ifPresent(
        registry ->
            registry
                .counter(
                    "ezkey.reencryption.batch.failures",
                    "target_table",
                    managed.getTargetTable(),
                    "target_column",
                    managed.getTargetColumn())
                .increment());

    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.REENCRYPTION_FAILED)
            .eventAction("batch_processing_failed")
            .eventStatus(EventStatus.ERROR)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .reencryptionBatchId(managed.getBatchId())
                    .errorSummary(errorMessage)
                    .retryCount(managed.getRetryCount())
                    .toJson())
            .errorMessage(errorMessage)
            .build());
  }

  private void updateKeyStatistics(ReencryptionBatch batch, int recordsReencrypted) {
    EncryptionKey oldKey = batch.getOldKey();
    oldKey.setRecordsReencrypted(oldKey.getRecordsReencrypted() + recordsReencrypted);
    oldKey.setLastReencryptAt(OffsetDateTime.now());
    keyRepository.save(oldKey);
  }

  private void recordRowMetric(ReencryptionBatch batch) {
    meterRegistry.ifPresent(
        registry ->
            registry
                .counter(
                    "ezkey.reencryption.rows.processed",
                    "target_table",
                    batch.getTargetTable(),
                    "target_column",
                    batch.getTargetColumn())
                .increment());
  }

  private void recordBatchTimer(ReencryptionBatch batch, long nanos) {
    meterRegistry.ifPresent(
        registry ->
            Timer.builder("ezkey.reencryption.batch.duration")
                .tag("target_table", batch.getTargetTable())
                .tag("target_column", batch.getTargetColumn())
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS));
  }
}
