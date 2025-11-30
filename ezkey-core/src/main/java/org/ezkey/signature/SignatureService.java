/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: SignatureService
 * Description: Cryptographic signature service providing digital signature generation and validation.
 */

package org.ezkey.signature;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ezkey.config.EzkeyCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic signature service providing digital signature generation and validation.
 *
 * <p>This service is a fundamental component of the Ezkey security architecture, implementing
 * Ed25519 digital signatures. It provides the cryptographic foundation for ensuring data
 * integrity, authenticity, and non-repudiation across the Ezkey platform.
 *
 * <p><b>Cryptographic Implementation:</b>
 *
 * <ul>
 *   <li><b>Algorithm:</b> Ed25519 (pure Ed25519, not EdDSA)
 *   <li><b>Key Format:</b> 32-byte private key seed, 32-byte public key (Ed25519 standard)
 *   <li><b>Encoding:</b> Base64 for key and signature representation
 *   <li><b>Security Level:</b> Production-grade cryptographic strength (equivalent to RSA-3072)
 * </ul>
 *
 * <p><b>Security Applications:</b>
 *
 * <ul>
 *   <li><b>Authentication:</b> Verifying the origin of messages and requests
 *   <li><b>Integrity:</b> Ensuring data has not been tampered with
 *   <li><b>Non-repudiation:</b> Providing proof of message origin
 *   <li><b>Trust:</b> Establishing secure communication channels
 * </ul>
 *
 * <p><b>Usage Context:</b> This service is used throughout the Ezkey platform for securing API
 * communications, validating enrollment requests, and ensuring the integrity of authentication
 * attempts. It forms the cryptographic backbone that enables secure MFA and passkey operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Security Level:</b> Production-grade cryptographic implementation
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.bouncycastle.crypto.signers.Ed25519Signer
 * @see org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
 * @see org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
 */
@Service
public class SignatureService {

  private static final Logger logger = LoggerFactory.getLogger(SignatureService.class);

  private static final int PROOF_TOKEN_RANDOM_BYTES = 32; // 256 bits

  private static final int PROOF_TOKEN_SALT_BYTES = 16; // 128 bits

  private static final String ED25519_ALGORITHM = "Ed25519";

  // Reuse a single SecureRandom instance
  private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

  private final EzkeyCoreProperties ezkeyCoreProperties;

  static {
    // Register BouncyCastle provider if not already registered
    if (java.security.Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      java.security.Security.addProvider(new BouncyCastleProvider());
    }
  }

  /**
   * Constructs the signature service with configuration properties.
   *
   * @param ezkeyCoreProperties the ezkey core configuration properties
   */
  public SignatureService(EzkeyCoreProperties ezkeyCoreProperties) {
    this.ezkeyCoreProperties = ezkeyCoreProperties;
  }

  /**
   * Generates a digital signature for the provided data using Ed25519 private key.
   *
   * <p>This method creates a cryptographically secure digital signature that can be used to verify
   * the authenticity and integrity of the original data. The signature is generated using Ed25519,
   * providing production-grade security with compact signatures.
   *
   * <p><b>Cryptographic Process:</b>
   *
   * <ol>
   *   <li>Decode the Base64-encoded private key seed (32 bytes)
   *   <li>Create Ed25519 private key parameters from seed
   *   <li>Initialize Ed25519 signer
   *   <li>Sign the data bytes
   *   <li>Return Base64-encoded signature (64 bytes)
   * </ol>
   *
   * <p><b>Security Considerations:</b>
   *
   * <ul>
   *   <li>Private keys must be securely stored and managed
   *   <li>Input data should be validated before signing
   *   <li>Generated signatures should be transmitted securely
   * </ul>
   *
   * @param data the data to be signed (typically JSON payload or message content)
   * @param base64PrivateKey the Base64-encoded Ed25519 private key seed (32 bytes raw) or PKCS#8
   *     format
   * @return Base64-encoded digital signature (64 bytes raw)
   * @throws RuntimeException if signature generation fails due to cryptographic errors
   * @see org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
   * @see org.bouncycastle.crypto.signers.Ed25519Signer
   */
  public String generateSignature(String data, String base64PrivateKey) {
    Objects.requireNonNull(data, "Data cannot be null");
    Objects.requireNonNull(base64PrivateKey, "Private key cannot be null");
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
      // Handle both raw 32-byte keys and PKCS#8 encoded keys
      Ed25519PrivateKeyParameters privateKeyParams;
      if (keyBytes.length == 32) {
        // Raw 32-byte Ed25519 private key seed
        privateKeyParams = new Ed25519PrivateKeyParameters(keyBytes, 0);
      } else {
        // PKCS#8 encoded key - extract the raw key bytes
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(keyBytes);
        byte[] rawKey = privateKeyInfo.getPrivateKey().getOctets();
        if (rawKey.length != 32) {
          throw new IllegalArgumentException(
              "Ed25519 private key must be 32 bytes, got: " + rawKey.length);
        }
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

  /**
   * Validates a digital signature against the provided data using Ed25519 public key.
   *
   * <p>This method verifies the authenticity and integrity of data by validating its associated
   * digital signature. It confirms that the data was signed by the holder of the corresponding
   * private key and has not been altered since signing.
   *
   * <p><b>Cryptographic Process:</b>
   *
   * <ol>
   *   <li>Decode the Base64-encoded public key (32 bytes)
   *   <li>Create Ed25519 public key parameters
   *   <li>Initialize Ed25519 verifier
   *   <li>Verify the signature against the data
   *   <li>Return verification result
   * </ol>
   *
   * <p><b>Security Behavior:</b>
   *
   * <ul>
   *   <li>Returns <code>false</code> for any cryptographic errors (fail-secure)
   *   <li>Validates both signature format and cryptographic correctness
   *   <li>Ensures data integrity and authenticity verification
   * </ul>
   *
   * @param data the original data that was signed
   * @param signatureBase64 the Base64-encoded digital signature to validate (64 bytes raw)
   * @param base64PublicKey the Base64-encoded Ed25519 public key (32 bytes raw) or X.509 format
   * @return <code>true</code> if the signature is valid, <code>false</code> otherwise
   * @see org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
   * @see org.bouncycastle.crypto.signers.Ed25519Signer
   */
  public boolean validateSignature(String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
      // Handle both raw 32-byte keys and X.509 encoded keys
      Ed25519PublicKeyParameters publicKeyParams;
      if (keyBytes.length == 32) {
        // Raw 32-byte Ed25519 public key
        publicKeyParams = new Ed25519PublicKeyParameters(keyBytes, 0);
      } else {
        // X.509 encoded key - extract the raw key bytes
        SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyBytes);
        byte[] rawKey = publicKeyInfo.getPublicKeyData().getOctets();
        if (rawKey.length != 32) {
          logger.warn("Ed25519 public key must be 32 bytes, got: {}", rawKey.length);
          return false;
        }
        publicKeyParams = new Ed25519PublicKeyParameters(rawKey, 0);
      }
      Ed25519Signer verifier = new Ed25519Signer();
      verifier.init(false, publicKeyParams);
      byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
      verifier.update(dataBytes, 0, dataBytes.length);
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      if (signatureBytes.length != 64) {
        logger.warn("Ed25519 signature must be 64 bytes, got: {}", signatureBytes.length);
        return false;
      }
      return verifier.verifySignature(signatureBytes);
    } catch (Exception e) {
      logger.debug("Signature validation failed", e);
      return false;
    }
  }

  /**
   * Generates a new Ed25519 key pair and returns it as Base64-encoded strings.
   *
   * <p>The generated private key is a 32-byte seed (Ed25519 standard) and the public key is a
   * 32-byte public key (Ed25519 standard). Keys are generated using BouncyCastle's Ed25519
   * implementation and encoded with Base64 for storage/transmission.
   *
   * @return an immutable {@link Ed25519KeyPair} containing Base64-encoded keys
   * @throws RuntimeException if key generation fails due to cryptographic errors
   */
  public Ed25519KeyPair generateEd25519KeyPair() {
    try {
      Ed25519KeyPairGenerator keyGen = new Ed25519KeyPairGenerator();
      keyGen.init(new Ed25519KeyGenerationParameters(secureRandom));
      AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
      Ed25519PrivateKeyParameters privateKey =
          (Ed25519PrivateKeyParameters) keyPair.getPrivate();
      Ed25519PublicKeyParameters publicKey = (Ed25519PublicKeyParameters) keyPair.getPublic();
      // Get raw 32-byte keys
      String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
      String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
      return new Ed25519KeyPair(privateKeyBase64, publicKeyBase64);
    } catch (Exception e) {
      throw new RuntimeException("Ed25519 key pair generation failed", e);
    }
  }

  /**
   * Generates a cryptographically secure challenge number for enrollment and authentication.
   *
   * <p>This method creates a cryptographically secure random challenge number suitable for use in
   * MFA scenarios where security is paramount. Unlike standard Random generators, this method uses
   * SecureRandom to ensure the challenge cannot be predicted by attackers.
   *
   * <p><b>Security Properties:</b>
   *
   * <ul>
   *   <li>Uses {@link java.security.SecureRandom} for cryptographic strength
   *   <li>Generates numbers in the range appropriate for the specified digit count
   *   <li>Ensures minimum digit requirements (no leading zeros in multi-digit challenges)
   *   <li>Suitable for MFA challenge-response authentication flows
   * </ul>
   *
   * @param digits the number of digits for the challenge (minimum 1, maximum 6)
   * @return a cryptographically secure random challenge number
   * @throws IllegalArgumentException if digits is outside the valid range
   * @since 2025
   */
  public Integer generateSecureChallenge(int digits) {
    if (digits < 1 || digits > 6) {
      throw new IllegalArgumentException(
          "Challenge digits must be between 1 and 6, got: " + digits);
    }
    try {
      // Calculate the range for the specified number of digits
      int minValue = (int) Math.pow(10, digits - 1);
      int maxValue = (int) Math.pow(10, digits) - 1;

      // For 1 digit, minValue would be 1, for 2 digits minValue is 10, etc.
      // Generate secure random number in the range [minValue, maxValue]
      return minValue + secureRandom.nextInt(maxValue - minValue + 1);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate secure challenge", e);
    }
  }

  /**
   * Generates a cryptographically secure proof token for signature operations.
   *
   * <p>This method creates a random, unpredictable token suitable for use as a payload to be signed
   * in authentication or enrollment flows. The token is composed of:
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
      byte[] randomBytes = new byte[PROOF_TOKEN_RANDOM_BYTES];
      secureRandom.nextBytes(randomBytes);
      long timestamp = System.currentTimeMillis();
      byte[] salt = new byte[PROOF_TOKEN_SALT_BYTES];
      secureRandom.nextBytes(salt);
      String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
      String saltPart = Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
      return randomPart + "." + timestamp + "." + saltPart;
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate proof token", e);
    }
  }
}
