/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeysetStorageCharacterizationTest
 * Description: Post-cutover assertions for blob-only keyset behavior (#952 / ADR-0016).
 *              Mutation-probed invariants 1–8 live in KeysetBlobOnlyInvariantTest.
 */
package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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

/**
 * Post-cutover keyset storage assertions for the DB-blob-only cutover (ADR-0016 / #952).
 *
 * <p>Decision pack: {@code
 * product-docs/global/decisions/2026-10-21-keyset-storage-strategy/design.md}. Suite methods that
 * formerly pinned pre-refactor behavior have been flipped to target post-cutover semantics.
 * Mutation-probed invariants 1–8 are in {@link KeysetBlobOnlyInvariantTest}. The remaining {@code
 * EXPECTED TO CHANGE} marker is deferred to GitHub issue 942 ({@code required=false} purge).
 *
 * @since 2026
 */
class KeysetStorageCharacterizationTest {

  @Test
  @DisplayName("StorageMode enum exposes only DATABASE after blob-only cutover")
  void storageModeEnum_databaseOnly() {
    // Flipped — design §2.2 / §4 (FILE and HYBRID removed)
    Set<String> names =
        Arrays.stream(TinkProperties.Keyset.StorageMode.values())
            .map(Enum::name)
            .collect(Collectors.toSet());
    assertTrue(names.contains("DATABASE"));
    assertFalse(names.contains("FILE"));
    assertFalse(names.contains("HYBRID"));
    assertEquals(1, names.size());
  }

  @Test
  @DisplayName(
      "version-check throttle upper bound is 5000 ms (tick shorter than typical sync windows)")
  void databaseCheckIntervalMs_isAtMostFiveSeconds() throws Exception {
    // Pins invariant-4 baseline (design §3 #4 / Q5); hard upper bound ≤ 5 s.
    Field field = TinkKeyManager.class.getDeclaredField("DATABASE_CHECK_INTERVAL_MS");
    field.setAccessible(true);
    assertEquals(5000L, field.getLong(null));
    assertTrue(field.getLong(null) <= 5000L, "DATABASE_CHECK_INTERVAL_MS must be ≤ 5000 ms");
  }

  @Test
  @DisplayName("writer DATABASE boot ignores leftover keyset file and claims a new blob")
  void initialize_writerDatabase_existingFile_ignoresFileAndClaimsBlob(@TempDir Path tempDir)
      throws Exception {
    // Flipped — design §2.1 / §4 (no file authority; STARTUP_FILE_SYNC removed)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    writeNativeEncryptedKeysetFile(keysetPath, masterKey);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            repository,
            true);

    try {
      assertTrue(manager.isInitialized());
      assertTrue(Files.exists(keysetPath), "leftover file may remain on disk");
      assertTrue(storedBlob.get() != null, "writer must claim a blob without file sync");
      verify(repository, times(1)).insertSingletonIfAbsent(any(), any(), any());
      verify(repository, never()).save(any(KeysetBlob.class));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("writer DATABASE boot claims a blob without writing a keyset file")
  void initialize_writerDatabase_empty_claimsBlobWithoutFile(@TempDir Path tempDir)
      throws Exception {
    // Flipped — design §2.1 (generate blob without writing a keyset file)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            repository,
            true);

    try {
      assertTrue(manager.isInitialized());
      assertFalse(Files.exists(keysetPath), "blob-only writer must not materialize a keyset file");
      assertTrue(storedBlob.get() != null);
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("non-writer DATABASE with required=false times out without throwing")
  void initialize_nonWriterDatabase_requiredFalse_timeoutDoesNotThrow(@TempDir Path tempDir)
      throws Exception {
    // EXPECTED TO CHANGE — design §2.5 / #942 (never required=false; explicit failure after wait)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenReturn(Optional.empty());

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(false);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(false);
    properties.getKeyset().setBootstrapWait(Duration.ZERO);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      manager.initialize();
      assertFalse(manager.isInitialized());
      verify(repository, never()).save(any(KeysetBlob.class));
      verify(repository, never()).insertSingletonIfAbsent(any(), any(), any());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("non-writer DATABASE with required=true times out with IllegalStateException")
  void initialize_nonWriterDatabase_requiredTrue_timeoutThrows(@TempDir Path tempDir)
      throws Exception {
    // Preserve: design §2.4 bounded wait then fail closed (crypto-api / peripherals).
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenReturn(Optional.empty());

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(false);
    properties.getKeyset().setBootstrapWait(Duration.ZERO);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      assertThrows(IllegalStateException.class, manager::initialize);
      assertFalse(manager.isInitialized());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("enabled encryption without repository fails when required=true (FILE mode removed)")
  void initialize_requiredWithoutRepository_failsClosed(@TempDir Path tempDir) throws Exception {
    // Flipped — design §2.2 / §4 (FILE mode removed)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(false);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(null));
    try {
      assertThrows(IllegalStateException.class, manager::initialize);
      assertFalse(manager.isInitialized());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("legacy outer-encrypted DB blob fails writer boot without overwrite (invariant 8)")
  void initialize_legacyDatabaseBlob_failsWithoutOverwrite(@TempDir Path tempDir) throws Exception {
    // Flipped — design §3 invariant #8 / §2.3 (legacy fails startup; no overwrite)
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

    TinkKeyManager manager =
        new TinkKeyManager(properties, fixedProvider(versionedRepository(storedBlob)));
    try {
      assertThrows(IllegalStateException.class, manager::initialize);
      assertFalse(manager.isInitialized());
      assertTrue(java.util.Arrays.equals(originalBytes, storedBlob.get().getKeysetData()));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "unloadable blob fails closed and does not delete leftover keyset file (invariant 7)")
  void initialize_tamperedBlob_failsClosedWithoutDeletingFile(@TempDir Path tempDir)
      throws Exception {
    // Flipped — design §3 #7 / #8: fail closed on unloadable blob; never unlink key material files.
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    writeNativeEncryptedKeysetFile(keysetPath, masterKey);

    KeysetBlob tampered =
        new KeysetBlob("not-a-valid-envelope".getBytes(StandardCharsets.UTF_8), "TAMPER");
    tampered.setVersion(1L);
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>(tampered);

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(true);

    TinkKeyManager manager =
        new TinkKeyManager(properties, fixedProvider(versionedRepository(storedBlob)));
    try {
      assertThrows(IllegalStateException.class, manager::initialize);
      assertTrue(Files.exists(keysetPath), "runtime must not delete leftover keyset files");
      assertFalse(manager.isInitialized());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("DataAccessException on findKeyset never generates a new keyset (invariant 6)")
  void initialize_writer_dbFindThrows_leavesUninitialized(@TempDir Path tempDir) throws Exception {
    // Flipped — design §3 invariant #6 (DB error must never generate; also no file fallback)
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
      assertFalse(manager.isInitialized(), "DB find error must leave encryption uninitialized");
      verify(repository, never()).insertSingletonIfAbsent(any(), any(), any());
      verify(repository, never()).save(any(KeysetBlob.class));
      assertTrue(Files.exists(keysetPath), "leftover file must not be deleted");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("rotateKey persists a new blob version and updates the in-memory primary kid")
  void rotateKey_persistsNewBlobVersion(@TempDir Path tempDir) throws Exception {
    // Post-cutover: design §2.3 online rotation via CAS (see KeysetBlobOnlyInvariantTest).
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            repository,
            true);

    try {
      long before = manager.getCurrentPrimaryKeyId();
      long versionBefore = storedBlob.get().getVersion();
      long after = manager.rotateKey();
      assertNotEquals(before, after);
      assertTrue(storedBlob.get().getVersion() > versionBefore);
      assertEquals(after, manager.getCurrentPrimaryKeyId());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("rotateKey discards staging and keeps prior primary when database save fails (CAS)")
  void rotateKey_whenDbSaveFails_discardsStagingAndKeepsPriorPrimary(@TempDir Path tempDir)
      throws Exception {
    // Flipped — design §2.3 / Q4 (NEW CAS: install only after successful CAS; fail closed)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            repository,
            true);

    try {
      long before = manager.getCurrentPrimaryKeyId();
      long versionBefore = storedBlob.get().getVersion();

      when(repository.save(any(KeysetBlob.class)))
          .thenThrow(new DataAccessResourceFailureException("simulated rotation save failure"));

      assertThrows(IllegalStateException.class, manager::rotateKey);

      assertEquals(
          before,
          manager.getCurrentPrimaryKeyId(),
          "CAS failure must not install an orphan in-memory primary");
      assertEquals(
          versionBefore,
          storedBlob.get().getVersion(),
          "blob version must not advance when save throws");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("DATABASE mode reloads when the database blob version advances")
  void database_databaseVersionBump_reloadsKeyset(@TempDir Path tempDir) throws Exception {
    // Preserve: design §2.3 / §3 invariant #4 tick + DB reload (HYBRID removed)
    assertDatabaseVersionBumpReloads(tempDir, TinkProperties.Keyset.StorageMode.DATABASE);
  }

  private static void assertDatabaseVersionBumpReloads(
      Path tempDir, TinkProperties.Keyset.StorageMode storageMode) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager =
        initializeManager(masterKeyPath, keysetPath, storageMode, repository, true);
    long primaryAtStartup = manager.getCurrentPrimaryKeyId();

    manager.saveKeysetToDatabase("TEST_INIT");
    KeysetBlob stalePrimaryBlob = copyBlob(storedBlob.get());

    long rotatedPrimary = manager.rotateKey();
    KeysetBlob rotatedBlob = copyBlob(storedBlob.get());

    assertNotEquals(primaryAtStartup, rotatedPrimary);

    storedBlob.set(stalePrimaryBlob);
    manager.loadKeysetFromDatabase();
    assertEquals(primaryAtStartup, manager.getCurrentPrimaryKeyId());

    storedBlob.set(rotatedBlob);
    manager.setDatabaseKeysetVersionForTests(stalePrimaryBlob.getVersion());
    manager.runKeysetVersionCheckSynchronouslyForTests();

    assertEquals(rotatedPrimary, manager.getCurrentPrimaryKeyId());
    manager.shutdownKeysetCheckExecutor();
  }

  @Test
  @DisplayName("EncryptionService.decrypt fails closed when AEAD decrypt fails (invariant 5)")
  void decrypt_whenAeadFails_throwsIllegalStateException() throws Exception {
    // Flipped — design §2.7 / §3 invariant #5 (fail closed on unknown kid / AEAD failure)
    TinkKeyManager keyManager = Mockito.mock(TinkKeyManager.class);
    Aead aead = Mockito.mock(Aead.class);
    when(keyManager.isInitialized()).thenReturn(true);
    when(keyManager.isDatabaseStorageAvailable()).thenReturn(false);
    when(keyManager.getAeadPrimitive()).thenReturn(aead);
    when(aead.decrypt(any(byte[].class), any()))
        .thenThrow(new GeneralSecurityException("unknown key id / AEAD failure"));

    ObjectProvider<TinkProperties> propertiesProvider = Mockito.mock(ObjectProvider.class);
    ObjectProvider<EncryptionKeyRepository> keyRepositoryProvider =
        Mockito.mock(ObjectProvider.class);
    when(propertiesProvider.getIfAvailable()).thenReturn(null);
    when(keyRepositoryProvider.getIfAvailable()).thenReturn(null);

    EncryptionService encryptionService =
        new EncryptionService(keyManager, propertiesProvider, keyRepositoryProvider);

    String ciphertextBase64 =
        Base64.getEncoder()
            .encodeToString("dummy-ciphertext-20b+".getBytes(StandardCharsets.UTF_8));
    String encryptedValue = "ENC:424242:" + ciphertextBase64;

    assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(encryptedValue));
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
      Path masterKeyPath,
      Path keysetPath,
      TinkProperties.Keyset.StorageMode storageMode,
      KeysetBlobRepository repository,
      boolean writer)
      throws Exception {
    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.getKeyset().setStorageMode(storageMode);
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

  private static KeysetBlob copyBlob(KeysetBlob source) {
    KeysetBlob copy = new KeysetBlob(source.getKeysetData(), source.getUpdatedBy());
    copy.setVersion(source.getVersion());
    return copy;
  }

  private record LegacyDatabaseKeyset(KeysetBlob blob, long primaryKeyId) {}
}
