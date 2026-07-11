/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptAtRestEncryptionReadPathTest
 * Description: Fail-closed read policy for auth attempt proof token (I-2026-07-09).
 */

package org.ezkey.authattempt.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
@DisplayName("AuthAttempt at-rest encryption read path")
class AuthAttemptAtRestEncryptionReadPathTest {

  @Mock private EncryptionOperations encryptionOperations;

  private static final String PLAIN_TOKEN = "auth-attempt-proof-token-plain";
  private static final String CIPHER_TOKEN = "ENC:1:abcdefghijklmnopqrstuvwxyz0123456789AB=";

  @AfterEach
  void tearDown() {
    EncryptionOperationsHolder.clear();
  }

  @Test
  @DisplayName("getAuthAttemptProofToken fails closed when encryption required and value plaintext")
  void getAuthAttemptProofToken_failsWhenRequiredAndPlaintext() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(PLAIN_TOKEN)).thenReturn(false);
    EncryptionOperationsHolder.set(encryptionOperations);

    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(99);
    authAttempt.setEncryptedField("auth_attempt_proof_token", PLAIN_TOKEN);

    assertThrows(IllegalStateException.class, authAttempt::getAuthAttemptProofToken);
  }

  @Test
  @DisplayName("getAuthAttemptProofToken decrypts ENC values on read")
  void getAuthAttemptProofToken_decryptsWhenEncrypted() {
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(CIPHER_TOKEN)).thenReturn(true);
    when(encryptionOperations.decrypt(CIPHER_TOKEN)).thenReturn(PLAIN_TOKEN);
    EncryptionOperationsHolder.set(encryptionOperations);

    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setEncryptedField("auth_attempt_proof_token", CIPHER_TOKEN);

    assertEquals(PLAIN_TOKEN, authAttempt.getAuthAttemptProofToken());
  }
}
