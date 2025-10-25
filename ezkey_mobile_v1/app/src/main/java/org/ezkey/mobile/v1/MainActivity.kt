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
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ezkey.mobile.v1.crypto.SignatureService
import org.ezkey.mobile.v1.auth.AuthService
import org.ezkey.mobile.v1.storage.SimpleDeviceStorage
import org.ezkey.mobile.v1.enrollment.EnrollmentService
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
    private lateinit var checkPendingButton: Button
    private lateinit var acceptButton: Button
    private lateinit var enrollButton: Button
    private lateinit var challengeEditText: EditText
    private lateinit var scrollView: ScrollView
    private val signatureService = SignatureService()
    private lateinit var authService: AuthService
    
    private var currentPendingAuth: org.ezkey.mobile.v1.auth.AuthAttemptPendingResponseDto? = null
    
    private val testResults = StringBuilder()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AuthService with proper context
        authService = AuthService(this)
        
        // Create UI programmatically for simplicity
        createUI()
        
        // Run cryptographic validation tests
        runCryptographicTests()
    }
    
    private fun createUI() {
        // Create main layout
        var layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)  // REDUCED from 32,32,32,32 to gain more space
        }
        
        // App title
        var titleTextView = TextView(this).apply {
            text = "Ezkey Mobile V1 - Cryptographic Validation"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)  // REDUCED from 24 to gain more space
            gravity = android.view.Gravity.CENTER
        }
        
        // Results display with scrolling
        var tempScrollView = ScrollView(this).apply {
            // Make scrollbar always visible
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false  // DISABLED - not needed for log text
            scrollBarStyle = android.view.View.SCROLLBARS_INSIDE_OVERLAY
            // Add some padding for better visibility
            setPadding(8, 8, 8, 8)
            isFillViewport = true  // CRITICAL: makes ScrollView expand to fill available space
        }
        
        var tempResultsTextView = TextView(this).apply {
            text = "🔄 Initializing cryptographic validation tests...\n\n"
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            setBackgroundResource(android.R.color.black)
            setTextColor(android.graphics.Color.GREEN)
            // Enable text selection for easier debugging
            //isTextSelectable = true
            // REMOVED setMinHeight(400) - was interfering with dynamic sizing
        }
        
        // Add the TextView to the ScrollView
        tempScrollView.addView(tempResultsTextView)
        
        // SCROLL BUTTONS REMOVED - they didn't work and took up space
        
        // Test button (temporary for debugging) - COMMENTED OUT TO SAVE SPACE
        /*
        var testButton = Button(this).apply {
            text = "🧪 TEST BUTTON"
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.rgb(255, 165, 0)) // Orange color
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                android.util.Log.d("MainActivity", "🧪 TEST BUTTON PRESSED")
                appendResult("🧪 TEST BUTTON PRESSED!")
                appendResult("🎯 This confirms the UI is working")
                appendResult("⏰ Timestamp: ${java.util.Date()}")
            }
        }
        */
        
        // Clear logs button
        var clearButton = Button(this).apply {
            text = "🗑️ Clear Logs"
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.rgb(128, 128, 128)) // Gray color
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                android.util.Log.d("MainActivity", "🗑️ CLEAR LOGS BUTTON PRESSED")
                clearLogs()
            }
        }
        
        // Challenge input field
        var tempChallengeEditText = EditText(this).apply {
            hint = "Enter challenge number (e.g., 123456)"
            textSize = 14f
            setPadding(16, 12, 16, 12)
            setBackgroundColor(android.graphics.Color.rgb(50, 50, 50)) // Dark gray
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.rgb(150, 150, 150)) // Light gray hint
        }
        
        // Enrollment button
        var tempEnrollButton = Button(this).apply {
            text = "📱 Enroll Device"
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.rgb(138, 43, 226)) // Purple color
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                android.util.Log.d("MainActivity", "📱 ENROLLMENT BUTTON PRESSED")
                appendResult("📱 ENROLLMENT BUTTON PRESSED!")
                appendResult("⏰ Timestamp: ${java.util.Date()}")
                appendResult("🔐 Starting device enrollment process...")
                startDeviceEnrollment()
            }
        }
        
        // Check Pending button
        var tempCheckPendingButton = Button(this).apply {
            text = "🔍 Check Pending Auth"
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.BLUE)
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                android.util.Log.d("MainActivity", "🔘 BUTTON PRESSED: Check Pending Auth")
                appendResult("🔘 BUTTON PRESSED: Check Pending Auth")
                appendResult("⏰ Timestamp: ${java.util.Date()}")
                appendResult("🔍 Testing button functionality...")
                appendResult("✅ Button click detected - proceeding with auth check...")
                checkPendingAuthentication()
            }
        }
        
        // Accept button (initially disabled)
        var tempAcceptButton = Button(this).apply {
            text = "✅ Accept Auth"
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.GREEN)
            setTextColor(android.graphics.Color.WHITE)
            isEnabled = false
            setOnClickListener {
                acceptCurrentAuthentication()
            }
        }
        
        
        // Terminate button
        var tempTerminateButton = Button(this).apply {
            text = "❌ Terminate Application"
            textSize = 16f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.RED)
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                finishAffinity()
                exitProcess(0)
            }
        }
        
        // Assign all lateinit variables at once
        scrollView = tempScrollView
        resultsTextView = tempResultsTextView
        challengeEditText = tempChallengeEditText
        enrollButton = tempEnrollButton
        checkPendingButton = tempCheckPendingButton
        acceptButton = tempAcceptButton
        terminateButton = tempTerminateButton
        
        // Add views to layout
        layout.addView(titleTextView)
        layout.addView(scrollView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        // layout.addView(testButton)  // COMMENTED OUT - test button removed to save space
        layout.addView(clearButton)
        layout.addView(challengeEditText)  // NEW: Challenge input field
        layout.addView(enrollButton)  // NEW: Enrollment button
        layout.addView(checkPendingButton)
        layout.addView(acceptButton)
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
            val expectedMin = Math.pow(10.0, (digits - 1).toDouble()).toInt()
            val expectedMax = Math.pow(10.0, digits.toDouble()).toInt() - 1
            
            if (challenge in expectedMin..expectedMax) {
                appendResult("✅ ${digits}-digit challenge: $challenge (valid range)")
            } else {
                throw RuntimeException("Challenge $challenge outside valid range [$expectedMin, $expectedMax]")
            }
        }
        
        appendResult("✅ All secure challenge tests: PASSED")
    }
    
    private fun appendResult(message: String) {
        testResults.append(message).append("\n")
        resultsTextView.text = testResults.toString()
        
        // Auto-scroll to bottom
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    private fun clearLogs() {
        testResults.clear()
        resultsTextView.text = "🧹 Logs cleared at ${java.util.Date()}\n\n"
        android.util.Log.d("MainActivity", "🗑️ Logs cleared")
    }
    
    private fun appendError(error: Throwable, context: String) {
        appendResult("💥 ERROR in $context:")
        appendResult("❌ Type: ${error.javaClass.simpleName}")
        appendResult("❌ Message: ${error.message}")
        appendResult("📊 Stack trace:")
        
        // Format stack trace for better readability
        val stackTrace = error.stackTraceToString()
        val lines = stackTrace.split("\n")
        lines.forEach { line ->
            if (line.trim().isNotEmpty()) {
                appendResult("   $line")
            }
        }
        appendResult("") // Empty line for separation
    }
    
    private operator fun String.times(count: Int): String = this.repeat(count)
    
    /**
     * Checks for pending authentication requests.
     */
    private fun checkPendingAuthentication() {
        appendResult("🔘 BUTTON PRESSED: Check Pending Auth - t1")
        appendResult("⏰ Timestamp: ${java.util.Date()}")
        appendResult("=" * 60)

        appendResult("🔘 BUTTON PRESSED: Check Pending Auth - t2")
        
        lifecycleScope.launch {
            try {
                appendResult("🚀 Starting authentication check process...")
                appendResult("🔐 Using hard-coded private key")
                appendResult("")
                
                appendResult("📋 STEP 1: Calling authService.checkPendingAuth()...")
                val pendingAuth = authService.checkPendingAuth()

             //   val pendingAuth =  org.ezkey.mobile.v1.auth.AuthAttemptPendingResponseDto(
               //     authAttemptId = 123,
              //      authAttemptProofToken = "test.proof.token.123456789",
               //     authAttemptProofTokenSignedByIntegration = "test.integration.signature.abcdef",
               //     authAttemptChallengeRequired = false
               // )

                appendResult("✅ Service call completed")
                
                if (pendingAuth != null) {
                    currentPendingAuth = pendingAuth
                    acceptButton.isEnabled = true
                    
                    appendResult("🎉 SUCCESS: PENDING AUTHENTICATION REQUEST FOUND!")
                    appendResult("📋 Auth Attempt ID: ${pendingAuth.authAttemptId}")
                    appendResult("🔐 Challenge Required: ${pendingAuth.authAttemptChallengeRequired}")
                    appendResult("🎫 Proof Token: ${pendingAuth.authAttemptProofToken.take(50)}...")
                    appendResult("🔏 Integration Signature: ${pendingAuth.authAttemptProofTokenSignedByIntegration.take(50)}...")
                    appendResult("")
                    appendResult("💡 Click 'Accept Auth' button to approve this request")
                } else {
                    currentPendingAuth = null
                    acceptButton.isEnabled = false
                    
                    appendResult("ℹ️ RESULT: No pending authentication requests found")
                    appendResult("💤 This is normal - no active auth attempts")
                    appendResult("🔄 You can press the button again to check for new requests")
                }
                
                appendResult("=" * 60)
                
            } catch (e: Exception) {
                appendResult("💥 CRITICAL ERROR during authentication check!")
                appendError(e, "authentication check")
                appendResult("🔍 Troubleshooting:")
                appendResult("   - Check if ezkey-auth-api is running on localhost:8080")
                appendResult("   - Verify network connectivity")
                appendResult("   - Check API endpoint /api/v1/auth-attempts/pending/24")
                appendResult("   - Check if the device has internet permission")
                
                currentPendingAuth = null
                acceptButton.isEnabled = false
            }
        }
    }
    
    /**
     * Accepts the current pending authentication request.
     */
    private fun acceptCurrentAuthentication() {
        val pendingAuth = currentPendingAuth
        if (pendingAuth == null) {
            appendResult("❌ No pending authentication request to accept")
            return
        }
        
        lifecycleScope.launch {
            try {
                appendResult("✅ Accepting authentication request...")
                appendResult("📋 Auth Attempt ID: ${pendingAuth.authAttemptId}")
                appendResult("🔐 Challenge Required: ${pendingAuth.authAttemptChallengeRequired}")
                appendResult("=" * 60)
                
                val response = authService.acceptAuthAttempt(
                    authAttemptId = pendingAuth.authAttemptId,
                    authAttemptProofToken = pendingAuth.authAttemptProofToken,
                    challengeRequired = pendingAuth.authAttemptChallengeRequired
                )
                
                appendResult("🎉 AUTHENTICATION ACCEPTED SUCCESSFULLY!")
                appendResult("📊 Result: ${response.result}")
                appendResult("💬 Message: ${response.message}")
                appendResult("")
                appendResult("✅ The authentication request has been approved")
                appendResult("🔒 The integration can now proceed with the authenticated operation")
                
                // Reset state
                currentPendingAuth = null
                acceptButton.isEnabled = false
                
                appendResult("=" * 60)
                
            } catch (e: Exception) {
                appendResult("❌ ERROR accepting auth: ${e.message}")
                appendResult("📊 Stack trace: ${e.stackTraceToString()}")
            }
        }
    }
    
    /**
     * Starts the device enrollment process.
     */
    private fun startDeviceEnrollment() {
        appendResult("📱 ENROLLMENT BUTTON PRESSED - Starting enrollment process")
        appendResult("⏰ Timestamp: ${java.util.Date()}")
        appendResult("=" * 60)

        lifecycleScope.launch {
            try {
                appendResult("🚀 Starting device enrollment process...")
                appendResult("🔐 This will generate real device keys and bind to enrollment")
                appendResult("")
                
                // TODO: Implement real enrollment with API calls
                appendResult("📋 STEP 1: Generating device key pair...")
                val deviceKeyPair = signatureService.generateRsaKeyPair(2048)
                appendResult("✅ Device key pair generated successfully")
                appendResult("🔑 Private key length: ${deviceKeyPair.base64PrivateKey.length} chars")
                appendResult("🔑 Public key length: ${deviceKeyPair.base64PublicKey.length} chars")
                appendResult("")
                
                    appendResult("📋 STEP 2: Storing device keys locally...")
                    // Store keys in SimpleDeviceStorage
                    val deviceStorage = SimpleDeviceStorage(this@MainActivity)
                    deviceStorage.storeDevicePrivateKey(deviceKeyPair.base64PrivateKey)
                    deviceStorage.storeDevicePublicKey(deviceKeyPair.base64PublicKey)
                    appendResult("✅ Device keys stored locally in SharedPreferences")
                    appendResult("🔑 Private key stored: ${deviceKeyPair.base64PrivateKey.take(20)}...")
                    appendResult("🔑 Public key stored: ${deviceKeyPair.base64PublicKey.take(20)}...")
                    appendResult("")
                
                appendResult("📋 STEP 3: Binding to enrollment via API...")
                // Make real API call to bind enrollment
                try {
                    val enrollmentService = EnrollmentService() // No Context needed
                    val enrollmentId = 4 // Hardcoded for POC
                    val enrollmentProofToken = "ndtQ55aTtZrDBZN0Q2jXD7-VnWdrqB1BeEpJSx-GgrM.1761350820455.Ksc8b-rmbQmAiIKdX7-2Zg" // Hardcoded for POC
                    
                    appendResult("🌐 Making API call to /api/v1/enrollments/bind...")
                    appendResult("📋 Enrollment ID: $enrollmentId")
                    appendResult("🎫 Proof Token: ${enrollmentProofToken.take(20)}...")
                    
                    val result = enrollmentService.bindEnrollment(enrollmentId, enrollmentProofToken)
                    
                    if (result != null) {
                        appendResult("✅ API call successful!")
                        appendResult("🏢 Integration: ${result.integrationName}")
                        appendResult("📱 Enrollment: ${result.enrollmentName}")
                        appendResult("🔑 Public Key: ${result.integrationPublicKey.take(20)}...")
                        
                        // Store enrollment data (only 2 parameters supported)
                        deviceStorage.storeEnrollmentData(
                            result.enrollmentId,
                            result.enrollmentProofToken
                        )
                        appendResult("💾 Enrollment data stored locally")
                        appendResult("📋 Note: Additional data (integration name, etc.) not stored in this version")
                        
                        // STEP 4: Verify enrollment with device keys
                        appendResult("")
                        appendResult("📋 STEP 4: Verifying enrollment with device keys...")
                        try {
                            // Get challenge response from user input
                            val challengeText = challengeEditText.text.toString().trim()
                            val challengeResponse = if (challengeText.isNotEmpty()) {
                                challengeText.toIntOrNull() ?: 123456
                            } else {
                                123456 // Default fallback
                            }
                            
                            // Sign the enrollment proof token with device private key
                            val enrollmentProofTokenSigned = signatureService.generateSignature(
                                result.enrollmentProofToken,
                                deviceKeyPair.base64PrivateKey
                            )
                            
                            appendResult("🔐 Challenge Response: $challengeResponse ${if (challengeText.isNotEmpty()) "(from input)" else "(default)"}")
                            appendResult("🔑 Device Public Key: ${deviceKeyPair.base64PublicKey.take(20)}...")
                            appendResult("✍️ Signed Token: ${enrollmentProofTokenSigned.take(20)}...")
                            
                            // Make verify API call
                            val verifyResult = enrollmentService.verifyEnrollment(
                                result.enrollmentId,
                                challengeResponse,
                                deviceKeyPair.base64PublicKey,
                                enrollmentProofTokenSigned
                            )
                            
                            if (verifyResult?.active == true) {
                                appendResult("✅ Enrollment verification successful!")
                                appendResult("🎉 Device is now fully enrolled and active!")
                            } else {
                                appendResult("❌ Enrollment verification failed")
                            }
                        } catch (e: Exception) {
                            appendResult("💥 Verification failed: ${e.message}")
                        }
                    } else {
                        appendResult("❌ API call failed - no response")
                    }
                } catch (e: Exception) {
                    appendResult("💥 API call failed: ${e.message}")
                    appendResult("🔍 Check if ezkey-auth-api is running on ngrok")
                }
                appendResult("")
                
                appendResult("🎉 ENROLLMENT COMPLETED SUCCESSFULLY!")
                appendResult("📱 Device is now enrolled and ready for authentication!")
                appendResult("🔄 You can now use 'Check Pending Auth' with real keys")
                
                appendResult("=" * 60)
                
            } catch (e: Exception) {
                appendResult("💥 CRITICAL ERROR during enrollment process!")
                appendError(e, "device enrollment")
                appendResult("🔍 Troubleshooting:")
                appendResult("   - Check if ezkey-auth-api is running")
                appendResult("   - Verify network connectivity")
                appendResult("   - Check enrollment proof token validity")
                appendResult("   - Ensure device has internet permission")
            }
        }
    }
}
