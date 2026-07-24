/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * File: EzkeyCryptoModuleInstrumentedTest.kt
 * Description: Android instrumentation coverage for EzkeyCryptoModule Keystore lifecycle (MOB-006).
 * Security Context: Emulator-safe assertions only; StrongBox STRONG tier is manual/device evidence.
 * @since 2026
 */

package org.ezkey.mobile.crypto

import android.os.Build
import android.security.keystore.KeyInfo
import android.util.Base64
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.react.bridge.BridgeReactContext
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator instrumentation for [EzkeyCryptoModule] Keystore paths.
 *
 * Does **not** assert StrongBox (`STRONG`). Emulators typically report `NONE` or `STANDARD`.
 * Physical StrongBox evidence: `docs/MOBILE_STRONGBOX_MANUAL_CHECKLIST.md`.
 */
@RunWith(AndroidJUnit4::class)
class EzkeyCryptoModuleInstrumentedTest {

  private lateinit var module: EzkeyCryptoModule
  private val enrollmentIds = mutableListOf<String>()

  @Before
  fun setUp() {
    val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    // BridgeReactContext is the concrete RN test host; Keystore methods do not need Catalyst.
    val reactContext = BridgeReactContext(appContext)
    module = EzkeyCryptoModule(reactContext)
  }

  @After
  fun tearDown() {
    for (enrollmentId in enrollmentIds) {
      val promise = CapturingPromise()
      module.deleteKeyPair(enrollmentId, promise)
      promise.await()
    }
    enrollmentIds.clear()
  }

  @Test
  fun generateGetPublicKeyAndSign_roundTrip() {
    val enrollmentId = newEnrollmentId()
    val generatePromise = CapturingPromise()
    module.generateEnrollmentKeyPair(enrollmentId, null, generatePromise)
    assertEquals(true, generatePromise.requireResolved())

    val publicKeyPromise = CapturingPromise()
    module.getPublicKey(enrollmentId, publicKeyPromise)
    val publicKeyBase64 = publicKeyPromise.requireResolved() as String
    assertTrue("public key Base64 should be non-empty", publicKeyBase64.isNotBlank())

    val payload = "ezkey-mob-006-sign-round-trip"
    val signPromise = CapturingPromise()
    module.sign(enrollmentId, payload, signPromise)
    val signatureBase64 = signPromise.requireResolved() as String
    assertTrue("signature Base64 should be non-empty", signatureBase64.isNotBlank())

    val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
    val publicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyBytes))
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(publicKey)
    verifier.update(payload.toByteArray(StandardCharsets.UTF_8))
    assertTrue(
        "ECDSA signature must verify with the enrollment public key",
        verifier.verify(Base64.decode(signatureBase64, Base64.NO_WRAP)),
    )
  }

  @Test
  fun deleteKeyPair_removesEnrollmentAlias() {
    val enrollmentId = newEnrollmentId()
    val generatePromise = CapturingPromise()
    module.generateEnrollmentKeyPair(enrollmentId, null, generatePromise)
    assertEquals(true, generatePromise.requireResolved())

    val alias = "ezkey_enrollment_$enrollmentId"
    assertTrue("alias should exist after generate", keyStoreContains(alias))

    val deletePromise = CapturingPromise()
    module.deleteKeyPair(enrollmentId, deletePromise)
    assertEquals(true, deletePromise.requireResolved())
    enrollmentIds.remove(enrollmentId)

    assertFalse("alias should be removed after deleteKeyPair", keyStoreContains(alias))

    val missingPromise = CapturingPromise()
    module.getPublicKey(enrollmentId, missingPromise)
    assertEquals("EZK_KEY_NOT_FOUND", missingPromise.requireRejectedCode())
  }

  @Test
  fun sealSecretAndUnsealSecret_roundTripViaModule() {
    val logicalKey = "mob-006-seal-${UUID.randomUUID()}"
    val plaintext = "enrollment-proof-token-sample"

    val sealPromise = CapturingPromise()
    module.sealSecret(logicalKey, plaintext, sealPromise)
    val sealedJson = sealPromise.requireResolved() as String
    assertTrue("sealed envelope JSON should be non-empty", sealedJson.isNotBlank())
    assertTrue("envelope should look like JSON", sealedJson.startsWith("{"))

    val unsealPromise = CapturingPromise()
    module.unsealSecret(logicalKey, sealedJson, unsealPromise)
    assertEquals(plaintext, unsealPromise.requireResolved())
  }

  @Test
  fun getEnrollmentPrivateKeyStorageTier_returnsKnownTier() {
    val enrollmentId = newEnrollmentId()
    val generatePromise = CapturingPromise()
    module.generateEnrollmentKeyPair(enrollmentId, null, generatePromise)
    assertEquals(true, generatePromise.requireResolved())

    val tierPromise = CapturingPromise()
    module.getEnrollmentPrivateKeyStorageTier(enrollmentId, tierPromise)
    val tier = tierPromise.requireResolved() as String

    // Evidence for StrongBox checklist (physical device). Emulator: usually NONE/STANDARD.
    Log.i(TAG, "MOB-006 storage tier evidence: $tier")

    // Emulator: usually NONE (sometimes STANDARD). STRONG is physical StrongBox only — not CI.
    assertTrue(
        "tier must be one of NONE/STANDARD/STRONG, got: $tier",
        tier in setOf("NONE", "STANDARD", "STRONG"),
    )
  }

  /**
   * MOB-012: with user-auth off, unlocked-device-required is only set on Android 15+ (API 35+).
   * KeyInfo exposes the flag from API 31.
   */
  @Test
  fun enrollmentKey_unlockedDeviceRequired_matchesAndroid15Gate() {
    Assume.assumeTrue(
        "KeyInfo.isUnlockedDeviceRequired requires API 31+",
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )

    val enrollmentId = newEnrollmentId()
    val generatePromise = CapturingPromise()
    module.generateEnrollmentKeyPair(enrollmentId, null, generatePromise)
    assertEquals(true, generatePromise.requireResolved())

    val alias = "ezkey_enrollment_$enrollmentId"
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val privateKey = keyStore.getKey(alias, null) as PrivateKey
    val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
    val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
    val expected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    assertEquals(
        "unlockedDeviceRequired should follow API 35+ gate (MOB-012)",
        expected,
        keyInfo.isUnlockedDeviceRequired,
    )
  }

  companion object {
    private const val TAG = "EzkeyCryptoTest"
  }

  private fun newEnrollmentId(): String {
    val id = "mob006-${UUID.randomUUID()}"
    enrollmentIds.add(id)
    return id
  }

  private fun keyStoreContains(alias: String): Boolean {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    return keyStore.containsAlias(alias)
  }
}
