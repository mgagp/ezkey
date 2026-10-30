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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.KeysetManager;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
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
      when(repository.saveAndFlush(any(KeysetBlob.class)))
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

      org.mockito.stubbing.Answer<KeysetBlob> peerWinsCas =
          invocation -> {
            // Peer CAS wins: replace blob, then fail this writer's save.
            long nextVersion = storedBlob.get().getVersion() + 1;
            peer.blob().setVersion(nextVersion);
            storedBlob.set(peer.blob());
            peerInstalled.set(true);
            throw new OptimisticLockingFailureException("simulated concurrent CAS loss");
          };
      when(repository.save(any(KeysetBlob.class))).thenAnswer(peerWinsCas);
      when(repository.saveAndFlush(any(KeysetBlob.class))).thenAnswer(peerWinsCas);

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

  @Test
  @DisplayName(
      "probe A / B1: CAS save fails and reload findKeyset fails — live primary unchanged"
          + " (neutralizes install-before-CAS)")
  void probeA_casFailAndReloadFail_livePrimaryUnchanged(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      AtomicBoolean saveAttempted = new AtomicBoolean(false);
      org.mockito.stubbing.Answer<KeysetBlob> casPersistFails =
          invocation -> {
            saveAttempted.set(true);
            throw new DataAccessResourceFailureException("simulated CAS persist failure");
          };
      when(repository.save(any(KeysetBlob.class))).thenAnswer(casPersistFails);
      when(repository.saveAndFlush(any(KeysetBlob.class))).thenAnswer(casPersistFails);
      when(repository.findKeyset())
          .thenAnswer(
              invocation -> {
                if (saveAttempted.get()) {
                  throw new DataAccessResourceFailureException("simulated reload DB outage");
                }
                return Optional.ofNullable(storedBlob.get());
              });

      assertThrows(IllegalStateException.class, manager::rotateKey);
      assertEquals(
          primaryBefore,
          manager.getCurrentPrimaryKeyId(),
          "must not serve a staging primary absent from the blob when reload also fails");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "probe B / B2: peer commits between re-read and save — app version pre-check required"
          + " (realistic repository)")
  void probeB_peerCommitBetweenRereadAndSave_preCheckRequired(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      PeerBlob peer = peerNativeBlob(masterKey, "PEER_BETWEEN_REREAD_AND_SAVE");
      byte[] peerEnvelope = peer.blob().getKeysetData().clone();
      AtomicInteger findsAfterInit = new AtomicInteger(0);
      AtomicBoolean peerInstalledBetween = new AtomicBoolean(false);

      when(repository.findKeyset())
          .thenAnswer(
              invocation -> {
                int n = findsAfterInit.incrementAndGet();
                if (n == 1) {
                  // Mutation re-read — still the boot blob.
                  return Optional.ofNullable(storedBlob.get());
                }
                if (n == 2 && !peerInstalledBetween.get()) {
                  // Save-time find: peer committed after re-read. Hibernate @Version on this
                  // freshly loaded entity would match; only app version==databaseKeysetVersion
                  // pre-check rejects the overwrite (B2).
                  peer.blob().setVersion(storedBlob.get().getVersion() + 1);
                  storedBlob.set(peer.blob());
                  peerInstalledBetween.set(true);
                }
                return Optional.ofNullable(storedBlob.get());
              });

      assertThrows(IllegalStateException.class, manager::rotateKey);
      assertTrue(peerInstalledBetween.get(), "peer must win between re-read and save");
      assertArrayEquals(
          peerEnvelope,
          storedBlob.get().getKeysetData(),
          "without app version==read-version pre-check, realistic save would overwrite the peer");
      assertEquals(peer.primaryKeyId(), manager.getCurrentPrimaryKeyId());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("probe C / B3: promoteToPrimary re-reads under lock (peer kid adopted)")
  void probeC_promoteToPrimary_reReadsUnderLock(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      // Build a peer blob that contains an extra pending key, then promote that kid.
      AeadConfig.register();
      Aead masterAead = new AesGcmJce(masterKey);
      KeysetHandle base = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
      var managerTink = KeysetManager.withKeysetHandle(base).add(KeyTemplates.get("AES256_GCM"));
      KeysetHandle withPending = managerTink.getKeysetHandle();
      long pendingKid =
          withPending.getKeysetInfo().getKeyInfoList().stream()
              .mapToLong(k -> Integer.toUnsignedLong(k.getKeyId()))
              .filter(id -> id != Integer.toUnsignedLong(withPending.getPrimary().getId()))
              .findFirst()
              .orElseThrow();
      String encrypted =
          TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
              withPending, masterAead, new byte[0], RegistryConfiguration.get());
      KeysetBlob peerBlob = new KeysetBlob(encrypted.getBytes(StandardCharsets.UTF_8), "PEER_ADD");
      peerBlob.setVersion(storedBlob.get().getVersion() + 1);
      storedBlob.set(peerBlob);

      long oldPrimary =
          assertDoesNotThrow(
              () -> manager.promoteToPrimary(pendingKid),
              "re-read under lock must adopt peer keyset before promote; without re-read the kid"
                  + " is absent");
      assertNotEquals(pendingKid, oldPrimary);
      assertEquals(pendingKid, manager.getCurrentPrimaryKeyId());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("probe D / B3: addKeyWithoutPromotion re-reads under lock (peer kids preserved)")
  void probeD_addKeyWithoutPromotion_reReadsUnderLock(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      PeerBlob peer = peerNativeBlob(masterKey, "PEER_ROTATION_BEFORE_ADD");
      long peerVersion = storedBlob.get().getVersion() + 1;
      peer.blob().setVersion(peerVersion);
      storedBlob.set(peer.blob());
      long peerPrimary = peer.primaryKeyId();

      long addedKid = assertDoesNotThrow(manager::addKeyWithoutPromotion);
      assertNotEquals(peerPrimary, addedKid);
      assertEquals(
          peerPrimary,
          manager.getCurrentPrimaryKeyId(),
          "add without promotion must keep peer primary after re-read");
      assertTrue(
          manager.getAllKeyIds().contains(peerPrimary),
          "peer primary kid must remain in the envelope after add");
      assertTrue(manager.getAllKeyIds().contains(addedKid));
      assertTrue(storedBlob.get().getVersion() > peerVersion);
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("minor: DB error during mutation re-read fails closed (no save, primary unchanged)")
  void mutationReread_dbError_failsClosed(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      byte[] envelopeBefore = storedBlob.get().getKeysetData().clone();
      when(repository.findKeyset())
          .thenThrow(new DataAccessResourceFailureException("simulated re-read outage"));

      IllegalStateException thrown = assertThrows(IllegalStateException.class, manager::rotateKey);
      assertTrue(thrown.getMessage().contains("database error re-reading blob"));
      assertEquals(primaryBefore, manager.getCurrentPrimaryKeyId());
      assertArrayEquals(envelopeBefore, storedBlob.get().getKeysetData());
      verify(repository, never()).save(any(KeysetBlob.class));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("minor: tick DB error keeps cached keyset (M11)")
  void tick_dbError_keepsCachedKeyset(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      when(repository.findVersion())
          .thenThrow(new DataAccessResourceFailureException("simulated tick outage"));

      assertDoesNotThrow(manager::runKeysetVersionCheckForTests);
      assertEquals(primaryBefore, manager.getCurrentPrimaryKeyId());
      assertTrue(manager.isInitialized());
      assertFalse(manager.isKeysetReloadDegraded());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "minor: tick reload-step DB error keeps cached keyset, logs WARN, no partial install")
  void tick_reloadStep_dbError_keepsCachedKeyset(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    Logger tinkLogger = (Logger) LoggerFactory.getLogger(TinkKeyManager.class);
    ListAppender<ILoggingEvent> logCapture = new ListAppender<>();
    logCapture.start();
    tinkLogger.addAppender(logCapture);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      long versionBefore = manager.getDatabaseKeysetVersion();
      byte[] envelopeBefore = storedBlob.get().getKeysetData().clone();

      // Version check succeeds and advances → enter reload; findKeyset fails on reload step.
      when(repository.findVersion()).thenReturn(Optional.of(versionBefore + 1));
      when(repository.findKeyset())
          .thenThrow(new DataAccessResourceFailureException("simulated tick reload outage"));

      assertDoesNotThrow(manager::runKeysetVersionCheckForTests);
      assertEquals(
          primaryBefore,
          manager.getCurrentPrimaryKeyId(),
          "reload-step DB error must keep the last blob-backed primary");
      assertEquals(
          versionBefore,
          manager.getDatabaseKeysetVersion(),
          "reload-step DB error must not partially install a newer version");
      assertArrayEquals(envelopeBefore, storedBlob.get().getKeysetData());
      assertTrue(manager.isInitialized());
      assertFalse(
          manager.isKeysetReloadDegraded(),
          "DataAccessException on reload is kept-cache WARN, not KEYSET_RELOAD_DEGRADED");
      verify(repository, never()).save(any(KeysetBlob.class));
      assertTrue(
          logCapture.list.stream()
              .anyMatch(
                  event ->
                      event.getLevel() == Level.WARN
                          && event.getFormattedMessage().contains("Failed to reload keyset")
                          && event.getFormattedMessage().contains("simulated tick reload outage")),
          "reload-step DB error must be surfaced in logs");
    } finally {
      tinkLogger.detachAppender(logCapture);
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("minor: tick unloadable peer blob keeps cache and signals degraded health")
  void tick_unloadablePeerBlob_keepsCacheAndSignalsDegraded(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager = initializeManager(masterKeyPath, keysetPath, repository, true);
    try {
      long primaryBefore = manager.getCurrentPrimaryKeyId();
      LegacyDatabaseKeyset legacy = legacyEncryptedDatabaseKeyset(masterKey);
      legacy.blob().setVersion(storedBlob.get().getVersion() + 1);
      storedBlob.set(legacy.blob());

      assertDoesNotThrow(manager::runKeysetVersionCheckForTests);
      assertEquals(primaryBefore, manager.getCurrentPrimaryKeyId());
      assertTrue(manager.isInitialized());
      assertTrue(
          manager.isKeysetReloadDegraded(),
          "unloadable peer during tick must be explicitly signalled (not silent)");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "minor: non-writer bootstrap fails closed when legacy blob appears during wait (M13)"
          + " — assert failure and cause")
  void invariant8_nonWriter_legacyBlobDuringWait_failsClosed(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    LegacyDatabaseKeyset legacy = legacyEncryptedDatabaseKeyset(masterKey);
    byte[] originalBytes = legacy.blob().getKeysetData().clone();
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    AtomicInteger finds = new AtomicInteger(0);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.keysetExists()).thenAnswer(_ -> storedBlob.get() != null);
    when(repository.findVersion())
        .thenAnswer(_ -> Optional.ofNullable(storedBlob.get()).map(KeysetBlob::getVersion));
    when(repository.findKeyset())
        .thenAnswer(
            invocation -> {
              // First finds empty (enter wait); then legacy appears (must fail closed, M13).
              if (finds.incrementAndGet() >= 2) {
                storedBlob.set(legacy.blob());
              }
              return Optional.ofNullable(storedBlob.get());
            });

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(false);
    properties.getKeyset().setBootstrapWait(Duration.ofMillis(120));
    properties.getKeyset().setBootstrapPollInterval(Duration.ofMillis(20));

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      IllegalStateException thrown = assertThrows(IllegalStateException.class, manager::initialize);
      assertTrue(
          thrown.getMessage().contains("legacy or tampered envelope")
              || thrown.getMessage().contains("cannot be parsed"),
          "non-writer must fail closed with the unloadable-blob message, got: "
              + thrown.getMessage());
      assertNotNull(thrown.getCause(), "legacy parse failure must carry the Tink cause");
      assertInstanceOf(
          GeneralSecurityException.class,
          thrown.getCause(),
          "cause must be the Tink parse/decrypt failure, not a generic wrapper");
      assertFalse(manager.isInitialized());
      assertArrayEquals(originalBytes, storedBlob.get().getKeysetData());
      verify(repository, never()).save(any(KeysetBlob.class));
      verify(repository, never()).insertSingletonIfAbsent(any(), any(), any());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("minor #726: bootstrap-wait elapses then required encryption fails closed")
  void bootstrapWait_elapsed_requiredFailsClosed(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenReturn(Optional.empty());
    when(repository.keysetExists()).thenReturn(false);
    when(repository.findVersion()).thenReturn(Optional.empty());

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(false);
    properties.getKeyset().setBootstrapWait(Duration.ofMillis(60));
    properties.getKeyset().setBootstrapPollInterval(Duration.ofMillis(15));

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      long started = System.nanoTime();
      IllegalStateException thrown = assertThrows(IllegalStateException.class, manager::initialize);
      long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
      assertTrue(
          thrown.getMessage().contains("required") || thrown.getMessage().contains("failed"));
      assertFalse(manager.isInitialized());
      assertTrue(
          elapsedMs >= 40,
          "must wait near bootstrap-wait before failing (elapsed=" + elapsedMs + ")");
      verify(repository, atLeastOnce()).findKeyset();
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("minor #726: bootstrap-poll-interval drives repeated findKeyset polls")
  void bootstrapPollInterval_drivesRepeatedFinds(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenReturn(Optional.empty());
    when(repository.keysetExists()).thenReturn(false);

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(false);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(false);
    properties.getKeyset().setBootstrapWait(Duration.ofMillis(90));
    properties.getKeyset().setBootstrapPollInterval(Duration.ofMillis(25));

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      manager.initialize();
      assertFalse(manager.isInitialized());
      // Initial find + polls within the wait window (at least 3 total).
      verify(repository, Mockito.atLeast(3)).findKeyset();
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("minor #726: AEAD recovery succeeds on reload retry N (maxReloadRetries)")
  void maxReloadRetries_succeedsOnRetryN() throws Exception {
    TinkProperties props = new TinkProperties();
    props.setEnabled(true);
    props.getKeyset().setMaxReloadRetries(2);

    ObjectProvider<TinkProperties> propertiesProvider = Mockito.mock(ObjectProvider.class);
    ObjectProvider<EncryptionKeyRepository> keyRepositoryProvider =
        Mockito.mock(ObjectProvider.class);
    when(propertiesProvider.getIfAvailable()).thenReturn(props);
    when(keyRepositoryProvider.getIfAvailable()).thenReturn(null);

    TinkKeyManager mockManager = Mockito.mock(TinkKeyManager.class);
    Aead aead = Mockito.mock(Aead.class);
    when(mockManager.isInitialized()).thenReturn(true);
    when(mockManager.isDatabaseStorageAvailable()).thenReturn(true);
    when(mockManager.forceReloadFromDatabase()).thenReturn(true);
    when(mockManager.getAeadPrimitive()).thenReturn(aead);
    // Long enough to pass EncryptionService minimum Base64 ciphertext length gate.
    byte[] ct =
        ("cipher-payload-long-enough-for-tink-aead-min-size-check-0123456789")
            .getBytes(StandardCharsets.UTF_8);
    // First AEAD decrypt fails → recovery reload; second succeeds within maxReloadRetries.
    when(aead.decrypt(any(), any()))
        .thenThrow(new GeneralSecurityException("aeAD fail"))
        .thenReturn("retry-n-payload".getBytes(StandardCharsets.UTF_8));

    EncryptionService retryService =
        new EncryptionService(mockManager, propertiesProvider, keyRepositoryProvider);
    String forged = "ENC:1234567:" + Base64.getEncoder().encodeToString(ct);

    assertEquals("retry-n-payload", retryService.decrypt(forged));
    verify(mockManager, times(1)).forceReloadFromDatabase();
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
    org.mockito.stubbing.Answer<KeysetBlob> hibernateRealisticSave =
        invocation -> {
          // Hibernate-realistic: @Version matches the entity's version to the row.
          // A fresh findKeyset() before save loads the peer version, so @Version alone
          // does not protect against a peer commit between re-read and save (probe B).
          KeysetBlob blob = invocation.getArgument(0);
          KeysetBlob current = storedBlob.get();
          if (current != null
              && blob.getVersion() != null
              && current.getVersion() != null
              && !blob.getVersion().equals(current.getVersion())) {
            throw new OptimisticLockingFailureException("simulated Hibernate @Version conflict");
          }
          long version = current == null ? 1L : current.getVersion() + 1;
          blob.setVersion(version);
          storedBlob.set(blob);
          return blob;
        };
    when(repository.save(any(KeysetBlob.class))).thenAnswer(hibernateRealisticSave);
    when(repository.saveAndFlush(any(KeysetBlob.class))).thenAnswer(hibernateRealisticSave);
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
