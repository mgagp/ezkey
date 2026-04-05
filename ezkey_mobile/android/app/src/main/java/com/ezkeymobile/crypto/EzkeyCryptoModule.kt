/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyCryptoModule.kt
 * Description: Android native module exposing EC P-256 key management and signing operations to React Native.
 * Security Context: Implements EC P-256 through Android Keystore, requesting StrongBox when available, as described in docs/MOBILE_CRYPTO_REFERENCE.md.
 * @since 2025
 */

package com.ezkeymobile.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.ezkeymobile.BuildConfig
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * React Native module providing EC P-256 key generation, retrieval, signing, and deletion.
 *
 * Uses Android Keystore and requests StrongBox on supported devices.
 *
 * @since 2025
 */
class EzkeyCryptoModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  /**
   * Returns the module name exposed to React Native.
   *
   * @since 2025
   */
  override fun getName(): String = NAME

  /**
   * Generates an EC P-256 key pair for a specific enrollment.
   *
   * The key pair is stored in Android Keystore with StrongBox preference when available.
   * Each enrollment gets its own key pair through the platform keystore.
   *
   * @param enrollmentId The enrollment ID to generate the key pair for.
   * @param promise Promise resolved with true when the key pair already exists or after generation.
   * @since 2025
   */
  @ReactMethod
  fun generateEnrollmentKeyPair(enrollmentId: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val alias = getEnrollmentAlias(enrollmentId)

      // Check if key pair already exists
      if (keyStore.containsAlias(alias)) {
        promise.resolve(true)
        return
      }

      val keyPairGenerator = KeyPairGenerator.getInstance(
          KeyProperties.KEY_ALGORITHM_EC,
          ANDROID_KEY_STORE
      )

      val builder = KeyGenParameterSpec.Builder(
          alias,
          KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
      )
          .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")) // EC P-256
          .setDigests(KeyProperties.DIGEST_SHA256)
          .setUserAuthenticationRequired(false)

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setUnlockedDeviceRequired(true)
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        try {
          builder.setIsStrongBoxBacked(true) // Hardware-backed if available
        } catch (error: StrongBoxUnavailableException) {
          // Device does not provide StrongBox; continue without it.
        }
      }

      keyPairGenerator.initialize(builder.build())
      keyPairGenerator.generateKeyPair()

      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_KEY_GENERATION, error)
    }
  }

  /**
   * Retrieves the EC P-256 public key for a given enrollment.
   *
   * @param enrollmentId The enrollment ID to get the public key for.
   * @param promise Promise resolved with Base64-encoded X.509 public key.
   * @since 2025
   */
  @ReactMethod
  fun getPublicKey(enrollmentId: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val alias = getEnrollmentAlias(enrollmentId)

      if (!keyStore.containsAlias(alias)) {
        promise.reject(ERROR_CODE_NOT_FOUND, "Key pair not found for enrollment $enrollmentId")
        return
      }

      val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
      val publicKey = entry?.certificate?.publicKey
          ?: throw IllegalStateException("Public key not found for enrollment $enrollmentId")

      // Encode public key as X.509 SubjectPublicKeyInfo (ASN.1 DER)
      val encoded = publicKey.encoded
      val encodedBase64 = Base64.encodeToString(encoded, Base64.NO_WRAP)

      promise.resolve(encodedBase64)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_PUBLIC_KEY, error)
    }
  }

  /**
   * Signs data using the EC P-256 private key for a given enrollment.
   *
   * Uses ECDSA with SHA-256 signature algorithm.
   *
   * @param enrollmentId The enrollment ID to sign with.
   * @param data The data to sign (UTF-8 string).
   * @param promise Promise resolved with Base64-encoded ECDSA signature.
   * @since 2025
   */
  @ReactMethod
  fun sign(enrollmentId: String, data: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val alias = getEnrollmentAlias(enrollmentId)

      if (!keyStore.containsAlias(alias)) {
        promise.reject(ERROR_CODE_NOT_FOUND, "Key pair not found for enrollment $enrollmentId")
        return
      }

      val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
      val privateKey = entry?.privateKey
          ?: throw IllegalStateException("Private key not found for enrollment $enrollmentId")

      // Sign using ECDSA with SHA-256
      val signature = Signature.getInstance("SHA256withECDSA")
      signature.initSign(privateKey)
      signature.update(data.toByteArray(StandardCharsets.UTF_8))
      val signatureBytes = signature.sign()

      val encodedBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
      promise.resolve(encodedBase64)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_SIGN, error)
    }
  }

  /**
   * Verifies an Ed25519 signature over the given data using the integration public key.
   *
   * Used for integration-signed fields on Pending and Respond (see `AUTH_ATTEMPT_SIGNATURE_PAYLOAD`).
   * Public key is raw 32 bytes (Base64URL without padding on the wire); signature is raw 64 bytes
   * (Base64URL without padding). {@link IntegrationKeyVerifier} accepts Base64URL or standard Base64.
   *
   * @param data The exact payload that was signed (UTF-8).
   * @param signatureBase64 Base64-encoded raw Ed25519 signature.
   * @param publicKeyBase64 Base64-encoded raw Ed25519 public key.
   * @param promise Promise resolved with true if the signature is valid, false otherwise.
   * @since 2025
   */
  @ReactMethod
  fun verify(data: String, signatureBase64: String, publicKeyBase64: String, promise: Promise) {
    try {
      val valid = IntegrationKeyVerifier.verify(data, signatureBase64, publicKeyBase64)
      if (!valid) {
        Log.w(
            TAG,
            "Integration pending signature verify returned false (check Auth API JCA self-verify log vs logcat)",
        )
      }
      promise.resolve(valid)
    } catch (error: Exception) {
      Log.e(TAG, "verify threw", error)
      promise.reject(ERROR_CODE_VERIFY, error)
    }
  }

  /**
   * Returns the UTC ISO-8601 timestamp embedded at **native compile time** (Gradle `BuildConfig`).
   * Use for diagnostics to identify which APK/binary is installed (e.g. debug vs an older build).
   *
   * @param promise Resolved with the timestamp string.
   * @since 2025
   */
  @ReactMethod
  fun getBuildTimestamp(promise: Promise) {
    try {
      promise.resolve(BuildConfig.BUILD_TIMESTAMP)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_BUILD_TIMESTAMP, error)
    }
  }

  /**
   * Generates a device proof token for pending auth: same wire format as {@code
   * SignatureService.generateProofToken()} in ezkey-core (32 + 16 random bytes, URL-safe Base64
   * without padding, dot-separated). Uses {@link SecureRandom} (platform CSPRNG); does not depend
   * on {@code react-native-get-random-values}.
   *
   * @param promise Resolved with the token string.
   * @since 2025
   */
  @ReactMethod
  fun generateProofToken(promise: Promise) {
    try {
      val secureRandom = SecureRandom()
      val randomBytes = ByteArray(32)
      val saltBytes = ByteArray(16)
      secureRandom.nextBytes(randomBytes)
      secureRandom.nextBytes(saltBytes)
      val randomPart = base64UrlEncodeNoPadding(randomBytes)
      val saltPart = base64UrlEncodeNoPadding(saltBytes)
      promise.resolve("$randomPart.$saltPart")
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_PROOF_TOKEN, error)
    }
  }

  /**
   * Deletes the EC P-256 key pair for a given enrollment.
   *
   * @param enrollmentId The enrollment ID to delete the key pair for.
   * @param promise Promise resolved with true after deletion.
   * @since 2025
   */
  @ReactMethod
  fun deleteKeyPair(enrollmentId: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val alias = getEnrollmentAlias(enrollmentId)

      if (keyStore.containsAlias(alias)) {
        keyStore.deleteEntry(alias)
      }

      promise.resolve(true)
    } catch (error: Exception) {
      promise.reject(ERROR_CODE_DELETE, error)
    }
  }

  /**
   * Generates the Android Keystore alias for an enrollment.
   *
   * @param enrollmentId The enrollment ID.
   * @return The keystore alias.
   * @since 2025
   */
  private fun getEnrollmentAlias(enrollmentId: String): String {
    return "ezkey_enrollment_$enrollmentId"
  }

  /** Base64 URL-safe encoding without padding (parity with JDK {@code Base64.getUrlEncoder().withoutPadding()}). */
  private fun base64UrlEncodeNoPadding(bytes: ByteArray): String {
    var flags = Base64.URL_SAFE or Base64.NO_WRAP
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      flags = flags or Base64.NO_PADDING
    }
    var encoded = Base64.encodeToString(bytes, flags)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      while (encoded.endsWith("=")) {
        encoded = encoded.substring(0, encoded.length - 1)
      }
    }
    return encoded
  }

  companion object {
    const val NAME = "EzkeyCryptoModule"
    private const val TAG = "EzkeyCrypto"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val ERROR_CODE_KEY_GENERATION = "EZK_KEY_GENERATION_ERROR"
    private const val ERROR_CODE_PUBLIC_KEY = "EZK_PUBLIC_KEY_ERROR"
    private const val ERROR_CODE_SIGN = "EZK_SIGN_ERROR"
    private const val ERROR_CODE_NOT_FOUND = "EZK_KEY_NOT_FOUND"
    private const val ERROR_CODE_DELETE = "EZK_DELETE_ERROR"
    private const val ERROR_CODE_VERIFY = "EZK_VERIFY_ERROR"
    private const val ERROR_CODE_BUILD_TIMESTAMP = "EZK_BUILD_TIMESTAMP_ERROR"
    private const val ERROR_CODE_PROOF_TOKEN = "EZK_PROOF_TOKEN_ERROR"
  }
}
