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

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ezkey.mobile.v1.crypto.SignatureService
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
class AuthService {
    
    companion object {
        private const val BASE_URL = "http://192.168.1.92:8080"
        private const val ENROLLMENT_ID = 24 // Hard-coded for POC
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
    }
    
    private val httpClient = OkHttpClient()
    private val signatureService = SignatureService()
    
    // Hard-coded values from 24.json for POC
    private val devicePrivateKeyBase64 = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCgamDfY+L6IsF2t3Msaa+UYktJAfpM9m9pxYGmz/k22soT641GmDvkM8Ik21NaCzTgQaT8+Sdibt5bxLzpBkQ4txdhDtlpYYvxu/XS4My+xi+9yq9YzrKZkbpzzervH3WSgfrGf1e8FhpJ2EU5FbSeVRxbZqbXKsQHwvGrQV5JHA57s4IxI1bh0m7VL3ZZDgelrygrSIQOkBZJLYmvUmmM+utp8QbyQ46ViRKfu1NXssgPmtjS/xSds3pBYyp8y+so0+uLws6js9mGOex4fu238/pg1pee7d6nbYzy1fBWcxMYGYdXDL5F0UFmGHKEo451sq+Kj3qdFcYlHz5AkzONAgMBAAECggEAAfabV4PI80fGnckMAB10OF9lmklyAn9UbyZ4LXSUXijNjVkPyK8afvLO0i9CNP/Rg1pT7LaxIs7GXbgpzO5QANbln3RhK9C1hKovJyGrz9ZShXYI5FzcECFM92R447lUvov+gW+gjfaofRvhz2hZGaqWZyksxJH+hgIx5ADJglQjgo5XkslyjWjDjZWFwU+RGrxfml1kr8vYfWU7Q89lAzWhNAP4q2gBSrazzFZ7HZWyoAc0IcGSKZzfT3drD3LtAAC9zz/rP5yu6+ALeKZ5KCEXf8bLrZwQpxE26dMMs0MHABE1GH3VKc5OmzJSiGfmy+Z/7nezgF6rWrVZEfKG4QKBgQDWXbNPBO9pG1RvOwpnlwlEu8Oo2eS64gkBulZXatuPtWzBUrjuVvGNJfgB4VhAhOsQNBYqjw6Icb6QLQcEfCbJonRQpg4+ObN3jo8cAUZDq1N+KlMAjD8LdIiSDAAO9xVZwIo6kJu4HhbPDGW3EatHUXA9W1yunU2AaxEpfkWAkQKBgQC/kkFEhp1mPbGuzo8aTQnlIIkegPphzPXNVvr8suAaeJdSAOLJ8Lx6jUhTFa89kfQ8+Ma6aAk2e1pmilhXg9tnorvWxmLsSn1zoWBnHKD/Am4cwvqew1t4zUB1SblGixUgbuNY3yri+KEiHKIgW/KAFF3R6Vr1Fad8mQ2LndMBPQKBgQCjAmXT9QDJgIrYeqES3PujVNUYlXkl07Tdp4D4wL1trpyg9fLC60KL/w90/pNJnMnTbIhenKKEh1pN8K+hbXdhZTPmECBMmTwr8jBJL37q/mFjU3QCuqNGZrtjs9RnjOSjd5Klrkl73QWjNN40NC928CrVvZ/g+KVyvfgzAV6AwQKBgQCe6tbV383mqLsjRhsUOGBDANB+y4Ppabtmkgc3ZbPegbcVwcJgvyG7kEQ1GEH9E3zGr6E7tV4fIgkGEzHht3Jk8GxeaMhbOBgFuhNctjUgFoX9uglpdqoE9k/IfFYEHRBzjUlvygGrs5EH2TTtHxl2Am8p2SkpMsNz6wt/jHckCQKBgQCv/8nm1+7w7RmFb9k2lGT8TNvk2Mm86jC1DoT4w8fova5I4h8+BBTarSzxi0EFeks2C+xEv1yHBk14RMYf4rPWKwbDbtrppGIMEDFS142uKpiYY+Fs4SMoeRnLHDsWPyE6gouJd9OQNm71cE5nfvKSbbMbhPdWId0hSrtyNHzTrg=="
    
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
            val devicePrivateKey = decodePrivateKey(devicePrivateKeyBase64)
            val deviceProofTokenSigned = signatureService.generateSignature(deviceProofToken, devicePrivateKeyBase64)
            android.util.Log.d("AuthService", "✅ Proof token signed: ${deviceProofTokenSigned.take(50)}...")
            
            // Prepare request payload
            android.util.Log.d("AuthService", "📋 STEP 3: Preparing request payload...")
            val requestPayload = JSONObject().apply {
                put("enrollmentId", ENROLLMENT_ID)
                put("deviceProofToken", deviceProofToken)
                put("deviceProofTokenSigned", deviceProofTokenSigned)
            }
            
            val payloadJson = requestPayload.toString()
            android.util.Log.d("AuthService", "📤 REQUEST PAYLOAD JSON:")
            android.util.Log.d("AuthService", payloadJson)
            
            // Make API call
            android.util.Log.d("AuthService", "📋 STEP 4: Making HTTP POST request...")
            android.util.Log.d("AuthService", "🌐 URL: $BASE_URL/api/v1/auth-attempts/pending/$ENROLLMENT_ID")
            android.util.Log.d("AuthService", "📤 Method: POST")
            android.util.Log.d("AuthService", "📋 Content-Type: $MEDIA_TYPE_JSON")
            
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/auth-attempts/pending/$ENROLLMENT_ID")
                .post(payloadJson.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
                .build()
            
            android.util.Log.d("AuthService", "📤 Sending request...")
            val response = httpClient.newCall(request).execute()
            android.util.Log.d("AuthService", "📥 Response received!")
            android.util.Log.d("AuthService", "📊 HTTP Status Code: ${response.code}")
            android.util.Log.d("AuthService", "📋 Response Headers: ${response.headers}")
            
            val responseBody = response.body?.string()
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
            val authAttemptProofTokenSigned = signatureService.generateSignature(authAttemptProofToken, devicePrivateKeyBase64)
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
            android.util.Log.d("AuthService", "🌐 URL: $BASE_URL/api/v1/auth-attempts/respond/$authAttemptId")
            android.util.Log.d("AuthService", "📤 Method: POST")
            
            val request = Request.Builder()
                .url("$BASE_URL/api/v1/auth-attempts/respond/$authAttemptId")
                .post(payloadJson.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
                .build()
            
            android.util.Log.d("AuthService", "📤 Sending accept request...")
            val response = httpClient.newCall(request).execute()
            android.util.Log.d("AuthService", "📥 Accept response received!")
            android.util.Log.d("AuthService", "📊 HTTP Status Code: ${response.code}")
            
            val responseBody = response.body?.string()
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
