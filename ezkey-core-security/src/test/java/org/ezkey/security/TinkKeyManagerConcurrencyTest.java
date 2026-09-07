/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerConcurrencyTest
 * Description: Verifies SEC-009 concurrent read-path behavior for getAeadPrimitive().
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.subtle.AesGcmJce;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

/**
 * Concurrency and reload tests for {@link TinkKeyManager} read-path locking (SEC-009).
 *
 * @since 2026
 */
class TinkKeyManagerConcurrencyTest {

  @Test
  @DisplayName("getAeadPrimitive concurrent calls do not block on slow database version check")
  void getAeadPrimitive_concurrentReaders_completeWithoutSerializingOnDbCheck(@TempDir Path tempDir)
      throws Exception {
    TinkFixture fixture = TinkFixture.hybridWithRepository(tempDir, slowVersionRepository(400));

    int threadCount = 12;
    int iterationsPerThread = 25;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger failures = new AtomicInteger(0);

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    try {
      for (int i = 0; i < threadCount; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                for (int j = 0; j < iterationsPerThread; j++) {
                  assertNotNull(fixture.manager().getAeadPrimitive());
                }
              } catch (Exception e) {
                failures.incrementAndGet();
              } finally {
                doneLatch.countDown();
              }
            });
      }

      assertTimeout(
          Duration.ofSeconds(5),
          () -> {
            startLatch.countDown();
            doneLatch.await();
          });

      if (failures.get() > 0) {
        throw new AssertionError("Concurrent getAeadPrimitive failures: " + failures.get());
      }
    } finally {
      executor.shutdownNow();
      fixture.close();
    }
  }

  @Test
  @DisplayName("database version bump reloads keyset under write lock")
  void runKeysetVersionCheck_whenDatabaseVersionIncreases_reloadsKeyset(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    writeMasterKey(masterKeyPath);

    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();
    KeysetBlobRepository repository = versionedRepository(storedBlob);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath, keysetPath, TinkProperties.Keyset.StorageMode.HYBRID, repository);
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
  @DisplayName("addKeyWithoutPromotion allows concurrent getAeadPrimitive readers")
  void addKeyWithoutPromotion_whileConcurrentReaders_allSucceed(@TempDir Path tempDir)
      throws Exception {
    TinkFixture fixture = TinkFixture.fileOnly(tempDir);

    int readerCount = 8;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch readersDone = new CountDownLatch(readerCount);
    AtomicInteger failures = new AtomicInteger(0);

    ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
    try {
      for (int i = 0; i < readerCount; i++) {
        executor.submit(
            () -> {
              try {
                startLatch.await();
                for (int j = 0; j < 50; j++) {
                  assertNotNull(fixture.manager().getAeadPrimitive());
                }
              } catch (Exception e) {
                failures.incrementAndGet();
              } finally {
                readersDone.countDown();
              }
            });
      }

      executor.submit(
          () -> {
            try {
              startLatch.await();
              Thread.sleep(10);
              fixture.manager().addKeyWithoutPromotion();
            } catch (Exception e) {
              failures.incrementAndGet();
            }
          });

      assertTimeout(
          Duration.ofSeconds(10),
          () -> {
            startLatch.countDown();
            readersDone.await();
          });

      if (failures.get() > 0) {
        throw new AssertionError("Concurrent read during key add failures: " + failures.get());
      }
    } finally {
      executor.shutdownNow();
      fixture.close();
    }
  }

  @Test
  @DisplayName("database keyset blob stores a Tink encrypted-keyset envelope")
  void saveKeysetToDatabase_storesTinkEncryptedKeysetEnvelope(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = writeMasterKey(masterKeyPath);
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            versionedRepository(storedBlob));

    try {
      KeysetBlob blob = storedBlob.get();
      assertNotNull(blob);

      String blobJson = new String(blob.getKeysetData(), StandardCharsets.UTF_8);
      assertTrue(blobJson.contains("\"encryptedKeyset\""));
      assertTrue(blobJson.contains("\"keysetInfo\""));
      assertTrue(blobJson.contains("\"primaryKeyId\""));

      Aead masterAead = new AesGcmJce(masterKey);
      assertThrows(
          GeneralSecurityException.class, () -> masterAead.decrypt(blob.getKeysetData(), null));

      KeysetHandle parsed =
          TinkJsonProtoKeysetFormat.parseEncryptedKeyset(
              blobJson, masterAead, new byte[0], RegistryConfiguration.get());
      assertEquals(
          manager.getCurrentPrimaryKeyId(), Integer.toUnsignedLong(parsed.getPrimary().getId()));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("tampered database encrypted-keyset envelope is rejected")
  void loadKeysetFromDatabase_whenEncryptedEnvelopeIsTampered_keepsCurrentKeyset(
      @TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    writeMasterKey(masterKeyPath);
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>();

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            versionedRepository(storedBlob));

    try {
      long primaryKeyId = manager.getCurrentPrimaryKeyId();
      KeysetBlob tamperedBlob = copyBlob(storedBlob.get());
      String tamperedJson =
          tamperEncryptedKeyset(new String(tamperedBlob.getKeysetData(), StandardCharsets.UTF_8));
      tamperedBlob.setKeysetData(tamperedJson.getBytes(StandardCharsets.UTF_8));
      storedBlob.set(tamperedBlob);

      assertFalse(manager.loadKeysetFromDatabase());
      assertEquals(primaryKeyId, manager.getCurrentPrimaryKeyId());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("new encrypted keyset reader loads legacy JSON encrypted files")
  void initialize_whenLegacyEncryptedKeysetFileExists_loadsIt(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = writeMasterKey(masterKeyPath);
    long expectedPrimaryKeyId = writeLegacyEncryptedKeyset(keysetPath, masterKey);

    TinkKeyManager manager =
        initializeManager(masterKeyPath, keysetPath, TinkProperties.Keyset.StorageMode.FILE, null);

    try {
      assertEquals(expectedPrimaryKeyId, manager.getCurrentPrimaryKeyId());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("database keyset reader no longer treats legacy outer-encrypted blobs as current")
  void initialize_whenLegacyDatabaseKeysetBlobExists_createsTinkNativeBlob(@TempDir Path tempDir)
      throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = writeMasterKey(masterKeyPath);
    LegacyDatabaseKeyset legacyKeyset = legacyEncryptedDatabaseKeyset(masterKey);
    AtomicReference<KeysetBlob> storedBlob = new AtomicReference<>(legacyKeyset.blob());

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath,
            keysetPath,
            TinkProperties.Keyset.StorageMode.DATABASE,
            versionedRepository(storedBlob));

    try {
      assertNotEquals(legacyKeyset.primaryKeyId(), manager.getCurrentPrimaryKeyId());
      String blobJson = new String(storedBlob.get().getKeysetData(), StandardCharsets.UTF_8);
      assertTrue(blobJson.contains("\"encryptedKeyset\""));
      assertTrue(blobJson.contains("\"keysetInfo\""));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  private static KeysetBlobRepository slowVersionRepository(int sleepMs) {
    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenReturn(Optional.empty());
    when(repository.findVersion())
        .thenAnswer(
            _ -> {
              Thread.sleep(sleepMs);
              return Optional.of(0L);
            });
    return repository;
  }

  private static KeysetBlobRepository versionedRepository(AtomicReference<KeysetBlob> storedBlob) {
    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenAnswer(_ -> Optional.ofNullable(storedBlob.get()));
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

  private static TinkKeyManager initializeManager(
      Path masterKeyPath,
      Path keysetPath,
      TinkProperties.Keyset.StorageMode storageMode,
      KeysetBlobRepository repository)
      throws Exception {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(false);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    properties.getKeyset().setStorageMode(storageMode);
    properties.getKeyset().setWriter(true);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    manager.initialize();
    manager.resetDatabaseCheckThrottleForTests();
    return manager;
  }

  private static byte[] writeMasterKey(Path masterKeyPath) throws Exception {
    return TestMasterKeys.write(masterKeyPath);
  }

  @SuppressWarnings("deprecation")
  private static long writeLegacyEncryptedKeyset(Path keysetPath, byte[] masterKey)
      throws Exception {
    Aead masterAead = new AesGcmJce(masterKey);
    KeysetHandle legacyHandle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
    try (var outputStream = Files.newOutputStream(keysetPath)) {
      legacyHandle.write(JsonKeysetWriter.withOutputStream(outputStream), masterAead);
    }
    return Integer.toUnsignedLong(legacyHandle.getPrimary().getId());
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

  private static String tamperEncryptedKeyset(String encryptedKeysetJson) {
    int fieldIndex = encryptedKeysetJson.indexOf("\"encryptedKeyset\"");
    int colonIndex = encryptedKeysetJson.indexOf(':', fieldIndex);
    int valueStart = encryptedKeysetJson.indexOf('"', colonIndex) + 1;
    char original = encryptedKeysetJson.charAt(valueStart);
    char replacement = original == 'A' ? 'B' : 'A';
    return encryptedKeysetJson.substring(0, valueStart)
        + replacement
        + encryptedKeysetJson.substring(valueStart + 1);
  }

  @Test
  @DisplayName("saveKeysetToDatabase throws IllegalStateException when repository returns null")
  void saveKeysetToDatabase_whenRepositoryReturnsNull_throwsIllegalStateException(
      @TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    writeMasterKey(masterKeyPath);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenReturn(Optional.empty());
    when(repository.save(any(KeysetBlob.class))).thenReturn(null);

    TinkKeyManager manager =
        initializeManager(
            masterKeyPath, keysetPath, TinkProperties.Keyset.StorageMode.DATABASE, repository);

    try {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> manager.saveKeysetToDatabase("TEST_NULL_SAVE"));
      assertTrue(
          exception.getMessage().contains("repository returned null"),
          "Exception message should mention null repository return");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  private record LegacyDatabaseKeyset(KeysetBlob blob, long primaryKeyId) {}

  private record TinkFixture(TinkKeyManager manager) {
    static TinkFixture hybridWithRepository(Path tempDir, KeysetBlobRepository repository)
        throws Exception {
      Path masterKeyPath = tempDir.resolve("master.key");
      Path keysetPath = tempDir.resolve("keyset.json.encrypted");
      writeMasterKey(masterKeyPath);
      return new TinkFixture(
          initializeManager(
              masterKeyPath, keysetPath, TinkProperties.Keyset.StorageMode.HYBRID, repository));
    }

    static TinkFixture fileOnly(Path tempDir) throws Exception {
      Path masterKeyPath = tempDir.resolve("master.key");
      Path keysetPath = tempDir.resolve("keyset.json.encrypted");
      writeMasterKey(masterKeyPath);
      return new TinkFixture(
          initializeManager(
              masterKeyPath, keysetPath, TinkProperties.Keyset.StorageMode.FILE, null));
    }

    void close() {
      manager.shutdownKeysetCheckExecutor();
    }
  }
}
