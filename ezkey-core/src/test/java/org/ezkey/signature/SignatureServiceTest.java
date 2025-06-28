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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.ezkey.signature.SignatureService;

/**
 * Unit tests for SignatureService.
 * <p>
 * Tests cryptographic operations including key pair generation, signature creation,
 * and signature validation. Ensures the security integrity of the signature process.
 * </p>
 *
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Key pair generation and encoding</li>
 *   <li>Signature generation with private key</li>
 *   <li>Signature validation with public key</li>
 *   <li>Invalid signature detection</li>
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Usage:</b> Cryptographic signature service tests</p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see SignatureService
 */
@DisplayName("Signature Service Tests")
class SignatureServiceTest {

    private SignatureService signatureService;
    private KeyPair keyPair;
    private String base64PrivateKey;
    private String base64PublicKey;
    private String testData;

    /**
     * Setup test data before each test method.
     */
    @BeforeEach
    void setUp() throws Exception {
        // Initialize signature service
        signatureService = new SignatureService();
        
        // Generate RSA key pair for testing
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();

        // Encode keys to Base64
        base64PrivateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        base64PublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        // Test data
        testData = "Test de signature pour Ezkey";
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
        assertTrue(isValid, "La signature doit être valide pour les données originales");
    }

    @Test
    @DisplayName("Should reject signature for modified data")
    void testSignatureValidationWithModifiedData() throws Exception {
        // Arrange
        String signature = signatureService.generateSignature(testData, base64PrivateKey);
        String modifiedData = "Données modifiées pour test";

        // Act
        boolean isInvalid = signatureService.validateSignature(modifiedData, signature, base64PublicKey);

        // Assert
        assertFalse(isInvalid, "La signature ne doit pas être valide pour des données modifiées");
    }

    @Test
    @DisplayName("Should reject invalid signature")
    void testInvalidSignatureRejection() throws Exception {
        // Arrange
        String invalidSignature = "signature_invalide_base64_encoded";

        // Act
        boolean isValid = signatureService.validateSignature(testData, invalidSignature, base64PublicKey);

        // Assert
        assertFalse(isValid, "Une signature invalide doit être rejetée");
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
        assertTrue(isValid, "La signature doit être valide même pour des données vides");
    }

    @Test
    @DisplayName("Should handle large data")
    void testSignatureWithLargeData() throws Exception {
        // Arrange
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeData.append("Données de test répétées ");
        }
        String largeDataString = largeData.toString();

        // Act
        String signature = signatureService.generateSignature(largeDataString, base64PrivateKey);
        boolean isValid = signatureService.validateSignature(largeDataString, signature, base64PublicKey);

        // Assert
        assertTrue(isValid, "La signature doit être valide pour de grandes quantités de données");
    }

    @Test
    @DisplayName("Should generate different signatures for different data")
    void testDifferentSignaturesForDifferentData() throws Exception {
        // Arrange
        String data1 = "Première donnée de test";
        String data2 = "Deuxième donnée de test";

        // Act
        String signature1 = signatureService.generateSignature(data1, base64PrivateKey);
        String signature2 = signatureService.generateSignature(data2, base64PrivateKey);

        // Assert
        assertFalse(signature1.equals(signature2), "Les signatures doivent être différentes pour des données différentes");
    }

    @Test
    @DisplayName("Should generate consistent signatures for same data")
    void testConsistentSignaturesForSameData() throws Exception {
        // Arrange
        String data = "Données identiques pour test de cohérence";

        // Act
        String signature1 = signatureService.generateSignature(data, base64PrivateKey);
        String signature2 = signatureService.generateSignature(data, base64PrivateKey);

        // Assert
        assertTrue(signature1.equals(signature2), "Les signatures doivent être identiques pour les mêmes données");
    }
} 