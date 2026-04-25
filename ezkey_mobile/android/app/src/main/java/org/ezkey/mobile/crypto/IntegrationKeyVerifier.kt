/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Integration public key parsing and Ed25519 signature verification (JDK / Conscrypt).
 * Matches {@code SignatureService.verifyIntegrationSignature} on the Auth API: raw 32-byte public
 * key and raw 64-byte signature, Base64URL or standard Base64 (see server
 * {@code decodeFlexibleBase64ToBytes}).
 *
 * @since 2025
 */

package org.ezkey.mobile.crypto

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.conscrypt.Conscrypt

/**
 * Parses integration public keys (raw Ed25519, 32 bytes after Base64 decode) and verifies Ed25519
 * signatures over the exact UTF-8 payload.
 */
object IntegrationKeyVerifier {

  private val SPKI_PREFIX_44 =
      byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

  init {
    try {
      val p = Conscrypt.newProvider()
      if (Security.getProvider(p.name) == null) {
        Security.insertProviderAt(p, 1)
      }
    } catch (_: Throwable) {
      // Platform may already expose Ed25519; Conscrypt is best-effort for older API levels.
    }
  }

  /**
   * Strips PEM-style wrappers and whitespace from a Base64-ish string (same idea as server
   * [stripPemAndWhitespace]).
   */
  fun stripPemAndWhitespace(input: String): String {
    if (input.contains("-----BEGIN")) {
      return input.lineSequence()
          .filter { line -> line.isNotBlank() && !line.startsWith("-----") }
          .joinToString("") { it.trim() }
    }
    return input.replace(Regex("\\s"), "")
  }

  /**
   * Decodes Base64URL (no padding) or standard Base64 to bytes; returns null on failure (server
   * [decodeFlexibleBase64ToBytes]).
   */
  fun decodeFlexibleBase64ToBytes(value: String?): ByteArray? {
    if (value.isNullOrBlank()) {
      return null
    }
    val t = stripPemAndWhitespace(value)
    return try {
      Base64.getUrlDecoder().decode(t)
    } catch (_: IllegalArgumentException) {
      try {
        Base64.getDecoder().decode(t)
      } catch (_: IllegalArgumentException) {
        null
      }
    }
  }

  private fun rawPublicKeyToSpki(raw32: ByteArray): ByteArray {
    require(raw32.size == 32) { "Ed25519 raw public key must be 32 bytes" }
    return SPKI_PREFIX_44 + raw32
  }

  /**
   * Builds a JCA public key from canonical integration material (32 raw bytes after flexible
   * Base64 decode).
   */
  fun parseEd25519PublicKey(publicKeyBase64: String): PublicKey {
    val raw =
        decodeFlexibleBase64ToBytes(publicKeyBase64)
            ?: throw IllegalArgumentException("Invalid Base64 for integration public key")
    require(raw.size == 32) { "Ed25519 integration public key must decode to 32 bytes" }
    val spki = rawPublicKeyToSpki(raw)
    return KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
  }

  /**
   * Same as [parseEd25519PublicKey] but named for tests that previously used SPKI EC parsing.
   */
  fun parsePublicKeyFromBase64(publicKeyBase64: String): PublicKey {
    return parseEd25519PublicKey(publicKeyBase64)
  }

  /**
   * Verifies an Ed25519 signature over [data]. [signatureBase64] and [publicKeyBase64] use the same
   * flexible Base64 rules as the Auth API (Base64URL without padding preferred for wire values).
   *
   * @param data Exact UTF-8 payload that was signed.
   * @param signatureBase64 Raw 64-byte signature after decode.
   * @param publicKeyBase64 Raw 32-byte public key after decode.
   */
  fun verify(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
    return try {
      val pubRaw = decodeFlexibleBase64ToBytes(publicKeyBase64) ?: return false
      val sigRaw = decodeFlexibleBase64ToBytes(signatureBase64) ?: return false
      if (pubRaw.size != 32 || sigRaw.size != 64) {
        return false
      }
      val spki = rawPublicKeyToSpki(pubRaw)
      val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
      val signature = newEd25519Signature()
      signature.initVerify(publicKey)
      signature.update(data.toByteArray(StandardCharsets.UTF_8))
      signature.verify(sigRaw)
    } catch (_: Exception) {
      false
    }
  }

  private fun newEd25519Signature(): Signature {
    return try {
      Signature.getInstance("Ed25519")
    } catch (_: Exception) {
      Signature.getInstance("Ed25519", Conscrypt.newProvider())
    }
  }
}
