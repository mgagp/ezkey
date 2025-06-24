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

import org.springframework.stereotype.Service;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

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
 *   <li><b>Algorithm:</b> RSA with SHA-256 (SHA256withRSA)</li>
 *   <li><b>Key Format:</b> PKCS#8 for private keys, X.509 for public keys</li>
 *   <li><b>Encoding:</b> Base64 for key and signature representation</li>
 *   <li><b>Security Level:</b> Industry-standard cryptographic strength</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Applications:</b>
 * <ul>
 *   <li><b>Authentication:</b> Verifying the origin of messages and requests</li>
 *   <li><b>Integrity:</b> Ensuring data has not been tampered with</li>
 *   <li><b>Non-repudiation:</b> Providing proof of message origin</li>
 *   <li><b>Trust:</b> Establishing secure communication channels</li>
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative</p>
 * <p><b>License:</b> MIT</p>
 * <p><b>Security Level:</b> Production-grade cryptographic implementation</p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see java.security.Signature
 * @see java.security.KeyFactory
 */
@Service
public class SignatureService {
    
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
     *   <li>Decode the Base64-encoded private key</li>
     *   <li>Create PKCS#8 key specification</li>
     *   <li>Initialize RSA signature with SHA-256</li>
     *   <li>Sign the data bytes</li>
     *   <li>Return Base64-encoded signature</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Security Considerations:</b>
     * <ul>
     *   <li>Private keys must be securely stored and managed</li>
     *   <li>Input data should be validated before signing</li>
     *   <li>Generated signatures should be transmitted securely</li>
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
    public String generateSignature(String data, String base64PrivateKey) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(base64PrivateKey);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = kf.generatePrivate(spec);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes());
            byte[] signed = signature.sign();
            return java.util.Base64.getEncoder().encodeToString(signed);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
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
     *   <li>Decode the Base64-encoded public key</li>
     *   <li>Create X.509 key specification</li>
     *   <li>Initialize RSA signature verification with SHA-256</li>
     *   <li>Verify the signature against the data</li>
     *   <li>Return verification result</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Security Behavior:</b>
     * <ul>
     *   <li>Returns <code>false</code> for any cryptographic errors (fail-secure)</li>
     *   <li>Validates both signature format and cryptographic correctness</li>
     *   <li>Ensures data integrity and authenticity verification</li>
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
    public boolean validateSignature(String data, String signatureBase64, String base64PublicKey) {
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(data.getBytes());
            byte[] signatureBytes = java.util.Base64.getDecoder().decode(signatureBase64);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }
}