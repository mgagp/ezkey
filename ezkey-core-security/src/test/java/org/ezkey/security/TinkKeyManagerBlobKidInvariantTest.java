/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerBlobKidInvariantTest
 * Description: B3/B4 invariant — no startup path may remove kids from the blob or stay ready with
 *     orphan kids; injected failures leave the blob intact and fail closed.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import java.io.ByteArrayOutputStream;
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
 * Patrick / Christophe B3/B4: load or claim failures must never destroy kids or leave a ready
 * orphan handle.
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
    /** exists=false → insert 0 → find throws (claim path race loser). */
    FIRST_BOOT_RACE_LOSER_LOAD_FAILS,
    /** Same claim-path race as FIRST_BOOT (explicit alias for lot 5). */
    RACE_LOSER_EXISTS_FALSE_INSERT_ZERO_FIND_THROWS,
    /** Opt-in legacy replace with CAS miss — blob kids must survive; fail closed. */
    LEGACY_REPLACE_CAS_FAILS
  }

  static Stream<Arguments> failureModes() {
    return Stream.of(FailureMode.values()).map(Arguments::of);
  }

  @ParameterizedTest(name = "kid invariant: {0}")
  @MethodSource("failureModes")
  @DisplayName(
      "Invariant: injected startup failure never removes kids; fail-closed when not adopted")
  void startupOrMutation_neverRemovesKidsFromBlob(FailureMode mode, @TempDir Path tempDir)
      throws Exception {
    AeadConfig.register();
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository seedRepo = casRepository(sharedBlob);

    Path masterKey = tempDir.resolve("master.key");
    Path peerFile = tempDir.resolve("peer-keyset.json.encrypted");
    Path writerFile = tempDir.resolve("writer-keyset.json.encrypted");
    byte[] masterKeyBytes = TestMasterKeys.write(masterKey);

    long keyK;
    byte[] blobBefore;
    long versionBefore;

    if (mode == FailureMode.LEGACY_REPLACE_CAS_FAILS) {
      KeysetBlob legacy = legacyOuterEncryptedBlob(masterKeyBytes);
      sharedBlob.set(legacy);
      keyK = -1L; // not used for memory assert when fail-closed
      blobBefore = legacy.getKeysetData().clone();
      versionBefore = legacy.getVersion();
    } else {
      TinkKeyManager peer = initializeManager(masterKey, peerFile, seedRepo);
      try {
        Files.copy(peerFile, writerFile);
        keyK = peer.addKeyWithoutPromotion();
        peer.promoteToPrimary(keyK);
        assertTrue(peer.getAllKeyIds().contains(keyK), "setup: peer blob must contain K");
      } finally {
        peer.shutdownKeysetCheckExecutor();
      }
      blobBefore = sharedBlob.get().getKeysetData().clone();
      versionBefore = sharedBlob.get().getVersion();
    }

    KeysetBlobRepository flaky = repositoryForMode(mode, sharedBlob);
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
        // non-POSIX
      }
      props.setMasterKeyFile(wrongMaster.toString());
    }
    if (mode == FailureMode.STALE_UNREADABLE_FILE) {
      Files.writeString(writerFile, "not-a-valid-keyset");
    }
    if (mode == FailureMode.LEGACY_REPLACE_CAS_FAILS) {
      props.getKeyset().setAllowLegacyEnvelopeReplace(true);
    }

    TinkKeyManager writer = new TinkKeyManager(props, fixedProvider(flaky));
    try {
      writer.initialize();

      assertTrue(
          Arrays.equals(blobBefore, sharedBlob.get().getKeysetData()),
          "FAILURE ["
              + mode
              + "]: blob bytes changed — kids destroyed (version now "
              + sharedBlob.get().getVersion()
              + ", was "
              + versionBefore
              + ")");
      assertTrue(
          sharedBlob.get().getVersion() == versionBefore,
          "FAILURE [" + mode + "]: blob version advanced (destructive CAS)");

      if (mode == FailureMode.FIRST_BOOT_RACE_LOSER_LOAD_FAILS
          || mode == FailureMode.RACE_LOSER_EXISTS_FALSE_INSERT_ZERO_FIND_THROWS) {
        verify(flaky, atLeastOnce()).insertSingletonIfAbsent(any(), any(), any());
        assertFalse(
            writer.isInitialized(),
            "FAILURE [" + mode + "]: race loser must fail closed (not ready with orphan kids)");
      }
      if (mode == FailureMode.LEGACY_REPLACE_CAS_FAILS) {
        assertFalse(
            writer.isInitialized(), "FAILURE [" + mode + "]: legacy CAS miss must fail closed");
      }

      if (writer.isInitialized() && keyK != -1L) {
        assertTrue(
            writer.getAllKeyIds().contains(keyK),
            "FAILURE [" + mode + "]: memory missing K while initialized (memory != blob)");
      }
    } finally {
      writer.shutdownKeysetCheckExecutor();
    }
  }

  @SuppressWarnings("deprecation")
  private static KeysetBlob legacyOuterEncryptedBlob(byte[] masterKey) throws Exception {
    Aead masterAead = new AesGcmJce(masterKey);
    KeysetHandle legacyHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
    byte[] keysetJson;
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      CleartextKeysetHandle.write(legacyHandle, JsonKeysetWriter.withOutputStream(outputStream));
      keysetJson = outputStream.toByteArray();
    }
    KeysetBlob blob = new KeysetBlob(masterAead.encrypt(keysetJson, null), "TEST_LEGACY");
    blob.setVersion(3L);
    return blob;
  }

  private static KeysetBlobRepository repositoryForMode(
      FailureMode mode, AtomicReference<KeysetBlob> sharedBlob) {
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
        // real blob; failure is master key / file
      }
      case FIRST_BOOT_RACE_LOSER_LOAD_FAILS, RACE_LOSER_EXISTS_FALSE_INSERT_ZERO_FIND_THROWS -> {
        // Claim path: exists=false so materialize generates; insert loses; find throws.
        when(repository.keysetExists()).thenReturn(false);
        when(repository.insertSingletonIfAbsent(any(), any(), any())).thenReturn(0);
        when(repository.findKeyset())
            .thenAnswer(
                _ -> {
                  int n = finds.getAndIncrement();
                  if (n < 2) {
                    return Optional.empty();
                  }
                  throw new QueryTimeoutException("race loser cannot load winner row");
                });
      }
      case LEGACY_REPLACE_CAS_FAILS -> {
        when(repository.updateKeysetIfVersionMatches(any(), any(), any(), anyLong())).thenReturn(0);
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
