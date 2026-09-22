/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: ReencryptionService
 * Description: Orchestrates batch re-encryption (scheduler, manual triggers). Row work is in
 *     {@link ReencryptionBatchProcessingService}; batch creation in {@link
 *     ReencryptionBatchCreationService}; parallel execution in {@link ReencryptionBatchParallelRunner}.
 */

package org.ezkey.security;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.integrity.ScheduledJobKey;
import org.ezkey.audit.integrity.ScheduledJobLastRunService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.ezkey.security.exception.EncryptionLifecycleDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Orchestrates re-encryption batch creation and processing (scheduled and manual entry points).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class ReencryptionService {

  private static final Logger logger = LoggerFactory.getLogger(ReencryptionService.class);

  private final EncryptionOperations encryptionOperations;
  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final TinkProperties properties;
  private final AuditLogService auditLogService;
  private final ReencryptionBatchCreationService batchCreationService;
  private final ReencryptionBatchProcessingService batchProcessingService;
  private final ReencryptionBatchParallelRunner parallelRunner;
  private final ScheduledJobLastRunService jobLastRunService;

  public ReencryptionService(
      EncryptionOperations encryptionOperations,
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      TinkProperties properties,
      AuditLogService auditLogService,
      ReencryptionBatchCreationService batchCreationService,
      ReencryptionBatchProcessingService batchProcessingService,
      ReencryptionBatchParallelRunner parallelRunner,
      ScheduledJobLastRunService jobLastRunService) {
    this.encryptionOperations = encryptionOperations;
    this.keyRepository = keyRepository;
    this.batchRepository = batchRepository;
    this.properties = properties;
    this.auditLogService = auditLogService;
    this.batchCreationService = batchCreationService;
    this.batchProcessingService = batchProcessingService;
    this.parallelRunner = parallelRunner;
    this.jobLastRunService = jobLastRunService;
  }

  /**
   * Whether scheduled and manual re-encryption is enabled.
   *
   * @return {@code true} when {@code ezkey.encryption.reencryption.enabled} is on
   */
  public boolean isReencryptionEnabled() {
    return properties.getReencryption().isEnabled();
  }

  private void ensureReencryptionEnabled() {
    if (!isReencryptionEnabled()) {
      throw new EncryptionLifecycleDisabledException(
          EncryptionLifecycleDisabledException.Operation.REENCRYPTION);
    }
  }

  @Scheduled(cron = "${ezkey.encryption.reencryption.schedule:0 0 3 * * ?}")
  @SchedulerLock(name = "REENCRYPTION", lockAtMostFor = "PT2H")
  public void processReencryptionBatches() {
    if (!properties.getReencryption().isEnabled()) {
      logger.debug("Re-encryption is disabled via configuration");
      return;
    }

    if (!encryptionOperations.isEncryptionAvailable()) {
      logger.warn("Encryption not available, skipping re-encryption batch processing");
      return;
    }

    try {
      logger.info("🔄 Starting re-encryption batch processing...");

      OffsetDateTime startTime = OffsetDateTime.now();
      int maxBatches = properties.getReencryption().getMaxBatchesPerRun();
      int maxDurationMinutes = properties.getReencryption().getMaxDurationMinutes();
      int parallelWorkers = properties.getReencryption().getParallelBatchWorkers();

      List<ReencryptionBatch> batchesToProcess = batchRepository.findBatchesEligibleForResume();
      batchesToProcess.addAll(batchRepository.findByStatus(BatchStatus.PENDING));

      batchCreationService.createBatchesForOldKeys();

      int batchesProcessed = 0;
      if (parallelWorkers <= 1) {
        for (ReencryptionBatch batch : batchesToProcess) {
          if (batchesProcessed >= maxBatches) {
            logger.info("Reached max batches per run limit: {}", maxBatches);
            break;
          }

          long elapsedMinutes =
              java.time.Duration.between(startTime, OffsetDateTime.now()).toMinutes();
          if (elapsedMinutes >= maxDurationMinutes) {
            logger.info("Reached max duration limit: {} minutes", maxDurationMinutes);
            break;
          }

          try {
            batchProcessingService.processBatchInternal(batch);
            batchesProcessed++;
          } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
            logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
            batchProcessingService.markBatchFailed(batch, e.getMessage());
          }
        }
        logger.info(
            "✅ Re-encryption batch processing completed. Processed {} batches", batchesProcessed);
      } else {
        List<ReencryptionBatch> slice = new ArrayList<>();
        for (ReencryptionBatch batch : batchesToProcess) {
          if (slice.size() >= maxBatches) {
            break;
          }
          slice.add(batch);
        }
        parallelRunner.runBatches(
            slice, (batch, e) -> batchProcessingService.markBatchFailed(batch, e.getMessage()));
        batchesProcessed = slice.size();
        logger.info(
            "✅ Re-encryption batch processing completed (parallel workers={}). Submitted {}"
                + " batches",
            parallelWorkers,
            batchesProcessed);
      }

      String scope = buildReencryptionScope(batchesProcessed);
      jobLastRunService.recordSuccess(ScheduledJobKey.REENCRYPTION, scope);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Failed to process re-encryption batches", e);
      jobLastRunService.recordFailure(
          ScheduledJobKey.REENCRYPTION, "Batch re-encryption cycle", e.getMessage());
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("scheduled_batch_processing")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Scheduled batch processing failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
    }
  }

  public void processBatch(ReencryptionBatch batch) {
    batchProcessingService.processBatchInternal(batch);
  }

  /**
   * Schedules one or more batches on the background executor (HTTP-safe; does not wait for
   * completion).
   *
   * @param batches batches to process asynchronously
   */
  public void enqueueBatchProcessing(List<ReencryptionBatch> batches) {
    ensureReencryptionEnabled();
    submitBatchesForBackgroundProcessing(batches);
  }

  /** Enqueues batches for all applicable old keys and targets (no row crypto). */
  public void createBatchesForOldKeys() {
    ensureReencryptionEnabled();
    batchCreationService.createBatchesForOldKeys();
  }

  /**
   * Enqueues full manual re-encryption: creates batches, then schedules processing on the batch
   * executor (non-blocking for HTTP callers).
   *
   * @return enqueue summary with batch ids submitted for processing
   */
  public ManualReencryptionEnqueueResult enqueueFullReencryption() {
    ensureReencryptionEnabled();
    if (!encryptionOperations.isEncryptionAvailable()) {
      throw new IllegalStateException("Encryption not available");
    }

    logger.info("Manual full re-encryption accepted (enqueue)");

    batchCreationService.createBatchesForOldKeys();

    List<ReencryptionBatch> batchesToProcess = collectPendingAndResumableBatches();
    submitBatchesForBackgroundProcessing(batchesToProcess);

    List<Integer> batchIds = batchesToProcess.stream().map(ReencryptionBatch::getBatchId).toList();

    logger.info("Manual full re-encryption enqueued {} batch(es): {}", batchIds.size(), batchIds);

    return new ManualReencryptionEnqueueResult(batchIds.size(), batchIds);
  }

  /**
   * Enqueues re-encryption for one old key: creates target batches, then schedules processing on
   * the batch executor (non-blocking for HTTP callers).
   *
   * @param keyId old key identifier (must not be PRIMARY)
   * @return enqueue summary with batch ids submitted for processing
   */
  public ManualReencryptionEnqueueResult enqueueReencryptionForKey(Long keyId) {
    ensureReencryptionEnabled();
    if (!encryptionOperations.isEncryptionAvailable()) {
      throw new IllegalStateException("Encryption not available");
    }

    EncryptionKey oldKey =
        keyRepository
            .findById(keyId)
            .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

    if (oldKey.getKeyStatus() == EncryptionKey.KeyStatus.PRIMARY) {
      throw new IllegalArgumentException("Cannot re-encrypt PRIMARY key: " + keyId);
    }

    logger.info("Manual re-encryption accepted for key {} (enqueue)", keyId);

    EncryptionKey primaryKey = batchCreationService.getPrimaryKeyFromKeyset();
    if (primaryKey == null) {
      throw new IllegalStateException("No PRIMARY key found");
    }

    List<ReencryptionBatchCreationService.Target> targets =
        batchCreationService.discoverReencryptableTargets();

    List<ReencryptionBatch> created = new ArrayList<>();

    for (ReencryptionBatchCreationService.Target target : targets) {
      created.addAll(
          batchCreationService.createBatchesForTarget(oldKey, primaryKey, target, "ADMIN_MANUAL"));
    }

    submitBatchesForBackgroundProcessing(created);

    List<Integer> batchIds = created.stream().map(ReencryptionBatch::getBatchId).toList();

    logger.info(
        "Manual re-encryption for key {} enqueued {} batch(es): {}",
        keyId,
        batchIds.size(),
        batchIds);

    return new ManualReencryptionEnqueueResult(batchIds.size(), batchIds);
  }

  /**
   * Synchronous full re-encryption (legacy). Prefer {@link #enqueueFullReencryption()} for HTTP
   * manual triggers.
   *
   * @deprecated Use {@link #enqueueFullReencryption()} for HTTP manual triggers.
   */
  @Deprecated
  public ReencryptionSummary triggerFullReencryption() {
    if (!encryptionOperations.isEncryptionAvailable()) {
      throw new IllegalStateException("Encryption not available");
    }

    logger.info("🔄 Manual full re-encryption triggered");

    batchCreationService.createBatchesForOldKeys();

    List<ReencryptionBatch> batchesToProcess = batchRepository.findBatchesEligibleForResume();
    batchesToProcess.addAll(batchRepository.findByStatus(BatchStatus.PENDING));

    int batchesCreated = batchesToProcess.size();

    parallelRunner.runBatches(
        batchesToProcess,
        (batch, e) -> {
          logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
          batchProcessingService.markBatchFailed(batch, e.getMessage());
        });

    int batchesProcessed = 0;
    int batchesFailed = 0;
    for (ReencryptionBatch batch : batchesToProcess) {
      ReencryptionBatch refreshed =
          batchRepository
              .findById(batch.getBatchId())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Re-encryption batch missing after processing: " + batch.getBatchId()));
      if (refreshed.getStatus() == BatchStatus.COMPLETED) {
        batchesProcessed++;
      } else if (refreshed.getStatus() == BatchStatus.FAILED) {
        batchesFailed++;
      }
    }

    logger.info(
        "✅ Full re-encryption completed. Created: {}, Processed: {}, Failed: {}",
        batchesCreated,
        batchesProcessed,
        batchesFailed);

    return new ReencryptionSummary(batchesCreated, batchesProcessed, batchesFailed);
  }

  /**
   * Synchronous per-key re-encryption (legacy). Prefer {@link #enqueueReencryptionForKey(Long)} for
   * HTTP manual triggers.
   *
   * @deprecated Use {@link #enqueueReencryptionForKey(Long)} for HTTP manual triggers.
   */
  @Deprecated
  public ReencryptionSummary triggerReencryptionForKey(Long keyId) {
    if (!encryptionOperations.isEncryptionAvailable()) {
      throw new IllegalStateException("Encryption not available");
    }

    EncryptionKey oldKey =
        keyRepository
            .findById(keyId)
            .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

    if (oldKey.getKeyStatus() == EncryptionKey.KeyStatus.PRIMARY) {
      throw new IllegalArgumentException("Cannot re-encrypt PRIMARY key: " + keyId);
    }

    logger.info("🔄 Manual re-encryption triggered for key: {}", keyId);

    EncryptionKey primaryKey = batchCreationService.getPrimaryKeyFromKeyset();
    if (primaryKey == null) {
      throw new IllegalStateException("No PRIMARY key found");
    }

    List<ReencryptionBatchCreationService.Target> targets =
        batchCreationService.discoverReencryptableTargets();

    int batchesCreated = 0;
    List<ReencryptionBatch> created = new ArrayList<>();

    for (ReencryptionBatchCreationService.Target target : targets) {
      List<ReencryptionBatch> newBatches =
          batchCreationService.createBatchesForTarget(oldKey, primaryKey, target, "ADMIN_MANUAL");
      batchesCreated += newBatches.size();
      created.addAll(newBatches);
    }

    int batchesProcessed = 0;
    int batchesFailed = 0;

    if (!created.isEmpty()) {
      parallelRunner.runBatches(
          created,
          (batch, e) -> {
            logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
            batchProcessingService.markBatchFailed(batch, e.getMessage());
          });

      for (ReencryptionBatch batch : created) {
        ReencryptionBatch refreshed =
            batchRepository
                .findById(batch.getBatchId())
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Re-encryption batch missing after processing: " + batch.getBatchId()));
        if (refreshed.getStatus() == BatchStatus.COMPLETED) {
          batchesProcessed++;
        } else if (refreshed.getStatus() == BatchStatus.FAILED) {
          batchesFailed++;
        }
      }
    }

    logger.info(
        "✅ Re-encryption for key {} completed. Created: {}, Processed: {}, Failed: {}",
        keyId,
        batchesCreated,
        batchesProcessed,
        batchesFailed);

    return new ReencryptionSummary(batchesCreated, batchesProcessed, batchesFailed);
  }

  private List<ReencryptionBatch> collectPendingAndResumableBatches() {
    List<ReencryptionBatch> batchesToProcess = new ArrayList<>();
    batchesToProcess.addAll(batchRepository.findBatchesEligibleForResume());
    batchesToProcess.addAll(batchRepository.findByStatus(BatchStatus.PENDING));
    return batchesToProcess;
  }

  private void submitBatchesForBackgroundProcessing(List<ReencryptionBatch> batches) {
    if (batches.isEmpty()) {
      return;
    }
    parallelRunner.runBatchesAsync(
        batches,
        (batch, e) -> {
          logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
          batchProcessingService.markBatchFailed(batch, e.getMessage());
        });
  }

  private static String buildReencryptionScope(int batchesProcessed) {
    if (batchesProcessed == 1) {
      return "Batch re-encryption cycle (1 batch)";
    }
    return "Batch re-encryption cycle (" + batchesProcessed + " batches)";
  }

  /**
   * Result of accepting a manual re-encryption trigger (HTTP returns before row crypto completes).
   *
   * @param batchesEnqueued number of batches submitted to the background executor
   * @param batchIds identifiers of enqueued batches (may be empty when nothing to process)
   */
  public record ManualReencryptionEnqueueResult(int batchesEnqueued, List<Integer> batchIds) {}

  /**
   * Summary of re-encryption operation.
   *
   * @param batchesCreated number of batches created
   * @param batchesProcessed number of batches successfully processed
   * @param batchesFailed number of batches that failed
   */
  public record ReencryptionSummary(int batchesCreated, int batchesProcessed, int batchesFailed) {}
}
