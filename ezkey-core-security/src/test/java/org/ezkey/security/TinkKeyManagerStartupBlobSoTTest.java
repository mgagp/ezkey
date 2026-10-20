/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerStartupBlobSoTTest
 * Description: Startup SoT — existing DB blob must not be overwritten by a stale local file.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.QueryTimeoutException;

/**
 * Patrick B2 / Christophe S1: stale local keyset file must not overwrite an existing DB blob at
 * startup (blind CAS via {@code expectedVersion <= 0} / {@code STARTUP_FILE_SYNC}).
 *
 * @since 2026
 */
class TinkKeyManagerStartupBlobSoTTest {

  @Test
  @DisplayName("Startup SoT (a): blob has K, stale file lacks K → writer adopts blob; K preserved")
  void startup_whenBlobHasKAndFileLacksK_adoptsBlobAndKeepsK(@TempDir Path tempDir)
      throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository seedRepo = casRepository(sharedBlob);

    Path masterKey = tempDir.resolve("master.key");
    Path peerFile = tempDir.resolve("peer-keyset.json.encrypted");
    Path staleFile = tempDir.resolve("stale-writer-keyset.json.encrypted");

    TinkKeyManager peer = initializeManager(masterKey, peerFile, seedRepo);
    long keyK;
    try {
      // Capture a file without K, then introduce K only in the blob.
      Files.copy(peerFile, staleFile);
      keyK = peer.addKeyWithoutPromotion();
      peer.promoteToPrimary(keyK);
      assertTrue(peer.getAllKeyIds().contains(keyK));
      assertTrue(sharedBlob.get().getVersion() >= 2);
    } finally {
      peer.shutdownKeysetCheckExecutor();
    }

    byte[] blobBefore = sharedBlob.get().getKeysetData().clone();
    long versionBefore = sharedBlob.get().getVersion();

    // Force the historical bug path: first findKeyset misses while the row exists, so init falls
    // through to materializeWriterKeyset → load stale file → STARTUP_FILE_SYNC blind CAS.
    AtomicInteger finds = new AtomicInteger();
    KeysetBlobRepository repository = casRepository(sharedBlob);
    when(repository.findKeyset())
        .thenAnswer(
            _ -> {
              if (finds.getAndIncrement() == 0) {
                return Optional.empty();
              }
              return Optional.ofNullable(sharedBlob.get());
            });

    TinkKeyManager writer =
        new TinkKeyManager(writerProps(masterKey, staleFile), fixedProvider(repository));
    try {
      writer.initialize();
      assertTrue(writer.isInitialized(), "writer must initialize from blob SoT");
      assertTrue(
          writer.getAllKeyIds().contains(keyK),
          "Startup SoT FAILURE: memory missing peer key K after writer start");
      assertEquals(
          versionBefore,
          sharedBlob.get().getVersion(),
          "Startup SoT FAILURE: blob version changed (stale file overwrote DB)");
      assertTrue(
          java.util.Arrays.equals(blobBefore, sharedBlob.get().getKeysetData()),
          "Startup SoT FAILURE: blob bytes changed — rotated key K was lost");
      assertEquals(
          keyK,
          writer.getCurrentPrimaryKeyId(),
          "Startup SoT: memory primary must match blob primary (K)");
    } finally {
      writer.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("Startup SoT (b): transient DB load failure must not push stale file over the blob")
  void startup_whenDbLoadFailsTransiently_doesNotOverwriteBlobWithFile(@TempDir Path tempDir)
      throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository base = casRepository(sharedBlob);

    Path masterKey = tempDir.resolve("master.key");
    Path peerFile = tempDir.resolve("peer-keyset.json.encrypted");
    Path staleFile = tempDir.resolve("stale-writer-keyset.json.encrypted");

    TinkKeyManager peer = initializeManager(masterKey, peerFile, base);
    long keyK;
    try {
      Files.copy(peerFile, staleFile);
      keyK = peer.addKeyWithoutPromotion();
      peer.promoteToPrimary(keyK);
    } finally {
      peer.shutdownKeysetCheckExecutor();
    }

    byte[] blobBefore = sharedBlob.get().getKeysetData().clone();
    long versionBefore = sharedBlob.get().getVersion();

    AtomicInteger findCalls = new AtomicInteger();
    KeysetBlobRepository flaky = Mockito.mock(KeysetBlobRepository.class);
    when(flaky.keysetExists()).thenAnswer(_ -> sharedBlob.get() != null);
    when(flaky.findKeyset())
        .thenAnswer(
            _ -> {
              if (findCalls.getAndIncrement() == 0) {
                throw new QueryTimeoutException("simulated transient DB failure at startup");
              }
              return Optional.ofNullable(sharedBlob.get());
            });
    when(flaky.findVersion())
        .thenAnswer(_ -> Optional.ofNullable(sharedBlob.get()).map(KeysetBlob::getVersion));
    when(flaky.insertSingletonIfAbsent(any(), any(), any())).thenReturn(0);
    when(flaky.updateKeysetIfVersionMatches(any(), any(), any(), anyLong()))
        .thenAnswer(
            invocation -> {
              // If production still blind-writes, this would mutate the blob — fail the test.
              long expected = invocation.getArgument(3);
              KeysetBlob current = sharedBlob.get();
              if (current == null || current.getVersion() != expected) {
                return 0;
              }
              KeysetBlob updated =
                  new KeysetBlob(invocation.getArgument(0), invocation.getArgument(2));
              updated.setLastUpdatedAt(invocation.getArgument(1));
              updated.setVersion(expected + 1);
              sharedBlob.set(updated);
              return 1;
            });

    TinkProperties props = writerProps(masterKey, staleFile);
    props.setRequired(false);
    TinkKeyManager writer = new TinkKeyManager(props, fixedProvider(flaky));
    try {
      writer.initialize();

      assertEquals(
          versionBefore,
          sharedBlob.get().getVersion(),
          "Startup SoT (b) FAILURE: blob version changed after transient DB failure");
      assertTrue(
          java.util.Arrays.equals(blobBefore, sharedBlob.get().getKeysetData()),
          "Startup SoT (b) FAILURE: stale file was pushed to DB after transient load failure");
      verify(flaky, never()).updateKeysetIfVersionMatches(any(), any(), any(), anyLong());
      // Fail-closed: may leave encryption uninitialized rather than sync file→DB.
      if (writer.isInitialized()) {
        assertTrue(
            writer.getAllKeyIds().contains(keyK),
            "If init recovered, memory must still match blob (contain K)");
      }
    } finally {
      writer.shutdownKeysetCheckExecutor();
    }
  }

  private static KeysetBlobRepository casRepository(AtomicReference<KeysetBlob> storedBlob) {
    AtomicBoolean forceCasMiss = new AtomicBoolean(false);
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
              if (forceCasMiss.get()) {
                return 0;
              }
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

  private static TinkProperties writerProps(Path masterKeyPath, Path keysetPath) {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(false);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);
    properties.getKeyset().setReloadBeforeMutation(true);
    properties.getRotation().setBackupBeforeRotation(false);
    return properties;
  }

  private static TinkKeyManager initializeManager(
      Path masterKeyPath, Path keysetPath, KeysetBlobRepository repository) throws Exception {
    if (!masterKeyPath.toFile().exists()) {
      TestMasterKeys.write(masterKeyPath);
    }
    TinkKeyManager manager =
        new TinkKeyManager(writerProps(masterKeyPath, keysetPath), fixedProvider(repository));
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
