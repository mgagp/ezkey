/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: ExpoCryptoNativeModule
 * Description: React Native native module for cryptographic operations
 */

package expo.modules.cryptonative

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class ExpoCryptoNativeModule : Module() {
    companion object {
        private const val PROOF_TOKEN_RANDOM_BYTES = 32 // 256 bits
        private const val PROOF_TOKEN_SALT_BYTES = 16 // 128 bits
        private const val RSA_ALGORITHM = "RSA"
        private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    }

    override fun definition() = ModuleDefinition {
        Name("ExpoCryptoNative")

        Function("generateRsaKeyPair") { keySize: Int ->
            try {
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
                
                mapOf(
                    "privateKey" to privateKeyBase64,
                    "publicKey" to publicKeyBase64
                )
            } catch (e: Exception) {
                throw Exception("RSA key pair generation failed: ${e.message}")
            }
        }

        Function("generateProofToken") {
            try {
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
                throw Exception("Failed to generate proof token: ${e.message}")
            }
        }

        Function("generateSignature") { data: String, base64PrivateKey: String ->
            try {
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
                throw Exception("Failed to generate signature: ${e.message}")
            }
        }

        Function("validateSignature") { data: String, signatureBase64: String, base64PublicKey: String ->
            try {
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
    }
}
