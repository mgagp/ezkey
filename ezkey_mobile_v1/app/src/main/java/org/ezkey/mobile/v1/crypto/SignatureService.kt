/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: SignatureService (Kotlin/Android Implementation)
 * Description: Cryptographic signature service providing digital signature generation and validation for Android.
 */

package org.ezkey.mobile.v1.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Android/Kotlin implementation of Ezkey's cryptographic signature service.
 * 
 * This service provides the same cryptographic functionality as the Java SignatureService
 * but optimized for Android platform using Kotlin language features.
 * 
 * Cryptographic Implementation:
 * - Algorithm: RSA with SHA-256 (SHA256withRSA)
 * - Key Format: PKCS#8 for private keys, X.509 for public keys
 * - Encoding: Base64 for key and signature representation
 * - Security Level: Industry-standard cryptographic strength
 */
class SignatureService {
    
    companion object {
        private const val PROOF_TOKEN_RANDOM_BYTES = 32 // 256 bits
        private const val PROOF_TOKEN_SALT_BYTES = 16 // 128 bits
        private const val RSA_ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    }
    
    /**
     * Data class representing an RSA key pair with Base64-encoded keys.
     */
    data class RsaKeyPair(
        val base64PrivateKey: String,
        val base64PublicKey: String
    )
    
    /**
     * Generates a new RSA key pair and returns it as Base64-encoded strings.
     * 
     * @param keySize the RSA key size in bits (e.g., 2048)
     * @return RsaKeyPair containing Base64-encoded keys
     * @throws RuntimeException if key generation fails
     */
    fun generateRsaKeyPair(keySize: Int): RsaKeyPair {
        return try {
            val keyGen = KeyPairGenerator.getInstance(RSA_ALGORITHM)
            keyGen.initialize(keySize)
            val keyPair = keyGen.generateKeyPair()
            
            val privateKeyBase64 = Base64.encodeToString(
                keyPair.private.encoded, 
                Base64.NO_WRAP
            )
            val publicKeyBase64 = Base64.encodeToString(
                keyPair.public.encoded, 
                Base64.NO_WRAP
            )
            
            RsaKeyPair(privateKeyBase64, publicKeyBase64)
        } catch (e: Exception) {
            throw RuntimeException("RSA key pair generation failed", e)
        }
    }
    
    /**
     * Generates a cryptographically secure proof token for signature operations.
     * 
     * @return a Base64 URL-safe encoded proof token string
     * @throws RuntimeException if token generation fails
     */
    fun generateProofToken(): String {
        return try {
            val secureRandom = SecureRandom()
            
            // Generate random bytes
            val randomBytes = ByteArray(PROOF_TOKEN_RANDOM_BYTES)
            secureRandom.nextBytes(randomBytes)
            
            // Get timestamp
            val timestamp = System.currentTimeMillis()
            
            // Generate salt
            val salt = ByteArray(PROOF_TOKEN_SALT_BYTES)
            secureRandom.nextBytes(salt)
            
            // Encode parts as Base64 URL-safe without padding
            val randomPart = Base64.encodeToString(randomBytes, Base64.URL_SAFE or Base64.NO_PADDING)
            val saltPart = Base64.encodeToString(salt, Base64.URL_SAFE or Base64.NO_PADDING)
            
            "$randomPart.$timestamp.$saltPart"
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate proof token", e)
        }
    }
    
    /**
     * Generates a digital signature for the provided data using RSA private key.
     * 
     * @param data the data to be signed
     * @param base64PrivateKey the Base64-encoded RSA private key in PKCS#8 format
     * @return Base64-encoded digital signature
     * @throws RuntimeException if signature generation fails
     */
    fun generateSignature(data: String, base64PrivateKey: String): String {
        require(data.isNotBlank()) { "Data cannot be null or empty" }
        require(base64PrivateKey.isNotBlank()) { "Private key cannot be null or empty" }
        
        return try {
            // Decode the private key
            val keyBytes = Base64.decode(base64PrivateKey, Base64.NO_WRAP)
            val spec = PKCS8EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance(RSA_ALGORITHM)
            val privateKey = kf.generatePrivate(spec)
            
            // Create and sign
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data.toByteArray(StandardCharsets.UTF_8))
            val signed = signature.sign()
            
            Base64.encodeToString(signed, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate signature", e)
        }
    }
    
    /**
     * Validates a digital signature against the provided data using RSA public key.
     * 
     * @param data the original data that was signed
     * @param signatureBase64 the Base64-encoded digital signature to validate
     * @param base64PublicKey the Base64-encoded RSA public key in X.509 format
     * @return true if the signature is valid, false otherwise
     */
    fun validateSignature(data: String, signatureBase64: String, base64PublicKey: String): Boolean {
        return try {
            // Decode the public key
            val keyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance(RSA_ALGORITHM)
            val publicKey = kf.generatePublic(spec)
            
            // Verify signature
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(data.toByteArray(StandardCharsets.UTF_8))
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false // Fail-secure: any error returns false
        }
    }
    
    /**
     * Generates a cryptographically secure challenge number for enrollment and authentication.
     * 
     * @param digits the number of digits for the challenge (minimum 1, maximum 6)
     * @return a cryptographically secure random challenge number
     * @throws IllegalArgumentException if digits is outside the valid range
     */
    fun generateSecureChallenge(digits: Int): Int {
        if (digits < 1 || digits > 6) {
            throw IllegalArgumentException("Challenge digits must be between 1 and 6, got: $digits")
        }
        
        return try {
            val secureRandom = SecureRandom()
            
            // Calculate the range for the specified number of digits
            val minValue = Math.pow(10.0, (digits - 1).toDouble()).toInt()
            val maxValue = Math.pow(10.0, digits.toDouble()).toInt() - 1
            
            // Generate secure random number in the range [minValue, maxValue]
            minValue + secureRandom.nextInt(maxValue - minValue + 1)
        } catch (e: Exception) {
            throw RuntimeException("Failed to generate secure challenge", e)
        }
    }
}