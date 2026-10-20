/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerBlobKidInvariantTest
 * Description: B3 invariant — no startup/mutation path may remove kids from the blob except
 *     explicit removal; injected failures must leave the blob intact or fail closed.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.QueryTimeoutException;

/**
 * Patrick / Christophe B3: load failures must not be treated as ABSENT and must never CAS-replace a
 * live blob that still holds kids.
 *
 * @since 2026
 */
class TinkKeyManagerBlobKidInvariantTest {

  enum FailureMode {
    FIND_EXCEPTIONS_1,
    FIND_EXCEPTIONS_2,
    FIND_EXCEPTIONS_3,
    FIND_EXCEPTIONS_4,
    FIND_EXCEPTIONS_5,
    FIND_EXCEPTIONS_6,
    FIND_PERSISTENT_FAILURE,
    WRONG_MASTER_KEY,
    STALE_UNREADABLE_FILE,
    FIRST_BOOT_RACE_LOSER_LOAD_FAILS
  }

  static Stream<Arguments> failureModes() {
    return Stream.of(FailureMode.values()).map(Arguments::of);
  }

  @ParameterizedTest(name = "B3 kid invariant: {0}")
  @MethodSource("failureModes")
  @DisplayName(
      "Invariant: injected startup failure never removes kids from the blob (byte-intact or"
          + " fail-closed)")
  void startupOrMutation_neverRemovesKidsFromBlob(FailureMode mode, @TempDir Path tempDir)
      throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository seedRepo = casRepository(sharedBlob);

    Path masterKey = tempDir.resolve("master.key");
    Path peerFile = tempDir.resolve("peer-keyset.json.encrypted");
    Path writerFile = tempDir.resolve("writer-keyset.json.encrypted");

    TinkKeyManager peer = initializeManager(masterKey, peerFile, seedRepo);
    long keyK;
    try {
      Files.copy(peerFile, writerFile);
      keyK = peer.addKeyWithoutPromotion();
      peer.promoteToPrimary(keyK);
      assertTrue(peer.getAllKeyIds().contains(keyK), "setup: peer blob must contain K");
    } finally {
      peer.shutdownKeysetCheckExecutor();
    }

    byte[] blobBefore = sharedBlob.get().getKeysetData().clone();
    long versionBefore = sharedBlob.get().getVersion();

    KeysetBlobRepository flaky = repositoryForMode(mode, sharedBlob, blobBefore, versionBefore);
    TinkProperties props = writerProps(masterKey, writerFile);
    if (mode == FailureMode.WRONG_MASTER_KEY) {
      Path wrongMaster = tempDir.resolve("wrong-master.key");
      byte[] other = new byte[32];
      Arrays.fill(other, (byte) 0x42);
      Files.write(wrongMaster, java.util.Base64.getEncoder().encode(other));
      try {
        Files.setPosixFilePermissions(
            wrongMaster, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
        // non-POSIX temp FS
      }
      props.setMasterKeyFile(wrongMaster.toString());
    }
    if (mode == FailureMode.STALE_UNREADABLE_FILE) {
      Files.writeString(writerFile, "not-a-valid-keyset");
    }

    TinkKeyManager writer = new TinkKeyManager(props, fixedProvider(flaky));
    try {
      writer.initialize();

      assertTrue(
          Arrays.equals(blobBefore, sharedBlob.get().getKeysetData()),
          "B3 FAILURE ["
              + mode
              + "]: blob bytes changed — kids were destroyed (version now "
              + sharedBlob.get().getVersion()
              + ", was "
              + versionBefore
              + ")");
      assertTrue(
          sharedBlob.get().getVersion() == versionBefore,
          "B3 FAILURE [" + mode + "]: blob version advanced (destructive CAS)");

      if (writer.isInitialized()) {
        assertTrue(
            writer.getAllKeyIds().contains(keyK),
            "B3 FAILURE [" + mode + "]: memory missing K while initialized (memory != blob)");
      }
    } finally {
      writer.shutdownKeysetCheckExecutor();
    }
  }

  private static KeysetBlobRepository repositoryForMode(
      FailureMode mode,
      AtomicReference<KeysetBlob> sharedBlob,
      byte[] blobBefore,
      long versionBefore) {
    AtomicInteger finds = new AtomicInteger();
    KeysetBlobRepository repository = casRepository(sharedBlob);

    switch (mode) {
      case FIND_EXCEPTIONS_1,
          FIND_EXCEPTIONS_2,
          FIND_EXCEPTIONS_3,
          FIND_EXCEPTIONS_4,
          FIND_EXCEPTIONS_5,
          FIND_EXCEPTIONS_6 -> {
        int failCount =
            switch (mode) {
              case FIND_EXCEPTIONS_1 -> 1;
              case FIND_EXCEPTIONS_2 -> 2;
              case FIND_EXCEPTIONS_3 -> 3;
              case FIND_EXCEPTIONS_4 -> 4;
              case FIND_EXCEPTIONS_5 -> 5;
              case FIND_EXCEPTIONS_6 -> 6;
              default -> 0;
            };
        when(repository.findKeyset())
            .thenAnswer(
                _ -> {
                  if (finds.getAndIncrement() < failCount) {
                    throw new QueryTimeoutException(
                        "simulated transient findKeyset #" + finds.get());
                  }
                  return Optional.ofNullable(sharedBlob.get());
                });
      }
      case FIND_PERSISTENT_FAILURE ->
          when(repository.findKeyset())
              .thenThrow(new QueryTimeoutException("persistent findKeyset failure"));
      case WRONG_MASTER_KEY, STALE_UNREADABLE_FILE -> {
        // repository returns the real blob; failure is master key / file side
      }
      case FIRST_BOOT_RACE_LOSER_LOAD_FAILS -> {
        // Pretend insert lost the race (row exists) but every load fails — must not overwrite.
        when(repository.keysetExists()).thenReturn(true);
        when(repository.insertSingletonIfAbsent(any(), any(), any())).thenReturn(0);
        when(repository.findKeyset())
            .thenThrow(new QueryTimeoutException("race loser cannot load winner row"));
        // Keep update path honest so a destructive CAS would mutate bytes if attempted.
        when(repository.updateKeysetIfVersionMatches(any(), any(), any(), anyLong()))
            .thenAnswer(
                invocation -> {
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
      }
      default -> throw new IllegalStateException("unhandled mode " + mode);
    }
    return repository;
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
