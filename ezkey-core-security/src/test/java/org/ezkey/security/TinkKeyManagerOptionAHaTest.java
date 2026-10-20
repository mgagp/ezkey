/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerOptionAHaTest
 * Description: Option A / #914 / #920 / #921 HA criteria — reload-before-mutation, unknown kid,
 *     SEC-009 hot path, H2 throttle, tick vs sync-window WARN.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.ezkey.security.exception.KeysetKeyNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * CI-runnable HA criteria from Patrick brief §4 (Option A + H2 + SEC-009), without requiring the
 * Docker HA stack / {@code ezkey-tests}.
 *
 * @since 2026
 */
class TinkKeyManagerOptionAHaTest {

  @Test
  @DisplayName("§4.7 unknown kid after reload: refuse setPrimary and blob write")
  void promoteUnknownKid_afterReload_refusesWithoutBlobWrite(@TempDir Path tempDir)
      throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = casRepository(sharedBlob);

    TinkKeyManager manager =
        initializeManager(
            tempDir.resolve("master.key"),
            tempDir.resolve("keyset.json.encrypted"),
            repository,
            true);
    try {
      long versionBefore = manager.getDatabaseKeysetVersion();
      long unknownKid = 0xDEADBEEFL;

      KeysetKeyNotFoundException ex =
          assertThrows(
              KeysetKeyNotFoundException.class, () -> manager.promoteToPrimary(unknownKid));
      assertEquals(unknownKid, ex.getKeyId());
      assertTrue(ex.isAfterFreshReload(), "blob should be current after reload-before-mutation");
      assertEquals(
          versionBefore,
          manager.getDatabaseKeysetVersion(),
          "refused promote must not advance blob version");
      assertFalse(manager.getAllKeyIds().contains(unknownKid));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("§4.8 SEC-009: getAeadPrimitive does not call findVersion synchronously per op")
  void getAeadPrimitive_hotPath_zeroSynchronousFindVersion(@TempDir Path tempDir) throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = casRepository(sharedBlob);

    TinkKeyManager manager =
        initializeManager(
            tempDir.resolve("master.key"),
            tempDir.resolve("keyset.json.encrypted"),
            repository,
            true);
    try {
      // Warm the traffic-triggered async check so the throttle is armed, then wait for any
      // in-flight async findVersion before measuring the hot path.
      manager.getAeadPrimitive();
      Thread.sleep(250);
      manager.resetFindVersionInvocationCountForTests();

      int ops = 50;
      for (int i = 0; i < ops; i++) {
        assertTrue(manager.getAeadPrimitive() != null);
      }
      Thread.sleep(50);

      assertEquals(
          0L,
          manager.getFindVersionInvocationCountForTests(),
          "hot path must not invoke findVersion per op while throttle is warm (SEC-009)");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("§4.3 / H2: decrypt-failure reload is throttled to at most one per window")
  void tryReloadAfterDecryptFailure_throttled(@TempDir Path tempDir) throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = casRepository(sharedBlob);

    TinkProperties properties = baseProperties(tempDir);
    properties.getKeyset().setDecryptFailureReloadThrottleMs(60_000L);

    TinkKeyManager api1 = new TinkKeyManager(properties, fixedProvider(repository));
    api1.initialize();

    TinkProperties props2 = baseProperties(tempDir);
    props2.setKeysetFile(tempDir.resolve("api2-keyset.json.encrypted").toString());
    props2.getKeyset().setDecryptFailureReloadThrottleMs(60_000L);
    TinkKeyManager api2 = new TinkKeyManager(props2, fixedProvider(repository));
    api2.initialize();

    try {
      long k = api1.addKeyWithoutPromotion();
      api1.promoteToPrimary(k);
      assertFalse(api2.getAllKeyIds().contains(k), "precondition: api2 stale");

      assertTrue(api2.tryReloadAfterDecryptFailure(), "first decrypt-failure reload must run");
      assertTrue(api2.getAllKeyIds().contains(k), "reload must pick up peer key");
      assertFalse(
          api2.tryReloadAfterDecryptFailure(),
          "second reload within throttle window must be skipped");
    } finally {
      api1.shutdownKeysetCheckExecutor();
      api2.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("§4.4 tick >= syncWindow emits startup WARN")
  void versionTick_whenGreaterOrEqualSyncWindow_logsStartupWarn(@TempDir Path tempDir)
      throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = casRepository(sharedBlob);

    Logger logger = (Logger) LoggerFactory.getLogger(KeysetHaSyncSupport.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      TinkProperties properties = baseProperties(tempDir);
      properties.getKeyset().setVersionCheckIntervalSeconds(30);
      properties.getRotation().setSyncWindowSeconds(10);

      TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
      manager.initialize();
      try {
        boolean warned =
            appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("version-check-interval-seconds"));
        assertTrue(warned, "startup must WARN when tick >= sync-window");
      } finally {
        manager.shutdownKeysetCheckExecutor();
      }
    } finally {
      logger.detachAppender(appender);
    }
  }

  @Test
  @DisplayName("§4.2 stale promote reloads under lock and succeeds (Option A)")
  void stalePromote_reloadsAndSucceeds(@TempDir Path tempDir) throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = casRepository(sharedBlob);

    TinkKeyManager api1 =
        initializeManager(
            tempDir.resolve("master.key"),
            tempDir.resolve("api1-keyset.json.encrypted"),
            repository,
            true);
    TinkKeyManager api2 =
        initializeManager(
            tempDir.resolve("master.key"),
            tempDir.resolve("api2-keyset.json.encrypted"),
            repository,
            true);
    try {
      api2.loadKeysetFromDatabase();
      long pending = api1.addKeyWithoutPromotion();
      assertFalse(api2.getAllKeyIds().contains(pending), "precondition: api2 idle/stale");

      api2.promoteToPrimary(pending);
      assertEquals(pending, api2.getCurrentPrimaryKeyId());
      assertTrue(api1.getAllKeyIds().contains(pending));
    } finally {
      api1.shutdownKeysetCheckExecutor();
      api2.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("reload-before-mutation flag false skips versioned reload (emergency rollback)")
  void reloadBeforeMutationDisabled_skipsReload(@TempDir Path tempDir) throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = casRepository(sharedBlob);

    TinkKeyManager api1 =
        initializeManager(
            tempDir.resolve("master.key"),
            tempDir.resolve("api1-keyset.json.encrypted"),
            repository,
            true);

    TinkProperties props2 = baseProperties(tempDir);
    props2.setKeysetFile(tempDir.resolve("api2-keyset.json.encrypted").toString());
    props2.getKeyset().setReloadBeforeMutation(false);
    TinkKeyManager api2 = new TinkKeyManager(props2, fixedProvider(repository));
    api2.initialize();

    try {
      api2.loadKeysetFromDatabase();
      long pending = api1.addKeyWithoutPromotion();
      assertFalse(api2.getAllKeyIds().contains(pending));

      assertThrows(
          KeysetKeyNotFoundException.class,
          () -> api2.promoteToPrimary(pending),
          "without reload-before-mutation, stale promote must still fail closed");
    } finally {
      api1.shutdownKeysetCheckExecutor();
      api2.shutdownKeysetCheckExecutor();
    }
  }

  private static KeysetBlobRepository casRepository(AtomicReference<KeysetBlob> storedBlob) {
    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenAnswer(_ -> Optional.ofNullable(storedBlob.get()));
    when(repository.keysetExists()).thenAnswer(_ -> storedBlob.get() != null);
    when(repository.insertSingletonIfAbsent(any(), any(), any()))
        .thenAnswer(
            invocation -> {
              if (storedBlob.get() != null) {
                return 0;
              }
              KeysetBlob blob =
                  new KeysetBlob(invocation.getArgument(0), invocation.getArgument(2));
              blob.setLastUpdatedAt(invocation.getArgument(1));
              blob.setVersion(1L);
              storedBlob.set(blob);
              return 1;
            });
    when(repository.findVersion())
        .thenAnswer(_ -> Optional.ofNullable(storedBlob.get()).map(KeysetBlob::getVersion));
    when(repository.updateKeysetIfVersionMatches(any(), any(), any(), anyLong()))
        .thenAnswer(
            invocation -> {
              long expectedVersion = invocation.getArgument(3);
              KeysetBlob current = storedBlob.get();
              if (current == null || current.getVersion() != expectedVersion) {
                return 0;
              }
              KeysetBlob updated =
                  new KeysetBlob(invocation.getArgument(0), invocation.getArgument(2));
              updated.setLastUpdatedAt(invocation.getArgument(1));
              updated.setVersion(expectedVersion + 1);
              storedBlob.set(updated);
              return 1;
            });
    return repository;
  }

  private static TinkProperties baseProperties(Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    if (!masterKeyPath.toFile().exists()) {
      TestMasterKeys.write(masterKeyPath);
    }
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(false);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(tempDir.resolve("keyset.json.encrypted").toString());
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);
    properties.getKeyset().setReloadBeforeMutation(true);
    properties.getKeyset().setVersionCheckIntervalSeconds(5);
    properties.getRotation().setSyncWindowSeconds(30);
    properties.getRotation().setBackupBeforeRotation(false);
    return properties;
  }

  private static TinkKeyManager initializeManager(
      Path masterKeyPath,
      Path keysetPath,
      KeysetBlobRepository repository,
      boolean reloadBeforeMutation)
      throws Exception {
    if (!masterKeyPath.toFile().exists()) {
      TestMasterKeys.write(masterKeyPath);
    }
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(false);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);
    properties.getKeyset().setReloadBeforeMutation(reloadBeforeMutation);
    properties.getRotation().setBackupBeforeRotation(false);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    manager.initialize();
    manager.resetDatabaseCheckThrottleForTests();
    return manager;
  }

  private static ObjectProvider<KeysetBlobRepository> fixedProvider(KeysetBlobRepository repo) {
    return new ObjectProvider<>() {
      @Override
      public KeysetBlobRepository getObject(Object... args) {
        return repo;
      }

      @Override
      public KeysetBlobRepository getIfAvailable() {
        return repo;
      }

      @Override
      public KeysetBlobRepository getIfUnique() {
        return repo;
      }

      @Override
      public KeysetBlobRepository getObject() {
        return repo;
      }
    };
  }
}
