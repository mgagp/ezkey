/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeysetStorageCharacterizationTest
 * Description: Pins current main keyset load/startup/rotation/failure behavior before the
 *              DB-only refactor (#952 / ADR-0016).
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
 * Characterization of keyset storage behavior on {@code main} before the DB-blob-only cutover.
 *
 * <p>Decision pack: {@code
 * product-docs/global/decisions/2026-10-21-keyset-storage-strategy/design.md}. Methods that pin
 * behavior the refactor will invert are marked {@code EXPECTED TO CHANGE} with the design section
 * that owns the target.
 *
 * @since 2026
 */
class KeysetStorageCharacterizationTest {

  @Test
  @DisplayName("StorageMode enum currently exposes FILE, DATABASE, and HYBRID")
  void storageModeEnum_includesFileDatabaseAndHybrid() {
    // EXPECTED TO CHANGE — design §2.2 / §4 (FILE and HYBRID removed)
    Set<String> names =
        Arrays.stream(TinkProperties.Keyset.StorageMode.values())
            .map(Enum::name)
            .collect(Collectors.toSet());
    assertTrue(names.contains("FILE"));
    assertTrue(names.contains("DATABASE"));
    assertTrue(names.contains("HYBRID"));
    assertEquals(3, names.size());
  }

  @Test
  @DisplayName(
      "version-check throttle constant is 5000 ms (tick shorter than typical sync windows)")
  void databaseCheckIntervalMs_isFiveSeconds() throws Exception {
    // Pins invariant-4 baseline (design §3 #4); implementation may keep or bound this value.
    Field field = TinkKeyManager.class.getDeclaredField("DATABASE_CHECK_INTERVAL_MS");
    field.setAccessible(true);
    assertEquals(5000L, field.getLong(null));
  }

  @Test
  @DisplayName("writer DATABASE boot with existing keyset file syncs blob via STARTUP_FILE_SYNC")
  void initialize_writerDatabase_existingFile_syncsBlobFromFile(@TempDir Path tempDir)
      throws Exception {
    // EXPECTED TO CHANGE — design §2.1 / §4 (no file authority; STARTUP_FILE_SYNC removed)
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
      assertTrue(Files.exists(keysetPath));
      assertTrue(storedBlob.get() != null, "STARTUP_FILE_SYNC must persist a blob today");
      verify(repository, times(1)).save(any(KeysetBlob.class));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("writer DATABASE boot generates a keyset file when blob and file are both missing")
  void initialize_writerDatabase_empty_generatesFileAndClaimsBlob(@TempDir Path tempDir)
      throws Exception {
    // EXPECTED TO CHANGE — design §2.1 (generate blob without writing a keyset file)
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
      assertTrue(Files.exists(keysetPath), "today the writer still materializes a keyset file");
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
  @DisplayName("FILE mode non-writer loads an existing keyset file without touching the repository")
  void initialize_fileMode_nonWriter_loadsExistingFile(@TempDir Path tempDir) throws Exception {
    // EXPECTED TO CHANGE — design §2.2 / §4 (FILE mode removed)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    long expectedPrimary = writeNativeEncryptedKeysetFile(keysetPath, masterKey);

    TinkProperties properties = baseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.FILE);
    properties.getKeyset().setWriter(false);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(null));
    try {
      manager.initialize();
      assertTrue(manager.isInitialized());
      assertEquals(expectedPrimary, manager.getCurrentPrimaryKeyId());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "legacy outer-encrypted DB blob is overwritten with a Tink-native envelope on writer boot")
  void initialize_legacyDatabaseBlob_overwritesWithNativeEnvelope(@TempDir Path tempDir)
      throws Exception {
    // EXPECTED TO CHANGE — design §3 invariant #8 / §2.3 (legacy fails startup; no overwrite)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    LegacyDatabaseKeyset legacy = legacyEncryptedDatabaseKeyset(masterKey);
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>(legacy.blob());

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            versionedRepository(storedBlob),
            true);

    try {
      assertTrue(manager.isInitialized());
      assertNotEquals(legacy.primaryKeyId(), manager.getCurrentPrimaryKeyId());
      String blobJson = new String(storedBlob.get().getKeysetData(), StandardCharsets.UTF_8);
      assertTrue(blobJson.contains("\"encryptedKeyset\""));
      assertTrue(blobJson.contains("\"keysetInfo\""));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("failed DB load leaves an existing keyset file on disk (no B6 delete on main)")
  void initialize_tamperedBlob_doesNotDeleteExistingKeysetFile(@TempDir Path tempDir)
      throws Exception {
    // Pins invariant-7 baseline on main (design §3 #7); B6 clearKeysetHandleFailClosed is absent.
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    writeNativeEncryptedKeysetFile(keysetPath, masterKey);

    KeysetBlob tampered =
        new KeysetBlob("not-a-valid-envelope".getBytes(StandardCharsets.UTF_8), "TAMPER");
    tampered.setVersion(1L);
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>(tampered);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            versionedRepository(storedBlob),
            true);

    try {
      assertTrue(Files.exists(keysetPath), "runtime must not delete the keyset file today");
      assertTrue(
          manager.isInitialized(), "writer falls back to the existing file after unloadable blob");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName(
      "DataAccessException on findKeyset does not invent a second blob when a file already exists")
  void initialize_writer_dbFindThrows_fallsBackToExistingFile(@TempDir Path tempDir)
      throws Exception {
    // EXPECTED TO CHANGE — design §3 invariant #6 (DB error must never generate; also no file
    // fallback)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = TestMasterKeys.write(masterKeyPath);
    long expectedPrimary = writeNativeEncryptedKeysetFile(keysetPath, masterKey);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset())
        .thenThrow(new DataAccessResourceFailureException("simulated DB outage"));
    when(repository.keysetExists()).thenReturn(false);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            repository,
            true);

    try {
      assertTrue(manager.isInitialized());
      assertEquals(expectedPrimary, manager.getCurrentPrimaryKeyId());
      assertTrue(Files.exists(keysetPath));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("rotateKey persists a new blob version and updates the in-memory primary kid")
  void rotateKey_persistsNewBlobVersion(@TempDir Path tempDir) throws Exception {
    // Preserve intent of design §2.3 online rotation; CAS loser semantics still to be hardened.
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
  @DisplayName("HYBRID mode reloads when the database blob version advances")
  void hybrid_databaseVersionBump_reloadsKeyset(@TempDir Path tempDir) throws Exception {
    // EXPECTED TO CHANGE — design §4 (HYBRID removed; DB-only reload remains)
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath, keysetPath, TinkProperties.Keyset.StorageMode.HYBRID, repository, true);
    long primaryAtStartup = manager.getCurrentPrimaryKeyId();

    manager.saveKeysetToDatabase("TEST_INIT");
    KeysetBlob stalePrimaryBlob = copyBlob(storedBlob.get());

    long rotatedPrimary = manager.rotateKey();
    manager.saveKeysetToDatabase("TEST_ROTATE");
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
  @DisplayName("EncryptionService.decrypt returns the original ciphertext when AEAD decrypt fails")
  void decrypt_whenAeadFails_returnsOriginalCiphertext() throws Exception {
    // EXPECTED TO CHANGE — design §2.7 / §3 invariant #5 (fail closed on unknown kid / AEAD
    // failure)
    TinkKeyManager keyManager = Mockito.mock(TinkKeyManager.class);
    Aead aead = Mockito.mock(Aead.class);
    when(keyManager.isInitialized()).thenReturn(true);
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

    assertEquals(encryptedValue, encryptionService.decrypt(encryptedValue));
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
