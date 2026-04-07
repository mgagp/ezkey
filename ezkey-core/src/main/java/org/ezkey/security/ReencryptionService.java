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
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private final EncryptionService encryptionService;
  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final TinkProperties properties;
  private final AuditLogService auditLogService;
  private final ReencryptionBatchCreationService batchCreationService;
  private final ReencryptionBatchProcessingService batchProcessingService;
  private final ReencryptionBatchParallelRunner parallelRunner;

  public ReencryptionService(
      EncryptionService encryptionService,
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      TinkProperties properties,
      AuditLogService auditLogService,
      ReencryptionBatchCreationService batchCreationService,
      ReencryptionBatchProcessingService batchProcessingService,
      ReencryptionBatchParallelRunner parallelRunner) {
    this.encryptionService = encryptionService;
    this.keyRepository = keyRepository;
    this.batchRepository = batchRepository;
    this.properties = properties;
    this.auditLogService = auditLogService;
    this.batchCreationService = batchCreationService;
    this.batchProcessingService = batchProcessingService;
    this.parallelRunner = parallelRunner;
  }

  @Scheduled(cron = "${ezkey.encryption.reencryption.schedule:0 0 3 * * ?}")
  @SchedulerLock(name = "REENCRYPTION", lockAtMostFor = "PT2H")
  @Transactional
  public void processReencryptionBatches() {
    if (!properties.getReencryption().isEnabled()) {
      logger.debug("Re-encryption is disabled via configuration");
      return;
    }

    if (!encryptionService.isEncryptionAvailable()) {
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

      if (parallelWorkers <= 1) {
        int batchesProcessed = 0;
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
          } catch (Exception e) {
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
        logger.info(
            "✅ Re-encryption batch processing completed (parallel workers={}). Submitted {}"
                + " batches",
            parallelWorkers,
            slice.size());
      }
    } catch (Exception e) {
      logger.error("Failed to process re-encryption batches", e);
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

  /** Enqueues batches for all applicable old keys and targets (no row crypto). */
  public void createBatchesForOldKeys() {
    batchCreationService.createBatchesForOldKeys();
  }

  public ReencryptionSummary triggerFullReencryption() {
    if (!encryptionService.isEncryptionAvailable()) {
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

  public ReencryptionSummary triggerReencryptionForKey(Long keyId) {
    if (!encryptionService.isEncryptionAvailable()) {
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

  /**
   * Summary of re-encryption operation.
   *
   * @param batchesCreated number of batches created
   * @param batchesProcessed number of batches successfully processed
   * @param batchesFailed number of batches that failed
   */
  public record ReencryptionSummary(int batchesCreated, int batchesProcessed, int batchesFailed) {}
}
