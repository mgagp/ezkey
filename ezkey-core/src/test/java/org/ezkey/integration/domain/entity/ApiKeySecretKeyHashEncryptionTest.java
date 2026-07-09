/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ApiKeySecretKeyHashEncryptionTest
 * Description: Verifies at-rest encryption contract for API key secret hash (SEC-010).
 */

package org.ezkey.integration.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.ezkey.security.EncryptionOperations;
import org.ezkey.security.EncryptionOperationsHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKey secret hash at-rest encryption")
class ApiKeySecretKeyHashEncryptionTest {

  @Mock private EncryptionOperations encryptionOperations;

  private static final String BCRYPT =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
  private static final String CIPHERTEXT = "ENC:1:abcdefghijklmnopqrstuvwxyz0123456789AB=";

  @AfterEach
  void tearDown() {
    EncryptionOperationsHolder.clear();
  }

  @Test
  @DisplayName("getEncryptedFields exposes secret_key_hash column")
  void getEncryptedFields_includesSecretKeyHash() {
    ApiKey apiKey = new ApiKey();
    apiKey.setEncryptedField("secret_key_hash", CIPHERTEXT);

    assertTrue(apiKey.getEncryptedFields().containsKey("secret_key_hash"));
    assertEquals(CIPHERTEXT, apiKey.getEncryptedFields().get("secret_key_hash"));
  }

  @Test
  @DisplayName("getSecretKeyHash fails closed when encryption is required but value is plaintext")
  void getSecretKeyHash_failsWhenRequiredAndPlaintext() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(BCRYPT)).thenReturn(false);
    EncryptionOperationsHolder.set(encryptionOperations);

    ApiKey apiKey = new ApiKey();
    apiKey.setEncryptedField("secret_key_hash", BCRYPT);

    assertThrows(IllegalStateException.class, apiKey::getSecretKeyHash);
  }

  @Test
  @DisplayName("getSecretKeyHash decrypts ENC: values on read")
  void getSecretKeyHash_decryptsWhenEncrypted() {
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(CIPHERTEXT)).thenReturn(true);
    when(encryptionOperations.decrypt(CIPHERTEXT)).thenReturn(BCRYPT);
    EncryptionOperationsHolder.set(encryptionOperations);

    ApiKey apiKey = new ApiKey();
    apiKey.setEncryptedField("secret_key_hash", CIPHERTEXT);

    assertEquals(BCRYPT, apiKey.getSecretKeyHash());
  }

  @Test
  @DisplayName("setEncryptedField clears transient cache")
  void setEncryptedField_clearsTransientCache() throws Exception {
    ApiKey apiKey = new ApiKey();
    apiKey.setSecretKeyHash(BCRYPT);
    apiKey.getSecretKeyHash();

    apiKey.setEncryptedField("secret_key_hash", CIPHERTEXT);

    var transientField = ApiKey.class.getDeclaredField("secretKeyHashPlaintext");
    transientField.setAccessible(true);
    assertNull(transientField.get(apiKey));
  }
}
