package org.ezkey.demo.device.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.ezkey.demo.device.service.DeviceCryptoService.Ed25519DeviceKeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test to verify signature compatibility between DeviceCryptoService and SignatureService.
 *
 * @since 2025
 */
public class SignatureCompatibilityTest {

  private DeviceCryptoService deviceCryptoService;

  private Ed25519DeviceKeyPair testKeyPair;

  @BeforeEach
  void setUp() {
    deviceCryptoService = new DeviceCryptoService();
    testKeyPair = deviceCryptoService.generateDeviceKeyPair();
  }

  /** Simulates the SignatureService.generateSignature method from ezkey-core */
  private String simulateCoreSignature(String data, String base64PrivateKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
      // Handle both raw 32-byte keys and PKCS#8 encoded keys
      Ed25519PrivateKeyParameters privateKeyParams;
      if (keyBytes.length == 32) {
        privateKeyParams = new Ed25519PrivateKeyParameters(keyBytes, 0);
      } else {
        // Extract from PKCS#8 format (not needed for demo device, but for compatibility)
        org.bouncycastle.asn1.pkcs.PrivateKeyInfo privateKeyInfo =
            org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(keyBytes);
        byte[] rawKey = privateKeyInfo.getPrivateKey().getOctets();
        privateKeyParams = new Ed25519PrivateKeyParameters(rawKey, 0);
      }
      Ed25519Signer signer = new Ed25519Signer();
      signer.init(true, privateKeyParams);
      byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
      signer.update(dataBytes, 0, dataBytes.length);
      byte[] signature = signer.generateSignature();
      return Base64.getEncoder().encodeToString(signature);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate signature", e);
    }
  }

  /** Simulates the SignatureService.validateSignature method from ezkey-core */
  private boolean simulateCoreValidation(
      String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
      // Handle both raw 32-byte keys and X.509 encoded keys
      Ed25519PublicKeyParameters publicKeyParams;
      if (keyBytes.length == 32) {
        publicKeyParams = new Ed25519PublicKeyParameters(keyBytes, 0);
      } else {
        // Extract from X.509 format (not needed for demo device, but for compatibility)
        org.bouncycastle.asn1.x509.SubjectPublicKeyInfo publicKeyInfo =
            org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(keyBytes);
        byte[] rawKey = publicKeyInfo.getPublicKeyData().getOctets();
        publicKeyParams = new Ed25519PublicKeyParameters(rawKey, 0);
      }
      Ed25519Signer verifier = new Ed25519Signer();
      verifier.init(false, publicKeyParams);
      byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
      verifier.update(dataBytes, 0, dataBytes.length);
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      return verifier.verifySignature(signatureBytes);
    } catch (Exception e) {
      return false;
    }
  }

  @Test
  void testSignatureCompatibility() {
    // Test data
    String testData = "test-enrollment-proof-token-123";

    // Generate signatures using both approaches
    String deviceSignature =
        deviceCryptoService.signStringToBase64(testData, testKeyPair.base64PrivateKey());
    String coreSignature = simulateCoreSignature(testData, testKeyPair.base64PrivateKey());

    // Log for debugging
    System.out.println("Test data: " + testData);
    System.out.println("Device signature: " + deviceSignature);
    System.out.println("Core signature: " + coreSignature);
    System.out.println("Signatures match: " + deviceSignature.equals(coreSignature));

    // Verify that both signatures can be validated by the core service
    String publicKeyBase64 = testKeyPair.base64PublicKey();

    boolean deviceSignatureValid =
        simulateCoreValidation(testData, deviceSignature, publicKeyBase64);
    boolean coreSignatureValid = simulateCoreValidation(testData, coreSignature, publicKeyBase64);

    System.out.println("Device signature valid: " + deviceSignatureValid);
    System.out.println("Core signature valid: " + coreSignatureValid);

    // Both signatures should be valid
    assertTrue(deviceSignatureValid, "Device signature should be valid");
    assertTrue(coreSignatureValid, "Core signature should be valid");

    // The signatures should be identical if the implementations are compatible
    assertEquals(deviceSignature, coreSignature, "Signatures should be identical");
  }

  @Test
  void testCrossValidation() {
    // Test data
    String testData = "enrollment-proof-token-456";

    // Generate signature with device service
    String deviceSignature =
        deviceCryptoService.signStringToBase64(testData, testKeyPair.base64PrivateKey());

    // Validate with simulated core service
    String publicKeyBase64 = testKeyPair.base64PublicKey();
    boolean isValid = simulateCoreValidation(testData, deviceSignature, publicKeyBase64);

    System.out.println("Cross-validation test:");
    System.out.println("Test data: " + testData);
    System.out.println("Device signature: " + deviceSignature);
    System.out.println("Public key: " + publicKeyBase64);
    System.out.println("Validation result: " + isValid);

    assertTrue(isValid, "Device signature should be validated by core service");
  }
}
