/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthService
 * Description: Service for handling authentication requests with ezkey-auth-api.
 */

package org.ezkey.mobile.v1.auth

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ezkey.mobile.v1.crypto.SignatureService
import org.ezkey.mobile.v1.storage.SimpleDeviceStorage
import org.json.JSONObject
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.KeyFactory
import java.nio.charset.StandardCharsets

/**
 * Service for handling authentication requests with the ezkey-auth-api.
 * 
 * This service provides methods to:
 * - Check for pending authentication requests
 * - Respond to authentication attempts
 * - Handle cryptographic operations for API communication
 * 
 * @since 2025
 */
class AuthService(private val context: Context) {
    
    companion object {
        private const val BASE_URL = "https://goateed-katalina-monsoonal.ngrok-free.dev"
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
    }
    
    private val httpClient = OkHttpClient()
    private val signatureService = SignatureService()
    private val deviceStorage = SimpleDeviceStorage(context)
    
    // Fallback hard-coded values if no stored keys
    private val fallbackDevicePrivateKeyBase64 = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDxjNnamFBMhTNoTDZ6AU3KNcv8+TpBM14TzxaB1KOzf9WdbhALONm2uyuEMW5jehHLLnEOLJ2eMSBQ/AKjd3NpgvbfgrvRzslqCWJpp/HVGLQlmVaG+xjUW6vQNd3tSmGVXn60Y368VHybgzULWczD1rlgVQJJQwnrCEW72HiFimbQ6RpPKRA/GRZAJXyf7HZ/e42D5dOxUBgNWloMPy0uvmDAtep5GyEGmCUCY89ntfrrhuso06fArLwyWLdgton+TV5manpPU3haWWEasJFicXaqIBJbw9CordRGXjRDIUWDpxmxoa38G0VvQBQeatnf7jjN0lQ31Bavzk5CiM/NAgMBAAECggEADaQLpXmWh0u6ZHhxVyB9uR6in22fqZDyDiJSvhA5Emj0skhF5axXNyeIxJVaC4oYOSYtQkSovgc+MPSaXYrgXKQFtweV/bo0y6UuBpNyZ7tWaQ0owsSpWUy3/jEckEr0CdBlTWCVBqOqycl2FGcE1kZo/5StZV/AzqIP9hS6cagMH9oYqmA8jIzPCm2hb8+ctF+BV3/AH+Qhwt81rOK65lEwf0PVF5i5uyQAwPKM2Pdm3P1utlh9HZJSh80Gt+7FEG1SGUdncnEOEmMtLIT32U2qSS0bkdsviKjzNk6eG/9BkVS+arEekH3wpgq+7g2QuxBvk7uY8YHMk7s/q/JyLQKBgQDyQu8+UvxOGN1jAxrLcL+33BZiaUWCgcfb40avTFQMEnQjq9dDeATaOhHLSsQ6mPDZ7LjTbCNrGa2fCR/NOXPntzqvl67ph1CkBIfT7ObQCVvt2AVeyoH/uUex/PSR7uzaKHb29E26L/7qU4tkIaGAtU3utJx4Nca+M5YCzQd+lwKBgQD/P5cvt8WhYJaeVSLF1fEDz03O9/cm8oGMrbXVfY5RP/nOYJawukTf85/htbwX/QchG0hoIOcOplf9rg9yE4NO+5TTScCQlZ7F/ICVSvfd09OBSseTeYxslFZNgCNlsWva4zmPcgiJ7JYEXqK0f1idiFczd9LUZFx4iyw7ML3VOwKBgQC9eX5Wd18f0bCs9MurG7bGnRrgw0b7KHfg0aQCDKebfX9aOtc0zJS2/T3XitVoox+UweFYcjZNWJsDTIaT4wB01UjP9sl1mkCG14hIRvvK79b3ccHZfncoQ4gAfD/oNz8F7SoGQdLc8RblvIvDt83xtVuLe7T5C84yCnSkIilfZwKBgHws6JVLVzciMURH8MnEQiNzV8wnsDJfag0ReVOqaHE4qYPwU38Yr2cwM4jwC9izvSMrDbeywhXLcSU158e8nHXxSL7ds3PjhkGVjMyUky903QGaaqthR6KPK8k6XH4dqXXsc1VIycSnt3favlcHWQoSiTG9ynCPfrkaI+OL295/AoGBAM/SHCiMPexN8rtBQcAop//mRtwvA8wdPpwbVSiLctSXFoDyW2raiKYCGJ5J3V/TmjwBsFvhEhEBQqoq3nlnxbq3o+glsyM/zg/OE3d2uj8wIltDRKBT0v60hZSFM+0BiBg1rIFHX/fM4toQET3gEIcRhmnneNyIUCS0hLZ9PlB+"

    /**
     * Checks for pending authentication requests for the hard-coded enrollment.
     * 
     * @return AuthAttemptPendingResponseDto if pending request exists, null otherwise
     * @throws Exception if API communication fails
     */
    suspend fun checkPendingAuth(): AuthAttemptPendingResponseDto? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AuthService", "🔍 Starting checkPendingAuth()")
            
            // Generate device proof token
            android.util.Log.d("AuthService", "📋 STEP 1: Generating device proof token...")
            val deviceProofToken = signatureService.generateProofToken()
            android.util.Log.d("AuthService", "✅ Proof token generated: ${deviceProofToken.take(50)}...")
            
            // Sign the proof token with device private key
            android.util.Log.d("AuthService", "📋 STEP 2: Signing proof token with device private key...")
            
            // Get device private key from storage or use fallback
            val devicePrivateKey = deviceStorage.getDevicePrivateKey() ?: fallbackDevicePrivateKeyBase64
            android.util.Log.d("AuthService", "🔑 Using ${if (deviceStorage.getDevicePrivateKey() != null) "stored" else "fallback"} device key")
            
            val deviceProofTokenSigned = signatureService.generateSignature(deviceProofToken, devicePrivateKey)
            android.util.Log.d("AuthService", "✅ Proof token signed: ${deviceProofTokenSigned.take(50)}...")
            
            // Prepare request payload
            android.util.Log.d("AuthService", "📋 STEP 3: Preparing request payload...")
            
            // Get enrollment data from storage or use fallback
            val enrollmentId = if (deviceStorage.isDeviceEnrolled()) {
                deviceStorage.getEnrollmentId()
            } else {
                3 // Fallback enrollment ID
            }
            
            val enrollmentProofToken = deviceStorage.getEnrollmentProofToken() 
                ?: "wiHKoa-qVqSdj83Kg8kTnfWHUJkjOC-eKMVqNMiPAHY.1761340112663.JEypt05tUml7wyyQBDp9XQ" // Fallback
            
            android.util.Log.d("AuthService", "🎫 Enrollment ID: $enrollmentId")
            android.util.Log.d("AuthService", "🎫 Enrollment Proof Token: ${enrollmentProofToken.take(20)}...")
            
            val requestPayload = JSONObject().apply {
                put("enrollmentId", enrollmentId)
                put("enrollmentProofToken", enrollmentProofToken)
                put("deviceProofToken", deviceProofToken)
                put("deviceProofTokenSigned", deviceProofTokenSigned)
            }
            
            val payloadJson = requestPayload.toString()
            android.util.Log.d("AuthService", "📤 REQUEST PAYLOAD JSON:")
            android.util.Log.d("AuthService", payloadJson)
            
            // Make API call
            android.util.Log.d("AuthService", "📋 STEP 4: Making HTTP POST request...")
            val fullUrl = "$BASE_URL/api/v1/auth-attempts/pending"
            android.util.Log.d("AuthService", "🌐 URL: $fullUrl")
            android.util.Log.d("AuthService", "📤 Method: POST")
            android.util.Log.d("AuthService", "📋 Content-Type: $MEDIA_TYPE_JSON")
            android.util.Log.d("AuthService", "📋 Content-Length: ${payloadJson.length}")
            
            val request = Request.Builder()
                .url(fullUrl)
                .post(payloadJson.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
                .build()
            
            // Log request headers
            android.util.Log.d("AuthService", "📤 REQUEST HEADERS:")
            request.headers.forEach { (name, value) ->
                android.util.Log.d("AuthService", "   $name: $value")
            }
            
            android.util.Log.d("AuthService", "📤 Sending request...")
            val startTime = System.currentTimeMillis()
            val response = httpClient.newCall(request).execute()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            android.util.Log.d("AuthService", "📥 Response received in ${duration}ms!")
            android.util.Log.d("AuthService", "📊 HTTP Status Code: ${response.code}")
            android.util.Log.d("AuthService", "📊 Response Time: ${duration}ms")
            
            // Log response headers
            android.util.Log.d("AuthService", "📥 RESPONSE HEADERS:")
            response.headers.forEach { (name, value) ->
                android.util.Log.d("AuthService", "   $name: $value")
            }
            
            val responseBody = response.body?.string()
            android.util.Log.d("AuthService", "📥 Response Body Length: ${responseBody?.length ?: 0}")
            android.util.Log.d("AuthService", "📥 Response Body: $responseBody")
            
            when (response.code) {
                200 -> {
                    android.util.Log.d("AuthService", "✅ SUCCESS: Pending request found (HTTP 200)")
                    // Pending request found
                    parsePendingResponse(responseBody)
                }
                204 -> {
                    android.util.Log.d("AuthService", "ℹ️ NO CONTENT: No pending requests (HTTP 204)")
                    // No pending requests
                    null
                }
                else -> {
                    android.util.Log.e("AuthService", "❌ ERROR: API call failed with code ${response.code}")
                    android.util.Log.e("AuthService", "📥 Error response body: $responseBody")
                    throw Exception("API call failed with code ${response.code}: $responseBody")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthService", "💥 EXCEPTION in checkPendingAuth: ${e.message}")
            android.util.Log.e("AuthService", "📊 Stack trace: ${e.stackTraceToString()}")
            throw Exception("Failed to check pending auth: ${e.message}", e)
        }
    }
    
    /**
     * Responds to an authentication attempt by accepting it.
     * 
     * @param authAttemptId the ID of the authentication attempt to respond to
     * @param authAttemptProofToken the proof token from the pending request
     * @param challengeRequired whether a challenge response is required
     * @return AuthAttemptRespondResponseDto containing the response result
     * @throws Exception if API communication fails
     */
    suspend fun acceptAuthAttempt(
        authAttemptId: Int,
        authAttemptProofToken: String,
        challengeRequired: Boolean
    ): AuthAttemptRespondResponseDto = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AuthService", "🔍 Starting acceptAuthAttempt()")
            android.util.Log.d("AuthService", "📋 Auth Attempt ID: $authAttemptId")
            android.util.Log.d("AuthService", "🔐 Challenge Required: $challengeRequired")
            
            // Sign the auth attempt proof token with device private key
            android.util.Log.d("AuthService", "📋 STEP 1: Signing auth attempt proof token...")
            
            // Get device private key from storage or use fallback
            val devicePrivateKey = deviceStorage.getDevicePrivateKey() ?: fallbackDevicePrivateKeyBase64
            android.util.Log.d("AuthService", "🔑 Using ${if (deviceStorage.getDevicePrivateKey() != null) "stored" else "fallback"} device key")
            
            val authAttemptProofTokenSigned = signatureService.generateSignature(authAttemptProofToken, devicePrivateKey)
            android.util.Log.d("AuthService", "✅ Token signed: ${authAttemptProofTokenSigned.take(50)}...")
            
            // Prepare request payload
            android.util.Log.d("AuthService", "📋 STEP 2: Preparing accept request payload...")
            val requestPayload = JSONObject().apply {
                put("authAttemptId", authAttemptId)
                put("authAttemptProofTokenSignedByDevice", authAttemptProofTokenSigned)
                put("authAttemptAccepted", true)
                if (challengeRequired) {
                    put("authAttemptChallengeResponse", 123456) // Hard-coded challenge response for POC
                }
            }
            
            val payloadJson = requestPayload.toString()
            android.util.Log.d("AuthService", "📤 ACCEPT REQUEST PAYLOAD JSON:")
            android.util.Log.d("AuthService", payloadJson)
            
            // Make API call
            android.util.Log.d("AuthService", "📋 STEP 3: Making HTTP POST request to accept...")
            val fullUrl = "$BASE_URL/api/v1/auth-attempts/respond"
            android.util.Log.d("AuthService", "🌐 URL: $fullUrl")
            android.util.Log.d("AuthService", "📤 Method: POST")
            android.util.Log.d("AuthService", "📋 Content-Type: $MEDIA_TYPE_JSON")
            android.util.Log.d("AuthService", "📋 Content-Length: ${payloadJson.length}")
            
            val request = Request.Builder()
                .url(fullUrl)
                .post(payloadJson.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
                .build()
            
            // Log request headers
            android.util.Log.d("AuthService", "📤 ACCEPT REQUEST HEADERS:")
            request.headers.forEach { (name, value) ->
                android.util.Log.d("AuthService", "   $name: $value")
            }
            
            android.util.Log.d("AuthService", "📤 Sending accept request...")
            val startTime = System.currentTimeMillis()
            val response = httpClient.newCall(request).execute()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            android.util.Log.d("AuthService", "📥 Accept response received in ${duration}ms!")
            android.util.Log.d("AuthService", "📊 HTTP Status Code: ${response.code}")
            android.util.Log.d("AuthService", "📊 Response Time: ${duration}ms")
            
            // Log response headers
            android.util.Log.d("AuthService", "📥 ACCEPT RESPONSE HEADERS:")
            response.headers.forEach { (name, value) ->
                android.util.Log.d("AuthService", "   $name: $value")
            }
            
            val responseBody = response.body?.string()
            android.util.Log.d("AuthService", "📥 Accept Response Body Length: ${responseBody?.length ?: 0}")
            android.util.Log.d("AuthService", "📥 Accept Response Body: $responseBody")
            
            if (response.code == 200) {
                android.util.Log.d("AuthService", "✅ SUCCESS: Authentication accepted (HTTP 200)")
                parseRespondResponse(responseBody)
            } else {
                android.util.Log.e("AuthService", "❌ ERROR: Accept API call failed with code ${response.code}")
                android.util.Log.e("AuthService", "📥 Error response body: $responseBody")
                throw Exception("API call failed with code ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthService", "💥 EXCEPTION in acceptAuthAttempt: ${e.message}")
            android.util.Log.e("AuthService", "📊 Stack trace: ${e.stackTraceToString()}")
            throw Exception("Failed to accept auth attempt: ${e.message}", e)
        }
    }
    
    /**
     * Decodes a Base64-encoded private key to PrivateKey object.
     */
    private fun decodePrivateKey(base64PrivateKey: String): PrivateKey {
        val keyBytes = Base64.decode(base64PrivateKey, Base64.NO_WRAP)
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePrivate(spec)
    }
    
    /**
     * Parses the pending response JSON into DTO.
     */
    private fun parsePendingResponse(jsonString: String?): AuthAttemptPendingResponseDto {
        val json = JSONObject(jsonString ?: "{}")
        return AuthAttemptPendingResponseDto(
            authAttemptId = json.optInt("authAttemptId"),
            authAttemptProofToken = json.optString("authAttemptProofToken"),
            authAttemptProofTokenSignedByIntegration = json.optString("authAttemptProofTokenSignedByIntegration"),
            authAttemptChallengeRequired = json.optBoolean("authAttemptChallengeRequired")
        )
    }
    
    /**
     * Parses the respond response JSON into DTO.
     */
    private fun parseRespondResponse(jsonString: String?): AuthAttemptRespondResponseDto {
        val json = JSONObject(jsonString ?: "{}")
        return AuthAttemptRespondResponseDto(
            result = json.optString("result"),
            message = json.optString("message")
        )
    }
}

/**
 * Data class for pending authentication response.
 */
data class AuthAttemptPendingResponseDto(
    val authAttemptId: Int,
    val authAttemptProofToken: String,
    val authAttemptProofTokenSignedByIntegration: String,
    val authAttemptChallengeRequired: Boolean
)

/**
 * Data class for authentication response result.
 */
data class AuthAttemptRespondResponseDto(
    val result: String,
    val message: String
)
