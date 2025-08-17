package org.ezkey.demo.device.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test to verify signature compatibility between DeviceCryptoService and SignatureService.
 *
 * @since 2025
 */
public class SignatureCompatibilityTest {

    private DeviceCryptoService deviceCryptoService;

    private KeyPair testKeyPair;

    @BeforeEach
    void setUp() {
        deviceCryptoService = new DeviceCryptoService();
        testKeyPair = deviceCryptoService.generateDeviceKeyPair();
    }

    /**
     * Simulates the SignatureService.generateSignature method from ezkey-core
     */
    private String simulateCoreSignature(String data,String base64PrivateKey) {
        try{
            byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return Base64.getEncoder().encodeToString(signed);
        } catch (Exception e){
            throw new RuntimeException("Failed to generate signature",e);
        }
    }

    /**
     * Simulates the SignatureService.validateSignature method from ezkey-core
     */
    private boolean simulateCoreValidation(String data,String signatureBase64,String base64PublicKey) {
        try{
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes()); // Default charset like in core
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            return signature.verify(signatureBytes);
        } catch (Exception e){
            return false;
        }
    }

    @Test
    void testSignatureCompatibility() {
        // Test data
        String testData = "test-enrollment-proof-token-123";

        // Generate signatures using both approaches
        String deviceSignature = deviceCryptoService.signStringToBase64(testData,testKeyPair.getPrivate());
        String coreSignature = simulateCoreSignature(testData,deviceCryptoService.privateKeyToBase64(testKeyPair.getPrivate()));

        // Log for debugging
        System.out.println("Test data: " + testData);
        System.out.println("Device signature: " + deviceSignature);
        System.out.println("Core signature: " + coreSignature);
        System.out.println("Signatures match: " + deviceSignature.equals(coreSignature));

        // Verify that both signatures can be validated by the core service
        String publicKeyBase64 = deviceCryptoService.publicKeyToBase64(testKeyPair.getPublic());

        boolean deviceSignatureValid = simulateCoreValidation(testData,deviceSignature,publicKeyBase64);
        boolean coreSignatureValid = simulateCoreValidation(testData,coreSignature,publicKeyBase64);

        System.out.println("Device signature valid: " + deviceSignatureValid);
        System.out.println("Core signature valid: " + coreSignatureValid);

        // Both signatures should be valid
        assertTrue(deviceSignatureValid,"Device signature should be valid");
        assertTrue(coreSignatureValid,"Core signature should be valid");

        // The signatures should be identical if the implementations are compatible
        assertEquals(deviceSignature,coreSignature,"Signatures should be identical");
    }

    @Test
    void testCrossValidation() {
        // Test data
        String testData = "enrollment-proof-token-456";

        // Generate signature with device service
        String deviceSignature = deviceCryptoService.signStringToBase64(testData,testKeyPair.getPrivate());

        // Validate with simulated core service
        String publicKeyBase64 = deviceCryptoService.publicKeyToBase64(testKeyPair.getPublic());
        boolean isValid = simulateCoreValidation(testData,deviceSignature,publicKeyBase64);

        System.out.println("Cross-validation test:");
        System.out.println("Test data: " + testData);
        System.out.println("Device signature: " + deviceSignature);
        System.out.println("Public key: " + publicKeyBase64);
        System.out.println("Validation result: " + isValid);

        assertTrue(isValid,"Device signature should be validated by core service");
    }
}
