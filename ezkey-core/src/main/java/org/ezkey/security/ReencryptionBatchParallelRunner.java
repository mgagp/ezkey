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
 * <p>When parallel workers &gt; 1, a mutex key is derived per batch: {@code ezkey_enrollment} uses
 * the table name only (two encrypted columns on the same row); {@code ezkey_auth_attempt} without
 * sharding uses the table name only (single encrypted column {@code auth_attempt_proof_token});
 * {@code ezkey_auth_attempt} with {@code shard_count &gt; 1} uses {@code table|column|shard_index}
 * so parallel shard workers do not share one lock.
 */
@Component
public class ReencryptionBatchParallelRunner {

  private static final Logger logger =
      LoggerFactory.getLogger(ReencryptionBatchParallelRunner.class);

  private static final String TABLE_ENROLLMENT = "ezkey_enrollment";
  private static final String TABLE_AUTH_ATTEMPT = "ezkey_auth_attempt";

  /**
   * Mutex per derived key (see {@link #mutexKeyForBatch(ReencryptionBatch)}) so incompatible batch
   * work does not run concurrently on the same pool workers.
   */
  private final ConcurrentHashMap<String, Object> batchMutexLocks = new ConcurrentHashMap<>();

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
   * Submits batch processing to {@code reencryptionBatchExecutor} and returns immediately (manual
   * HTTP triggers must not block on row crypto).
   *
   * <p>Each batch is submitted as its own top-level task on {@code reencryptionBatchExecutor} — it
   * does <b>not</b> wrap the dispatch-and-wait loop from {@link #runBatches} in a single task on
   * that same bounded pool. Doing so would permanently occupy one pool thread for the whole
   * duration of the run (blocked in {@code Future.get()}), leaving only {@code parallelBatchWorkers
   * - 1} threads actually available to process batches — the exact cause of an observed case where
   * 3 of 4 configured shard workers ran a shard batch concurrently while the 4th queued and ran
   * afterward (see {@code docs/REENCRYPTION_OPERATIONS.md}).
   */
  public void runBatchesAsync(List<ReencryptionBatch> batches, BatchFailureCallback onFailure) {
    if (batches.isEmpty()) {
      return;
    }
    int parallel = properties.getReencryption().getParallelBatchWorkers();
    if (parallel <= 1) {
      reencryptionBatchExecutor.execute(() -> runSequential(batches, onFailure));
      return;
    }
    for (ReencryptionBatch batch : batches) {
      reencryptionBatchExecutor.execute(() -> runOneIsolated(batch, onFailure));
    }
  }

  /**
   * Processes each batch, blocking the caller thread until all batches finish; on failure invokes
   * {@code onFailure} with the batch and exception (caller typically marks the batch failed).
   *
   * <p>Safe to call from a thread that is <b>not</b> a {@code reencryptionBatchExecutor} worker
   * (e.g. the {@code @Scheduled} scheduler thread, or a Tomcat request thread for the deprecated
   * synchronous trigger methods): submitting {@code batches.size()} sub-tasks to the pool and
   * blocking here does not reduce the pool's effective capacity for those callers. Do <b>not</b>
   * call this method from within a task already running on {@code reencryptionBatchExecutor} — see
   * {@link #runBatchesAsync} for why that self-consumes a worker thread.
   */
  public void runBatches(List<ReencryptionBatch> batches, BatchFailureCallback onFailure) {
    int parallel = properties.getReencryption().getParallelBatchWorkers();
    if (parallel <= 1) {
      runSequential(batches, onFailure);
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

  /** Processes batches one at a time on the calling thread. */
  private void runSequential(List<ReencryptionBatch> batches, BatchFailureCallback onFailure) {
    for (ReencryptionBatch batch : batches) {
      try {
        batchProcessingService.processBatchInternal(batch);
      } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
        logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
        onFailure.onFailure(batch, e);
      }
    }
  }

  /**
   * Processes one batch under its mutex, invoking {@code onFailure} in place on error. Used as a
   * top-level {@code reencryptionBatchExecutor} task by {@link #runBatchesAsync} so each batch
   * competes independently for a pool thread (no dispatcher task holding one hostage).
   */
  private void runOneIsolated(ReencryptionBatch batch, BatchFailureCallback onFailure) {
    try {
      processBatchIsolatedByTargetTable(batch);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Parallel batch failed for {}: {}", batch.getBatchId(), e.getMessage(), e);
      onFailure.onFailure(batch, e);
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
   * Runs batch processing while holding the mutex for this batch's isolation key (table-wide or
   * per-shard for auth attempts).
   */
  private void processBatchIsolatedByTargetTable(ReencryptionBatch batch) {
    String key = mutexKeyForBatch(batch);
    Object lock = batchMutexLocks.computeIfAbsent(key, _ -> new Object());
    synchronized (lock) {
      batchProcessingService.processBatchInternal(batch);
    }
  }

  /**
   * Enrollment: one lock per table. Auth attempt without sharding: one lock per table (single
   * encrypted column). Auth attempt with sharding: one lock per (table, column, shard).
   */
  static String mutexKeyForBatch(ReencryptionBatch batch) {
    String table = batch.getTargetTable();
    if (TABLE_ENROLLMENT.equals(table)) {
      return table;
    }
    if (TABLE_AUTH_ATTEMPT.equals(table)) {
      Integer shardCount = batch.getShardCount();
      if (shardCount != null && shardCount > 1) {
        return table + "|" + batch.getTargetColumn() + "|" + batch.getShardIndex();
      }
      return table;
    }
    return table;
  }
}
