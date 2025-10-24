/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentService
 * Description: Service for handling device enrollment with ezkey-auth-api.
 */

package org.ezkey.mobile.v1.enrollment

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

/**
 * Service for handling device enrollment with the ezkey-auth-api.
 * 
 * This service provides methods to:
 * - Bind a device to an enrollment using enrollment proof token
 * - Generate device key pairs for enrollment
 * - Handle cryptographic operations for enrollment process
 * 
 * @since 2025
 */
class EnrollmentService {
    
    companion object {
        private const val BASE_URL = "https://goateed-katalina-monsoonal.ngrok-free.dev"
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
    }
    
    private val httpClient = OkHttpClient()
    private val signatureService = SignatureService()
    
    /**
     * Binds a device to an enrollment using enrollment proof token.
     * 
     * @param enrollmentId the ID of the enrollment to bind to
     * @param enrollmentProofToken the proof token for enrollment binding
     * @param language the language preference (default: "en")
     * @return EnrollmentBindResponseDto containing the binding information
     * @throws Exception if API communication fails
     */
    suspend fun bindEnrollment(
        enrollmentId: Int,
        enrollmentProofToken: String,
        language: String = "en"
    ): EnrollmentBindResponseDto = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("EnrollmentService", "🔍 Starting bindEnrollment()")
            android.util.Log.d("EnrollmentService", "📋 Enrollment ID: $enrollmentId")
            android.util.Log.d("EnrollmentService", "🎫 Proof Token: ${enrollmentProofToken.take(20)}...")
            android.util.Log.d("EnrollmentService", "🌐 Language: $language")
            
            // Prepare request payload
            android.util.Log.d("EnrollmentService", "📋 STEP 1: Preparing enrollment bind request...")
            val requestPayload = JSONObject().apply {
                put("enrollmentId", enrollmentId)
                put("enrollmentProofToken", enrollmentProofToken)
                put("language", language)
            }
            
            val payloadJson = requestPayload.toString()
            android.util.Log.d("EnrollmentService", "📤 ENROLLMENT BIND REQUEST PAYLOAD JSON:")
            android.util.Log.d("EnrollmentService", payloadJson)
            
            // Make API call
            android.util.Log.d("EnrollmentService", "📋 STEP 2: Making HTTP POST request to bind enrollment...")
            val fullUrl = "$BASE_URL/api/v1/enrollments/bind"
            android.util.Log.d("EnrollmentService", "🌐 URL: $fullUrl")
            android.util.Log.d("EnrollmentService", "📤 Method: POST")
            android.util.Log.d("EnrollmentService", "📋 Content-Type: $MEDIA_TYPE_JSON")
            android.util.Log.d("EnrollmentService", "📋 Content-Length: ${payloadJson.length}")
            
            val request = Request.Builder()
                .url(fullUrl)
                .post(payloadJson.toRequestBody(MEDIA_TYPE_JSON.toMediaType()))
                .build()
            
            // Log request headers
            android.util.Log.d("EnrollmentService", "📤 ENROLLMENT BIND REQUEST HEADERS:")
            request.headers.forEach { (name, value) ->
                android.util.Log.d("EnrollmentService", "   $name: $value")
            }
            
            android.util.Log.d("EnrollmentService", "📤 Sending enrollment bind request...")
            val startTime = System.currentTimeMillis()
            val response = httpClient.newCall(request).execute()
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            android.util.Log.d("EnrollmentService", "📥 Enrollment bind response received in ${duration}ms!")
            android.util.Log.d("EnrollmentService", "📊 HTTP Status Code: ${response.code}")
            android.util.Log.d("EnrollmentService", "📊 Response Time: ${duration}ms")
            
            // Log response headers
            android.util.Log.d("EnrollmentService", "📥 ENROLLMENT BIND RESPONSE HEADERS:")
            response.headers.forEach { (name, value) ->
                android.util.Log.d("EnrollmentService", "   $name: $value")
            }
            
            val responseBody = response.body?.string()
            android.util.Log.d("EnrollmentService", "📥 Enrollment Bind Response Body Length: ${responseBody?.length ?: 0}")
            android.util.Log.d("EnrollmentService", "📥 Enrollment Bind Response Body: $responseBody")
            
            if (response.code == 200) {
                android.util.Log.d("EnrollmentService", "✅ SUCCESS: Enrollment bound successfully (HTTP 200)")
                parseBindResponse(responseBody)
            } else {
                android.util.Log.e("EnrollmentService", "❌ ERROR: Enrollment bind API call failed with code ${response.code}")
                android.util.Log.e("EnrollmentService", "📥 Error response body: $responseBody")
                throw Exception("Enrollment bind API call failed with code ${response.code}: $responseBody")
            }
        } catch (e: Exception) {
            android.util.Log.e("EnrollmentService", "💥 EXCEPTION in bindEnrollment: ${e.message}")
            android.util.Log.e("EnrollmentService", "📊 Stack trace: ${e.stackTraceToString()}")
            throw Exception("Failed to bind enrollment: ${e.message}", e)
        }
    }
    
    /**
     * Generates a new RSA key pair for device enrollment.
     * 
     * @param keySize the size of the RSA key (default: 2048)
     * @return SignatureService.RsaKeyPair containing the generated key pair
     */
    suspend fun generateDeviceKeyPair(keySize: Int = 2048): SignatureService.RsaKeyPair = withContext(Dispatchers.Default) {
        android.util.Log.d("EnrollmentService", "🔑 Generating device key pair (RSA-$keySize)...")
        
        val startTime = System.currentTimeMillis()
        val keyPair = signatureService.generateRsaKeyPair(keySize)
        val endTime = System.currentTimeMillis()
        
        android.util.Log.d("EnrollmentService", "✅ Device key pair generated successfully in ${endTime - startTime}ms")
        android.util.Log.d("EnrollmentService", "📏 Private key length: ${keyPair.base64PrivateKey.length} chars")
        android.util.Log.d("EnrollmentService", "📏 Public key length: ${keyPair.base64PublicKey.length} chars")
        android.util.Log.d("EnrollmentService", "🔍 Private key preview: ${keyPair.base64PrivateKey.take(50)}...")
        android.util.Log.d("EnrollmentService", "🔍 Public key preview: ${keyPair.base64PublicKey.take(50)}...")
        
        keyPair
    }
    
    /**
     * Parses the enrollment bind response JSON into DTO.
     */
    private fun parseBindResponse(jsonString: String?): EnrollmentBindResponseDto {
        val json = JSONObject(jsonString ?: "{}")
        return EnrollmentBindResponseDto(
            enrollmentId = json.optInt("enrollmentId"),
            enrollmentProofToken = json.optString("enrollmentProofToken"),
            integrationPublicKey = json.optString("integrationPublicKey"),
            integrationName = json.optString("integrationName"),
            integrationDescription = json.optString("integrationDescription"),
            integrationLogo = json.optString("integrationLogo"),
            enrollmentName = json.optString("enrollmentName")
        )
    }
}

/**
 * Data class for enrollment bind response.
 */
data class EnrollmentBindResponseDto(
    val enrollmentId: Int,
    val enrollmentProofToken: String,
    val integrationPublicKey: String,
    val integrationName: String,
    val integrationDescription: String,
    val integrationLogo: String,
    val enrollmentName: String
)
