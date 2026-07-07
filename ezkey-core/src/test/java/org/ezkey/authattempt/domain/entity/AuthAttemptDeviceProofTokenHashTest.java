/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptDeviceProofTokenHashTest
 * Description: Verifies hash-only device proof token storage on AuthAttempt (ADR-0007).
 */

package org.ezkey.authattempt.domain.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ezkey.security.SensitiveDataHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthAttempt device proof token hash-only storage")
class AuthAttemptDeviceProofTokenHashTest {

  @Test
  @DisplayName("getEncryptedFields excludes device proof token column")
  void getEncryptedFields_excludesDeviceProofToken() {
    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setEncryptedField("auth_attempt_proof_token", "ENC:1:abc");

    assertTrue(authAttempt.getEncryptedFields().containsKey("auth_attempt_proof_token"));
    assertFalse(authAttempt.getEncryptedFields().containsKey("device_proof_token"));
  }

  @Test
  @DisplayName("setDeviceProofTokenHash stores SHA-256 digest only")
  void setDeviceProofTokenHash_storesHash() {
    String plaintext = "device-proof-token-example";
    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setDeviceProofTokenHash(SensitiveDataHasher.sha256Hex(plaintext));

    assertEquals(SensitiveDataHasher.sha256Hex(plaintext), authAttempt.getDeviceProofTokenHash());
  }
}
