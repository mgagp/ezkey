/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: LockingTaskExecutor
 * Description: Helper service for programmatic distributed locking using ShedLock.
 */

package org.ezkey.admin.service;

import java.time.Duration;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Helper service for programmatic distributed locking using ShedLock.
 *
 * <p>This service provides a simple API for executing tasks with distributed locks, useful for
 * startup bootstrap operations that need to be HA-safe but are not scheduled jobs.
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * lockingTaskExecutor.executeWithLock("MY_LOCK", Duration.ofMinutes(5), () -> {
 *     // Critical section - only one instance will execute this
 *     performBootstrapOperation();
 * });
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
public class LockingTaskExecutor {

  private static final Logger logger = LoggerFactory.getLogger(LockingTaskExecutor.class);

  private final LockProvider lockProvider;

  public LockingTaskExecutor(LockProvider lockProvider) {
    this.lockProvider = lockProvider;
  }

  /**
   * Execute a task with a distributed lock.
   *
   * <p>If the lock cannot be acquired, the task is skipped and false is returned. This is useful
   * for startup bootstrap operations where only one instance should perform initialization.
   *
   * @param lockName the name of the lock (must be unique)
   * @param lockAtMostFor maximum duration to hold the lock (safety margin for crash recovery)
   * @param task the task to execute if lock is acquired
   * @return true if lock was acquired and task executed, false if lock was not available
   */
  public boolean executeWithLock(String lockName, Duration lockAtMostFor, Runnable task) {
    LockConfiguration lockConfig =
        new LockConfiguration(java.time.Instant.now(), lockName, lockAtMostFor, Duration.ZERO);

    java.util.Optional<SimpleLock> lock = lockProvider.lock(lockConfig);

    if (lock.isPresent()) {
      try {
        logger.debug("Acquired lock '{}', executing task", lockName);
        task.run();
        logger.debug("Task completed, releasing lock '{}'", lockName);
        return true;
      } finally {
        lock.get().unlock();
      }
    } else {
      logger.info(
          "Lock '{}' not available, skipping task (another instance is handling it)", lockName);
      return false;
    }
  }
}
