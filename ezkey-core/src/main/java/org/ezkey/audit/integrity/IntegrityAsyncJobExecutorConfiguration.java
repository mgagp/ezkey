/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: IntegrityAsyncJobExecutorConfiguration
 * Description: Single-thread executor for Integrity async operator jobs.
 */

package org.ezkey.audit.integrity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Single-worker executor for Integrity async operator jobs (one global slot).
 *
 * @since 2026
 */
@Configuration
public class IntegrityAsyncJobExecutorConfiguration {

  /**
   * Creates the Integrity async job executor.
   *
   * @return single-thread pool
   */
  @Bean(name = "integrityAsyncJobExecutor")
  public ThreadPoolTaskExecutor integrityAsyncJobExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(1);
    executor.setThreadNamePrefix("integrity-async-");
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.initialize();
    return executor;
  }
}
