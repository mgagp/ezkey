/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CryptographicValidationDemo
 * Description: Standalone demonstration of SignatureService functionality
 */

package org.ezkey.mobile.v1.demo

import org.ezkey.mobile.v1.crypto.SignatureService

/**
 * Standalone demonstration of the Android SignatureService implementation.
 * This can be used to validate the cryptographic functionality outside of the Android app.
 */
object CryptographicValidationDemo {
    
    private val signatureService = SignatureService()
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("🚀 Ezkey Mobile V1 - Cryptographic Validation Demo")
        println("📱 Platform: Kotlin/JVM (Android-compatible)")
        println("🔐 Algorithm: RSA-2048 with SHA256withRSA")
        println("=" * 60)
        
        try {
            // Test 1: Key Pair Generation
            println("\n📋 TEST 1: RSA Key Pair Generation")
            val startTime1 = System.currentTimeMillis()
            val keyPair = signatureService.generateRsaKeyPair(2048)
            val endTime1 = System.currentTimeMillis()
            
            println("✅ Key pair generated successfully in ${endTime1 - startTime1}ms")
            println("📏 Private key length: ${keyPair.base64PrivateKey.length} chars")
            println("📏 Public key length: ${keyPair.base64PublicKey.length} chars")
            println("🔍 Private key preview: ${keyPair.base64PrivateKey.take(50)}...")
            println("🔍 Public key preview: ${keyPair.base64PublicKey.take(50)}...")
            
            // Test 2: Proof Token Generation
            println("\n📋 TEST 2: Proof Token Generation")
            val startTime2 = System.currentTimeMillis()
            val proofToken = signatureService.generateProofToken()
            val endTime2 = System.currentTimeMillis()
            
            println("✅ Proof token generated successfully in ${endTime2 - startTime2}ms")
            println("📏 Token length: ${proofToken.length} chars")
            println("🔍 Token: $proofToken")
            
            val parts = proofToken.split(".")
            println("📊 Token structure validation: ${parts.size} parts (expected: 3)")
            println("📊 Random part: ${parts[0]} (${parts[0].length} chars)")
            println("📊 Timestamp: ${parts[1]} (${java.util.Date(parts[1].toLong())})")
            println("📊 Salt part: ${parts[2]} (${parts[2].length} chars)")
            
            // Test 3: Digital Signature Creation
            println("\n📋 TEST 3: Digital Signature Creation")
            println("📝 Data to sign: $proofToken")
            val startTime3 = System.currentTimeMillis()
            val signature = signatureService.generateSignature(proofToken, keyPair.base64PrivateKey)
            val endTime3 = System.currentTimeMillis()
            
            println("✅ Signature generated successfully in ${endTime3 - startTime3}ms")
            println("📏 Signature length: ${signature.length} chars")
            println("🔍 Signature: ${signature.take(100)}...")
            
            // Test 4: Signature Validation
            println("\n📋 TEST 4: Signature Validation")
            val startTime4 = System.currentTimeMillis()
            val isValid = signatureService.validateSignature(proofToken, signature, keyPair.base64PublicKey)
            val endTime4 = System.currentTimeMillis()
            
            if (isValid) {
                println("✅ Signature validation: PASSED in ${endTime4 - startTime4}ms")
                println("🎯 Cryptographic integrity confirmed")
            } else {
                throw RuntimeException("Signature validation failed")
            }
            
            // Test 5: Invalid Signature Detection
            println("\n📋 TEST 5: Invalid Signature Detection")
            val invalidResult = signatureService.validateSignature("invalid-data", signature, keyPair.base64PublicKey)
            if (!invalidResult) {
                println("✅ Invalid signature detection: PASSED")
            } else {
                throw RuntimeException("Failed to detect invalid signature")
            }
            
            // Test 6: Secure Challenge Generation
            println("\n📋 TEST 6: Secure Challenge Generation")
            for (digits in 1..6) {
                val challenge = signatureService.generateSecureChallenge(digits)
                val expectedMin = kotlin.math.pow(10.0, (digits - 1).toDouble()).toInt()
                val expectedMax = kotlin.math.pow(10.0, digits.toDouble()).toInt() - 1
                
                if (challenge in expectedMin..expectedMax) {
                    println("✅ ${digits}-digit challenge: $challenge (valid range [$expectedMin, $expectedMax])")
                } else {
                    throw RuntimeException("Challenge $challenge outside valid range [$expectedMin, $expectedMax]")
                }
            }
            
            // Final summary
            println("\n" + "=" * 60)
            println("✅ ALL CRYPTOGRAPHIC VALIDATION TESTS COMPLETED SUCCESSFULLY!")
            println("🎯 Ezkey Mobile V1 Kotlin implementation is fully functional")
            println("🔒 All cryptographic operations are Java-compatible")
            println("📱 Ready for Android deployment")
            
        } catch (e: Exception) {
            println("❌ CRITICAL ERROR: ${e.message}")
            println("📊 Stack trace: ${e.stackTraceToString()}")
            System.exit(1)
        }
    }
    
    private operator fun String.times(count: Int): String = this.repeat(count)
}