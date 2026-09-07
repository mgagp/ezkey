/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerAdminFirstBootstrapTest
 * Description: Non-writer DATABASE boot waits for the blob and never upserts or generates.
 */
package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.subtle.AesGcmJce;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Layer 1 locks for ADR-0012 peripheral keyset readiness.
 *
 * @since 2026
 */
class TinkKeyManagerAdminFirstBootstrapTest {

  @Test
  @DisplayName("non-writer DATABASE boot does not generate or save when the blob is missing")
  void initialize_nonWriterEmptyBlob_doesNotSaveOrGenerate(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    writeMasterKey(masterKeyPath);

    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset()).thenReturn(Optional.empty());

    TinkProperties properties = peripheralDatabaseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setBootstrapWait(Duration.ZERO);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      assertThrows(IllegalStateException.class, manager::initialize);
      assertFalse(manager.isInitialized());
      assertFalse(Files.exists(keysetPath), "non-writer must not generate a keyset file");
      verify(repository, never()).save(any(KeysetBlob.class));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("non-writer DATABASE boot loads the blob on the second poll")
  void initialize_nonWriter_waitThenLoad(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    byte[] masterKey = writeMasterKey(masterKeyPath);
    KeysetBlob blob = encryptedBlob(masterKey);

    AtomicInteger finds = new AtomicInteger();
    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.findKeyset())
        .thenAnswer(_ -> finds.incrementAndGet() == 1 ? Optional.empty() : Optional.of(blob));

    TinkProperties properties = peripheralDatabaseProperties(masterKeyPath, keysetPath);
    properties.setRequired(true);
    properties.getKeyset().setBootstrapWait(Duration.ofSeconds(2));
    properties.getKeyset().setBootstrapPollInterval(Duration.ofMillis(1));

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(repository));
    try {
      manager.initialize();
      assertTrue(manager.isInitialized());
      verify(repository, never()).save(any(KeysetBlob.class));
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  @Test
  @DisplayName("non-writer FILE boot does not generate a missing keyset file")
  void initialize_nonWriterFileMissing_doesNotGenerate(@TempDir Path tempDir) throws Exception {
    Path masterKeyPath = tempDir.resolve("master.key");
    Path keysetPath = tempDir.resolve("keyset.json.encrypted");
    writeMasterKey(masterKeyPath);

    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(true);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.FILE);
    properties.getKeyset().setWriter(false);

    TinkKeyManager manager = new TinkKeyManager(properties, fixedProvider(null));
    try {
      assertThrows(IllegalStateException.class, manager::initialize);
      assertFalse(manager.isInitialized());
      assertFalse(Files.exists(keysetPath), "non-writer FILE mode must not generate a keyset");
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
  }

  private static TinkProperties peripheralDatabaseProperties(Path masterKeyPath, Path keysetPath) {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setMasterKeyFile(masterKeyPath.toString());
    properties.setKeysetFile(keysetPath.toString());
    properties.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    properties.getKeyset().setWriter(false);
    return properties;
  }

  private static byte[] writeMasterKey(Path masterKeyPath) throws Exception {
    return TestMasterKeys.write(masterKeyPath);
  }

  private static KeysetBlob encryptedBlob(byte[] masterKey) throws Exception {
    AeadConfig.register();
    Aead masterAead = new AesGcmJce(masterKey);
    KeysetHandle handle = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
    String encrypted =
        TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(
            handle, masterAead, new byte[0], RegistryConfiguration.get());
    KeysetBlob blob = new KeysetBlob(encrypted.getBytes(StandardCharsets.UTF_8), "TEST");
    blob.setVersion(1L);
    return blob;
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
