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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.subtle.AesGcmJce;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
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
  @DisplayName("new database keyset reader loads legacy outer-encrypted JSON blobs")
  void initialize_whenLegacyDatabaseKeysetBlobExists_loadsIt(@TempDir Path tempDir)
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
      assertEquals(legacyKeyset.primaryKeyId(), manager.getCurrentPrimaryKeyId());
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

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    manager.initialize();
    manager.resetDatabaseCheckThrottleForTests();
    return manager;
  }

  private static byte[] writeMasterKey(Path masterKeyPath) throws Exception {
    byte[] masterKey = new byte[32];
    new SecureRandom().nextBytes(masterKey);
    Files.writeString(masterKeyPath, Base64.getEncoder().encodeToString(masterKey));
    return masterKey;
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
