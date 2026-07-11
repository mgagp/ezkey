/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentAtRestEncryptionReadPathTest
 * Description: Fail-closed read policy for enrollment encrypted fields (I-2026-07-09).
 */

package org.ezkey.enrollment.domain.entity;

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
@DisplayName("Enrollment at-rest encryption read path")
class EnrollmentAtRestEncryptionReadPathTest {

  @Mock private EncryptionOperations encryptionOperations;

  private static final String PLAIN_PROOF = "enrollment-proof-token-plain";
  private static final String PLAIN_KEY = "integration-private-key-plain";
  private static final String CIPHER_PROOF = "ENC:1:abcdefghijklmnopqrstuvwxyz0123456789AB=";
  private static final String CIPHER_KEY = "ENC:1:zyxwvutsrqponmlkjihgfedcba9876543210ZY=";

  @AfterEach
  void tearDown() {
    EncryptionOperationsHolder.clear();
  }

  @Test
  @DisplayName("getEnrollmentProofToken fails closed when encryption required and value plaintext")
  void getEnrollmentProofToken_failsWhenRequiredAndPlaintext() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(PLAIN_PROOF)).thenReturn(false);
    EncryptionOperationsHolder.set(encryptionOperations);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(42);
    enrollment.setEncryptedField("enrollment_proof_token", PLAIN_PROOF);

    assertThrows(IllegalStateException.class, enrollment::getEnrollmentProofToken);
  }

  @Test
  @DisplayName("getEnrollmentProofToken decrypts ENC values on read")
  void getEnrollmentProofToken_decryptsWhenEncrypted() {
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(CIPHER_PROOF)).thenReturn(true);
    when(encryptionOperations.decrypt(CIPHER_PROOF)).thenReturn(PLAIN_PROOF);
    EncryptionOperationsHolder.set(encryptionOperations);

    Enrollment enrollment = new Enrollment();
    enrollment.setEncryptedField("enrollment_proof_token", CIPHER_PROOF);

    assertEquals(PLAIN_PROOF, enrollment.getEnrollmentProofToken());
  }

  @Test
  @DisplayName("getIntegrationPrivateKey fails closed when encryption required and value plaintext")
  void getIntegrationPrivateKey_failsWhenRequiredAndPlaintext() {
    when(encryptionOperations.isEncryptionRequired()).thenReturn(true);
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(PLAIN_KEY)).thenReturn(false);
    EncryptionOperationsHolder.set(encryptionOperations);

    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(7);
    enrollment.setEncryptedField("integration_private_key", PLAIN_KEY);

    assertThrows(IllegalStateException.class, enrollment::getIntegrationPrivateKey);
  }

  @Test
  @DisplayName("getIntegrationPrivateKey decrypts ENC values on read")
  void getIntegrationPrivateKey_decryptsWhenEncrypted() {
    when(encryptionOperations.isEncryptionAvailable()).thenReturn(true);
    when(encryptionOperations.isEncrypted(CIPHER_KEY)).thenReturn(true);
    when(encryptionOperations.decrypt(CIPHER_KEY)).thenReturn(PLAIN_KEY);
    EncryptionOperationsHolder.set(encryptionOperations);

    Enrollment enrollment = new Enrollment();
    enrollment.setEncryptedField("integration_private_key", CIPHER_KEY);

    assertEquals(PLAIN_KEY, enrollment.getIntegrationPrivateKey());
  }
}
