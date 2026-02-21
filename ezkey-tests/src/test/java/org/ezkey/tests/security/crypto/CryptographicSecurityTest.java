/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CryptographicSecurityTest
 * Description: Security tests for cryptographic validation (signatures, proof tokens)
 */

package org.ezkey.tests.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CryptoApiClient.EcP256KeyPair;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Security tests for cryptographic validation.
 *
 * <p>Validates cryptographic operations including:
 *
 * <ul>
 *   <li>EC P-256 key pair generation
 *   <li>Proof token generation
 *   <li>Data signing with private keys
 *   <li>Signature validation
 * </ul>
 *
 * <p>These tests use the Crypto API deployed in Docker, not the ezkey-core crypto module.
 *
 * @since 2025
 */
@Tag(TestTags.FAST)
@Tag(TestTags.ENCRYPTION)
@DisplayName("Cryptographic Security Tests")
public class CryptographicSecurityTest extends AbstractSecurityTest {

  @Test
  @DisplayName("Can generate EC P-256 key pair via Crypto API")
  public void testGenerateKeyPair() {
    EcP256KeyPair keyPair = cryptoApiClient.generateKeyPair();

    assertThat(keyPair.privateKey()).isNotNull().isNotEmpty();
    assertThat(keyPair.publicKey()).isNotNull().isNotEmpty();
    // EC P-256 PKCS#8 private key is ~121 chars Base64; X.509 public key is ~88 chars Base64
    assertThat(keyPair.privateKey().length()).isGreaterThan(80);
    assertThat(keyPair.publicKey().length()).isGreaterThan(60);
  }

  @Test
  @DisplayName("Can generate proof token via Crypto API")
  public void testGenerateProofToken() {
    String proofToken = cryptoApiClient.generateProofToken();

    assertThat(proofToken).isNotNull().isNotEmpty();
  }

  @Test
  @DisplayName("Can sign data with private key via Crypto API")
  public void testSignData() {
    EcP256KeyPair keyPair = cryptoApiClient.generateKeyPair();
    String data = "test-data-to-sign";
    String signature = cryptoApiClient.signData(data, keyPair.privateKey());

    assertThat(signature).isNotNull().isNotEmpty();
  }

  @Test
  @DisplayName("Can validate signature via Crypto API")
  public void testValidateSignature() {
    EcP256KeyPair keyPair = cryptoApiClient.generateKeyPair();
    String data = "test-data-to-sign";
    String signature = cryptoApiClient.signData(data, keyPair.privateKey());

    boolean isValid = cryptoApiClient.validateSignature(data, signature, keyPair.publicKey());

    assertThat(isValid).isTrue();
  }

  @Test
  @DisplayName("Invalid signature should fail validation")
  public void testInvalidSignatureFails() {
    EcP256KeyPair keyPair = cryptoApiClient.generateKeyPair();
    String data = "test-data-to-sign";
    String invalidSignature = "invalid-signature-data";

    boolean isValid =
        cryptoApiClient.validateSignature(data, invalidSignature, keyPair.publicKey());

    assertThat(isValid).isFalse();
  }

  @Test
  @DisplayName("Signature validation fails with wrong public key")
  public void testSignatureValidationFailsWithWrongKey() {
    EcP256KeyPair keyPair1 = cryptoApiClient.generateKeyPair();
    EcP256KeyPair keyPair2 = cryptoApiClient.generateKeyPair();
    String data = "test-data-to-sign";
    String signature = cryptoApiClient.signData(data, keyPair1.privateKey());

    // Try to validate with wrong public key
    boolean isValid = cryptoApiClient.validateSignature(data, signature, keyPair2.publicKey());

    assertThat(isValid).isFalse();
  }
}
