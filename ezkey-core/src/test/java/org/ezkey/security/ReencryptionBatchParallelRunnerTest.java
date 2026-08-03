/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("ReencryptionBatchParallelRunner mutex keys")
class ReencryptionBatchParallelRunnerTest {

  @Test
  @DisplayName("Enrollment batches use table-only lock")
  void mutexKey_enrollment_tableOnly() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_enrollment");
    b.setTargetColumn("integration_private_key");
    assertEquals("ezkey_enrollment", ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }

  @Test
  @DisplayName("Auth attempt without sharding uses table-only lock")
  void mutexKey_authAttempt_unsharded_tableOnly() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_auth_attempt");
    b.setTargetColumn("auth_attempt_proof_token");
    b.setShardCount(null);
    assertEquals("ezkey_auth_attempt", ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }

  @Test
  @DisplayName("Auth attempt with shard_count 1 uses table-only lock")
  void mutexKey_authAttempt_shardCountOne_tableOnly() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_auth_attempt");
    b.setTargetColumn("auth_attempt_proof_token");
    b.setShardCount(1);
    b.setShardIndex(0);
    assertEquals("ezkey_auth_attempt", ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }

  @Test
  @DisplayName("Auth attempt with sharding uses table|column|shardIndex lock")
  void mutexKey_authAttempt_sharded_includesColumnAndShard() {
    ReencryptionBatch b = new ReencryptionBatch();
    b.setTargetTable("ezkey_auth_attempt");
    b.setTargetColumn("auth_attempt_proof_token");
    b.setShardCount(4);
    b.setShardIndex(2);
    assertEquals(
        "ezkey_auth_attempt|auth_attempt_proof_token|2",
        ReencryptionBatchParallelRunner.mutexKeyForBatch(b));
  }

  @Test
  @DisplayName(
      "runBatchesAsync submits one top-level task per batch, not one wrapping dispatcher task")
  void runBatchesAsync_submitsOneTaskPerBatch_notASingleDispatcherTask() {
    // Regression guard: submitting a single dispatcher task that internally submits-and-waits on
    // the SAME bounded pool permanently occupies one worker thread, leaving only
    // (parallelBatchWorkers - 1) threads actually available to process batches. This was observed
    // in production as 3 of 4 configured shard workers running concurrently while the 4th queued
    // and ran only after one of the first 3 finished (see docs/REENCRYPTION_OPERATIONS.md).
    ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
    ReencryptionBatchProcessingService processingService =
        mock(ReencryptionBatchProcessingService.class);
    TinkProperties properties = new TinkProperties();
    properties.getReencryption().setParallelBatchWorkers(4);

    ReencryptionBatchParallelRunner runner =
        new ReencryptionBatchParallelRunner(executor, processingService, properties);

    ReencryptionBatch batch1 = new ReencryptionBatch();
    batch1.setTargetTable("ezkey_auth_attempt");
    batch1.setTargetColumn("auth_attempt_proof_token");
    batch1.setShardCount(4);
    batch1.setShardIndex(0);
    ReencryptionBatch batch2 = new ReencryptionBatch();
    batch2.setTargetTable("ezkey_auth_attempt");
    batch2.setTargetColumn("auth_attempt_proof_token");
    batch2.setShardCount(4);
    batch2.setShardIndex(1);
    ReencryptionBatch batch3 = new ReencryptionBatch();
    batch3.setTargetTable("ezkey_auth_attempt");
    batch3.setTargetColumn("auth_attempt_proof_token");
    batch3.setShardCount(4);
    batch3.setShardIndex(2);

    runner.runBatchesAsync(List.of(batch1, batch2, batch3), (batch, e) -> {});

    // One execute() call per batch: each competes independently for a pool thread.
    verify(executor, times(3)).execute(any());
  }
}
