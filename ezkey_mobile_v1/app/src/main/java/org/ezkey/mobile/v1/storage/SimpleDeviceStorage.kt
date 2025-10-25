/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: SimpleDeviceStorage
 * Description: Simple local storage for device keys using SharedPreferences.
 */

package org.ezkey.mobile.v1.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple storage for device keys using SharedPreferences.
 * 
 * This class provides basic storage for device private/public keys and enrollment data.
 * Uses SharedPreferences for simplicity and reliability.
 * 
 * @since 2025
 */
class SimpleDeviceStorage(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("EzkeyDevice", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_DEVICE_PRIVATE_KEY = "device_private_key"
        private const val KEY_DEVICE_PUBLIC_KEY = "device_public_key"
        private const val KEY_ENROLLMENT_ID = "enrollment_id"
        private const val KEY_ENROLLMENT_PROOF_TOKEN = "enrollment_proof_token"
    }
    
    /**
     * Store device private key.
     */
    fun storeDevicePrivateKey(key: String) {
        prefs.edit().putString(KEY_DEVICE_PRIVATE_KEY, key).apply()
    }
    
    /**
     * Get device private key.
     */
    fun getDevicePrivateKey(): String? = prefs.getString(KEY_DEVICE_PRIVATE_KEY, null)
    
    /**
     * Store device public key.
     */
    fun storeDevicePublicKey(key: String) {
        prefs.edit().putString(KEY_DEVICE_PUBLIC_KEY, key).apply()
    }
    
    /**
     * Get device public key.
     */
    fun getDevicePublicKey(): String? = prefs.getString(KEY_DEVICE_PUBLIC_KEY, null)
    
    /**
     * Store enrollment data.
     */
    fun storeEnrollmentData(enrollmentId: Int, enrollmentProofToken: String) {
        prefs.edit()
            .putInt(KEY_ENROLLMENT_ID, enrollmentId)
            .putString(KEY_ENROLLMENT_PROOF_TOKEN, enrollmentProofToken)
            .apply()
    }
    
    /**
     * Get enrollment ID.
     */
    fun getEnrollmentId(): Int = prefs.getInt(KEY_ENROLLMENT_ID, -1)
    
    /**
     * Get enrollment proof token.
     */
    fun getEnrollmentProofToken(): String? = prefs.getString(KEY_ENROLLMENT_PROOF_TOKEN, null)
    
    /**
     * Check if device is enrolled.
     */
    fun isDeviceEnrolled(): Boolean = prefs.contains(KEY_ENROLLMENT_ID)
    
    /**
     * Clear all stored data.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
