package org.ezkey.demo.device.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic utilities for the simulated device (JDK-only, no Bouncy Castle).
 *
 * <p><b>Device keys:</b> EC P-256 (secp256r1), ECDSA-SHA256, DER signatures (Base64).
 *
 * <p><b>Integration verification:</b> Ed25519 raw public key (32 bytes, Base64URL) and signature
 * (64 bytes, Base64URL), matching the Auth API integration material.
 *
 * @since 2025
 */
@Service
public class DeviceCryptoService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceCryptoService.class);

  private static final int ED25519_PK_LEN = 32;
  private static final int ED25519_SIG_LEN = 64;

  private static final byte[] ED25519_SPKI_PREFIX =
      new byte[] {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

  private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

  /**
   * Generates a new EC P-256 key pair for the device.
   *
   * @return EC P-256 key pair (private PKCS#8, public SPKI, standard Base64)
   */
  public ECP256DeviceKeyPair generateDeviceKeyPair() {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
      kpg.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair kp = kpg.generateKeyPair();
      String priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
      String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
      return new ECP256DeviceKeyPair(priv, pub);
    } catch (Exception e) {
      logger.error("Failed to generate device key pair", e);
      throw new IllegalStateException("Unable to generate EC P-256 key pair", e);
    }
  }

  /**
   * EC P-256 device key material encoded as standard Base64 (PKCS#8 private, SPKI public).
   *
   * @param base64PrivateKey PKCS#8 private key, Base64-encoded
   * @param base64PublicKey X.509/SPKI public key, Base64-encoded
   */
  public record ECP256DeviceKeyPair(String base64PrivateKey, String base64PublicKey) {}

  /**
   * Signs UTF-8 content with an EC P-256 PKCS#8 private key; returns standard Base64 over DER ECDSA
   * signature.
   */
  public String signStringToBase64(String content, String base64PrivateKey) {
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(base64PrivateKey, "base64PrivateKey must not be null");
    try {
      byte[] pkcs8 = Base64.getDecoder().decode(base64PrivateKey);
      PrivateKey privateKey =
          KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      Signature sig = Signature.getInstance("SHA256withECDSA");
      sig.initSign(privateKey);
      sig.update(content.getBytes(StandardCharsets.UTF_8));
      byte[] der = sig.sign();
      return Base64.getEncoder().encodeToString(der);
    } catch (Exception e) {
      logger.error("Failed to sign data", e);
      throw new IllegalStateException("Signing failure", e);
    }
  }

  public String buildAndSignDeviceProof(Map<String, Object> claims, String base64PrivateKey) {
    String canonical =
        claims.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    return signStringToBase64(canonical, base64PrivateKey);
  }

  public String generateProofToken() {
    try {
      byte[] randomBytes = new byte[32];
      secureRandom.nextBytes(randomBytes);
      byte[] salt = new byte[16];
      secureRandom.nextBytes(salt);
      String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
      String saltPart = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
      return randomPart + "." + saltPart;
    } catch (Exception e) {
      logger.error("Failed to generate proof token", e);
      throw new IllegalStateException("Unable to generate proof token", e);
    }
  }

  /**
   * Verifies a signature: if the public key decodes to 32 bytes, treats Ed25519 (integration); else
   * EC P-256 ECDSA (device / SPKI).
   */
  public boolean validateSignature(String data, String signatureEncoded, String publicKeyEncoded) {
    try {
      byte[] pubRaw = decodeFlexibleBase64(publicKeyEncoded);
      byte[] sigRaw = decodeFlexibleBase64(signatureEncoded);
      if (pubRaw != null
          && pubRaw.length == ED25519_PK_LEN
          && sigRaw != null
          && sigRaw.length == ED25519_SIG_LEN) {
        return verifyEd25519(data, sigRaw, pubRaw);
      }
      return verifyEcdsa(data, signatureEncoded, publicKeyEncoded);
    } catch (Exception e) {
      logger.error("Signature validation failed", e);
      return false;
    }
  }

  private static boolean verifyEd25519(String data, byte[] sigRaw, byte[] pubRaw) {
    try {
      byte[] spki = new byte[44];
      System.arraycopy(ED25519_SPKI_PREFIX, 0, spki, 0, 12);
      System.arraycopy(pubRaw, 0, spki, 12, 32);
      PublicKey publicKey =
          KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(publicKey);
      verifier.update(data.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(sigRaw);
    } catch (Exception e) {
      logger.debug("Ed25519 verify failed", e);
      return false;
    }
  }

  private static boolean verifyEcdsa(String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
      PublicKey publicKey =
          KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
      Signature verifier = Signature.getInstance("SHA256withECDSA");
      verifier.initVerify(publicKey);
      verifier.update(data.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(Base64.getDecoder().decode(signatureBase64));
    } catch (Exception e) {
      logger.debug("ECDSA verify failed", e);
      return false;
    }
  }

  private static byte[] decodeFlexibleBase64(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String t = value.replaceAll("\\s", "");
    try {
      return Base64.getUrlDecoder().decode(t);
    } catch (IllegalArgumentException e1) {
      try {
        return Base64.getDecoder().decode(t);
      } catch (IllegalArgumentException e2) {
        return null;
      }
    }
  }
}
