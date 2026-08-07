/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: SignatureService
 * Description: Cryptographic signature service (JDK-only: Ed25519 for integration, EC P-256 for device).
 */
package org.ezkey.signature;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic signature service: <strong>Ed25519</strong> for per-enrollment integration keys
 * (minimal wire format: raw 32-byte public key and 64-byte signatures as Base64URL without padding)
 * and <strong>EC P-256 (secp256r1) ECDSA-SHA256</strong> for device keys (PKCS#8 / SPKI Base64, DER
 * signatures). Implemented with the JDK only (no Bouncy Castle).
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class SignatureService {

  private static final Logger logger = LoggerFactory.getLogger(SignatureService.class);

  private static final int PROOF_TOKEN_RANDOM_BYTES = 32;

  private static final int PROOF_TOKEN_SALT_BYTES = 16;

  private static final int ED25519_PUBLIC_KEY_BYTES = 32;

  private static final int ED25519_SIGNATURE_BYTES = 64;

  private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

  public SignatureService() {}

  /**
   * Generates an Ed25519 key pair for integration signing. Private key is PKCS#8 (standard Base64);
   * public key is raw 32 bytes (Base64URL without padding).
   *
   * @throws RuntimeException if Ed25519 algorithm is unavailable or key generation fails
   */
  public Ed25519KeyPair generateEd25519KeyPair() {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
      kpg.initialize(NamedParameterSpec.ED25519);
      KeyPair kp = kpg.generateKeyPair();
      String priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
      byte[] spki = kp.getPublic().getEncoded();
      byte[] raw = Arrays.copyOfRange(spki, spki.length - 32, spki.length);
      String pub = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
      return new Ed25519KeyPair(priv, pub);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Ed25519 key pair generation failed", e);
    }
  }

  /**
   * Signs UTF-8 data with an Ed25519 PKCS#8 private key (standard Base64). Returns Base64URL
   * without padding over the raw 64-byte signature.
   *
   * @param data the data to sign (UTF-8 encoded)
   * @param base64Pkcs8PrivateKey Ed25519 private key in PKCS#8 format (standard Base64)
   * @return Base64URL-encoded signature (64 bytes, no padding)
   * @throws RuntimeException if key is malformed, algorithm unavailable, or signing fails
   */
  public String signIntegrationPayload(String data, String base64Pkcs8PrivateKey) {
    Objects.requireNonNull(data, "Data cannot be null");
    Objects.requireNonNull(base64Pkcs8PrivateKey, "Private key cannot be null");
    try {
      byte[] pkcs8 = Base64.getDecoder().decode(base64Pkcs8PrivateKey);
      PrivateKey privateKey =
          KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      Signature sig = Signature.getInstance("Ed25519");
      sig.initSign(privateKey);
      sig.update(data.getBytes(StandardCharsets.UTF_8));
      byte[] signature = sig.sign();
      return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    } catch (IllegalArgumentException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to sign integration payload", e);
    }
  }

  /**
   * Verifies an Ed25519 signature over exact UTF-8 data. Public key and signature are Base64URL
   * without padding (raw 32- and 64-byte values). Accepts standard Base64 for decoding if needed.
   *
   * <p><b>Security Note:</b> This method uses a broad {@code catch (Exception)} intentionally to
   * avoid leaking information about <em>why</em> verification failed. Distinguishing between
   * "malformed key", "invalid signature", or "encoding error" would constitute a side-channel that
   * attackers could exploit. All verification failures return {@code false} with minimal logging.
   *
   * @param data the data that was signed (UTF-8)
   * @param signatureBase64Url Base64URL signature (64 bytes, no padding)
   * @param publicKeyBase64Url Base64URL public key (32 bytes, no padding)
   * @return {@code true} if signature is valid, {@code false} otherwise
   */
  public boolean verifyIntegrationSignature(
      String data, String signatureBase64Url, String publicKeyBase64Url) {
    try {
      byte[] pubRaw = decodeFlexibleBase64ToBytes(publicKeyBase64Url);
      byte[] sigRaw = decodeFlexibleBase64ToBytes(signatureBase64Url);
      if (pubRaw == null
          || sigRaw == null
          || pubRaw.length != ED25519_PUBLIC_KEY_BYTES
          || sigRaw.length != ED25519_SIGNATURE_BYTES) {
        return false;
      }
      byte[] spki = Ed25519SpkiBytes.rawPublicKeyToSpki(pubRaw);
      PublicKey publicKey =
          KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
      Signature verifier = Signature.getInstance("Ed25519");
      verifier.initVerify(publicKey);
      verifier.update(data.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(sigRaw);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.debug("Ed25519 integration signature verification failed", e);
      return false;
    }
  }

  /**
   * Normalizes integration public key material to canonical Base64URL (no padding) over 32 raw
   * bytes. Accepts PEM-style wrappers; strips whitespace. If the value is not decodable to 32
   * bytes, returns trimmed input (fail-soft for logging).
   */
  public String normalizeIntegrationPublicKeyToBase64(String base64PublicKey) {
    if (base64PublicKey == null || base64PublicKey.isBlank()) {
      return base64PublicKey;
    }
    String trimmed = stripPemAndWhitespace(base64PublicKey);
    byte[] raw = decodeFlexibleBase64ToBytes(trimmed);
    if (raw != null && raw.length == ED25519_PUBLIC_KEY_BYTES) {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
    logger.warn("Integration public key is not 32 bytes after decode; returning trimmed string");
    return trimmed;
  }

  /**
   * Signs UTF-8 data with an EC P-256 PKCS#8 private key. Returns standard Base64 over DER ECDSA
   * signature, with {@code s} normalized to low-S for Conscrypt/Android parity.
   *
   * @param data the data to sign (UTF-8 encoded)
   * @param base64Pkcs8PrivateKey EC P-256 private key in PKCS#8 format (standard Base64)
   * @return Base64-encoded DER ECDSA signature
   * @throws RuntimeException if key is malformed, algorithm unavailable, or signing fails
   */
  public String signEcdsaSha256(String data, String base64Pkcs8PrivateKey) {
    Objects.requireNonNull(data, "Data cannot be null");
    Objects.requireNonNull(base64Pkcs8PrivateKey, "Private key cannot be null");
    try {
      byte[] pkcs8 = Base64.getDecoder().decode(base64Pkcs8PrivateKey);
      PrivateKey privateKey =
          KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      Signature sig = Signature.getInstance("SHA256withECDSA");
      sig.initSign(privateKey);
      sig.update(data.getBytes(StandardCharsets.UTF_8));
      byte[] der = sig.sign();
      ECPrivateKey ecPriv = (ECPrivateKey) privateKey;
      BigInteger n = ecPriv.getParams().getOrder();
      BigInteger[] rs = EcdsaDerCodec.decodeSignature(der);
      if (rs == null) {
        throw new IllegalStateException("Invalid ECDSA DER from provider");
      }
      BigInteger s = normalizeEcdsaSToLowS(rs[1], n);
      if (!s.equals(rs[1])) {
        der = EcdsaDerCodec.encodeSignature(rs[0], s);
      }
      return Base64.getEncoder().encodeToString(der);
    } catch (IllegalArgumentException | IllegalStateException | GeneralSecurityException e) {
      throw new RuntimeException("Failed to generate ECDSA signature", e);
    }
  }

  /**
   * Verifies ECDSA-SHA256 (DER) over UTF-8 data using an EC P-256 X.509 public key (standard Base64
   * SPKI).
   *
   * <p><b>Security Note:</b> This method uses a broad {@code catch (Exception)} intentionally to
   * avoid leaking information about <em>why</em> verification failed. Distinguishing between
   * "malformed key", "invalid signature", or "encoding error" would constitute a side-channel that
   * attackers could exploit. All verification failures return {@code false} with minimal logging.
   *
   * @param data the data that was signed (UTF-8)
   * @param signatureBase64 Base64-encoded DER ECDSA signature
   * @param base64PublicKey EC P-256 public key in X.509 SPKI format (standard Base64)
   * @return {@code true} if signature is valid, {@code false} otherwise
   */
  public boolean validateSignature(String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
      PublicKey publicKey =
          KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      if (!isCanonicalLowSEcdsaSignature(publicKey, signatureBytes)) {
        logger.debug("ECDSA signature rejected: non-canonical high-S form");
        return false;
      }
      Signature verifier = Signature.getInstance("SHA256withECDSA");
      verifier.initVerify(publicKey);
      verifier.update(data.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(signatureBytes);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.debug("ECDSA signature validation failed", e);
      return false;
    }
  }

  /**
   * Rejects ECDSA signatures whose {@code s} component is not low-S (canonical form).
   *
   * <p>JDK {@code SHA256withECDSA} accepts both low-S and high-S ({@code s'} = {@code n - s})
   * malleable variants. Ezkey signs with low-S only ({@link #signEcdsaSha256}); verification must
   * reject high-S for defense in depth (SEC-012).
   */
  private static boolean isCanonicalLowSEcdsaSignature(PublicKey publicKey, byte[] signatureBytes) {
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

  /**
   * Verifies ECDSA-SHA256 using JCA (same path as Android Conscrypt). Retained for tests and
   * diagnostics.
   */
  public boolean validateSignatureWithJcaSha256WithEcdsa(
      String data, String signatureBase64, String base64PublicKey) {
    return validateSignature(data, signatureBase64, base64PublicKey);
  }

  /**
   * Generates an EC P-256 key pair (PKCS#8 private, SPKI public), standard Base64.
   *
   * @return EC P-256 key pair with Base64-encoded keys
   * @throws RuntimeException if EC algorithm is unavailable or key generation fails
   */
  public ECP256KeyPair generateECP256KeyPair() {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
      kpg.initialize(new ECGenParameterSpec("secp256r1"));
      KeyPair kp = kpg.generateKeyPair();
      String priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
      String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
      return new ECP256KeyPair(priv, pub);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("EC P-256 key pair generation failed", e);
    }
  }

  private static BigInteger normalizeEcdsaSToLowS(BigInteger s, BigInteger n) {
    BigInteger halfN = n.shiftRight(1);
    if (s.compareTo(halfN) > 0) {
      return n.subtract(s);
    }
    return s;
  }

  private static String stripPemAndWhitespace(String input) {
    if (input.contains("-----BEGIN")) {
      StringBuilder sb = new StringBuilder();
      for (String line : input.lines().toList()) {
        if (!line.isBlank() && !line.startsWith("-----")) {
          sb.append(line.trim());
        }
      }
      return sb.toString();
    }
    return input.replaceAll("\\s", "");
  }

  /** Decodes Base64URL (no padding) or standard Base64 to bytes; returns null on failure. */
  static byte[] decodeFlexibleBase64ToBytes(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String t = stripPemAndWhitespace(value);
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

  /**
   * Generates a cryptographically secure random challenge with the specified number of digits.
   *
   * @param digits number of digits (1-6)
   * @return random challenge with exactly {@code digits} digits (no leading zeros)
   * @throws IllegalArgumentException if digits is not in range 1-6
   */
  public Integer generateSecureChallenge(int digits) {
    if (digits < 1 || digits > 6) {
      throw new IllegalArgumentException(
          "Challenge digits must be between 1 and 6, got: " + digits);
    }
    int minValue = (int) Math.pow(10, digits - 1);
    int maxValue = (int) Math.pow(10, digits) - 1;
    return minValue + secureRandom.nextInt(maxValue - minValue + 1);
  }

  /**
   * Generates a proof token consisting of random bytes and salt, encoded as Base64URL.
   *
   * @return proof token in format "randomPart.saltPart" (Base64URL, no padding)
   */
  public String generateProofToken() {
    byte[] randomBytes = new byte[PROOF_TOKEN_RANDOM_BYTES];
    secureRandom.nextBytes(randomBytes);
    byte[] salt = new byte[PROOF_TOKEN_SALT_BYTES];
    secureRandom.nextBytes(salt);
    String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    String saltPart = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
    return randomPart + "." + saltPart;
  }
}
