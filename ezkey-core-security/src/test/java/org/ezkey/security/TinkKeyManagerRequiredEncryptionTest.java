/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkKeyManagerRequiredEncryptionTest
 * Description: Verifies SEC-002 required encryption fail-fast behavior.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ezkey.config.TinkProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit tests for {@link TinkKeyManager} required encryption enforcement (SEC-002).
 *
 * @since 2026
 */
class TinkKeyManagerRequiredEncryptionTest {

  @Test
  @DisplayName("initialize fails when required=true and master key file is missing")
  void initialize_requiredWithMissingMasterKey_throws() {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(true);
    properties.setMasterKeyFile("/nonexistent/ezkey/master.key");
    properties.setKeysetFile("/nonexistent/ezkey/keyset.json.encrypted");

    TinkKeyManager manager = new TinkKeyManager(properties, emptyRepositoryProvider());

    assertThrows(IllegalStateException.class, manager::initialize);
  }

  @Test
  @DisplayName("initialize fails when required=true and encryption is disabled")
  void initialize_requiredWithEncryptionDisabled_throws() {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(false);
    properties.setRequired(true);

    TinkKeyManager manager = new TinkKeyManager(properties, emptyRepositoryProvider());

    assertThrows(IllegalStateException.class, manager::initialize);
  }

  @Test
  @DisplayName("initialize succeeds when required=false and master key file is missing")
  void initialize_notRequiredWithMissingMasterKey_degradesGracefully() {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    properties.setRequired(false);
    properties.setMasterKeyFile("/nonexistent/ezkey/master.key");

    TinkKeyManager manager = new TinkKeyManager(properties, emptyRepositoryProvider());

    assertDoesNotThrow(manager::initialize);
  }

  private static ObjectProvider<org.ezkey.security.domain.repository.KeysetBlobRepository>
      emptyRepositoryProvider() {
    return new ObjectProvider<>() {
      @Override
      public org.ezkey.security.domain.repository.KeysetBlobRepository getObject(Object... args) {
        return null;
      }

      @Override
      public org.ezkey.security.domain.repository.KeysetBlobRepository getIfAvailable() {
        return null;
      }

      @Override
      public org.ezkey.security.domain.repository.KeysetBlobRepository getIfUnique() {
        return null;
      }

      @Override
      public org.ezkey.security.domain.repository.KeysetBlobRepository getObject() {
        return null;
      }
    };
  }
}
