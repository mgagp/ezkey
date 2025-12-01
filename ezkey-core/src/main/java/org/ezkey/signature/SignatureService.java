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
import java.math.BigInteger;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ezkey.config.EzkeyCoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic signature service providing digital signature generation and validation.
 *
 * <p>This service is a fundamental component of the Ezkey security architecture, implementing
 * EC P-256 (secp256r1) digital signatures with ECDSA-SHA256. It provides the cryptographic
 * foundation for ensuring data integrity, authenticity, and non-repudiation across the Ezkey
 * platform.
 *
 * <p><b>Cryptographic Implementation:</b>
 *
 * <ul>
 *   <li><b>Algorithm:</b> EC P-256 (secp256r1) with ECDSA-SHA256
 *   <li><b>Key Format:</b> PKCS#8 private key, X.509 public key (standard formats)
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
 * This implementation is compatible with mobile applications using native hardware-backed EC P-256
 * keys.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Security Level:</b> Production-grade cryptographic implementation
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.bouncycastle.crypto.signers.ECDSASigner
 * @see org.bouncycastle.crypto.params.ECPrivateKeyParameters
 * @see org.bouncycastle.crypto.params.ECPublicKeyParameters
 */
@Service
public class SignatureService {

  private static final Logger logger = LoggerFactory.getLogger(SignatureService.class);

  private static final int PROOF_TOKEN_RANDOM_BYTES = 32; // 256 bits

  private static final int PROOF_TOKEN_SALT_BYTES = 16; // 128 bits

  private static final String EC_P256_ALGORITHM = "EC_P256";
  
  /**
   * Gets EC P-256 domain parameters (secp256r1).
   *
   * @return EC domain parameters for secp256r1 curve
   * @throws IllegalStateException if curve parameters cannot be obtained
   */
  private static ECDomainParameters getECP256DomainParameters() {
    // Try CustomNamedCurves first (optimized implementation)
    X9ECParameters ecParams = CustomNamedCurves.getByName("secp256r1");
    if (ecParams == null) {
      // Fallback: use standard curve (should always be available)
      // secp256r1 is a standard NIST curve, should be in CustomNamedCurves
      throw new IllegalStateException(
          "Failed to get EC P-256 domain parameters. "
          + "BouncyCastle EC curve 'secp256r1' not available. "
          + "Please ensure BouncyCastle is properly configured.");
    }
    return new ECDomainParameters(
        ecParams.getCurve(),
        ecParams.getG(),
        ecParams.getN(),
        ecParams.getH(),
        ecParams.getSeed()
    );
  }

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
   * Generates a digital signature for the provided data using EC P-256 private key.
   *
   * <p>This method creates a cryptographically secure digital signature that can be used to verify
   * the authenticity and integrity of the original data. The signature is generated using EC P-256
   * with ECDSA-SHA256, providing production-grade security compatible with mobile hardware-backed
   * keys.
   *
   * <p><b>Cryptographic Process:</b>
   *
   * <ol>
   *   <li>Decode the Base64-encoded private key (PKCS#8 format)
   *   <li>Create EC P-256 private key parameters
   *   <li>Initialize ECDSA signer with SHA-256 digest
   *   <li>Sign the data bytes
   *   <li>Encode signature as ASN.1 DER format
   *   <li>Return Base64-encoded signature
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
   * @param base64PrivateKey the Base64-encoded EC P-256 private key (PKCS#8 format)
   * @return Base64-encoded digital signature (ASN.1 DER encoded ECDSA signature)
   * @throws RuntimeException if signature generation fails due to cryptographic errors
   * @see org.bouncycastle.crypto.params.ECPrivateKeyParameters
   * @see org.bouncycastle.crypto.signers.ECDSASigner
   */
  public String generateSignature(String data, String base64PrivateKey) {
    Objects.requireNonNull(data, "Data cannot be null");
    Objects.requireNonNull(base64PrivateKey, "Private key cannot be null");
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
      ECPrivateKeyParameters privateKeyParams = 
          (ECPrivateKeyParameters) PrivateKeyFactory.createKey(keyBytes);
      
      ECDSASigner signer = new ECDSASigner();
      signer.init(true, privateKeyParams);
      
      byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
      // Hash the data with SHA-256 before signing (ECDSA requires hashed input)
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(dataBytes);
      
      BigInteger[] signature = signer.generateSignature(hash);
      
      // Encode signature as ASN.1 DER format
      byte[] derSignature = encodeDERSignature(signature[0], signature[1]);
      return Base64.getEncoder().encodeToString(derSignature);
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate signature", e);
    }
  }

  /**
   * Validates a digital signature against the provided data using EC P-256 public key.
   *
   * <p>This method verifies the authenticity and integrity of data by validating its associated
   * digital signature. It confirms that the data was signed by the holder of the corresponding
   * private key and has not been altered since signing.
   *
   * <p><b>Cryptographic Process:</b>
   *
   * <ol>
   *   <li>Decode the Base64-encoded public key (X.509 format)
   *   <li>Create EC P-256 public key parameters
   *   <li>Decode signature from ASN.1 DER format
   *   <li>Hash the data with SHA-256
   *   <li>Verify the signature against the hash
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
   * @param signatureBase64 the Base64-encoded digital signature to validate (ASN.1 DER encoded)
   * @param base64PublicKey the Base64-encoded EC P-256 public key (X.509 format)
   * @return <code>true</code> if the signature is valid, <code>false</code> otherwise
   * @see org.bouncycastle.crypto.params.ECPublicKeyParameters
   * @see org.bouncycastle.crypto.signers.ECDSASigner
   */
  public boolean validateSignature(String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
      // Handle X.509 encoded public keys (standard format from mobile)
      ECPublicKeyParameters publicKeyParams = 
          (ECPublicKeyParameters) PublicKeyFactory.createKey(keyBytes);
      
      byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
      BigInteger[] signature = decodeDERSignature(signatureBytes);
      if (signature == null) {
        logger.warn("Invalid signature format: failed to decode ASN.1 DER");
        return false;
      }
      
      ECDSASigner verifier = new ECDSASigner();
      verifier.init(false, publicKeyParams);
      
      byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
      // Hash the data with SHA-256 before verification (ECDSA requires hashed input)
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(dataBytes);
      
      return verifier.verifySignature(hash, signature[0], signature[1]);
    } catch (Exception e) {
      logger.debug("Signature validation failed", e);
      return false;
    }
  }

  /**
   * Generates a new EC P-256 key pair and returns it as Base64-encoded strings.
   *
   * <p>The generated private key is in PKCS#8 format and the public key is in X.509 format
   * (SubjectPublicKeyInfo). Keys are generated using BouncyCastle's EC P-256 implementation
   * with secp256r1 curve and encoded with Base64 for storage/transmission.
   *
   * @return an immutable {@link ECP256KeyPair} containing Base64-encoded keys
   * @throws RuntimeException if key generation fails due to cryptographic errors
   */
  public ECP256KeyPair generateECP256KeyPair() {
    try {
      ECKeyPairGenerator keyGen = new ECKeyPairGenerator();
      ECDomainParameters domainParams = getECP256DomainParameters();
      keyGen.init(new ECKeyGenerationParameters(domainParams, secureRandom));
      AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
      ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) keyPair.getPrivate();
      ECPublicKeyParameters publicKey = (ECPublicKeyParameters) keyPair.getPublic();
      
      // Encode private key as PKCS#8
      PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey);
      String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyInfo.getEncoded());
      
      // Encode public key as X.509 SubjectPublicKeyInfo
      SubjectPublicKeyInfo publicKeyInfo = SubjectPublicKeyInfoFactory.
          createSubjectPublicKeyInfo(publicKey);

      String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyInfo.getEncoded());
      
      return new ECP256KeyPair(privateKeyBase64, publicKeyBase64);
    } catch (Exception e) {
      throw new RuntimeException("EC P-256 key pair generation failed", e);
    }
  }
  
  /**
   * Encodes an ECDSA signature (r, s) as ASN.1 DER format.
   *
   * @param r the r component of the signature
   * @param s the s component of the signature
   * @return ASN.1 DER encoded signature bytes
   * @throws RuntimeException if encoding fails
   */
  private byte[] encodeDERSignature(BigInteger r, BigInteger s) {
    try {
      ASN1EncodableVector v = new ASN1EncodableVector();
      v.add(new ASN1Integer(r));
      v.add(new ASN1Integer(s));
      return new DERSequence(v).getEncoded();
    } catch (Exception e) {
      throw new RuntimeException("Failed to encode DER signature", e);
    }
  }
  
  /**
   * Decodes an ASN.1 DER encoded ECDSA signature to (r, s) components.
   *
   * @param derSignature ASN.1 DER encoded signature bytes
   * @return array containing [r, s] components, or null if decoding fails
   */
  private BigInteger[] decodeDERSignature(byte[] derSignature) {
    try {
      ASN1Sequence seq = ASN1Sequence.getInstance(derSignature);
      if (seq.size() != 2) {
        return null;
      }
      BigInteger r = ASN1Integer.getInstance(seq.getObjectAt(0)).getValue();
      BigInteger s = ASN1Integer.getInstance(seq.getObjectAt(1)).getValue();
      return new BigInteger[] {r, s};
    } catch (Exception e) {
      logger.debug("Failed to decode DER signature", e);
      return null;
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
