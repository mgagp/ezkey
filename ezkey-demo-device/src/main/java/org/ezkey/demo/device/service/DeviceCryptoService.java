package org.ezkey.demo.device.service;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.bouncycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic utilities for the simulated device.
 *
 * <p>Generates EC P-256 keys and performs signing operations compatible with the Ezkey demo flows.
 * This service is intentionally independent from {@code ezkey-core} to keep the demo-device module
 * standalone.
 *
 * <p><b>Algorithm:</b> EC P-256 (secp256r1) with ECDSA-SHA256
 *
 * <p><b>Key Format:</b>
 *
 * <ul>
 *   <li>Private keys: PKCS#8 format, Base64 encoded
 *   <li>Public keys: X.509 SubjectPublicKeyInfo format, Base64 encoded
 * </ul>
 *
 * <p><b>Signature Format:</b> ASN.1 DER encoded ECDSA signature, Base64 encoded
 *
 * @since 2025
 */
@Service
public class DeviceCryptoService {

  private static final Logger logger = LoggerFactory.getLogger(DeviceCryptoService.class);

  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Gets EC P-256 domain parameters (secp256r1).
   *
   * @return EC domain parameters for secp256r1 curve
   * @throws IllegalStateException if curve parameters cannot be obtained
   */
  private static ECDomainParameters getECP256DomainParameters() {
    X9ECParameters ecParams = CustomNamedCurves.getByName("secp256r1");
    if (ecParams == null) {
      throw new IllegalStateException(
          "Failed to get EC P-256 domain parameters. "
              + "BouncyCastle EC curve 'secp256r1' not available. "
              + "Please ensure BouncyCastle is properly configured.");
    }
    return new ECDomainParameters(
        ecParams.getCurve(), ecParams.getG(), ecParams.getN(), ecParams.getH(), ecParams.getSeed());
  }

  /**
   * Generates a new EC P-256 key pair for the device.
   *
   * @return EC P-256 key pair (private key PKCS#8 and public key X.509, both Base64 encoded)
   */
  public ECP256DeviceKeyPair generateDeviceKeyPair() {
    try {
      ECKeyPairGenerator keyGen = new ECKeyPairGenerator();
      ECDomainParameters domainParams = getECP256DomainParameters();
      keyGen.init(new ECKeyGenerationParameters(domainParams, secureRandom));
      AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();
      ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) keyPair.getPrivate();
      ECPublicKeyParameters publicKey = (ECPublicKeyParameters) keyPair.getPublic();

      // Encode private key as PKCS#8
      org.bouncycastle.asn1.pkcs.PrivateKeyInfo privateKeyInfo =
          PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey);
      String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyInfo.getEncoded());

      // Encode public key as X.509 SubjectPublicKeyInfo
      org.bouncycastle.asn1.x509.SubjectPublicKeyInfo publicKeyInfo =
          SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(publicKey);
      String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyInfo.getEncoded());

      return new ECP256DeviceKeyPair(privateKeyBase64, publicKeyBase64);
    } catch (Exception e) {
      logger.error("Failed to generate device key pair", e);
      throw new IllegalStateException("Unable to generate EC P-256 key pair", e);
    }
  }

  /**
   * Immutable container for EC P-256 device key pair.
   *
   * @param base64PrivateKey Base64-encoded EC P-256 private key (PKCS#8 format)
   * @param base64PublicKey Base64-encoded EC P-256 public key (X.509 format)
   */
  public record ECP256DeviceKeyPair(String base64PrivateKey, String base64PublicKey) {}

  /**
   * Signs a UTF-8 string and returns the Base64-encoded signature.
   *
   * <p>Uses EC P-256 with ECDSA-SHA256. The signature is encoded as ASN.1 DER format.
   *
   * @param content string to sign
   * @param base64PrivateKey Base64-encoded EC P-256 private key (PKCS#8 format)
   * @return Base64-encoded signature (ASN.1 DER encoded ECDSA signature)
   */
  public String signStringToBase64(String content, String base64PrivateKey) {
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(base64PrivateKey, "base64PrivateKey must not be null");
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
      ECPrivateKeyParameters privateKeyParams =
          (ECPrivateKeyParameters) PrivateKeyFactory.createKey(keyBytes);

      ECDSASigner signer = new ECDSASigner();
      signer.init(true, privateKeyParams);

      byte[] dataBytes = content.getBytes(StandardCharsets.UTF_8);
      // Hash the data with SHA-256 before signing (ECDSA requires hashed input)
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(dataBytes);

      BigInteger[] signature = signer.generateSignature(hash);

      // Encode signature as ASN.1 DER format
      byte[] derSignature = encodeDERSignature(signature[0], signature[1]);
      return Base64.getEncoder().encodeToString(derSignature);
    } catch (Exception e) {
      logger.error("Failed to sign data", e);
      throw new IllegalStateException("Signing failure", e);
    }
  }

  /**
   * Encodes an ECDSA signature (r, s) as ASN.1 DER format.
   *
   * @param r the r component of the signature
   * @param s the s component of the signature
   * @return ASN.1 DER encoded signature bytes
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
   * Builds a simple device proof by concatenating sorted key=value pairs and signing the result.
   * This is deliberately simple for demo use; the server-side verifier only requires a valid
   * device-controlled signature over expected fields.
   *
   * @param claims key/value pairs to include in the proof
   * @param base64PrivateKey Base64-encoded EC P-256 private key (PKCS#8 format)
   * @return Base64-encoded signature over the canonicalized claims string (ASN.1 DER encoded)
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
   * Validates a digital signature against the provided data using EC P-256 public key.
   *
   * <p>This method verifies the authenticity and integrity of data by validating its associated
   * digital signature. It confirms that the data was signed by the holder of the corresponding
   * private key and has not been altered since signing.
   *
   * @param data the original data that was signed
   * @param signatureBase64 the Base64-encoded digital signature to validate (ASN.1 DER encoded)
   * @param base64PublicKey the Base64-encoded EC P-256 public key (X.509 format)
   * @return <code>true</code> if the signature is valid, <code>false</code> otherwise
   */
  public boolean validateSignature(String data, String signatureBase64, String base64PublicKey) {
    try {
      byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
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
      logger.error("Signature validation failed", e);
      return false;
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
}
