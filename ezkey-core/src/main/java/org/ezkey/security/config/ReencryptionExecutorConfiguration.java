/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security.config;

import org.ezkey.config.TinkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded thread pool for concurrent processing of <b>distinct</b> re-encryption batches (see
 * {@link org.ezkey.security.ReencryptionBatchParallelRunner}).
 */
@Configuration
public class ReencryptionExecutorConfiguration {

  private static final Logger logger =
      LoggerFactory.getLogger(ReencryptionExecutorConfiguration.class);

  @Bean(name = "reencryptionBatchExecutor")
  public ThreadPoolTaskExecutor reencryptionBatchExecutor(TinkProperties properties) {
    TinkProperties.Reencryption reencryption = properties.getReencryption();
    int workers = Math.max(1, reencryption.getParallelBatchWorkers());
    int shardCount = Math.max(1, reencryption.getAuthAttemptShardCount());
    if (shardCount > 1 && workers < shardCount) {
      logger.warn(
          "ezkey.encryption.reencryption.parallel-batch-workers={} is less than"
              + " auth-attempt-shard-count={}; auth-attempt shard batches will queue behind the"
              + " pool. Prefer workers >= shard-count (typical Admin pairing: 4/4).",
          workers,
          shardCount);
    }
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(workers);
    executor.setMaxPoolSize(workers);
    executor.setQueueCapacity(Math.max(1, reencryption.getParallelBatchQueueCapacity()));
    executor.setThreadNamePrefix("reencrypt-batch-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
  }
}
