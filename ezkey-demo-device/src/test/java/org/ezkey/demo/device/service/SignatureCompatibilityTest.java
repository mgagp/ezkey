package org.ezkey.demo.device.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.ezkey.demo.device.service.DeviceCryptoService.ECP256DeviceKeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies device ECDSA signatures from {@link DeviceCryptoService} match Auth API SEC-012
 * expectations: low-S DER only (JCA alone is insufficient because it accepts high-S).
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
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      if (!isCanonicalLowS(publicKey, signatureBytes)) {
        return false;
      }
      Signature verifier = Signature.getInstance("SHA256withECDSA");
      verifier.initVerify(publicKey);
      verifier.update(data.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(signatureBytes);
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isCanonicalLowS(PublicKey publicKey, byte[] signatureBytes) {
    if (!(publicKey instanceof ECPublicKey ecPublicKey)) {
      return false;
    }
    BigInteger[] rs = EcdsaDerCodec.decodeSignature(signatureBytes);
    if (rs == null) {
      return false;
    }
    BigInteger halfN = ecPublicKey.getParams().getOrder().shiftRight(1);
    return rs[1].compareTo(halfN) <= 0;
  }

  @Test
  void testSignatureCompatibility() {
    String testData = "test-enrollment-proof-token-123";
    String deviceSignature =
        deviceCryptoService.signStringToBase64(testData, testKeyPair.base64PrivateKey());
    assertTrue(
        simulateCoreValidation(testData, deviceSignature, testKeyPair.base64PublicKey()),
        "Device signature should validate with low-S + JCA (Auth API SEC-012 path)");
  }

  @Test
  void testCrossValidation() {
    String testData = "enrollment-proof-token-456";
    String deviceSignature =
        deviceCryptoService.signStringToBase64(testData, testKeyPair.base64PrivateKey());
    assertTrue(
        simulateCoreValidation(testData, deviceSignature, testKeyPair.base64PublicKey()),
        "Device signature should validate with low-S + JCA (Auth API SEC-012 path)");
  }

  @Test
  void signStringToBase64AlwaysEmitsLowS() {
    // Raw JCA ECDSA is ~50% high-S; after normalize, every sample must be low-S.
    for (int i = 0; i < 64; i++) {
      String data = "device-proof-sample-" + i;
      String signature =
          deviceCryptoService.signStringToBase64(data, testKeyPair.base64PrivateKey());
      assertTrue(
          simulateCoreValidation(data, signature, testKeyPair.base64PublicKey()),
          "Sample " + i + " must be low-S and JCA-valid");
    }
  }

  @Test
  void validateSignatureRejectsHighSVariant() throws Exception {
    String data = "pending-proof-token-for-high-s";
    String lowSSignature =
        deviceCryptoService.signStringToBase64(data, testKeyPair.base64PrivateKey());
    assertTrue(deviceCryptoService.validateSignature(data, lowSSignature, testKeyPair.base64PublicKey()));

    byte[] der = Base64.getDecoder().decode(lowSSignature);
    BigInteger[] rs = EcdsaDerCodec.decodeSignature(der);
    PublicKey publicKey =
        KeyFactory.getInstance("EC")
            .generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(testKeyPair.base64PublicKey())));
    BigInteger n = ((ECPublicKey) publicKey).getParams().getOrder();
    BigInteger highS = n.subtract(rs[1]);
    String highSSignature =
        Base64.getEncoder().encodeToString(EcdsaDerCodec.encodeSignature(rs[0], highS));

    assertFalse(
        deviceCryptoService.validateSignature(data, highSSignature, testKeyPair.base64PublicKey()),
        "High-S malleable variant must be rejected (SEC-012)");
  }
}
