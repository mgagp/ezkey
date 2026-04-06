/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security.config;

import org.ezkey.config.TinkProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded thread pool for concurrent processing of <b>distinct</b> re-encryption batches (see
 * {@link org.ezkey.security.ReencryptionBatchParallelRunner}).
 */
@Configuration
public class ReencryptionExecutorConfiguration {

  @Bean(name = "reencryptionBatchExecutor")
  public ThreadPoolTaskExecutor reencryptionBatchExecutor(TinkProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    int workers = Math.max(1, properties.getReencryption().getParallelBatchWorkers());
    executor.setCorePoolSize(workers);
    executor.setMaxPoolSize(workers);
    executor.setQueueCapacity(
        Math.max(1, properties.getReencryption().getParallelBatchQueueCapacity()));
    executor.setThreadNamePrefix("reencrypt-batch-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
  }
}
