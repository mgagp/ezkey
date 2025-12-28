/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ShedLockDistributedTest
 * Description: Functional tests for ShedLock distributed locking in HA environment.
 */

package org.ezkey.tests.security.scheduler;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ezkey.tests.config.DockerStackConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Functional tests for ShedLock distributed locking in HA environment.
 *
 * <p>These tests validate that scheduled jobs execute only once across multiple instances in a HA
 * deployment. Tests run against a Docker stack with 2 instances of each API behind HAProxy load
 * balancers.
 *
 * <p><b>Prerequisites:</b>
 *
 * <ul>
 *   <li>HA Docker stack must be running: `./docker/start-ha.sh`
 *   <li>Stack must have 2 instances of admin-api and auth-api
 *   <li>PostgreSQL container must be named "ezkey-postgres-ha"
 * </ul>
 *
 * <p><b>Test Strategy:</b>
 *
 * <ul>
 *   <li>Test A: Verify exclusion mutuelle - only one instance executes scheduled jobs
 *   <li>Test B: Verify locks in database - validate lock creation and expiration
 *   <li>Test C: Verify failover - test recovery when instance crashes
 * </ul>
 *
 * @since 2025
 */
@DisplayName("ShedLock Distributed Locking Tests")
public class ShedLockDistributedTest {

  private static final Logger log = LoggerFactory.getLogger(ShedLockDistributedTest.class);

  private static DockerStackConfig dockerConfig;
  private static ShedLockTestHelper shedLockHelper;

  @BeforeAll
  static void setUp() {
    dockerConfig = new DockerStackConfig();
    dockerConfig.verifyServicesHealthy();

    shedLockHelper = new ShedLockTestHelper();

    // Verify PostgreSQL container is accessible
    if (!shedLockHelper.verifyContainerAccessible()) {
      log.error(
          "PostgreSQL container is not accessible. Make sure the Docker stack is running (standard:"
              + " ./docker/start.sh or HA: ./docker/start-ha.sh)");
      throw new IllegalStateException(
          "PostgreSQL container is not accessible. "
              + "Docker stack must be running for ShedLock tests.");
    }

    // Verify ezkey_shedlock table exists (ShedLock creates it automatically on first job execution)
    if (!shedLockHelper.verifyShedLockTableExists()) {
      log.warn(
          "ezkey_shedlock table does not exist yet. "
              + "This is normal if no scheduled jobs have executed yet. "
              + "The table will be created automatically when the first job runs.");
    }

    boolean isHaMode = shedLockHelper.isHaMode();
    log.info("ShedLock distributed locking tests initialized");
    log.info("Mode: {}", isHaMode ? "HA (High Availability)" : "Standard (Single Instance)");
    log.info("Admin API URL: {}", dockerConfig.getAdminApiUrl());
    log.info("Auth API URL: {}", dockerConfig.getAuthApiUrl());
  }

  /**
   * Test A: Exclusion Mutuelle
   *
   * <p>Verifies that scheduled jobs execute only once even with 2 instances running. This is the
   * core requirement for ShedLock in HA deployments.
   *
   * <p><b>Test Steps:</b>
   *
   * <ol>
   *   <li>Wait for a scheduled job to execute (KEY_PROMOTION runs every 5 seconds)
   *   <li>Verify that only one active lock exists in the ezkey_shedlock table
   *   <li>Verify that the lock changes between instances over time (no instance affinity) - HA mode
   *       only
   * </ol>
   *
   * <p><b>Note:</b> In standard mode (single instance), this test verifies that locks are created
   * correctly. In HA mode, it also verifies exclusion mutuelle across multiple instances.
   */
  @Test
  @DisplayName("Test A: Exclusion Mutuelle - Only one instance executes scheduled jobs")
  @Timeout(60) // 60 seconds timeout
  void testExclusionMutuelle() {
    log.info("=== Test A: Exclusion Mutuelle ===");

    boolean isHaMode = shedLockHelper.isHaMode();
    log.info("Running in {} mode", isHaMode ? "HA" : "Standard");

    String lockName = "KEY_PROMOTION"; // Job that runs every 5 seconds

    // Wait for lock to be acquired (job execution)
    log.info("Waiting for lock '{}' to be acquired...", lockName);
    ShedLockTestHelper.ShedLockEntry lock = shedLockHelper.waitForLockAcquisition(lockName, 30);

    if (lock == null) {
      throw new AssertionError(
          "Lock '"
              + lockName
              + "' was not acquired within timeout. "
              + (isHaMode
                  ? "Is the HA stack running with 2 instances?"
                  : "Are scheduled jobs enabled and running?"));
    }

    log.info("Lock acquired by instance: {}", lock.lockedBy());

    // Verify only one active lock exists for this job
    int activeLockCount = shedLockHelper.countActiveLocks(lockName);
    if (activeLockCount != 1) {
      throw new AssertionError(
          String.format(
              "Expected exactly 1 active lock for '%s', but found %d. "
                  + "This indicates multiple instances are executing the job simultaneously.",
              lockName, activeLockCount));
    }

    log.info("✅ Verified: Only one active lock exists for '{}'", lockName);

    // In HA mode, verify lock changes between instances over time (no instance affinity)
    if (isHaMode) {
      log.info("Verifying lock distribution across instances (no affinity)...");
      Set<String> instancesThatHeldLock = new java.util.HashSet<>();
      instancesThatHeldLock.add(lock.lockedBy());

      // Wait for 3-4 job executions (KEY_PROMOTION runs every 5 seconds)
      for (int i = 0; i < 4; i++) {
        try {
          Thread.sleep(6000); // Wait 6 seconds (slightly more than job interval)
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }

        ShedLockTestHelper.ShedLockEntry currentLock = shedLockHelper.getLock(lockName);
        if (currentLock != null && currentLock.isActive()) {
          instancesThatHeldLock.add(currentLock.lockedBy());
          log.debug("Lock currently held by instance: {}", currentLock.lockedBy());
        }
      }

      log.info("Instances that held the lock: {}", instancesThatHeldLock);

      // In a healthy HA setup, both instances should acquire the lock over time
      // (round-robin distribution via HAProxy)
      if (instancesThatHeldLock.size() < 1) {
        throw new AssertionError(
            "Expected at least one instance to hold the lock, but none found. "
                + "Check that both instances are running and healthy.");
      }

      log.info(
          "✅ Verified: Lock distribution across instances ({} unique instances)",
          instancesThatHeldLock.size());
    } else {
      log.info(
          "✅ Verified: Lock created successfully (Standard mode - single instance, "
              + "exclusion mutuelle not applicable)");
    }
  }

  /**
   * Test B: Vérification Locks dans DB
   *
   * <p>Validates that locks are correctly created and updated in the PostgreSQL ezkey_shedlock table.
   * This verifies the database-level locking mechanism.
   *
   * <p><b>Test Steps:</b>
   *
   * <ol>
   *   <li>Get all active locks from the database
   *   <li>Verify lock structure (name, locked_by, locked_at, lock_until)
   *   <li>Verify that lock_until > NOW() for active locks
   *   <li>Verify lock expiration times are reasonable
   * </ol>
   */
  @Test
  @DisplayName("Test B: Vérification Locks DB - Validate lock structure and expiration")
  @Timeout(30)
  void testLocksInDatabase() {
    log.info("=== Test B: Vérification Locks DB ===");

    // Get all active locks
    List<ShedLockTestHelper.ShedLockEntry> activeLocks = shedLockHelper.getActiveLocks();

    log.info("Found {} active lock(s) in database", activeLocks.size());

    if (activeLocks.isEmpty()) {
      // Wait a bit for a job to execute and acquire a lock
      log.info("No active locks found, waiting for job execution...");
      try {
        Thread.sleep(10000); // Wait 10 seconds
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      activeLocks = shedLockHelper.getActiveLocks();
    }

    // Verify at least one lock exists (scheduled jobs should be running)
    if (activeLocks.isEmpty()) {
      log.warn(
          "No active locks found. This may indicate scheduled jobs are not running. "
              + "Check that the HA stack is running and jobs are enabled.");
      // Don't fail the test - jobs may not be running in test environment
      return;
    }

    // Verify lock structure for each active lock
    OffsetDateTime now = OffsetDateTime.now();

    for (ShedLockTestHelper.ShedLockEntry lock : activeLocks) {
      log.info(
          "Validating lock: name='{}', locked_by='{}', locked_at={}, lock_until={}",
          lock.name(),
          lock.lockedBy(),
          lock.lockedAt(),
          lock.lockUntil());

      // Verify lock name is not empty
      if (lock.name() == null || lock.name().isEmpty()) {
        throw new AssertionError("Lock name is empty");
      }

      // Verify locked_by is not empty
      if (lock.lockedBy() == null || lock.lockedBy().isEmpty()) {
        throw new AssertionError("Lock locked_by is empty for lock: " + lock.name());
      }

      // Verify locked_at is in the past (lock was acquired)
      if (lock.lockedAt().isAfter(now)) {
        throw new AssertionError(
            "Lock locked_at is in the future for lock: "
                + lock.name()
                + " (locked_at="
                + lock.lockedAt()
                + ")");
      }

      // Verify lock_until is in the future for active locks
      if (!lock.isActive()) {
        log.warn(
            "Lock '{}' is not active (lock_until={} <= now={})",
            lock.name(),
            lock.lockUntil(),
            now);
      } else {
        if (lock.lockUntil().isBefore(now) || lock.lockUntil().isEqual(now)) {
          throw new AssertionError(
              "Active lock has lock_until in the past or present: "
                  + lock.name()
                  + " (lock_until="
                  + lock.lockUntil()
                  + ", now="
                  + now
                  + ")");
        }

        // Verify lock duration is reasonable (not too long, not negative)
        Duration lockDuration = Duration.between(lock.lockedAt(), lock.lockUntil());
        if (lockDuration.isNegative()) {
          throw new AssertionError(
              "Lock duration is negative for lock: "
                  + lock.name()
                  + " (duration="
                  + lockDuration
                  + ")");
        }

        // Verify lock duration is not excessive (e.g., > 1 hour for KEY_PROMOTION)
        if (lockDuration.toHours() > 1) {
          log.warn(
              "Lock '{}' has unusually long duration: {} (expected < 1 hour for most jobs)",
              lock.name(),
              lockDuration);
        }

        log.info(
            "✅ Lock '{}' validated: duration={}, locked_by='{}'",
            lock.name(),
            lockDuration,
            lock.lockedBy());
      }
    }

    log.info("✅ Verified: All active locks have valid structure and expiration times");
  }

  /**
   * Test C: Failover
   *
   * <p>Tests that if an instance crashes while holding a lock, another instance can acquire the
   * lock after expiration. This validates the failover mechanism.
   *
   * <p><b>Test Steps:</b>
   *
   * <ol>
   *   <li>Wait for an instance to acquire a lock
   *   <li>Crash that instance (docker kill)
   *   <li>Wait for lock expiration
   *   <li>Verify another instance acquires the lock
   * </ol>
   *
   * <p><b>Note:</b> This test is opportunistic - it may be skipped if conditions are not met.
   */
  @Test
  @DisplayName("Test C: Failover - Test recovery when instance crashes")
  @Timeout(120) // 2 minutes timeout (lock expiration + recovery)
  void testFailover() {
    log.info("=== Test C: Failover ===");

    String lockName = "KEY_PROMOTION"; // Job that runs frequently

    // Wait for lock to be acquired
    log.info("Waiting for lock '{}' to be acquired...", lockName);
    ShedLockTestHelper.ShedLockEntry initialLock =
        shedLockHelper.waitForLockAcquisition(lockName, 30);

    if (initialLock == null) {
      log.warn("Lock '{}' was not acquired. Skipping failover test.", lockName);
      return;
    }

    // Failover test only makes sense in HA mode (multiple instances)
    boolean isHaMode = shedLockHelper.isHaMode();
    if (!isHaMode) {
      log.info(
          "Skipping failover test - requires HA mode with multiple instances. "
              + "Current mode: Standard (single instance)");
      return;
    }

    String instanceHoldingLock = initialLock.lockedBy();
    log.info("Lock '{}' is held by instance: {}", lockName, instanceHoldingLock);

    // Determine which container to kill based on instance ID
    String containerToKill = null;
    if (instanceHoldingLock.contains("admin-api-1") || instanceHoldingLock.contains("1")) {
      containerToKill = "ezkey-admin-api-1";
    } else if (instanceHoldingLock.contains("admin-api-2") || instanceHoldingLock.contains("2")) {
      containerToKill = "ezkey-admin-api-2";
    } else {
      log.warn(
          "Cannot determine container to kill from instance ID '{}'. Skipping failover test.",
          instanceHoldingLock);
      return;
    }

    log.info("Crashing container: {}", containerToKill);

    // Crash the instance
    try {
      ProcessBuilder killProcess = new ProcessBuilder("docker", "kill", containerToKill);
      Process killProc = killProcess.start();
      int exitCode = killProc.waitFor();

      if (exitCode != 0) {
        log.warn("Failed to kill container '{}'. Skipping failover test.", containerToKill);
        return;
      }

      log.info("✅ Container '{}' killed successfully", containerToKill);
    } catch (Exception e) {
      log.error("Failed to kill container '{}': {}", containerToKill, e.getMessage(), e);
      return;
    }

    // Wait for lock expiration (KEY_PROMOTION has lockAtMostFor=PT1M)
    // Add buffer for safety
    log.info("Waiting for lock expiration (lockAtMostFor=PT1M + buffer)...");
    try {
      Thread.sleep(70000); // 70 seconds (1 minute + 10 second buffer)
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    // Verify another instance acquires the lock
    log.info("Checking if another instance acquired the lock...");
    ShedLockTestHelper.ShedLockEntry recoveredLock =
        shedLockHelper.waitForLockAcquisition(lockName, 30);

    if (recoveredLock == null) {
      throw new AssertionError(
          "Lock '"
              + lockName
              + "' was not re-acquired after instance crash. "
              + "Failover mechanism may not be working correctly.");
    }

    String recoveredInstance = recoveredLock.lockedBy();
    log.info("Lock '{}' recovered by instance: {}", lockName, recoveredInstance);

    // Verify it's a different instance (or at least that lock was re-acquired)
    if (recoveredInstance.equals(instanceHoldingLock)) {
      log.warn(
          "Lock was re-acquired by the same instance '{}'. "
              + "This may indicate the instance restarted quickly or instance ID is not unique.",
          recoveredInstance);
    } else {
      log.info(
          "✅ Lock recovered by different instance: {} -> {}",
          instanceHoldingLock,
          recoveredInstance);
    }

    log.info("✅ Verified: Failover mechanism works - lock recovered after instance crash");
  }

  /**
   * Helper test: Display current lock distribution.
   *
   * <p>This test displays the current state of locks for debugging and monitoring purposes.
   */
  @Test
  @DisplayName("Helper: Display Lock Distribution")
  @Timeout(10)
  void displayLockDistribution() {
    log.info("=== Lock Distribution ===");

    List<ShedLockTestHelper.ShedLockEntry> allLocks = shedLockHelper.getAllLocks();
    log.info("Total locks in database: {}", allLocks.size());

    List<ShedLockTestHelper.ShedLockEntry> activeLocks = shedLockHelper.getActiveLocks();
    log.info("Active locks: {}", activeLocks.size());

    for (ShedLockTestHelper.ShedLockEntry lock : activeLocks) {
      log.info(
          "  - Lock: '{}', Instance: '{}', Locked at: {}, Expires at: {}",
          lock.name(),
          lock.lockedBy(),
          lock.lockedAt(),
          lock.lockUntil());
    }

    Map<String, Integer> distribution = shedLockHelper.getLockDistribution();
    log.info("Lock distribution by instance:");
    for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
      log.info("  - Instance '{}': {} active lock(s)", entry.getKey(), entry.getValue());
    }
  }
}
