/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Unit tests for IntegrationKeyVerifier: parse integration public key (SubjectPublicKeyInfo)
 * and verify ECDSA-SHA256 signatures. Fixtures can be populated from DB via
 * scripts/export-mobile-crypto-fixture.sh 3 --write (enrollment_id 3 = Jean-Martin).
 *
 * @since 2025
 */

package com.ezkeymobile.crypto

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

@DisplayName("IntegrationKeyVerifier")
class IntegrationKeyVerifierTest {

  private fun generateECP256KeyPair(): Pair<String, java.security.PrivateKey> {
    val keyGen = KeyPairGenerator.getInstance("EC")
    keyGen.initialize(ECGenParameterSpec("secp256r1"))
    val keyPair = keyGen.generateKeyPair()
    val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)
    return publicKeyBase64 to keyPair.private
  }

  private fun signPayload(payload: String, privateKey: java.security.PrivateKey): String {
    val sig = Signature.getInstance("SHA256withECDSA")
    sig.initSign(privateKey)
    sig.update(payload.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(sig.sign())
  }

  @Nested
  @DisplayName("parsePublicKeyFromBase64")
  inner class ParsePublicKey {

    @Test
    @DisplayName("parses Base64 SubjectPublicKeyInfo and returns EC public key")
    fun parsesSubjectPublicKeyInfo() {
      val (publicKeyBase64, _priv) = generateECP256KeyPair()
      val publicKey = IntegrationKeyVerifier.parsePublicKeyFromBase64(publicKeyBase64)
      assertNotNull(publicKey)
      assertEquals("EC", publicKey.algorithm)
    }

    @Test
    @DisplayName("parses fixture from file when present (Jean-Martin enrollment_id=3)")
    fun parsesFromFixtureFileWhenPresent() {
      val url = requireNotNull(javaClass.classLoader).getResource("fixtures/integration_public_key_base64.txt")
      if (url == null) {
        // Fixture not populated; run: ./scripts/export-mobile-crypto-fixture.sh 3 --write
        return
      }
      val publicKeyBase64 = url.readText().trim()
      if (publicKeyBase64.isEmpty()) return
      val publicKey = IntegrationKeyVerifier.parsePublicKeyFromBase64(publicKeyBase64)
      assertNotNull(publicKey)
      assertEquals("EC", publicKey.algorithm)
    }

    @Test
    @DisplayName("strips PEM wrapper and parses certificate")
    fun stripsPemWrapperAndParses() {
      val (raw, _priv) = generateECP256KeyPair()
      val pem = "-----BEGIN PUBLIC KEY-----\n$raw\n-----END PUBLIC KEY-----"
      val publicKey = IntegrationKeyVerifier.parsePublicKeyFromBase64(pem)
      assertNotNull(publicKey)
      assertEquals("EC", publicKey.algorithm)
    }

    @Test
    @DisplayName("ignores whitespace in Base64 body")
    fun ignoresWhitespace() {
      val (raw, _priv) = generateECP256KeyPair()
      val withSpaces = raw.chunked(40).joinToString("\n")
      val publicKey = IntegrationKeyVerifier.parsePublicKeyFromBase64(withSpaces)
      assertNotNull(publicKey)
      assertEquals("EC", publicKey.algorithm)
    }

    @Test
    @DisplayName("throws on empty string")
    fun throwsOnEmpty() {
      assertThrows<Exception> {
        IntegrationKeyVerifier.parsePublicKeyFromBase64("")
      }
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
      val (raw, _priv) = generateECP256KeyPair()
      val truncated = raw.take(20)
      assertThrows<Exception> {
        IntegrationKeyVerifier.parsePublicKeyFromBase64(truncated)
      }
    }
  }

  @Nested
  @DisplayName("verify")
  inner class Verify {

    @Test
    @DisplayName("returns true for known-good payload and signature")
    fun returnsTrueForValidSignature() {
      val (publicKeyBase64, privateKey) = generateECP256KeyPair()
      val payload = "proofToken123|true|Virement|Virement de 50€"
      val signatureBase64 = signPayload(payload, privateKey)
      val valid = IntegrationKeyVerifier.verify(payload, signatureBase64, publicKeyBase64)
      assertTrue(valid)
    }

    @Test
    @DisplayName("returns false for tampered payload")
    fun returnsFalseForTamperedPayload() {
      val (publicKeyBase64, privateKey) = generateECP256KeyPair()
      val payload = "token|true|Title|Message"
      val signatureBase64 = signPayload(payload, privateKey)
      val tamperedPayload = "token|true|Title|Tampered"
      val valid = IntegrationKeyVerifier.verify(tamperedPayload, signatureBase64, publicKeyBase64)
      assertTrue(!valid)
    }

    @Test
    @DisplayName("returns false for wrong signature")
    fun returnsFalseForWrongSignature() {
      val (publicKeyBase64, privateKey) = generateECP256KeyPair()
      val payload = "token|true|Title|Message"
      signPayload(payload, privateKey) // ensure we have a valid payload format
      val wrongSignature = Base64.getEncoder().encodeToString(ByteArray(64) { 0 })
      val valid = IntegrationKeyVerifier.verify(payload, wrongSignature, publicKeyBase64)
      assertTrue(!valid)
    }

    @Test
    @DisplayName("returns false for invalid public key")
    fun returnsFalseForInvalidPublicKey() {
      val (_pub, privateKey) = generateECP256KeyPair()
      val payload = "token|true|Title|Message"
      val signatureBase64 = signPayload(payload, privateKey)
      val valid = IntegrationKeyVerifier.verify(payload, signatureBase64, "invalid-key-base64")
      assertTrue(!valid)
    }

    @Test
    @DisplayName("returns false for empty public key")
    fun returnsFalseForEmptyPublicKey() {
      val (publicKeyBase64, privateKey) = generateECP256KeyPair()
      val payload = "token|true|Title|Message"
      val signatureBase64 = signPayload(payload, privateKey)
      val valid = IntegrationKeyVerifier.verify(payload, signatureBase64, "")
      assertTrue(!valid)
    }

    @Test
    @DisplayName("canonical Pending payload format matches AUTH_ATTEMPT_SIGNATURE_PAYLOAD")
    fun canonicalPendingPayloadFormat() {
      val (publicKeyBase64, privateKey) = generateECP256KeyPair()
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
}
