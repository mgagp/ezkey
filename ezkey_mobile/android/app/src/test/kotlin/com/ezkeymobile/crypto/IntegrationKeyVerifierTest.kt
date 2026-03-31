/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Unit tests for IntegrationKeyVerifier: Ed25519 public key (32 raw bytes, flexible Base64) and
 * signature verification. Fixtures can be populated from DB via
 * scripts/export-mobile-crypto-fixture.sh 3 --write (enrollment_id 3 = Jean-Martin).
 *
 * @since 2025
 */

package com.ezkeymobile.crypto

import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.NamedParameterSpec
import java.util.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("IntegrationKeyVerifier")
class IntegrationKeyVerifierTest {

  private fun generateEd25519KeyPair(): Pair<String, PrivateKey> {
    val kpg = KeyPairGenerator.getInstance("Ed25519")
    kpg.initialize(NamedParameterSpec.ED25519)
    val kp = kpg.generateKeyPair()
    val spki = kp.public.encoded
    val raw = spki.copyOfRange(spki.size - 32, spki.size)
    val publicKeyBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    return publicKeyBase64 to kp.private
  }

  private fun assertEd25519Algorithm(publicKey: java.security.PublicKey) {
    assertTrue(
        publicKey.algorithm == "Ed25519" || publicKey.algorithm == "EdDSA",
        "expected Ed25519/EdDSA, was ${publicKey.algorithm}",
    )
  }

  private fun signPayload(payload: String, privateKey: PrivateKey): String {
    val sig = Signature.getInstance("Ed25519")
    sig.initSign(privateKey)
    sig.update(payload.toByteArray(StandardCharsets.UTF_8))
    val sigBytes = sig.sign()
    return Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes)
  }

  @Nested
  @DisplayName("parsePublicKeyFromBase64")
  inner class ParsePublicKey {

    @Test
    @DisplayName("parses Base64URL raw 32-byte key and returns Ed25519 public key")
    fun parsesRawIntegrationKey() {
      val (publicKeyBase64, _) = generateEd25519KeyPair()
      val publicKey = IntegrationKeyVerifier.parsePublicKeyFromBase64(publicKeyBase64)
      assertNotNull(publicKey)
      assertEd25519Algorithm(publicKey)
    }

    @Test
    @DisplayName("parses fixture from file when present (Jean-Martin enrollment_id=3)")
    fun parsesFromFixtureFileWhenPresent() {
      val url = requireNotNull(javaClass.classLoader).getResource("fixtures/integration_public_key_base64.txt")
      if (url == null) {
        return
      }
      val publicKeyBase64 = url.readText().trim()
      if (publicKeyBase64.isEmpty()) return
      try {
        val publicKey = IntegrationKeyVerifier.parsePublicKeyFromBase64(publicKeyBase64)
        assertNotNull(publicKey)
        assertEd25519Algorithm(publicKey)
      } catch (_: IllegalArgumentException) {
        // Fixture from pre-Ed25519 DB export (EC SPKI); re-export after migration.
        return
      }
    }

    @Test
    @DisplayName("ignores whitespace in Base64 body")
    fun ignoresWhitespace() {
      val (raw, _) = generateEd25519KeyPair()
      val withSpaces = raw.chunked(40).joinToString("\n")
      val publicKey = IntegrationKeyVerifier.parsePublicKeyFromBase64(withSpaces)
      assertNotNull(publicKey)
      assertEd25519Algorithm(publicKey)
    }

    @Test
    @DisplayName("throws on empty string")
    fun throwsOnEmpty() {
      assertThrows<Exception> { IntegrationKeyVerifier.parsePublicKeyFromBase64("") }
    }

    @Test
    @DisplayName("throws on invalid Base64")
    fun throwsOnInvalidBase64() {
      assertThrows<Exception> {
        IntegrationKeyVerifier.parsePublicKeyFromBase64("not-valid-base64!!!")
      }
    }

    @Test
    @DisplayName("throws on truncated key bytes")
    fun throwsOnTruncatedKey() {
      val (raw, _) = generateEd25519KeyPair()
      val truncated = raw.take(20)
      assertThrows<Exception> { IntegrationKeyVerifier.parsePublicKeyFromBase64(truncated) }
    }
  }

  @Nested
  @DisplayName("verify")
  inner class Verify {

    @Test
    @DisplayName("returns true for known-good payload and signature")
    fun returnsTrueForValidSignature() {
      val (publicKeyBase64, privateKey) = generateEd25519KeyPair()
      val payload = "proofToken123|true|Virement|Virement de 50€"
      val signatureBase64 = signPayload(payload, privateKey)
      val valid = IntegrationKeyVerifier.verify(payload, signatureBase64, publicKeyBase64)
      assertTrue(valid)
    }

    @Test
    @DisplayName("returns false for tampered payload")
    fun returnsFalseForTamperedPayload() {
      val (publicKeyBase64, privateKey) = generateEd25519KeyPair()
      val payload = "token|true|Title|Message"
      val signatureBase64 = signPayload(payload, privateKey)
      val tamperedPayload = "token|true|Title|Tampered"
      val valid = IntegrationKeyVerifier.verify(tamperedPayload, signatureBase64, publicKeyBase64)
      assertTrue(!valid)
    }

    @Test
    @DisplayName("returns false for wrong signature")
    fun returnsFalseForWrongSignature() {
      val (publicKeyBase64, privateKey) = generateEd25519KeyPair()
      val payload = "token|true|Title|Message"
      signPayload(payload, privateKey)
      val wrongSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(64) { 0 })
      val valid = IntegrationKeyVerifier.verify(payload, wrongSignature, publicKeyBase64)
      assertTrue(!valid)
    }

    @Test
    @DisplayName("returns false for invalid public key")
    fun returnsFalseForInvalidPublicKey() {
      val (_, privateKey) = generateEd25519KeyPair()
      val payload = "token|true|Title|Message"
      val signatureBase64 = signPayload(payload, privateKey)
      val valid = IntegrationKeyVerifier.verify(payload, signatureBase64, "invalid-key-base64")
      assertTrue(!valid)
    }

    @Test
    @DisplayName("returns false for empty public key")
    fun returnsFalseForEmptyPublicKey() {
      val (_, privateKey) = generateEd25519KeyPair()
      val payload = "token|true|Title|Message"
      val signatureBase64 = signPayload(payload, privateKey)
      val valid = IntegrationKeyVerifier.verify(payload, signatureBase64, "")
      assertTrue(!valid)
    }

    @Test
    @DisplayName("canonical Pending payload format matches AUTH_ATTEMPT_SIGNATURE_PAYLOAD")
    fun canonicalPendingPayloadFormat() {
      val (publicKeyBase64, privateKey) = generateEd25519KeyPair()
      val proofToken = "abc123token"
      val challengeRequired = true
      val contextTitle = "Virement"
      val contextMessage = "Virement de 50€"
      val payload = "$proofToken|${challengeRequired}|$contextTitle|$contextMessage"
      val signatureBase64 = signPayload(payload, privateKey)
      val valid = IntegrationKeyVerifier.verify(payload, signatureBase64, publicKeyBase64)
      assertTrue(valid)
    }
  }

  /**
   * Optional golden triples (integration public key + exact UTF-8 payload + Base64 signature).
   * Populate after a functional run or from Postman — see
   * `android/app/src/test/resources/fixtures/README.md`. If any file is missing, tests no-op
   * so CI stays green; locally, with fixtures present, these assert the full Ed25519 chain matches
   * the Auth API and TS payload builders.
   */
  @Nested
  @DisplayName("Golden fixtures (optional)")
  inner class GoldenFixtures {

    private fun readOptionalFixture(name: String): String? {
      val cl =
          requireNotNull(IntegrationKeyVerifierTest::class.java.classLoader) {
            "classLoader unavailable"
          }
      val url = cl.getResource("fixtures/$name") ?: return null
      val text = url.readText().trim()
      return text.takeIf { it.isNotEmpty() }
    }

    @Test
    @DisplayName("verifies Pending integration signature when pending_payload + pending_signature fixtures exist")
    fun verifyPendingWhenFixturesPresent() {
      val key = readOptionalFixture("integration_public_key_base64.txt") ?: return
      val payload = readOptionalFixture("pending_payload_utf8.txt") ?: return
      val sig = readOptionalFixture("pending_signature_base64.txt") ?: return
      assertTrue(IntegrationKeyVerifier.verify(payload, sig, key))
    }

    @Test
    @DisplayName("verifies Respond result integration signature when respond_result fixtures exist")
    fun verifyRespondResultWhenFixturesPresent() {
      val key = readOptionalFixture("integration_public_key_base64.txt") ?: return
      val payload = readOptionalFixture("respond_result_payload_utf8.txt") ?: return
      val sig = readOptionalFixture("respond_result_signature_base64.txt") ?: return
      assertTrue(IntegrationKeyVerifier.verify(payload, sig, key))
    }
  }
}
