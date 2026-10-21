/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeysetBlobOnlyInvariantTest
 * Description: Mutation-probed invariants 1–8 for ADR-0016 blob-only keyset CAS (#952).
 */
package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

/**
 * Mutation-probed tests for the eight ADR-0016 / #952 blob-only keyset invariants.
 *
 * <p>Each method fails if the corresponding guard is removed or inverted (design §3). Helpers
 * follow {@link KeysetStorageCharacterizationTest} patterns ({@code versionedRepository}, {@link
 * TestMasterKeys}, legacy outer-AEAD envelopes).
 *
 * @since 2026
 */
class KeysetBlobOnlyInvariantTest {

  @Test
  @DisplayName("invariant 1: rotateKey re-reads blob under lock before CAS mutation")
  void invariant1_reReadUnderLock_beforeCasMutation(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      long stalePrimary = manager.getCurrentPrimaryKeyId();
      long versionAtBoot = storedBlob.get().getVersion();

      // Peer wins a rotation while this process still holds a stale in-memory handle.
      PeerBlob peer = peerNativeBlob(masterKey, "PEER_ROTATION");
      long peerVersion = versionAtBoot + 1;
      peer.blob().setVersion(peerVersion);
      storedBlob.set(peer.blob());

      long rotatedPrimary =
          assertDoesNotThrow(
              manager::rotateKey,
              "re-read under lock must adopt the peer blob before staging; "
                  + "without re-read, CAS version mismatch would throw");

      assertNotEquals(stalePrimary, rotatedPrimary);
      assertNotEquals(
          peer.primaryKeyId(),
          rotatedPrimary,
          "successful CAS installs a new primary staged from the re-read peer keyset");
      assertTrue(
          storedBlob.get().getVersion() > peerVersion,
          "CAS after re-read must advance version past the peer blob");
      verify(repository, atLeastOnce()).findKeyset();
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("invariant 2: install live primary only after successful CAS (throw + unchanged)")
  void invariant2_installOnlyAfterSuccessfulCas(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      long versionBefore = storedBlob.get().getVersion();
      byte[] envelopeBefore = storedBlob.get().getKeysetData().clone();

      when(repository.save(any(KeysetBlob.class)))
          .thenThrow(new DataAccessResourceFailureException("simulated CAS/persist failure"));

      IllegalStateException thrown = assertThrows(IllegalStateException.class, manager::rotateKey);
      assertTrue(
          thrown.getMessage().contains("CAS") || thrown.getCause() != null,
          "failure must surface as CAS/persist failure, not silent success");

      assertEquals(
          primaryBefore,
          manager.getCurrentPrimaryKeyId(),
          "live primary must not install when CAS/persist fails");
      assertEquals(versionBefore, storedBlob.get().getVersion());
      assertArrayEquals(envelopeBefore, storedBlob.get().getKeysetData());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("invariant 3: CAS loser discards staging handle and reloads peer blob")
  void invariant3_casLoserDiscardsAndReloads(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      PeerBlob peer = peerNativeBlob(masterKey, "CAS_PEER_WIN");
      AtomicBoolean peerInstalled = new AtomicBoolean(false);

      when(repository.save(any(KeysetBlob.class)))
          .thenAnswer(
              invocation -> {
                // Peer CAS wins: replace blob, then fail this writer's save.
                long nextVersion = storedBlob.get().getVersion() + 1;
                peer.blob().setVersion(nextVersion);
                storedBlob.set(peer.blob());
                peerInstalled.set(true);
                throw new OptimisticLockingFailureException("simulated concurrent CAS loss");
              });

      assertThrows(IllegalStateException.class, manager::rotateKey);
      assertTrue(peerInstalled.get(), "peer blob must be present for the post-CAS reload");
      assertEquals(
          peer.primaryKeyId(),
          manager.getCurrentPrimaryKeyId(),
          "CAS loser must discard staging and reload the peer envelope (no orphan primary)");
      assertNotEquals(primaryBefore, manager.getCurrentPrimaryKeyId());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("invariant 4: version-check tick interval clamps at most five seconds")
  void invariant4_tickIntervalAtMostFiveSeconds(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);
    // Operator misconfigures a long tick; clamp must keep the sync window short.
    properties.getKeyset().setVersionCheckInterval(Duration.ofSeconds(60));

    assertEquals(
        Duration.ofSeconds(5),
        properties.getKeyset().getVersionCheckInterval(),
        "TinkProperties must clamp version-check-interval to ≤ 5 s");

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      manager.initialize();
      assertTrue(
          manager.databaseCheckIntervalMs() <= 5000L,
          "effective tick must be ≤ 5000 ms (mutation: remove clamp → 60000)");
      assertEquals(5000L, manager.databaseCheckIntervalMs());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("invariant 5: unknown kid decrypt fails closed (never returns ciphertext)")
  void invariant5_unknownKidDecryptFailsClosed(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      ObjectProvider<TinkProperties> propertiesProvider = Mockito.mock(ObjectProvider.class);
      ObjectProvider<EncryptionKeyRepository> keyRepositoryProvider =
          Mockito.mock(ObjectProvider.class);
      when(propertiesProvider.getIfAvailable()).thenReturn(null);
      when(keyRepositoryProvider.getIfAvailable()).thenReturn(null);

      EncryptionService encryptionService =
          new EncryptionService(manager, propertiesProvider, keyRepositoryProvider);

      // Ciphertext produced by a foreign keyset (kid absent from the loaded blob).
      AeadConfig.register();
      KeysetHandle foreignHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
      Aead foreignAead = foreignHandle.getPrimitive(RegistryConfiguration.get(), Aead.class);
      long foreignKid = Integer.toUnsignedLong(foreignHandle.getPrimary().getId());
      byte[] foreignCiphertext =
          foreignAead.encrypt("secret-payload".getBytes(StandardCharsets.UTF_8), null);
      String forged =
          "ENC:"
              + Long.toUnsignedString(foreignKid)
              + ":"
              + Base64.getEncoder().encodeToString(foreignCiphertext);

      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () -> encryptionService.decrypt(forged),
              "unknown kid / foreign ciphertext must fail closed (never return ciphertext)");
      assertFalse(
          forged.equals(thrown.getMessage()), "exception message must not be the raw ENC: value");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("invariant 6: DataAccessException on find never generates a new keyset")
  void invariant6_dbErrorNeverGeneratesKeyset(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    writeNativeEncryptedKeysetFile(keysetPath, masterKey);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset())
        .thenThrow(new DataAccessResourceFailureException("simulated DB outage"));
    when(repository.keysetExists()).thenReturn(false);

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(false);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      manager.initialize();
      assertFalse(manager.isInitialized());
      verify(repository, never()).insertSingletonIfAbsent(any(), any(), any());
      verify(repository, never()).save(any(KeysetBlob.class));
      assertTrue(Files.exists(keysetPath), "DB error path must not unlink leftover key material");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "invariant 7: rotate (disable prior kid) leaves envelope; no Files.delete of key material")
  void invariant7_noDeleteTruncateBlob_disableKidLeavesEnvelope(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    // Leftover file from a pre-cutover host — API paths must never unlink it.
    writeNativeEncryptedKeysetFile(keysetPath, masterKey);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      byte[] envelopeBefore = storedBlob.get().getKeysetData().clone();
      long versionBefore = storedBlob.get().getVersion();
      long primaryBefore = manager.getCurrentPrimaryKeyId();

      long newPrimary = manager.rotateKey();
      assertNotEquals(primaryBefore, newPrimary);

      assertNotNull(storedBlob.get().getKeysetData());
      assertTrue(storedBlob.get().getKeysetData().length > 0);
      assertTrue(
          storedBlob.get().getVersion() > versionBefore,
          "disable of prior primary demotes in-keyset; envelope row must remain and advance");
      assertFalse(
          java.util.Arrays.equals(envelopeBefore, storedBlob.get().getKeysetData()),
          "CAS must rewrite envelope bytes, not truncate/delete the row");

      assertTrue(
          Files.exists(keysetPath),
          "API rotation must never Files.delete leftover key material files");

      verify(repository, never()).delete(any());
      verify(repository, never()).deleteById(anyInt());
      verify(repository, never()).deleteAll();
      verify(repository, never()).deleteAllInBatch();
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("invariant 8: legacy outer-AEAD blob fails startup without overwrite")
  void invariant8_legacyBlobFailsStartupWithoutOverwrite(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    LegacyDatabaseKeyset legacy = legacyEncryptedDatabaseKeyset(masterKey);
    byte[] originalBytes = legacy.blob().getKeysetData().clone();
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>(legacy.blob());

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);

    KeysetBlobRepository repository = versionedRepository(storedBlob);
    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      assertThrows(IllegalStateException.class, manager::initialize);
      assertFalse(manager.isInitialized());
      assertArrayEquals(
          originalBytes,
          storedBlob.get().getKeysetData(),
          "legacy unloadable blob must not be overwritten on startup");
      verify(repository, never()).save(any(KeysetBlob.class));
      verify(repository, never()).insertSingletonIfAbsent(any(), any(), any());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  private static TinkProperties baseProperties(Path masterKeyPath, Path keysetPath) {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(false);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    return properties;
  }

  private static TinkKeyManager initializeManager(
      Path masterKeyPath, Path keysetPath, KeysetBlobRepository repository, boolean writer)
      throws Exception {
    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(writer);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    manager.initialize();
    manager.resetDatabaseCheckThrottleForTests();
    return manager;
  }

  private static long writeNativeEncryptedKeysetFile(Path keysetPath, byte[] masterKey)
      throws Exception {
    AeadConfig.register();
    Aead masterAead = new AesGcmJce(masterKey);
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
    String encrypted =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, masterAead, new byte[0], RegistryConfiguration.get());
    Files.writeString(keysetPath, encrypted, StandardCharsets.UTF_8);
    TestMasterKeys.restrictToOwner(keysetPath);
    return Integer.toUnsignedLong(handle.getPrimary().getId());
  }

  private static PeerBlob peerNativeBlob(byte[] masterKey, String updatedBy) throws Exception {
    AeadConfig.register();
    Aead masterAead = new AesGcmJce(masterKey);
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
    String encrypted =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, masterAead, new byte[0], RegistryConfiguration.get());
    KeysetBlob blob = new KeysetBlob(encrypted.getBytes(StandardCharsets.UTF_8), updatedBy);
    blob.setVersion(1L);
    return new PeerBlob(blob, Integer.toUnsignedLong(handle.getPrimary().getId()));
  }

  @SuppressWarnings("deprecation")
  private static LegacyDatabaseKeyset legacyEncryptedDatabaseKeyset(byte[] masterKey)
      throws Exception {
    Aead masterAead = new AesGcmJce(masterKey);
    KeysetHandle legacyHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
    byte[] keysetJson;
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      CleartextKeysetHandle.write(legacyHandle, JsonKeysetWriter.withOutputStream(outputStream));
      keysetJson = outputStream.toByteArray();
    }
    KeysetBlob blob = new KeysetBlob(masterAead.encrypt(keysetJson, null), "TEST_LEGACY");
    blob.setVersion(1L);
    return new LegacyDatabaseKeyset(
        blob, Integer.toUnsignedLong(legacyHandle.getPrimary().getId()));
  }

  private static KeysetBlobRepository versionedRepository(AtomicReference<KeysetBlob> storedBlob) {
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
    when(repository.save(any(KeysetBlob.class)))
        .thenAnswer(
            invocation -> {
              KeysetBlob blob = invocation.getArgument(0);
              long version = storedBlob.get() == null ? 1L : storedBlob.get().getVersion() + 1;
              blob.setVersion(version);
              storedBlob.set(blob);
              return blob;
            });
    return repository;
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

  private record PeerBlob(KeysetBlob blob, long primaryKeyId) {}

  private record LegacyDatabaseKeyset(KeysetBlob blob, long primaryKeyId) {}
}
