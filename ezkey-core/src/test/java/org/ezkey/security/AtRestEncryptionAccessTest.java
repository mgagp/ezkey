/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AtRestEncryptionAccessTest
 * Description: Fail-closed policy when ezkey.encryption.required=true.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AtRestEncryptionAccess")
class AtRestEncryptionAccessTest {

  @Mock private EncryptionOperations encryptionOperations;

  private static final String BCRYPT =
      "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
  private static final String CIPHERTEXT = "ENC:1:abcdefghijklmnopqrstuvwxyz0123456789AB=";

  @Test
  @DisplayName("returns plaintext when encryption is not required and value is not encrypted")
  void resolveEncryptedField_allowsPlaintextWhenNotRequired() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(false);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(BCRYPT)).thenReturn(false);

    assertEquals(
        BCRYPT,
        AtRestEncryptionAccess.resolveEncryptedField(encryptionOperations, BCRYPT, "test field"));
  }

  @Test
  @DisplayName("throws when encryption is required but value is not encrypted")
  void resolveEncryptedField_failsWhenRequiredAndPlaintext() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(BCRYPT)).thenReturn(false);

    assertThrows(
        IllegalStateException.class,
        () ->
            AtRestEncryptionAccess.resolveEncryptedField(
                encryptionOperations, BCRYPT, "API key secret hash"));
  }

  @Test
  @DisplayName("decrypts when encryption is required and value is encrypted")
  void resolveEncryptedField_decryptsWhenRequiredAndEncrypted() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(CIPHERTEXT)).thenReturn(true);
    when(encryptionOperations.decrypt(CIPHERTEXT)).thenReturn(BCRYPT);

    assertEquals(
        BCRYPT,
        AtRestEncryptionAccess.resolveEncryptedField(
            encryptionOperations, CIPHERTEXT, "API key secret hash"));
  }

  @Test
  @DisplayName("throws when encryption is required but decrypt fails")
  void resolveEncryptedField_failsWhenRequiredAndDecryptFails() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(CIPHERTEXT)).thenReturn(true);
    when(encryptionOperations.decrypt(CIPHERTEXT)).thenReturn(CIPHERTEXT);

    assertThrows(
        IllegalStateException.class,
        () ->
            AtRestEncryptionAccess.resolveEncryptedField(
                encryptionOperations, CIPHERTEXT, "API key secret hash"));
  }

  @Test
  @DisplayName("requireEncryptionAvailableForPersist throws when required but unavailable")
  void requireEncryptionAvailableForPersist_failsWhenRequiredButUnavailable() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(false);

    assertThrows(
        IllegalStateException.class,
        () ->
            AtRestEncryptionAccess.requireEncryptionAvailableForPersist(
                encryptionOperations, "API key secret hash"));
  }
}
