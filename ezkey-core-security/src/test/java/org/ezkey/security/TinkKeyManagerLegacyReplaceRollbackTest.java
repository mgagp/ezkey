/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerLegacyReplaceRollbackTest
 * Description: Direct coverage for restoreHandleAfterFailedCas used when legacy replace CAS fails.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.AeadConfig;
import java.nio.file.Path;
import java.util.Optional;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Lot 6 minor: handle/version restore at legacy-replace CAS failure (~455).
 *
 * @since 2026
 */
class TinkKeyManagerLegacyReplaceRollbackTest {

  @Test
  @DisplayName("restoreHandleAfterFailedCas restores previous handle and version")
  void restoreHandleAfterFailedCas_restoresPreviousMemory(@TempDir Path tempDir) throws Exception {
    AeadConfig.register();
    Path masterKey = tempDir.resolve("master.key");
    Path keysetFile = tempDir.resolve("keyset.json.encrypted");
    TestMasterKeys.write(masterKey);

    KeysetBlobRepository repo = Mockito.mock(KeysetBlobRepository.class);
    when(repo.findKeyset()).thenReturn(Optional.empty());
    when(repo.keysetExists()).thenReturn(false);
    when(repo.insertSingletonIfAbsent(any(), any(), any())).thenReturn(1);
    when(repo.findVersion()).thenReturn(Optional.of(1L));
    when(repo.updateKeysetIfVersionMatches(any(), any(), any(), anyLong())).thenReturn(1);

    TinkProperties props = new TinkProperties();
    props.setEnabled(true);
    props.setRequired(false);
    props.setMasterKeyFile(masterKey.toString());
    props.setKeysetFile(keysetFile.toString());
    props.getKeyset().setStorageMode(TinkProperties.Keyset.StorageMode.DATABASE);
    props.getKeyset().setWriter(true);
    props.getRotation().setBackupBeforeRotation(false);

    TinkKeyManager manager = new TinkKeyManager(props, fixedProvider(repo));
    try {
      manager.initialize();
      assertTrue(manager.isInitialized());

      KeysetHandle previous = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"));
      long expectedPrimary = Integer.toUnsignedLong(previous.getPrimary().getId());
      manager.restoreHandleAfterFailedCas(previous, 7L);

      assertTrue(manager.isInitialized());
      assertEquals(7L, manager.getDatabaseKeysetVersion());
      assertEquals(expectedPrimary, manager.getCurrentPrimaryKeyId());

      manager.restoreHandleAfterFailedCas(null, 0L);
      assertFalse(manager.isInitialized());
      assertEquals(0L, manager.getDatabaseKeysetVersion());
    } finally {
      manager.shutdownKeysetCheckExecutor();
    }
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
