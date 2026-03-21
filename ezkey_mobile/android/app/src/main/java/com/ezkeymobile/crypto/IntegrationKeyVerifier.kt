/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Pure Kotlin/JVM integration public key parsing and ECDSA signature verification.
 * Verification uses BouncyCastle ECDSASigner over SHA-256 of the UTF-8 payload, matching
 * SignatureService.validateSignature on the Auth API (PublicKeyFactory.createKey on raw decoded
 * key bytes — not a JCA round-trip, which can re-encode SPKI differently). JCA fallbacks follow.
 *
 * @since 2025
 */

package com.ezkeymobile.crypto

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.util.PublicKeyFactory
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
      val signatureBytes = BASE64.decode(signatureBase64)
      if (verifyEcdsaSha256BouncyCastleLightweight(data, signatureBytes, keyBytes)) {
        return true
      }
      val publicKey = parsePublicKey(keyBytes)
      if (verifyEcdsaSha256BouncyCastleJcaProvider(data, signatureBytes, publicKey)) {
        return true
      }
      verifyEcdsaSha256JcaDefaultProvider(data, signatureBytes, publicKey)
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Mirrors backend [SignatureService.validateSignature]: decode Base64 to DER bytes, then
   * [PublicKeyFactory.createKey] on those bytes (same as server). JCA [PublicKey.getEncoded] can
   * differ from the original SPKI and break verification.
   */
  private fun verifyEcdsaSha256BouncyCastleLightweight(
      data: String,
      derSignature: ByteArray,
      keyBytes: ByteArray,
  ): Boolean {
    return try {
      val publicKeyParams = ecPublicKeyParametersFromIntegrationKeyBytes(keyBytes) ?: return false
      val rs = decodeDerEcdsaSignature(derSignature) ?: return false
      val verifier = ECDSASigner()
      verifier.init(false, publicKeyParams)
      val hash =
          MessageDigest.getInstance("SHA-256").digest(data.toByteArray(StandardCharsets.UTF_8))
      verifier.verifySignature(hash, rs.first, rs.second)
    } catch (e: Exception) {
      false
    }
  }

  /**
   * Same as server: SPKI bytes → BC public key params; if bytes are an X.509 certificate, extract
   * subject public key info first.
   */
  private fun ecPublicKeyParametersFromIntegrationKeyBytes(keyBytes: ByteArray): ECPublicKeyParameters? {
    return try {
      PublicKeyFactory.createKey(keyBytes) as ECPublicKeyParameters
    } catch (e: Exception) {
      try {
        val certFactory = CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(ByteArrayInputStream(keyBytes))
        val encoded = cert.publicKey.encoded
        PublicKeyFactory.createKey(encoded) as ECPublicKeyParameters
      } catch (e2: Exception) {
        try {
          if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
          }
          val keySpec = X509EncodedKeySpec(keyBytes)
          val pk =
              KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME).generatePublic(keySpec)
          PublicKeyFactory.createKey(pk.encoded) as ECPublicKeyParameters
        } catch (e3: Exception) {
          null
        }
      }
    }
  }

  /** BouncyCastle JCA [SHA256withECDSA] (avoids Conscrypt-only behaviour). */
  private fun verifyEcdsaSha256BouncyCastleJcaProvider(
      data: String,
      derSignature: ByteArray,
      publicKey: PublicKey,
  ): Boolean {
    return try {
      if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
        Security.addProvider(BouncyCastleProvider())
      }
      val signature = Signature.getInstance("SHA256withECDSA", BouncyCastleProvider.PROVIDER_NAME)
      signature.initVerify(publicKey)
      signature.update(data.toByteArray(StandardCharsets.UTF_8))
      signature.verify(derSignature)
    } catch (e: Exception) {
      false
    }
  }

  private fun verifyEcdsaSha256JcaDefaultProvider(
      data: String,
      derSignature: ByteArray,
      publicKey: PublicKey,
  ): Boolean {
    return try {
      val signature = Signature.getInstance("SHA256withECDSA")
      signature.initVerify(publicKey)
      signature.update(data.toByteArray(StandardCharsets.UTF_8))
      signature.verify(derSignature)
    } catch (e: Exception) {
      false
    }
  }

  private fun decodeDerEcdsaSignature(der: ByteArray): Pair<BigInteger, BigInteger>? {
    return try {
      val seq = ASN1Sequence.getInstance(der)
      if (seq.size() != 2) {
        return null
      }
      val r = ASN1Integer.getInstance(seq.getObjectAt(0)).value
      val s = ASN1Integer.getInstance(seq.getObjectAt(1)).value
      Pair(r, s)
    } catch (e: Exception) {
      null
    }
  }
}
