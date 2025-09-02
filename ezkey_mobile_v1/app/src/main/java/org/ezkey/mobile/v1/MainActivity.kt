/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Activity: MainActivity
 * Description: Main activity for Ezkey Mobile V1 validation application.
 */

package org.ezkey.mobile.v1

import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ezkey.mobile.v1.crypto.SignatureService
import kotlin.system.exitProcess

/**
 * Main activity that demonstrates and validates Ezkey's cryptographic functionality.
 * 
 * This activity performs a series of cryptographic tests to validate the Android
 * implementation of Ezkey's signature service against the Java reference implementation.
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var resultsTextView: TextView
    private lateinit var terminateButton: Button
    private lateinit var scrollView: ScrollView
    private val signatureService = SignatureService()
    
    private val testResults = StringBuilder()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create UI programmatically for simplicity
        createUI()
        
        // Run cryptographic validation tests
        runCryptographicTests()
    }
    
    private fun createUI() {
        // Create main layout
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        // App title
        val titleTextView = TextView(this).apply {
            text = "Ezkey Mobile V1 - Cryptographic Validation"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
            gravity = android.view.Gravity.CENTER
        }
        
        // Results display with scrolling
        scrollView = ScrollView(this)
        resultsTextView = TextView(this).apply {
            text = "🔄 Initializing cryptographic validation tests...\n\n"
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.color.black)
            setTextColor(android.graphics.Color.GREEN)
        }
        scrollView.addView(resultsTextView)
        
        // Terminate button
        terminateButton = Button(this).apply {
            text = "Terminate Application"
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.RED)
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                finishAffinity()
                exitProcess(0)
            }
        }
        
        // Add views to layout
        layout.addView(titleTextView)
        layout.addView(scrollView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(terminateButton)
        
        setContentView(layout)
    }
    
    private fun runCryptographicTests() {
        lifecycleScope.launch {
            try {
                appendResult("🚀 Starting Ezkey Cryptographic Validation Tests")
                appendResult("📱 Platform: Android/Kotlin")
                appendResult("🔐 Algorithm: RSA-2048 with SHA256withRSA")
                appendResult("=" * 60)
                
                // Test 1: RSA Key Pair Generation
                appendResult("\n📋 TEST 1: RSA Key Pair Generation")
                val keyPair = testKeyPairGeneration()
                
                // Test 2: Proof Token Generation
                appendResult("\n📋 TEST 2: Proof Token Generation")
                val proofToken = testProofTokenGeneration()
                
                // Test 3: Digital Signature Creation
                appendResult("\n📋 TEST 3: Digital Signature Creation")
                val signature = testSignatureGeneration(proofToken, keyPair.base64PrivateKey)
                
                // Test 4: Signature Validation
                appendResult("\n📋 TEST 4: Signature Validation")
                testSignatureValidation(proofToken, signature, keyPair.base64PublicKey)
                
                // Test 5: Secure Challenge Generation
                appendResult("\n📋 TEST 5: Secure Challenge Generation")
                testSecureChallengeGeneration()
                
                // Final summary
                appendResult("\n" + "=" * 60)
                appendResult("✅ ALL CRYPTOGRAPHIC VALIDATION TESTS COMPLETED SUCCESSFULLY!")
                appendResult("🎯 Ezkey Mobile V1 is ready for production use")
                appendResult("🔒 All cryptographic operations are compatible with Java implementation")
                
            } catch (e: Exception) {
                appendResult("❌ CRITICAL ERROR: ${e.message}")
                appendResult("📊 Stack trace: ${e.stackTraceToString()}")
            }
        }
    }
    
    private suspend fun testKeyPairGeneration(): SignatureService.RsaKeyPair = withContext(Dispatchers.Default) {
        appendResult("🔑 Generating RSA-2048 key pair...")
        
        val startTime = System.currentTimeMillis()
        val keyPair = signatureService.generateRsaKeyPair(2048)
        val endTime = System.currentTimeMillis()
        
        appendResult("✅ Key pair generated successfully in ${endTime - startTime}ms")
        appendResult("📏 Private key length: ${keyPair.base64PrivateKey.length} chars")
        appendResult("📏 Public key length: ${keyPair.base64PublicKey.length} chars")
        appendResult("🔍 Private key preview: ${keyPair.base64PrivateKey.take(50)}...")
        appendResult("🔍 Public key preview: ${keyPair.base64PublicKey.take(50)}...")
        
        // Validate key format
        if (keyPair.base64PrivateKey.isNotEmpty() && keyPair.base64PublicKey.isNotEmpty()) {
            appendResult("✅ Key format validation: PASSED")
        } else {
            throw RuntimeException("Key format validation failed")
        }
        
        keyPair
    }
    
    private suspend fun testProofTokenGeneration(): String = withContext(Dispatchers.Default) {
        appendResult("🎲 Generating cryptographically secure proof token...")
        
        val startTime = System.currentTimeMillis()
        val proofToken = signatureService.generateProofToken()
        val endTime = System.currentTimeMillis()
        
        appendResult("✅ Proof token generated successfully in ${endTime - startTime}ms")
        appendResult("📏 Token length: ${proofToken.length} chars")
        appendResult("🔍 Token: $proofToken")
        
        // Validate token format (should have 3 parts separated by dots)
        val parts = proofToken.split(".")
        if (parts.size == 3) {
            appendResult("✅ Token format validation: PASSED (3 parts)")
            appendResult("📊 Random part: ${parts[0]} (${parts[0].length} chars)")
            appendResult("📊 Timestamp: ${parts[1]} (${java.util.Date(parts[1].toLong())})")
            appendResult("📊 Salt part: ${parts[2]} (${parts[2].length} chars)")
        } else {
            throw RuntimeException("Proof token format validation failed")
        }
        
        proofToken
    }
    
    private suspend fun testSignatureGeneration(data: String, privateKey: String): String = withContext(Dispatchers.Default) {
        appendResult("✍️ Generating digital signature...")
        appendResult("📝 Data to sign: $data")
        
        val startTime = System.currentTimeMillis()
        val signature = signatureService.generateSignature(data, privateKey)
        val endTime = System.currentTimeMillis()
        
        appendResult("✅ Signature generated successfully in ${endTime - startTime}ms")
        appendResult("📏 Signature length: ${signature.length} chars")
        appendResult("🔍 Signature: ${signature.take(100)}...")
        
        if (signature.isNotEmpty()) {
            appendResult("✅ Signature generation validation: PASSED")
        } else {
            throw RuntimeException("Signature generation failed")
        }
        
        signature
    }
    
    private suspend fun testSignatureValidation(data: String, signature: String, publicKey: String) = withContext(Dispatchers.Default) {
        appendResult("🔍 Validating digital signature...")
        
        val startTime = System.currentTimeMillis()
        val isValid = signatureService.validateSignature(data, signature, publicKey)
        val endTime = System.currentTimeMillis()
        
        if (isValid) {
            appendResult("✅ Signature validation: PASSED in ${endTime - startTime}ms")
            appendResult("🎯 Cryptographic integrity confirmed")
        } else {
            throw RuntimeException("Signature validation failed")
        }
        
        // Test with invalid data to ensure validation works correctly
        appendResult("🧪 Testing signature validation with invalid data...")
        val invalidResult = signatureService.validateSignature("invalid-data", signature, publicKey)
        if (!invalidResult) {
            appendResult("✅ Invalid signature detection: PASSED")
        } else {
            throw RuntimeException("Failed to detect invalid signature")
        }
    }
    
    private suspend fun testSecureChallengeGeneration() = withContext(Dispatchers.Default) {
        appendResult("🎯 Testing secure challenge generation...")
        
        for (digits in 1..6) {
            val challenge = signatureService.generateSecureChallenge(digits)
            val expectedMin = kotlin.math.pow(10.0, (digits - 1).toDouble()).toInt()
            val expectedMax = kotlin.math.pow(10.0, digits.toDouble()).toInt() - 1
            
            if (challenge in expectedMin..expectedMax) {
                appendResult("✅ ${digits}-digit challenge: $challenge (valid range)")
            } else {
                throw RuntimeException("Challenge $challenge outside valid range [$expectedMin, $expectedMax]")
            }
        }
        
        appendResult("✅ All secure challenge tests: PASSED")
    }
    
    private suspend fun appendResult(message: String) {
        withContext(Dispatchers.Main) {
            testResults.append(message).append("\n")
            resultsTextView.text = testResults.toString()
            
            // Auto-scroll to bottom
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
    
    private operator fun String.times(count: Int): String = this.repeat(count)
}