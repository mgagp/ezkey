package org.ezkey.demo.device.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic utilities for the simulated device.
 *
 * <p>Generates Ed25519 keys and performs signing operations compatible with the Ezkey demo flows.
 * This service is intentionally independent from {@code ezkey-core} to keep the demo-device module
 * standalone.
 *
 * @since 2025
 */
@Service
public class DeviceCryptoService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceCryptoService.class);

  private static final int ED25519_KEY_SIZE_BYTES = 32;

  private static final int ED25519_SIGNATURE_SIZE_BYTES = 64;

  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Generates a new Ed25519 key pair for the device.
   *
   * @return Ed25519 key pair (private key seed and public key, both Base64 encoded)
   */
  public Ed25519DeviceKeyPair generateDeviceKeyPair() {
    try {
      Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
      keyGen.init(new Ed25519KeyGenerationParameters(secureRandom));
      AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
      Ed25519PrivateKeyParameters privateKey =
          (Ed25519PrivateKeyParameters) keyPair.getPrivate();
      Ed25519PublicKeyParameters publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();
      String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
      String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
      return new Ed25519DeviceKeyPair(privateKeyBase64, publicKeyBase64);
    } catch (Exception e) {
      logger.error("Failed to generate device key pair", e);
      throw new IllegalStateException("Unable to generate Ed25519 key pair", e);
    }
  }

  /**
   * Immutable container for Ed25519 device key pair.
   *
   * @param base64PrivateKey Base64-encoded Ed25519 private key seed (32 bytes raw)
   * @param base64PublicKey Base64-encoded Ed25519 public key (32 bytes raw)
   */
  public record Ed25519DeviceKeyPair(String base64PrivateKey, String base64PublicKey) {}

  /**
   * Signs raw bytes using the provided Ed25519 private key seed and returns the signature bytes.
   *
   * @param dataToSign bytes to sign
   * @param base64PrivateKey Base64-encoded Ed25519 private key seed (32 bytes raw)
   * @return signature bytes (64 bytes)
   */
  public byte[] signBytes(byte[] dataToSign, String base64PrivateKey) {
    Objects.requireNonNull(dataToSign, "dataToSign must not be null");
    Objects.requireNonNull(base64PrivateKey, "base64PrivateKey must not be null");
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
      if (keyBytes.length != ED25519_KEY_SIZE_BYTES) {
        throw new IllegalArgumentException(
            "Ed25519 private key must be 32 bytes, got: " + keyBytes.length);
      }
      Ed25519PrivateKeyParameters privateKey = new Ed25519PrivateKeyParameters(keyBytes, 0);
      Ed25519Signer signer = new Ed25519Signer();
      signer.init(true, privateKey);
      signer.update(dataToSign, 0, dataToSign.length);
      return signer.generateSignature();
    } catch (Exception e) {
      logger.error("Failed to sign data", e);
      throw new IllegalStateException("Signing failure", e);
    }
  }

  /**
   * Signs a UTF-8 string and returns the Base64-encoded signature.
   *
   * @param content string to sign
   * @param base64PrivateKey Base64-encoded Ed25519 private key seed (32 bytes raw)
   * @return Base64 signature string (64 bytes raw)
   */
  public String signStringToBase64(String content, String base64PrivateKey) {
    // Use default charset to match SignatureService implementation
    byte[] signature = signBytes(content.getBytes(StandardCharsets.UTF_8), base64PrivateKey);
    return Base64.getEncoder().encodeToString(signature);
  }


  /**
   * Builds a simple device proof by concatenating sorted key=value pairs and signing the result.
   * This is deliberately simple for demo use; the server-side verifier only requires a valid
   * device-controlled signature over expected fields.
   *
   * @param claims key/value pairs to include in the proof
   * @param base64PrivateKey Base64-encoded Ed25519 private key seed (32 bytes raw)
   * @return Base64-encoded signature over the canonicalized claims string (64 bytes raw)
   */
  public String buildAndSignDeviceProof(Map<String, Object> claims, String base64PrivateKey) {
    String canonical =
        claims.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + String.valueOf(e.getValue()))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    return signStringToBase64(canonical, base64PrivateKey);
  }

  /**
   * Generates a cryptographically secure proof token for authentication operations.
   *
   * <p>This method creates a random, unpredictable token suitable for use as a payload to be signed
   * in authentication flows. The token is composed of:
   *
   * <ul>
   *   <li>256 bits (32 bytes) of cryptographically secure random data
   *   <li>A timestamp (milliseconds since epoch)
   *   <li>An additional 128 bits (16 bytes) of random salt
   * </ul>
   *
   * The result is encoded as a Base64 URL-safe string (without padding), concatenating the random
   * bytes, timestamp, and salt, separated by a period ('.').
   *
   * @return a Base64 URL-safe encoded proof token string
   */
  public String generateProofToken() {
    try {
      SecureRandom secureRandom = new SecureRandom();
      byte[] randomBytes = new byte[32]; // 256 bits
      secureRandom.nextBytes(randomBytes);
      long timestamp = System.currentTimeMillis();
      byte[] salt = new byte[16]; // 128 bits
      secureRandom.nextBytes(salt);
      String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
      String saltPart = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
      return randomPart + "." + timestamp + "." + saltPart;
    } catch (Exception e) {
      logger.error("Failed to generate proof token", e);
      throw new IllegalStateException("Unable to generate proof token", e);
    }
  }

  /**
   * Validates a digital signature against the provided data using Ed25519 public key.
   *
   * <p>This method verifies the authenticity and integrity of data by validating its associated
   * digital signature. It confirms that the data was signed by the holder of the corresponding
   * private key and has not been altered since signing.
   *
   * @param data the original data that was signed
   * @param signatureBase64 the Base64-encoded digital signature to validate (64 bytes raw)
   * @param base64PublicKey the Base64-encoded Ed25519 public key (32 bytes raw)
   * @return <code>true</code> if the signature is valid, <code>false</code> otherwise
   */
  public boolean validateSignature(String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
      if (keyBytes.length != ED25519_KEY_SIZE_BYTES) {
        logger.warn("Ed25519 public key must be 32 bytes, got: {}", keyBytes.length);
        return false;
      }
      Ed25519PublicKeyParameters publicKey = new Ed25519PublicKeyParameters(keyBytes, 0);
      Ed25519Signer verifier = new Ed25519Signer();
      verifier.init(false, publicKey); // false = verification mode
      byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
      verifier.update(dataBytes, 0, dataBytes.length);
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      if (signatureBytes.length != ED25519_SIGNATURE_SIZE_BYTES) {
        logger.warn("Ed25519 signature must be 64 bytes, got: {}", signatureBytes.length);
        return false;
      }
      return verifier.verifySignature(signatureBytes);
    } catch (Exception e) {
      logger.error("Signature validation failed", e);
      return false;
    }
  }
}
