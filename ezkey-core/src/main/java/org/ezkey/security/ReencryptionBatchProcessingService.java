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
import org.ezkey.integration.domain.entity.ApiKey;
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

  /** Classified reason: the run was interrupted (shutdown) with old-key rows left (#1431). */
  static final String FAILURE_INTERRUPTED = "Interrupted";

  /** Classified reason: no more rows to fetch but old-key rows remain below the cursor (#1431). */
  static final String FAILURE_STALLED = "Stalled";

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
            .orElseThrow(() ->
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
    int keyIdAligned = 0;
    boolean interrupted = false;

    while (recordsDone + recordsFailed + recordsSkipped < batch.getRecordsTotal()) {
      int sliceSize = resolveEffectiveBatchSize(lastRecordId);
      List<? extends Reencryptable> records =
          targetQueryService.fetchRecords(batch, lastRecordId, sliceSize);
      if (records.isEmpty()) {
        break;
      }

      List<Integer> pendingEnrollmentIds = new ArrayList<>();
      List<Integer> pendingAuthAttemptIds = new ArrayList<>();
      List<Integer> pendingApiKeyIds = new ArrayList<>();

      for (Reencryptable record : records) {
        try {
          lastRecordId = record.getEntityId();

          if (!recordCipher.isCandidateForReencryption(batch, record)
              && !recordCipher.needsKeyIdAlignment(batch, record)) {
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
          } else if (record instanceof ApiKey apiKey) {
            if (apiKey.getApiKeyId() != null) {
              pendingApiKeyIds.add(apiKey.getApiKeyId());
            }
          }
        } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
          logger.warn(
              "Failed to classify record in batch {}: {}", batch.getBatchId(), ex.getMessage());
          recordsFailed++;
          lastRecordId = record.getEntityId();
        }
      }

      for (Integer enrollmentId : pendingEnrollmentIds) {
        try {
          ReencryptionRecordCipher.ReencryptResult result =
              rowPersistence.persistEnrollmentReencryption(batch, enrollmentId);
          if (result.reencrypted()) {
            recordsDone++;
            recordRowMetric(batch);
          } else {
            recordsSkipped++;
            keyIdAligned += result.keyIdAligned() ? 1 : 0;
          }
        } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
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
          ReencryptionRecordCipher.ReencryptResult result =
              rowPersistence.persistAuthAttemptReencryption(batch, authAttemptId);
          if (result.reencrypted()) {
            recordsDone++;
            recordRowMetric(batch);
          } else {
            recordsSkipped++;
            keyIdAligned += result.keyIdAligned() ? 1 : 0;
          }
        } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
          logger.warn(
              "Failed to persist re-encrypted auth attempt {} in batch {}: {}",
              authAttemptId,
              batch.getBatchId(),
              ex.getMessage());
          recordsFailed++;
        }
      }
      for (Integer apiKeyId : pendingApiKeyIds) {
        try {
          ReencryptionRecordCipher.ReencryptResult result =
              rowPersistence.persistApiKeyReencryption(batch, apiKeyId);
          if (result.reencrypted()) {
            recordsDone++;
            recordRowMetric(batch);
          } else {
            recordsSkipped++;
            keyIdAligned += result.keyIdAligned() ? 1 : 0;
          }
        } catch (Exception ex) { // CHECKSTYLE IGNORE IllegalCatch
          logger.warn(
              "Failed to persist re-encrypted API key {} in batch {}: {}",
              apiKeyId,
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
        interrupted = true;
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
        // #1431: never commit IN_PROGRESS here. Under the single-transaction model a committed
        // IN_PROGRESS row is an orphan no automatic path picks up. FAILED (classified reason) is
        // resumed by the scheduler while retries remain and stays visible to operators after.
        String reason = interrupted ? FAILURE_INTERRUPTED : FAILURE_STALLED;
        logger.warn(
            "Re-encryption batch {} stopped early ({}): {} row(s) still encrypted with old key"
                + " (accounted {} of {}); marking FAILED",
            batch.getBatchId(),
            reason,
            stillWithOldKey,
            accounted,
            batch.getRecordsTotal());
        batch.updateProgress(recordsDone, recordsFailed, recordsSkipped);
        if (!interrupted) {
          // The rows left on the old key sit below the cursor: a resume from it would fetch
          // nothing and stall again. Restart the batch on what is left: fetch filters on the old
          // key's key-id column, so a rescan only touches rows still to migrate. Credit the rows
          // migrated so far first, then reset the counters so the rescan cannot count rows twice.
          updateKeyStatistics(batch, recordsDone);
          batch.setLastRecordId(null);
          batch.setRecordsTotal(stillWithOldKey);
          batch.updateProgress(0, 0, 0);
        }
        // An interrupted run (shutdown) is not the batch's fault: it does not use up a retry.
        applyFailure(batch, reason, !interrupted);
        batchRepository.save(batch);
        logKeyIdAlignment(batch, keyIdAligned);
        recordBatchTimer(batch, System.nanoTime() - t0);
        return;
      }
    }

    logKeyIdAlignment(batch, keyIdAligned);

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
    applyFailure(managed, errorMessage, true);
    batchRepository.save(managed);
  }

  /**
   * Sets FAILED with a classified reason (never a raw exception message, #785), counts the failure
   * and (when {@code countRetry}) the retry, and records the failure metric and audit event.
   */
  private void applyFailure(ReencryptionBatch managed, String errorMessage, boolean countRetry) {
    managed.setStatus(BatchStatus.FAILED);
    managed.setErrorMessage(errorMessage);
    managed.setErrorCount(managed.getErrorCount() + 1);
    if (countRetry) {
      managed.setRetryCount(managed.getRetryCount() + 1);
    }
    managed.setCompletedAt(OffsetDateTime.now());

    meterRegistry.ifPresent(registry ->
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

  private void logKeyIdAlignment(ReencryptionBatch batch, int keyIdAligned) {
    if (keyIdAligned > 0) {
      logger.info(
          "Re-encryption batch {}: corrected the key-id column of {} row(s) already encrypted under"
              + " another key (counted as skipped)",
          batch.getBatchId(),
          keyIdAligned);
    }
  }

  private void updateKeyStatistics(ReencryptionBatch batch, int recordsReencrypted) {
    EncryptionKey oldKey = batch.getOldKey();
    oldKey.setRecordsReencrypted(oldKey.getRecordsReencrypted() + recordsReencrypted);
    oldKey.setLastReencryptAt(OffsetDateTime.now());
    keyRepository.save(oldKey);
  }

  private void recordRowMetric(ReencryptionBatch batch) {
    meterRegistry.ifPresent(registry ->
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
    meterRegistry.ifPresent(registry ->
        Timer.builder("ezkey.reencryption.batch.duration")
            .tag("target_table", batch.getTargetTable())
            .tag("target_column", batch.getTargetColumn())
            .register(registry)
            .record(nanos, TimeUnit.NANOSECONDS));
  }
}
