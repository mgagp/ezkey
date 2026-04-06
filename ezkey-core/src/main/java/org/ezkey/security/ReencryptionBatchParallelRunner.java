/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Runs {@link ReencryptionBatchProcessingService#processBatchInternal} for multiple batches. When
 * {@link TinkProperties.Reencryption#getParallelBatchWorkers()} is 1, work is sequential on the
 * caller thread (no pool). When &gt; 1, uses {@code reencryptionBatchExecutor}.
 *
 * <p>When parallel workers &gt; 1, each {@code target_table} is protected by a mutex so at most one
 * batch touches a given table at a time. That avoids concurrent updates to the same logical row
 * across different column batches (e.g. optimistic-lock conflicts on {@code Enrollment}). With the
 * current two physical tables, up to two batches may run simultaneously (one per table).
 */
@Component
public class ReencryptionBatchParallelRunner {

  private static final Logger logger =
      LoggerFactory.getLogger(ReencryptionBatchParallelRunner.class);

  /**
   * One monitor per {@link org.ezkey.security.domain.entity.ReencryptionBatch#getTargetTable()
   * target_table} so batches for the same table never execute {@link
   * ReencryptionBatchProcessingService#processBatchInternal} concurrently.
   */
  private final ConcurrentHashMap<String, Object> targetTableLocks = new ConcurrentHashMap<>();

  private final ThreadPoolTaskExecutor reencryptionBatchExecutor;
  private final ReencryptionBatchProcessingService batchProcessingService;
  private final TinkProperties properties;

  public ReencryptionBatchParallelRunner(
      @Qualifier("reencryptionBatchExecutor") ThreadPoolTaskExecutor reencryptionBatchExecutor,
      ReencryptionBatchProcessingService batchProcessingService,
      TinkProperties properties) {
    this.reencryptionBatchExecutor = reencryptionBatchExecutor;
    this.batchProcessingService = batchProcessingService;
    this.properties = properties;
  }

  /**
   * Processes each batch; on failure invokes {@code onFailure} with the batch and exception (caller
   * typically marks the batch failed).
   */
  public void runBatches(List<ReencryptionBatch> batches, BatchFailureCallback onFailure) {
    int parallel = properties.getReencryption().getParallelBatchWorkers();
    if (parallel <= 1) {
      for (ReencryptionBatch batch : batches) {
        try {
          batchProcessingService.processBatchInternal(batch);
        } catch (Exception e) {
          logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
          onFailure.onFailure(batch, e);
        }
      }
      return;
    }

    List<Future<?>> futures = new ArrayList<>();
    for (ReencryptionBatch batch : batches) {
      futures.add(reencryptionBatchExecutor.submit(() -> processBatchIsolatedByTargetTable(batch)));
    }

    for (int i = 0; i < futures.size(); i++) {
      try {
        futures.get(i).get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        onFailure.onFailure(
            batches.get(i), new IllegalStateException("Interrupted waiting for batch", e));
      } catch (ExecutionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        Exception ex = cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
        logger.error(
            "Parallel batch failed for {}: {}", batches.get(i).getBatchId(), ex.getMessage(), ex);
        onFailure.onFailure(batches.get(i), ex);
      }
    }
  }

  /**
   * Invoked when {@link ReencryptionBatchProcessingService#processBatchInternal} throws for a
   * batch.
   */
  @FunctionalInterface
  public interface BatchFailureCallback {

    /**
     * Notifies the caller that a batch failed; typically persists FAILED status and logs.
     *
     * @param batch the batch that failed
     * @param exception failure cause
     */
    void onFailure(ReencryptionBatch batch, Exception exception);
  }

  /**
   * Runs batch processing while holding a per-table lock so two workers never migrate different
   * columns on the same table at once (prevents same-row contention).
   */
  private void processBatchIsolatedByTargetTable(ReencryptionBatch batch) {
    String table = batch.getTargetTable();
    Object lock = targetTableLocks.computeIfAbsent(table, t -> new Object());
    synchronized (lock) {
      batchProcessingService.processBatchInternal(batch);
    }
  }
}
