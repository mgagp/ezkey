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

package org.ezkey.mobile.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.ezkey.mobile.BuildConfig
import java.io.IOException
import java.security.GeneralSecurityException
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** API 31+ {@link KeyInfo#getSecurityLevel()} (reflective). */
private fun keyInfoSecurityLevel(keyInfo: KeyInfo): Int? {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
    return null
  }
  return try {
    val m = KeyInfo::class.java.getMethod("getSecurityLevel")
    m.invoke(keyInfo) as Int
  } catch (_: ReflectiveOperationException) {
    null
  }
}

/** Matches {@link KeyProperties#SECURITY_LEVEL_STRONG_BOX} (value 2, API 31+). */
private const val SECURITY_LEVEL_STRONG_BOX = 2

/**
 * True when {@link KeyInfo#getSecurityLevel()} reports StrongBox (API 31+). On some devices/OS
 * levels this agrees with StrongBox placement while {@link KeyInfo#isStrongBoxBacked} still
 * reflects false via reflection — use both for tier classification.
 */
private fun keyInfoIsStrongBoxBySecurityLevel(keyInfo: KeyInfo): Boolean {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
    return false
  }
  val sl = keyInfoSecurityLevel(keyInfo) ?: return false
  return sl == SECURITY_LEVEL_STRONG_BOX
}

/**
 * StrongBox flag exists from API 28; call reflectively so Kotlin resolves against all compile SDKs.
 */
private fun keyInfoIsStrongBoxBacked(keyInfo: KeyInfo): Boolean {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
    return false
  }
  return try {
    val method = KeyInfo::class.java.getMethod("isStrongBoxBacked")
    method.invoke(keyInfo) as Boolean
  } catch (_: ReflectiveOperationException) {
    false
  }
}

/**
 * Whether new Keystore keys should set {@code setUnlockedDeviceRequired(true)}.
 *
 * Android documents critical bugs on API 31–34 when this flag is used without
 * {@code setUserAuthenticationRequired(true)}. Ezkey keeps user-auth off today (MOB-001 Track B
 * deferred), so enable the flag only on Android 15+ (API 35+). Existing keys are not rewritten
 * (MOB-012 forward-only).
 */
private fun shouldRequireUnlockedDevice(): Boolean {
  return Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
}

/**
 * React Native module providing EC P-256 key generation, retrieval, signing, and deletion.
 *
 * Uses Android Keystore and requests StrongBox on supported devices.
 *
 * @since 2025
 */
class EzkeyCryptoModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  private fun protectedAuthenticators(): Int {
    return BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
  }

  private fun canUseProtectedSigningNow(): Boolean {
    return BiometricManager.from(reactApplicationContext).canAuthenticate(
        protectedAuthenticators()
    ) == BiometricManager.BIOMETRIC_SUCCESS
  }

  private fun buildProtectedPromptInfo(title: String, subtitle: String): BiometricPrompt.PromptInfo {
    val builder =
        BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      builder.setAllowedAuthenticators(protectedAuthenticators())
    } else {
      builder.setDeviceCredentialAllowed(true)
    }

    return builder.build()
  }

  private fun getFragmentActivityOrReject(promise: Promise): FragmentActivity? {
    val activity = reactApplicationContext.currentActivity
    if (activity !is FragmentActivity) {
      promise.reject(ERROR_CODE_AUTH_UNAVAILABLE, "No compatible foreground activity for biometric prompt")
      return null
    }
    return activity
  }

  /**
   * Returns the module name exposed to React Native.
   *
   * @since 2025
   */
  override fun getName(): String = NAME

  /**
   * Exposes native build flavor constants to JS (sync).
   *
   * {@code isDebugBuild} mirrors Android {@link BuildConfig#DEBUG}. Test-only UI such as the F2a
   * enrollment seed bypass must require this flag in addition to explicit env opt-in so a release
   * APK cannot enable harness surfaces even if a local {@code .env} still has test flags set.
   *
   * @return map of constant name to value for React Native
   * @since 2025
   */
  override fun getConstants(): MutableMap<String, Any> {
    return hashMapOf("isDebugBuild" to BuildConfig.DEBUG)
  }

  /**
   * Generates an EC P-256 key pair for a specific enrollment.
   *
   * The key pair is stored in Android Keystore with StrongBox preference when available.
   * Each enrollment gets its own key pair through the platform keystore.
   *
   * @param enrollmentId The enrollment ID to generate the key pair for.
  * @param securityLevel Optional app-selected protection level. Unknown or null values fall back
  *     to the current behavior.
   * @param promise Promise resolved with true when the key pair already exists or after generation.
   * @since 2025
   */
  @ReactMethod
  fun generateEnrollmentKeyPair(enrollmentId: String, securityLevel: String?, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val alias = getEnrollmentAlias(enrollmentId)

      // Check if key pair already exists
      if (keyStore.containsAlias(alias)) {
        promise.resolve(true)
        return
      }

      // StrongBox is requested first; generateKeyPair throws when unavailable — then retry without.
      generateEnrollmentEcKeyPair(alias, requestStrongBox = true)

      promise.resolve(true)
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_KEY_GENERATION, error)
    } catch (error: IOException) {
      promise.reject(ERROR_CODE_KEY_GENERATION, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_KEY_GENERATION, error)
    } catch (error: StrongBoxUnavailableException) {
      promise.reject(ERROR_CODE_KEY_GENERATION, error)
    }
  }

  /**
   * Creates an EC P-256 enrollment key in Android Keystore, preferring StrongBox when requested.
   *
   * {@link StrongBoxUnavailableException} is thrown at key generation time (not when setting the
   * builder flag), so callers that want "StrongBox when available" must retry without StrongBox.
   *
   * @param alias Keystore alias for the enrollment key pair
   * @param requestStrongBox when true, set StrongBox-backed and fall back once if unavailable
   */
  private fun generateEnrollmentEcKeyPair(alias: String, requestStrongBox: Boolean) {
    val keyPairGenerator =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)

    val builder =
        KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")) // EC P-256
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

    if (shouldRequireUnlockedDevice()) {
      builder.setUnlockedDeviceRequired(true)
    }

    if (requestStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      builder.setIsStrongBoxBacked(true)
    }

    try {
      keyPairGenerator.initialize(builder.build())
      keyPairGenerator.generateKeyPair()
    } catch (error: StrongBoxUnavailableException) {
      if (!requestStrongBox) {
        throw error
      }
      Log.w(TAG, "StrongBox unavailable for enrollment key; falling back to regular Keystore")
      deleteAliasIfPresent(alias)
      generateEnrollmentEcKeyPair(alias, requestStrongBox = false)
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
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_PUBLIC_KEY, error)
    } catch (error: IOException) {
      promise.reject(ERROR_CODE_PUBLIC_KEY, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_PUBLIC_KEY, error)
    }
  }

  /**
   * Reports where the enrollment private key is stored, for Auth API verify ({@code NONE} /
   * {@code STANDARD} / {@code STRONG}). STRONG when {@link KeyInfo#isStrongBoxBacked} (reflective)
   * or API 31+ {@link KeyInfo#getSecurityLevel()} indicates StrongBox; else secure hardware →
   * STANDARD; otherwise NONE (e.g. emulator / software-only).
   *
   * @param enrollmentId enrollment id (same alias as {@link #generateEnrollmentKeyPair})
   * @param promise resolved with {@code "NONE"}, {@code "STANDARD"}, or {@code "STRONG"}
   */
  @ReactMethod
  fun getEnrollmentPrivateKeyStorageTier(enrollmentId: String, promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val alias = getEnrollmentAlias(enrollmentId)
      if (!keyStore.containsAlias(alias)) {
        promise.reject(ERROR_CODE_NOT_FOUND, "Key pair not found for enrollment $enrollmentId")
        return
      }
      val privateKey =
          (keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.privateKey
              ?: throw IllegalStateException("Private key not found for enrollment $enrollmentId")
      val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEY_STORE)
      val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo
      val strongBoxReflect = keyInfoIsStrongBoxBacked(keyInfo)
      val strongBoxByLevel = keyInfoIsStrongBoxBySecurityLevel(keyInfo)
      val strongBox = strongBoxReflect || strongBoxByLevel
      val secureHw = keyInfo.isInsideSecureHardware
      val tier =
          when {
            strongBox -> "STRONG"
            secureHw -> "STANDARD"
            else -> "NONE"
          }
      promise.resolve(tier)
    } catch (error: GeneralSecurityException) {
      Log.e(TAG, "getEnrollmentPrivateKeyStorageTier failed", error)
      promise.reject(ERROR_CODE_STORAGE_TIER, error)
    } catch (error: IOException) {
      Log.e(TAG, "getEnrollmentPrivateKeyStorageTier failed", error)
      promise.reject(ERROR_CODE_STORAGE_TIER, error)
    } catch (error: IllegalStateException) {
      Log.e(TAG, "getEnrollmentPrivateKeyStorageTier failed", error)
      promise.reject(ERROR_CODE_STORAGE_TIER, error)
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

      // Sign using ECDSA with SHA-256; normalize to low-S (Auth API SEC-012).
      promise.resolve(signUtf8WithLowS(privateKey, data))
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_SIGN, error)
    } catch (error: IOException) {
      promise.reject(ERROR_CODE_SIGN, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_SIGN, error)
    }
  }

  @ReactMethod
  fun canUseProtectedSigning(promise: Promise) {
    try {
      promise.resolve(canUseProtectedSigningNow())
    } catch (error: SecurityException) {
      promise.reject(ERROR_CODE_AUTH_UNAVAILABLE, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_AUTH_UNAVAILABLE, error)
    }
  }

  @ReactMethod
  fun signWithAuthentication(enrollmentId: String, data: String, promise: Promise) {
    try {
      if (!canUseProtectedSigningNow()) {
        promise.reject(
            ERROR_CODE_AUTH_UNAVAILABLE,
            "Device confirmation is not available on this phone",
        )
        return
      }

      val activity = getFragmentActivityOrReject(promise) ?: return
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      val alias = getEnrollmentAlias(enrollmentId)

      if (!keyStore.containsAlias(alias)) {
        promise.reject(ERROR_CODE_NOT_FOUND, "Key pair not found for enrollment $enrollmentId")
        return
      }

      activity.runOnUiThread {
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                  override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    try {
                      val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
                      val privateKey =
                          entry?.privateKey
                              ?: throw IllegalStateException("Private key not found for enrollment $enrollmentId")
                      // Low-S normalization required for Auth API SEC-012 (same as Demo Device).
                      promise.resolve(signUtf8WithLowS(privateKey, data))
                    } catch (error: GeneralSecurityException) {
                      promise.reject(ERROR_CODE_SIGN, error)
                    } catch (error: IOException) {
                      promise.reject(ERROR_CODE_SIGN, error)
                    } catch (error: IllegalStateException) {
                      promise.reject(ERROR_CODE_SIGN, error)
                    }
                  }

                  override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                      promise.reject(ERROR_CODE_AUTH_CANCELLED, errString.toString())
                      return
                    }

                    promise.reject(ERROR_CODE_AUTH_UNAVAILABLE, errString.toString())
                  }
                },
            )

        val promptInfo = buildProtectedPromptInfo(BIOMETRIC_PROMPT_TITLE, BIOMETRIC_PROMPT_SUBTITLE)

        prompt.authenticate(promptInfo)
      }
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_SIGN, error)
    } catch (error: IOException) {
      promise.reject(ERROR_CODE_SIGN, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_SIGN, error)
    }
  }

  @ReactMethod
  fun authenticateSecurityPreferenceDowngrade(promise: Promise) {
    try {
      if (!canUseProtectedSigningNow()) {
        promise.reject(
            ERROR_CODE_AUTH_UNAVAILABLE,
            "Device confirmation is not available on this phone",
        )
        return
      }

      val activity = getFragmentActivityOrReject(promise) ?: return
      activity.runOnUiThread {
        val prompt =
            BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                  override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promise.resolve(true)
                  }

                  override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                      promise.reject(ERROR_CODE_AUTH_CANCELLED, errString.toString())
                      return
                    }

                    promise.reject(ERROR_CODE_AUTH_UNAVAILABLE, errString.toString())
                  }
                },
            )

        val promptInfo =
            buildProtectedPromptInfo(
                SECURITY_DOWNGRADE_PROMPT_TITLE,
                SECURITY_DOWNGRADE_PROMPT_SUBTITLE,
            )

        prompt.authenticate(promptInfo)
      }
    } catch (error: SecurityException) {
      promise.reject(ERROR_CODE_AUTH_UNAVAILABLE, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_AUTH_UNAVAILABLE, error)
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
    } catch (error: GeneralSecurityException) {
      Log.e(TAG, "verify threw", error)
      promise.reject(ERROR_CODE_VERIFY, error)
    } catch (error: IllegalArgumentException) {
      Log.e(TAG, "verify threw", error)
      promise.reject(ERROR_CODE_VERIFY, error)
    } catch (error: IllegalStateException) {
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
    } catch (error: SecurityException) {
      promise.reject(ERROR_CODE_BUILD_TIMESTAMP, error)
    } catch (error: IllegalStateException) {
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
    } catch (error: SecurityException) {
      promise.reject(ERROR_CODE_PROOF_TOKEN, error)
    } catch (error: IllegalArgumentException) {
      promise.reject(ERROR_CODE_PROOF_TOKEN, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_PROOF_TOKEN, error)
    }
  }

  /**
   * Seals plaintext with the app-level AES/GCM key stored in Android Keystore. The logical key is
   * bound as AAD to prevent ciphertext replay under another storage identifier.
   */
  @ReactMethod
  fun sealSecret(logicalKey: String, plaintext: String, promise: Promise) {
    try {
      val envelope = SealedSecretEnvelope.seal(getOrCreateAppSealKey(), logicalKey, plaintext)
      promise.resolve(envelope.toJson())
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_SEAL_SECRET, error)
    } catch (error: IllegalArgumentException) {
      promise.reject(ERROR_CODE_SEAL_SECRET, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_SEAL_SECRET, error)
    }
  }

  /**
   * Unseals a JSON envelope previously produced by {@link #sealSecret(String, String, Promise)}.
   */
  @ReactMethod
  fun unsealSecret(logicalKey: String, sealedPayload: String, promise: Promise) {
    try {
      val envelope = SealedSecretEnvelope.fromJson(sealedPayload)
      promise.resolve(SealedSecretEnvelope.unseal(getOrCreateAppSealKey(), logicalKey, envelope))
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_UNSEAL_SECRET, error)
    } catch (error: IllegalArgumentException) {
      promise.reject(ERROR_CODE_UNSEAL_SECRET, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_UNSEAL_SECRET, error)
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
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_DELETE, error)
    } catch (error: IOException) {
      promise.reject(ERROR_CODE_DELETE, error)
    } catch (error: IllegalStateException) {
      promise.reject(ERROR_CODE_DELETE, error)
    }
  }

  /**
   * Deletes the app-level seal key so a Danger Zone clear-all is a true local reset (MOB-015).
   *
   * A dead or corrupted seal key otherwise survives clear-all and can poison re-enrollment,
   * because every sealed secret on this device is wrapped by this single key.
   *
   * @param promise Promise resolved with true when the alias was present and deleted, false when absent.
   * @since 2026
   */
  @ReactMethod
  fun deleteAppSealKey(promise: Promise) {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
      if (keyStore.containsAlias(APP_SEAL_KEY_ALIAS)) {
        keyStore.deleteEntry(APP_SEAL_KEY_ALIAS)
        promise.resolve(true)
      } else {
        promise.resolve(false)
      }
    } catch (error: GeneralSecurityException) {
      promise.reject(ERROR_CODE_DELETE, error)
    } catch (error: IOException) {
      promise.reject(ERROR_CODE_DELETE, error)
    } catch (error: IllegalStateException) {
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

  private fun getOrCreateAppSealKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    if (keyStore.containsAlias(APP_SEAL_KEY_ALIAS)) {
      val entry = keyStore.getEntry(APP_SEAL_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
      return entry?.secretKey
          ?: throw IllegalStateException("App seal key entry missing for alias $APP_SEAL_KEY_ALIAS")
    }

    return createAppSealKey(requestStrongBox = true)
  }

  /**
   * Creates the app-level AES/GCM seal key, preferring StrongBox when requested.
   *
   * @param requestStrongBox when true, set StrongBox-backed and fall back once if unavailable
   * @return newly generated Keystore secret key
   */
  private fun createAppSealKey(requestStrongBox: Boolean): SecretKey {
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
    val builder =
        KeyGenParameterSpec.Builder(
                APP_SEAL_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)

    if (shouldRequireUnlockedDevice()) {
      builder.setUnlockedDeviceRequired(true)
    }

    if (requestStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      builder.setIsStrongBoxBacked(true)
    }

    return try {
      keyGenerator.init(builder.build())
      keyGenerator.generateKey()
    } catch (error: StrongBoxUnavailableException) {
      if (!requestStrongBox) {
        throw error
      }
      Log.w(TAG, "StrongBox unavailable for app seal key; falling back to regular Keystore")
      deleteAliasIfPresent(APP_SEAL_KEY_ALIAS)
      createAppSealKey(requestStrongBox = false)
    }
  }

  private fun deleteAliasIfPresent(alias: String) {
    val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    if (keyStore.containsAlias(alias)) {
      keyStore.deleteEntry(alias)
    }
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

  /**
   * Signs UTF-8 data with ECDSA-SHA256 and returns standard Base64 over DER with {@code s}
   * normalized to low-S (Auth API SEC-012 / Demo Device parity).
   *
   * @param privateKey EC P-256 private key from Android Keystore
   * @param data UTF-8 string to sign
   * @return Base64 NO_WRAP DER signature
   */
  private fun signUtf8WithLowS(privateKey: java.security.PrivateKey, data: String): String {
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(privateKey)
    signature.update(data.toByteArray(StandardCharsets.UTF_8))
    val der = signature.sign()
    val lowSDer = EcdsaLowS.normalizeDerSignature(der, EcdsaLowS.curveOrder(privateKey))
    return Base64.encodeToString(lowSDer, Base64.NO_WRAP)
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
    private const val ERROR_CODE_STORAGE_TIER = "EZK_STORAGE_TIER_ERROR"
    private const val ERROR_CODE_SEAL_SECRET = "EZK_SEAL_SECRET_ERROR"
    private const val ERROR_CODE_UNSEAL_SECRET = "EZK_UNSEAL_SECRET_ERROR"
    private const val ERROR_CODE_AUTH_CANCELLED = "EZK_AUTH_CANCELLED"
    private const val ERROR_CODE_AUTH_UNAVAILABLE = "EZK_AUTH_UNAVAILABLE"
    private const val APP_SEAL_KEY_ALIAS = "ezkey_app_seal_v1"
    private const val SECURITY_LEVEL_CONFIRM_BEFORE_APPROVALS = "confirm-before-approvals"
    private const val BIOMETRIC_PROMPT_TITLE = "Confirm request"
    private const val BIOMETRIC_PROMPT_SUBTITLE =
        "Confirm with biometrics or your device PIN, pattern, or password"
    private const val SECURITY_DOWNGRADE_PROMPT_TITLE = "Confirm security change"
    private const val SECURITY_DOWNGRADE_PROMPT_SUBTITLE =
        "Use your device to turn off approval confirmation"
  }
}
