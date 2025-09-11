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
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cryptographic signature service providing digital signature generation and validation.
 * <p>
 * This service is a fundamental component of the Ezkey security architecture, implementing
 * RSA digital signatures with SHA-256 hashing. It provides the cryptographic foundation
 * for ensuring data integrity, authenticity, and non-repudiation across the Ezkey platform.
 * </p>
 *
 * <p>
 * <b>Cryptographic Implementation:</b>
 * <ul>
 * <li><b>Algorithm:</b> RSA with SHA-256 (SHA256withRSA)</li>
 * <li><b>Key Format:</b> PKCS#8 for private keys, X.509 for public keys</li>
 * <li><b>Encoding:</b> Base64 for key and signature representation</li>
 * <li><b>Security Level:</b> Industry-standard cryptographic strength</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Applications:</b>
 * <ul>
 * <li><b>Authentication:</b> Verifying the origin of messages and requests</li>
 * <li><b>Integrity:</b> Ensuring data has not been tampered with</li>
 * <li><b>Non-repudiation:</b> Providing proof of message origin</li>
 * <li><b>Trust:</b> Establishing secure communication channels</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Usage Context:</b>
 * This service is used throughout the Ezkey platform for securing API communications,
 * validating enrollment requests, and ensuring the integrity of authentication attempts.
 * It forms the cryptographic backbone that enables secure MFA and passkey operations.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Security Level:</b> Production-grade cryptographic implementation
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see java.security.Signature
 * @see java.security.KeyFactory
 */
@Service
public class SignatureService {

    private static final Logger logger = LoggerFactory.getLogger(SignatureService.class);

    private static final int PROOF_TOKEN_RANDOM_BYTES = 32; // 256 bits

    private static final int PROOF_TOKEN_SALT_BYTES = 16; // 128 bits

    private static final String RSA_ALGORITHM = "RSA";

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    // Reuse a single SecureRandom instance
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    /**
     * Generates a digital signature for the provided data using RSA private key.
     * <p>
     * This method creates a cryptographically secure digital signature that can be used
     * to verify the authenticity and integrity of the original data. The signature is
     * generated using RSA with SHA-256 hashing, providing industry-standard security.
     * </p>
     *
     * <p>
     * <b>Cryptographic Process:</b>
     * <ol>
     * <li>Decode the Base64-encoded private key</li>
     * <li>Create PKCS#8 key specification</li>
     * <li>Initialize RSA signature with SHA-256</li>
     * <li>Sign the data bytes</li>
     * <li>Return Base64-encoded signature</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Security Considerations:</b>
     * <ul>
     * <li>Private keys must be securely stored and managed</li>
     * <li>Input data should be validated before signing</li>
     * <li>Generated signatures should be transmitted securely</li>
     * </ul>
     * </p>
     *
     * @param data the data to be signed (typically JSON payload or message content)
     * @param base64PrivateKey the Base64-encoded RSA private key in PKCS#8 format
     * @return Base64-encoded digital signature
     * @throws RuntimeException if signature generation fails due to cryptographic errors
     * @see java.security.PrivateKey
     * @see java.security.spec.PKCS8EncodedKeySpec
     */
    public String generateSignature(String data,String base64PrivateKey) {
        Objects.requireNonNull(data,"Data cannot be null");
        Objects.requireNonNull(base64PrivateKey,"Private key cannot be null");
        try{
            byte[] keyBytes = java.util.Base64.getDecoder().decode(base64PrivateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
            PrivateKey privateKey = kf.generatePrivate(spec);
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();
            return java.util.Base64.getEncoder().encodeToString(signed);
        } catch (Exception e){
            throw new RuntimeException("Failed to generate signature",e);
        }
    }

    /**
     * Validates a digital signature against the provided data using RSA public key.
     * <p>
     * This method verifies the authenticity and integrity of data by validating its
     * associated digital signature. It confirms that the data was signed by the holder
     * of the corresponding private key and has not been altered since signing.
     * </p>
     *
     * <p>
     * <b>Cryptographic Process:</b>
     * <ol>
     * <li>Decode the Base64-encoded public key</li>
     * <li>Create X.509 key specification</li>
     * <li>Initialize RSA signature verification with SHA-256</li>
     * <li>Verify the signature against the data</li>
     * <li>Return verification result</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Security Behavior:</b>
     * <ul>
     * <li>Returns <code>false</code> for any cryptographic errors (fail-secure)</li>
     * <li>Validates both signature format and cryptographic correctness</li>
     * <li>Ensures data integrity and authenticity verification</li>
     * </ul>
     * </p>
     *
     * @param data the original data that was signed
     * @param signatureBase64 the Base64-encoded digital signature to validate
     * @param base64PublicKey the Base64-encoded RSA public key in X.509 format
     * @return <code>true</code> if the signature is valid, <code>false</code> otherwise
     * @see java.security.PublicKey
     * @see java.security.spec.X509EncodedKeySpec
     */
    public boolean validateSignature(String data,String signatureBase64,String base64PublicKey) {
        try{
            byte[] keyBytes = java.util.Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey publicKey = kf.generatePublic(spec);
            if (publicKey instanceof RSAPublicKey){
                RSAPublicKey rsaKey = (RSAPublicKey) publicKey;
                if (rsaKey.getModulus().bitLength() < 2048){
                    logger.warn("Weak RSA key detected: {} bits",rsaKey.getModulus().bitLength());
                    return false;
                }
            }
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = java.util.Base64.getDecoder().decode(signatureBase64);
            return signature.verify(signatureBytes);
        } catch (Exception e){
            return false;
        }
    }

    /**
     * Generates a new RSA key pair and returns it as Base64-encoded strings.
     * <p>
     * The generated private key is returned in PKCS#8 format and the public key in X.509 format.
     * Keys are generated using a secure {@link KeyPairGenerator} and encoded with Base64 for storage/transmission.
     * </p>
     *
     * @param keySize the RSA key size in bits (e.g., 2048)
     * @return an immutable {@link RsaKeyPair} containing Base64-encoded keys
     * @throws RuntimeException if key generation fails due to cryptographic errors
     */
    public RsaKeyPair generateRsaKeyPair(int keySize) {
        try{
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyGen.initialize(keySize);
            KeyPair keyPair = keyGen.generateKeyPair();
            String privateKeyBase64 = java.util.Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            return new RsaKeyPair(privateKeyBase64,publicKeyBase64);
        } catch (Exception e){
            throw new RuntimeException("RSA key pair generation failed",e);
        }
    }

    /**
     * Generates a cryptographically secure challenge number for enrollment and authentication.
     * <p>
     * This method creates a cryptographically secure random challenge number suitable for use
     * in MFA scenarios where security is paramount. Unlike standard Random generators, this
     * method uses SecureRandom to ensure the challenge cannot be predicted by attackers.
     * </p>
     *
     * <p>
     * <b>Security Properties:</b>
     * <ul>
     * <li>Uses {@link java.security.SecureRandom} for cryptographic strength</li>
     * <li>Generates numbers in the range appropriate for the specified digit count</li>
     * <li>Ensures minimum digit requirements (no leading zeros in multi-digit challenges)</li>
     * <li>Suitable for MFA challenge-response authentication flows</li>
     * </ul>
     * </p>
     *
     * @param digits the number of digits for the challenge (minimum 1, maximum 6)
     * @return a cryptographically secure random challenge number
     * @throws IllegalArgumentException if digits is outside the valid range
     * @since 2025
     */
    public Integer generateSecureChallenge(int digits) {
        if (digits < 1 || digits > 6){
            throw new IllegalArgumentException("Challenge digits must be between 1 and 6, got: " + digits);
        }
        try{
            // Calculate the range for the specified number of digits
            int minValue = (int) Math.pow(10,digits - 1);
            int maxValue = (int) Math.pow(10,digits) - 1;

            // For 1 digit, minValue would be 1, for 2 digits minValue is 10, etc.
            // Generate secure random number in the range [minValue, maxValue]
            return minValue + secureRandom.nextInt(maxValue - minValue + 1);
        } catch (Exception e){
            throw new RuntimeException("Failed to generate secure challenge",e);
        }
    }

    /**
     * Generates a cryptographically secure proof token for signature operations.
     * <p>
     * This method creates a random, unpredictable token suitable for use as a payload to be signed
     * in authentication or enrollment flows. The token is composed of:
     * <ul>
     * <li>256 bits (32 bytes) of cryptographically secure random data</li>
     * <li>A timestamp (milliseconds since epoch)</li>
     * <li>An additional 128 bits (16 bytes) of random salt</li>
     * </ul>
     * The result is encoded as a Base64 URL-safe string (without padding), concatenating the random bytes,
     * timestamp, and salt, separated by a period ('.').
     * </p>
     *
     * @return a Base64 URL-safe encoded proof token string
     */
    public String generateProofToken() {
        try{
            byte[] randomBytes = new byte[PROOF_TOKEN_RANDOM_BYTES];
            secureRandom.nextBytes(randomBytes);
            long timestamp = System.currentTimeMillis();
            byte[] salt = new byte[PROOF_TOKEN_SALT_BYTES];
            secureRandom.nextBytes(salt);
            String randomPart = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            String saltPart = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(salt);
            return randomPart + "." + timestamp + "." + saltPart;
        } catch (Exception e){
            throw new RuntimeException("Failed to generate proof token",e);
        }
    }
}