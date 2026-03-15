/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: SignatureServiceTest
 * Description: Unit tests for SignatureService cryptographic operations.
 */

package org.ezkey.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.ezkey.config.EzkeyCoreProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SignatureService.
 *
 * <p>Tests cryptographic operations including key pair generation, signature creation, and
 * signature validation. Ensures the security integrity of the signature process.
 *
 * <p><b>Test Coverage:</b>
 *
 * <ul>
 *   <li>Key pair generation and encoding
 *   <li>Signature generation with private key
 *   <li>Signature validation with public key
 *   <li>Invalid signature detection
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> Cryptographic signature service tests
 *
 * @author Ezkey contributors
 * @since 2025
 * @see SignatureService
 */
@DisplayName("SignatureService Tests")
class SignatureServiceTest {

  private SignatureService signatureService;
  private String base64PrivateKey;
  private String base64PublicKey;
  private String testData;

  /** Setup test data before each test method. */
  @BeforeEach
  void setUp() throws Exception {
    // Initialize signature service with default configuration
    EzkeyCoreProperties ezkeyCoreProperties = new EzkeyCoreProperties();
    signatureService = new SignatureService(ezkeyCoreProperties);

    // Generate EC P-256 key pair for testing via service API
    ECP256KeyPair ecp256KeyPair = signatureService.generateECP256KeyPair();
    base64PrivateKey = ecp256KeyPair.base64PrivateKey();
    base64PublicKey = ecp256KeyPair.base64PublicKey();

    // Test data
    testData = "Signature test payload for Ezkey";
  }

  @Test
  @DisplayName("Should generate a valid proof token")
  void testGenerateProofToken() {
    // Act
    String proofToken = signatureService.generateProofToken();

    // Assert
    // The proof token should not be null or empty and should match the expected format
    assertTrue(
        proofToken != null && !proofToken.isEmpty(), "Proof token should not be null or empty");
    String[] parts = proofToken.split("\\.");
    assertTrue(
        parts.length == 2,
        "Proof token should have two parts separated by '.' (randomPart.saltPart)");
  }

  @Test
  @DisplayName("Should generate secure challenge with valid digit count")
  void testGenerateSecureChallenge() {
    // Test 1-6 digits
    for (int digits = 1; digits <= 6; digits++) {
      // Act
      Integer challenge = signatureService.generateSecureChallenge(digits);

      // Assert
      assertNotNull(challenge, "Challenge should not be null for " + digits + " digits");
      assertTrue(challenge > 0, "Challenge should be positive for " + digits + " digits");

      // Verify the challenge has the correct number of digits
      String challengeStr = challenge.toString();
      assertEquals(
          digits,
          challengeStr.length(),
          "Challenge should have exactly " + digits + " digits, got: " + challengeStr);

      // Verify minimum value (no leading zeros)
      int expectedMin = (int) Math.pow(10, digits - 1);
      int expectedMax = (int) Math.pow(10, digits) - 1;
      assertTrue(
          challenge >= expectedMin && challenge <= expectedMax,
          "Challenge " + challenge + " should be between " + expectedMin + " and " + expectedMax);
    }
  }

  @Test
  @DisplayName("Should generate different secure challenges on multiple calls")
  void testSecureChallengeRandomness() {
    // Generate multiple challenges and verify they are different
    Set<Integer> challenges = new HashSet<>();
    int iterations = 100;

    for (int i = 0; i < iterations; i++) {
      Integer challenge = signatureService.generateSecureChallenge(4);
      challenges.add(challenge);
    }

    // We should have most challenges being unique (allowing for some collision due to randomness)
    assertTrue(
        challenges.size() > iterations * 0.8,
        "Should generate mostly unique challenges, got "
            + challenges.size()
            + " unique out of "
            + iterations);
  }

  @Test
  @DisplayName("Should reject invalid digit counts for secure challenge")
  void testSecureChallengeInvalidDigits() {
    // Test invalid digit counts
    assertThrows(
        IllegalArgumentException.class,
        () -> signatureService.generateSecureChallenge(0),
        "Should reject 0 digits");
    assertThrows(
        IllegalArgumentException.class,
        () -> signatureService.generateSecureChallenge(-1),
        "Should reject negative digits");
    assertThrows(
        IllegalArgumentException.class,
        () -> signatureService.generateSecureChallenge(7),
        "Should reject more than 6 digits");
    assertThrows(
        IllegalArgumentException.class,
        () -> signatureService.generateSecureChallenge(10),
        "Should reject more than 6 digits");
  }

  @Test
  @DisplayName("Should generate and validate signature successfully")
  void testKeyPairAndSignatureValidation() throws Exception {
    // Arrange
    // (Setup done in @BeforeEach)

    // Act
    String signature = signatureService.generateSignature(testData, base64PrivateKey);
    boolean isValid = signatureService.validateSignature(testData, signature, base64PublicKey);

    // Assert
    assertTrue(isValid, "Signature should be valid for original data");
  }

  @Test
  @DisplayName("Should reject signature for modified data")
  void testSignatureValidationWithModifiedData() throws Exception {
    // Arrange
    String signature = signatureService.generateSignature(testData, base64PrivateKey);
    String modifiedData = "Modified data for test";

    // Act
    boolean isInvalid =
        signatureService.validateSignature(modifiedData, signature, base64PublicKey);

    // Assert
    assertFalse(isInvalid, "Signature should not be valid for modified data");
  }

  @Test
  @DisplayName("Should reject invalid signature")
  void testInvalidSignatureRejection() throws Exception {
    // Arrange
    String invalidSignature = "invalid_signature_base64_encoded";

    // Act
    boolean isValid =
        signatureService.validateSignature(testData, invalidSignature, base64PublicKey);

    // Assert
    assertFalse(isValid, "An invalid signature should be rejected");
  }

  @Test
  @DisplayName("Should handle empty data")
  void testSignatureWithEmptyData() throws Exception {
    // Arrange
    String emptyData = "";

    // Act
    String signature = signatureService.generateSignature(emptyData, base64PrivateKey);
    boolean isValid = signatureService.validateSignature(emptyData, signature, base64PublicKey);

    // Assert
    assertTrue(isValid, "Signature should be valid even for empty data");
  }

  @Test
  @DisplayName("Should handle large data")
  void testSignatureWithLargeData() throws Exception {
    // Arrange
    StringBuilder largeData = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      largeData.append("Repeated test data ");
    }
    String largeDataString = largeData.toString();

    // Act
    String signature = signatureService.generateSignature(largeDataString, base64PrivateKey);
    boolean isValid =
        signatureService.validateSignature(largeDataString, signature, base64PublicKey);

    // Assert
    assertTrue(isValid, "Signature should be valid for large amounts of data");
  }

  @Test
  @DisplayName("Should generate different signatures for different data")
  void testDifferentSignaturesForDifferentData() throws Exception {
    // Arrange
    String data1 = "First test data";
    String data2 = "Second test data";

    // Act
    String signature1 = signatureService.generateSignature(data1, base64PrivateKey);
    String signature2 = signatureService.generateSignature(data2, base64PrivateKey);

    // Assert
    assertFalse(signature1.equals(signature2), "Signatures should differ for different data");
  }

  @Test
  @DisplayName("Should generate valid signatures for same data (ECDSA uses random nonce)")
  void testValidSignaturesForSameData() throws Exception {
    // Arrange
    String data = "Identical data for consistency test";

    // Act
    String signature1 = signatureService.generateSignature(data, base64PrivateKey);
    String signature2 = signatureService.generateSignature(data, base64PrivateKey);

    // Assert
    // ECDSA uses a random nonce (k) for each signature, so signatures will differ
    // However, both signatures should be valid for the same data
    assertFalse(
        signature1.equals(signature2), "ECDSA signatures should differ due to random nonce");

    // Both signatures should validate correctly
    assertTrue(
        signatureService.validateSignature(data, signature1, base64PublicKey),
        "First signature should be valid");
    assertTrue(
        signatureService.validateSignature(data, signature2, base64PublicKey),
        "Second signature should be valid");
  }

  @Test
  @DisplayName("Should generate EC P-256 key pair via service")
  void testGenerateECP256KeyPair() {
    // Act
    ECP256KeyPair pair = signatureService.generateECP256KeyPair();
    // Assert
    assertTrue(
        pair.base64PrivateKey() != null && !pair.base64PrivateKey().isEmpty(),
        "Private key must be present");
    assertTrue(
        pair.base64PublicKey() != null && !pair.base64PublicKey().isEmpty(),
        "Public key must be present");
    // Quick sanity: produced keys can sign/verify
    String sig = signatureService.generateSignature("data", pair.base64PrivateKey());
    assertTrue(
        signatureService.validateSignature("data", sig, pair.base64PublicKey()),
        "Generated keys should work for sign/verify");
  }
}
