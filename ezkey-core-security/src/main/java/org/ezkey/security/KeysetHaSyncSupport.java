/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: KeysetHaSyncSupport
 * Description: HA helpers for TinkKeyManager — version tick, CAS write, decrypt-failure throttle,
 *     findVersion instrumentation (Option A / #914 / #920).
 */

package org.ezkey.security;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.ezkey.security.exception.KeysetConcurrentModificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/**
 * Package-private HA synchronization helpers for {@link TinkKeyManager}.
 *
 * <p>Keeps traffic-independent version ticking, CAS blob writes, decrypt-failure reload throttling,
 * and {@code findVersion} instrumentation out of the main manager file (Checkstyle FileLength).
 *
 * @since 2026
 */
final class KeysetHaSyncSupport {

  private static final Logger logger = LoggerFactory.getLogger(KeysetHaSyncSupport.class);

  private final TinkProperties properties;
  private final KeysetBlobRepository keysetBlobRepository;

  private final ScheduledExecutorService keysetVersionTickExecutor =
      Executors.newSingleThreadScheduledExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "tink-keyset-version-tick");
            thread.setDaemon(true);
            return thread;
          });

  private final AtomicBoolean decryptReloadInFlight = new AtomicBoolean(false);
  private volatile long lastDecryptReloadTimeMs = 0;
  private final AtomicLong findVersionInvocationCount = new AtomicLong(0);

  /**
   * Creates HA sync support for the given properties and optional repository.
   *
   * @param properties Tink configuration
   * @param keysetBlobRepository database blob repository, or {@code null} when unavailable
   */
  KeysetHaSyncSupport(TinkProperties properties, KeysetBlobRepository keysetBlobRepository) {
    this.properties = properties;
    this.keysetBlobRepository = keysetBlobRepository;
  }

  /**
   * Starts the traffic-independent version tick when DATABASE/HYBRID storage is active.
   *
   * <p>Warns at startup when {@code version-check-interval-seconds >= sync-window-seconds}.
   *
   * @param databaseBacked whether this process uses DB-backed keyset storage
   * @param checkingFlag ThreadLocal re-entrancy guard shared with traffic-triggered checks
   * @param checkAndReload action that compares versions and reloads when needed
   */
  void startPeriodicVersionTickIfDatabaseBacked(
      boolean databaseBacked, ThreadLocal<Boolean> checkingFlag, Runnable checkAndReload) {
    if (!databaseBacked || keysetBlobRepository == null) {
      return;
    }
    int tickSeconds = Math.max(1, properties.getKeyset().getVersionCheckIntervalSeconds());
    int syncWindowSeconds = Math.max(1, properties.getRotation().getSyncWindowSeconds());
    if (tickSeconds >= syncWindowSeconds) {
      logger.warn(
          "ezkey.encryption.keyset.version-check-interval-seconds ({}) >= "
              + "ezkey.encryption.rotation.sync-window-seconds ({}). "
              + "An idle replica may stay stale past the PENDING promotion window. "
              + "Prefer tick <= syncWindow/2.",
          tickSeconds,
          syncWindowSeconds);
    }
    keysetVersionTickExecutor.scheduleAtFixedRate(
        () -> {
          if (Boolean.TRUE.equals(checkingFlag.get())) {
            return;
          }
          checkingFlag.set(true);
          try {
            checkAndReload.run();
          } catch (RuntimeException e) { // CHECKSTYLE IGNORE IllegalCatch
            logger.error(
                "Traffic-independent keyset version tick failed (will retry next interval): {}",
                e.getMessage());
            logger.debug("Version tick error", e);
          } finally {
            checkingFlag.set(false);
          }
        },
        tickSeconds,
        tickSeconds,
        TimeUnit.SECONDS);
    logger.info(
        "Started traffic-independent keyset version tick every {}s (sync-window={}s)",
        tickSeconds,
        syncWindowSeconds);
  }

  /** Shuts down the version-tick executor. */
  void shutdown() {
    keysetVersionTickExecutor.shutdown();
    try {
      if (!keysetVersionTickExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        keysetVersionTickExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      keysetVersionTickExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /** Immediate shutdown of the version-tick executor (interrupt path). */
  void shutdownNow() {
    keysetVersionTickExecutor.shutdownNow();
  }

  /**
   * Reads the blob version and increments the SEC-009 / test instrumentation counter.
   *
   * @return optional blob version
   */
  Optional<Long> findBlobVersionInstrumented() {
    findVersionInvocationCount.incrementAndGet();
    return keysetBlobRepository.findVersion();
  }

  long getFindVersionInvocationCount() {
    return findVersionInvocationCount.get();
  }

  void resetFindVersionInvocationCount() {
    findVersionInvocationCount.set(0);
  }

  /**
   * Claims the singleton row when absent, otherwise CAS-updates with {@code expectedVersion}.
   * Updates {@code onVersionAdvanced} with the resulting version (1 after claim, expected+1 after
   * CAS). P6: does not read JPA {@code @Version} before flush.
   *
   * @param encryptedData encrypted keyset envelope bytes
   * @param updatedBy actor label
   * @param memoryVersionSupplier current in-memory version (may be 0 before first sync)
   * @param onVersionAdvanced receives the new authoritative version
   * @throws KeysetConcurrentModificationException when CAS updates zero rows
   */
  void claimOrCasUpdateKeyset(
      byte[] encryptedData,
      String updatedBy,
      Supplier<Long> memoryVersionSupplier,
      LongConsumer onVersionAdvanced) {
    OffsetDateTime now = OffsetDateTime.now();
    if (!keysetBlobRepository.keysetExists()) {
      int claimed = keysetBlobRepository.insertSingletonIfAbsent(encryptedData, now, updatedBy);
      if (claimed > 0) {
        onVersionAdvanced.accept(1L);
        logger.info("✅ Keyset claimed in database (version: {}, updated by: {})", 1L, updatedBy);
        return;
      }
      // Peer won the insert race — fall through to CAS against their version.
      long peerVersion = findBlobVersionInstrumented().orElse(1L);
      onVersionAdvanced.accept(peerVersion);
    }

    long expectedVersion = memoryVersionSupplier.get();
    if (expectedVersion <= 0) {
      expectedVersion = findBlobVersionInstrumented().orElse(1L);
      onVersionAdvanced.accept(expectedVersion);
    }

    int updated =
        keysetBlobRepository.updateKeysetIfVersionMatches(
            encryptedData, now, updatedBy, expectedVersion);
    if (updated == 0) {
      throw new KeysetConcurrentModificationException(
          expectedVersion,
          "Keyset blob CAS failed: expected version "
              + expectedVersion
              + " was superseded by another writer (updatedBy attempted="
              + updatedBy
              + ")");
    }
    // P6: CAS success means new version is expected+1.
    onVersionAdvanced.accept(expectedVersion + 1);
    logger.info(
        "✅ Keyset saved to database (version: {}, updated by: {})", expectedVersion + 1, updatedBy);
  }

  /**
   * Under the write lock: if reload-before-mutation is enabled and the blob is newer, reload once.
   *
   * @param memoryVersionSupplier current in-memory blob version
   * @param reloadAction loads the blob into memory (write lock already held)
   */
  void reloadKeysetBeforeMutationIfNeeded(
      Supplier<Long> memoryVersionSupplier, BooleanSupplier reloadAction) {
    if (!properties.getKeyset().isReloadBeforeMutation()) {
      return;
    }
    if (keysetBlobRepository == null) {
      return;
    }
    TinkProperties.Keyset.StorageMode storageMode = properties.getKeyset().getStorageMode();
    if (storageMode != TinkProperties.Keyset.StorageMode.DATABASE
        && storageMode != TinkProperties.Keyset.StorageMode.HYBRID) {
      return;
    }
    try {
      Optional<Long> dbVersion = findBlobVersionInstrumented();
      long memoryVersion = memoryVersionSupplier.get();
      if (dbVersion.isPresent() && dbVersion.get() > memoryVersion) {
        logger.info(
            "🔄 Reloading keyset before mutation (db version {} > memory {})",
            dbVersion.get(),
            memoryVersion);
        reloadAction.getAsBoolean();
      }
    } catch (DataAccessException | IllegalStateException e) {
      logger.warn(
          "Failed to check/reload keyset before mutation: {}. Proceeding with memory keyset.",
          e.getMessage());
      logger.debug("Reload-before-mutation error", e);
    }
  }

  /**
   * Returns whether the database blob version is not ahead of memory (write lock held).
   *
   * @param memoryVersion in-memory cached version
   * @return {@code true} when versions match or repository is unavailable
   */
  boolean isDatabaseBlobCurrent(long memoryVersion) {
    if (keysetBlobRepository == null) {
      return true;
    }
    try {
      Optional<Long> dbVersion = findBlobVersionInstrumented();
      return dbVersion.isEmpty() || dbVersion.get() <= memoryVersion;
    } catch (DataAccessException | IllegalStateException e) {
      return false;
    }
  }

  /**
   * Single-flight, throttled synchronous reload for the decrypt-failure path only (H2).
   *
   * @param reloadAction performs {@code loadKeysetFromDatabase()}
   * @return {@code true} when a reload completed successfully
   */
  boolean tryReloadAfterDecryptFailure(BooleanSupplier reloadAction) {
    if (keysetBlobRepository == null) {
      return false;
    }
    long throttleMs = Math.max(0L, properties.getKeyset().getDecryptFailureReloadThrottleMs());
    long now = System.currentTimeMillis();
    if (throttleMs > 0 && (now - lastDecryptReloadTimeMs) < throttleMs) {
      logger.debug(
          "Decrypt-failure reload throttled (last {} ms ago, min interval {} ms)",
          now - lastDecryptReloadTimeMs,
          throttleMs);
      return false;
    }
    if (!decryptReloadInFlight.compareAndSet(false, true)) {
      logger.debug("Decrypt-failure reload already in flight on this node");
      return false;
    }
    try {
      logger.info("🔄 Decrypt-failure path: synchronous keyset reload from database");
      // Stamp throttle on every attempt (success or failure) to bound amplification.
      lastDecryptReloadTimeMs = System.currentTimeMillis();
      return reloadAction.getAsBoolean();
    } finally {
      decryptReloadInFlight.set(false);
    }
  }
}
