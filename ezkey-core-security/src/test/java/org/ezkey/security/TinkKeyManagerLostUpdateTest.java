/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerLostUpdateTest
 * Description: H1 (#920) lost-update proof — stale HA writer must not drop a peer-introduced key.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.Aead;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Proves H1 (#920): without versioned reload + CAS, a stale writer overwriting the blob after a
 * peer added key K drops K from the keyset (undecryptable data risk).
 *
 * <p>Brief §4 criterion 5 — write red on main first, then green after Option A.
 *
 * @since 2026
 */
class TinkKeyManagerLostUpdateTest {

  @Test
  @DisplayName(
      "H1 lost update: stale api-2 rotate must not drop peer-introduced key K (#920 / brief §4.5)")
  void staleWriterRotate_mustNotDropPeerIntroducedKey(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = sharedVersionedRepository(sharedBlob);

    TinkKeyManager api1 =
        initializeManager(masterKeyPath, tempDir.resolve("api1-keyset.json.encrypted"), repository);
    TinkKeyManager api2 =
        initializeManager(masterKeyPath, tempDir.resolve("api2-keyset.json.encrypted"), repository);

    try {
      // Both start synced on the shared blob.
      assertTrue(api1.getDatabaseKeysetVersion() > 0);
      api2.loadKeysetFromDatabase();
      long versionBeforePeerAdd = api2.getDatabaseKeysetVersion();

      // api-1 introduces key K (PENDING) and promotes it so ciphertext uses K.
      long keyK = api1.addKeyWithoutPromotion();
      api1.promoteToPrimary(keyK);
      assertTrue(api1.getAllKeyIds().contains(keyK), "api-1 must hold K after add+promote");
      assertTrue(
          api1.getDatabaseKeysetVersion() > versionBeforePeerAdd,
          "blob version must advance after api-1 mutation");

      Aead aeadUnderK = api1.getAeadPrimitive();
      byte[] ciphertext =
          aeadUnderK.encrypt("h1-lost-update-plaintext".getBytes(StandardCharsets.UTF_8), null);

      // api-2 remains on the pre-K in-memory handle (simulates idle HA replica).
      assertFalse(
          api2.getAllKeyIds().contains(keyK),
          "precondition: api-2 memory must still be stale (no K)");

      // Stale writer mutates (POST /rotate or KEY_ROTATION cron routed to api-2).
      api2.rotateKey();

      // Fresh reader from the shared blob — K must still be present.
      TinkKeyManager verifier =
          initializeManager(
              masterKeyPath, tempDir.resolve("verifier-keyset.json.encrypted"), repository);
      try {
        List<Long> blobKeyIds = verifier.getAllKeyIds();
        assertTrue(
            blobKeyIds.contains(keyK),
            "H1 FAILURE: peer-introduced key K was lost from the blob after stale rotate."
                + " blob keys="
                + blobKeyIds
                + " missing K="
                + Long.toUnsignedString(keyK));

        Aead aeadAfter = verifier.getAeadPrimitive();
        byte[] plaintext =
            assertDoesNotThrow(
                () -> aeadAfter.decrypt(ciphertext, null),
                "ciphertext produced under K must remain decryptable after concurrent mutation");
        assertTrue(
            new String(plaintext, StandardCharsets.UTF_8).equals("h1-lost-update-plaintext"));
      } finally {
        verifier.shutdownKeysetCheckExecutor();
      }
    } finally {
      api1.shutdownKeysetCheckExecutor();
      api2.shutdownKeysetCheckExecutor();
    }
  }

  /**
   * Shared in-memory blob store with CAS {@link KeysetBlobRepository#updateKeysetIfVersionMatches}
   * (Option A / #920). Without CAS, a stale writer would overwrite peer-introduced keys.
   */
  private static KeysetBlobRepository sharedVersionedRepository(
      AtomicReference<KeysetBlob> storedBlob) {
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
    when(repository.save(any(KeysetBlob.class)))
        .thenAnswer(
            invocation -> {
              KeysetBlob blob = invocation.getArgument(0);
              long version = storedBlob.get() == null ? 1L : storedBlob.get().getVersion() + 1;
              blob.setVersion(version);
              storedBlob.set(copyBlob(blob));
              return blob;
            });
    return repository;
  }

  private static KeysetBlob copyBlob(KeysetBlob source) {
    KeysetBlob copy = new KeysetBlob(source.getKeysetData(), source.getUpdatedBy());
    copy.setVersion(source.getVersion());
    copy.setLastUpdatedAt(source.getLastUpdatedAt());
    return copy;
  }

  private static TinkKeyManager initializeManager(
      Path masterKeyPath, Path keysetPath, KeysetBlobRepository repository) throws Exception {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(false);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);
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
