package org.ezkey.demo.device.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.ezkey.demo.device.service.DeviceCryptoService.ECP256DeviceKeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies device ECDSA signatures from {@link DeviceCryptoService} validate the same way as
 * ezkey-core {@code SignatureService.validateSignature} (JCA SHA256withECDSA).
 */
public class SignatureCompatibilityTest {

  private DeviceCryptoService deviceCryptoService;

  private ECP256DeviceKeyPair testKeyPair;

  @BeforeEach
  void setUp() {
    deviceCryptoService = new DeviceCryptoService();
    testKeyPair = deviceCryptoService.generateDeviceKeyPair();
  }

  private boolean simulateCoreValidation(
      String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
      PublicKey publicKey =
          KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
      Signature verifier = Signature.getInstance("SHA256withECDSA");
      verifier.initVerify(publicKey);
      verifier.update(data.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(Base64.getDecoder().decode(signatureBase64));
    } catch (Exception e) {
      return false;
    }
  }

  @Test
  void testSignatureCompatibility() {
    String testData = "test-enrollment-proof-token-123";
    String deviceSignature =
        deviceCryptoService.signStringToBase64(testData, testKeyPair.base64PrivateKey());
    assertTrue(
        simulateCoreValidation(testData, deviceSignature, testKeyPair.base64PublicKey()),
        "Device signature should validate with JCA (same as ezkey-core)");
  }

  @Test
  void testCrossValidation() {
    String testData = "enrollment-proof-token-456";
    String deviceSignature =
        deviceCryptoService.signStringToBase64(testData, testKeyPair.base64PrivateKey());
    assertTrue(
        simulateCoreValidation(testData, deviceSignature, testKeyPair.base64PublicKey()),
        "Device signature should validate with JCA (same as ezkey-core)");
  }
}
