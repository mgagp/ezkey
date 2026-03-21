/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Pure Kotlin/JVM integration public key parsing and ECDSA signature verification.
 * Uses only java.util.Base64 and java.security.* so it is testable on the JVM without Android.
 * Used by EzkeyCryptoModule.verify() for Pending response integration signature verification.
 *
 * @since 2025
 */

package com.ezkeymobile.crypto

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Parses integration public key (Base64 SubjectPublicKeyInfo or X.509 certificate) and verifies
 * ECDSA-SHA256 signatures. Behaviour matches EzkeyCryptoModule's previous private logic so that
 * unit tests can validate parsing and verification without Android runtime.
 */
object IntegrationKeyVerifier {

  private val BASE64 = Base64.getDecoder()

  /**
   * Decodes the integration public key input into raw bytes. Handles raw Base64 and PEM-style
   * strings (e.g. "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----"). If the string
   * contains PEM headers, only the Base64 body between headers is decoded.
   *
   * @param input Base64-encoded key/certificate, or PEM string including headers.
   * @return DER-encoded bytes (SubjectPublicKeyInfo or X.509 certificate).
   */
  fun decodePublicKeyInput(input: String): ByteArray {
    val base64Body = if (input.contains("-----BEGIN")) {
      val lines = input.lines().filter { line ->
        line.isNotBlank() && !line.startsWith("-----")
      }
      lines.joinToString("").replace(Regex("\\s"), "")
    } else {
      input.replace(Regex("\\s"), "")
    }
    return BASE64.decode(base64Body)
  }

  /**
   * Parses a public key from Base64 string. Accepts SubjectPublicKeyInfo first, then X.509
   * certificate; if the bytes look like ASCII (e.g. double-Base64), decodes again and retries.
   *
   * @param publicKeyBase64 Base64-encoded SubjectPublicKeyInfo or X.509 certificate.
   * @return The EC public key.
   * @throws Exception if the input is neither valid key nor certificate.
   */
  fun parsePublicKeyFromBase64(publicKeyBase64: String): PublicKey {
    val keyBytes = decodePublicKeyInput(publicKeyBase64)
    return parsePublicKey(keyBytes)
  }

  /**
   * Parses a public key from bytes that may be either X.509 SubjectPublicKeyInfo or a full X.509
   * certificate. Tries direct parse first; if the bytes look like ASCII (e.g. double-Base64),
   * decodes again and retries.
   *
   * @param keyBytes DER-encoded SubjectPublicKeyInfo or X.509 certificate bytes.
   * @return The EC public key.
   * @throws Exception if the bytes are neither valid key nor certificate.
   */
  fun parsePublicKey(keyBytes: ByteArray): PublicKey {
    return try {
      parsePublicKeyOnce(keyBytes)
    } catch (e: Exception) {
      if (keyBytes.size > 100 && keyBytes.all { it in 32..126 }) {
        try {
          val inner = BASE64.decode(String(keyBytes, StandardCharsets.UTF_8))
          parsePublicKeyOnce(inner)
        } catch (e2: Exception) {
          throw e
        }
      } else {
        throw e
      }
    }
  }

  private fun parsePublicKeyOnce(keyBytes: ByteArray): PublicKey {
    return try {
      val keySpec = X509EncodedKeySpec(keyBytes)
      KeyFactory.getInstance("EC").generatePublic(keySpec)
    } catch (jcaError: Exception) {
      try {
        val certFactory = CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(ByteArrayInputStream(keyBytes))
        cert.publicKey
      } catch (certError: Exception) {
        // Fallback: legacy EC keys encoded with explicit parameters that the default JCA provider
        // may reject; BouncyCastle accepts named-curve SPKI from the backend in normal operation.
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
          Security.addProvider(BouncyCastleProvider())
        }
        val keySpec = X509EncodedKeySpec(keyBytes)
        KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME).generatePublic(keySpec)
      }
    }
  }

  /**
   * Verifies an ECDSA-SHA256 signature over the given data using the provided public key.
   *
   * @param data The exact payload that was signed (UTF-8).
   * @param signatureBase64 Base64-encoded ECDSA signature (ASN.1 DER).
   * @param publicKeyBase64 Base64-encoded X.509 public key (SubjectPublicKeyInfo or certificate).
   * @return true if the signature is valid, false otherwise (e.g. invalid key, wrong signature).
   */
  fun verify(data: String, signatureBase64: String, publicKeyBase64: String): Boolean {
    return try {
      val keyBytes = decodePublicKeyInput(publicKeyBase64)
      val publicKey = parsePublicKey(keyBytes)
      val signature = Signature.getInstance("SHA256withECDSA")
      signature.initVerify(publicKey)
      signature.update(data.toByteArray(StandardCharsets.UTF_8))
      val signatureBytes = BASE64.decode(signatureBase64)
      signature.verify(signatureBytes)
    } catch (e: Exception) {
      false
    }
  }
}
