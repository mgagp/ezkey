/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerH3CasFirstTest
 * Description: H3 — CAS must commit before memory/file; B1 — CAS alone rejects stale overwrite.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.ezkey.security.exception.KeysetConcurrentModificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Review batch on #936: H3 fail-open (CAS before memory/file commit) and Patrick B1 (CAS alone).
 *
 * <p>Write RED on {@code e726866f}, then GREEN after CAS-first ordering.
 *
 * @since 2026
 */
class TinkKeyManagerH3CasFirstTest {

  @Test
  @DisplayName("H3: CAS miss on rotate leaves primary, kids, and file unchanged")
  void rotate_whenCasMisses_leavesMemoryAndFileUnchanged(@TempDir Path tempDir) throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    AtomicBoolean forceCasMiss = new AtomicBoolean(false);
    KeysetBlobRepository repository = casRepository(sharedBlob, forceCasMiss);
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");

    TinkKeyManager manager =
        initializeManager(tempDir.resolve("master.key"), keysetPath, repository, true);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      List<Long> kidsBefore = new ArrayList<>(manager.getAllKeyIds());
      byte[] fileBefore = Files.readAllBytes(keysetPath);

      forceCasMiss.set(true);
      assertThrows(KeysetConcurrentModificationException.class, manager::rotateKey);

      assertEquals(primaryBefore, manager.getCurrentPrimaryKeyId(), "H3: primary must not change");
      assertEquals(kidsBefore, manager.getAllKeyIds(), "H3: kid list must not change");
      assertTrue(
          java.util.Arrays.equals(fileBefore, Files.readAllBytes(keysetPath)),
          "H3: keyset file must be unchanged after CAS miss");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("H3: CAS miss on promote leaves primary, kids, and file unchanged")
  void promote_whenCasMisses_leavesMemoryAndFileUnchanged(@TempDir Path tempDir) throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    AtomicBoolean forceCasMiss = new AtomicBoolean(false);
    KeysetBlobRepository repository = casRepository(sharedBlob, forceCasMiss);
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");

    TinkKeyManager manager =
        initializeManager(tempDir.resolve("master.key"), keysetPath, repository, true);
    try {
      forceCasMiss.set(false);
      long pending = manager.addKeyWithoutPromotion();
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      List<Long> kidsBefore = new ArrayList<>(manager.getAllKeyIds());
      byte[] fileBefore = Files.readAllBytes(keysetPath);

      forceCasMiss.set(true);
      assertThrows(
          KeysetConcurrentModificationException.class, () -> manager.promoteToPrimary(pending));

      assertEquals(primaryBefore, manager.getCurrentPrimaryKeyId(), "H3: primary must not change");
      assertEquals(kidsBefore, manager.getAllKeyIds(), "H3: kid list must not change");
      assertTrue(
          java.util.Arrays.equals(fileBefore, Files.readAllBytes(keysetPath)),
          "H3: keyset file must be unchanged after CAS miss");
      assertFalse(
          manager.getCurrentPrimaryKeyId() == pending,
          "H3: pending must not become in-memory primary after failed CAS");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "B1: stale rotate with reload-before-mutation=false → typed CAS conflict, blob unchanged")
  void staleRotate_withoutReload_casRejectsAndBlobUnchanged(@TempDir Path tempDir)
      throws Exception {
    AtomicReference<KeysetBlob> sharedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = casRepository(sharedBlob, new AtomicBoolean(false));

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
      long versionBeforePeer = sharedBlob.get().getVersion();

      long keyK = api1.addKeyWithoutPromotion();
      api1.promoteToPrimary(keyK);
      long versionAfterPeer = sharedBlob.get().getVersion();
      byte[] blobAfterPeer = sharedBlob.get().getKeysetData().clone();
      assertTrue(versionAfterPeer > versionBeforePeer);
      assertFalse(api2.getAllKeyIds().contains(keyK), "precondition: api2 memory stale");

      // Stale api2 mutates without reload — CAS must reject; peer blob (incl. K) preserved.
      assertThrows(KeysetConcurrentModificationException.class, api2::rotateKey);

      assertEquals(
          versionAfterPeer,
          sharedBlob.get().getVersion(),
          "B1: blob version must be unchanged after rejected stale write");
      assertTrue(
          java.util.Arrays.equals(blobAfterPeer, sharedBlob.get().getKeysetData()),
          "B1: blob bytes must still be peer's post-K keyset");
      // Verifier loads K — proves CAS alone prevented overwrite without relying on reload.
      TinkKeyManager verifier =
          initializeManager(
              tempDir.resolve("master.key"),
              tempDir.resolve("verifier-keyset.json.encrypted"),
              repository,
              true);
      try {
        assertTrue(
            verifier.getAllKeyIds().contains(keyK),
            "B1 FAILURE: peer key K lost — CAS alone did not protect the blob");
      } finally {
        verifier.shutdownKeysetCheckExecutor();
      }
    } finally {
      api1.shutdownKeysetCheckExecutor();
      api2.shutdownKeysetCheckExecutor();
    }
  }

  private static KeysetBlobRepository casRepository(
      AtomicReference<KeysetBlob> storedBlob, AtomicBoolean forceCasMiss) {
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
