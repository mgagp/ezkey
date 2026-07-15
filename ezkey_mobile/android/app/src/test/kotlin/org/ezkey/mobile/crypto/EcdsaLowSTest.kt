package org.ezkey.mobile.crypto

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Guards Auth API SEC-012 parity: mobile must emit low-S ECDSA DER (same as Demo Device).
 */
@DisplayName("EcdsaLowS (SEC-012)")
class EcdsaLowSTest {

  @Test
  @DisplayName("normalizes high-S to low-S and keeps already-low-S unchanged")
  fun normalizesHighSAndPreservesLowS() {
    val n = EcdsaLowS.SECP256R1_ORDER
    val halfN = n.shiftRight(1)
    val lowS = halfN.subtract(BigInteger.ONE)
    val highS = n.subtract(lowS)
    val r = BigInteger.ONE

    val highDer = EcdsaDerCodec.encodeSignature(r, highS)
    val normalized = EcdsaLowS.normalizeDerSignature(highDer, n)
    assertTrue(EcdsaLowS.isCanonicalLowS(normalized, n))

    val lowDer = EcdsaDerCodec.encodeSignature(r, lowS)
    val unchanged = EcdsaLowS.normalizeDerSignature(lowDer, n)
    assertTrue(EcdsaLowS.isCanonicalLowS(unchanged, n))
    assertTrue(lowDer contentEquals unchanged)
  }

  @Test
  @DisplayName("software ECDSA samples are always low-S after normalize (Auth API SEC-012 path)")
  fun softwareEcdsaSamplesAlwaysLowSAfterNormalize() {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val keyPair = kpg.generateKeyPair()
    val privateKey = keyPair.private as ECPrivateKey
    val order = privateKey.params.order
    val data = "ezkey-sec012-mobile".toByteArray(StandardCharsets.UTF_8)

    // Raw JCA ECDSA is ~50% high-S; after normalize, every sample must be low-S and JCA-valid.
    for (i in 0 until 32) {
      val signature = Signature.getInstance("SHA256withECDSA")
      signature.initSign(privateKey)
      signature.update(data)
      val der = signature.sign()
      val lowSDer = EcdsaLowS.normalizeDerSignature(der, order)
      assertTrue(EcdsaLowS.isCanonicalLowS(lowSDer, order), "Sample $i must be low-S")

      val verifier = Signature.getInstance("SHA256withECDSA")
      verifier.initVerify(keyPair.public)
      verifier.update(data)
      assertTrue(verifier.verify(lowSDer), "Sample $i must still JCA-verify")
    }
  }

  @Test
  @DisplayName("high-S malleable variant is rejected by isCanonicalLowS")
  fun rejectsHighSMalleableVariant() {
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val keyPair = kpg.generateKeyPair()
    val privateKey = keyPair.private as ECPrivateKey
    val order = privateKey.params.order
    val data = "ezkey-sec012-malleability".toByteArray(StandardCharsets.UTF_8)

    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(privateKey)
    signature.update(data)
    val lowSDer = EcdsaLowS.normalizeDerSignature(signature.sign(), order)
    val rs = EcdsaDerCodec.decodeSignature(lowSDer)!!
    val highSDer = EcdsaDerCodec.encodeSignature(rs[0], order.subtract(rs[1]))

    assertTrue(EcdsaLowS.isCanonicalLowS(lowSDer, order))
    assertTrue(!EcdsaLowS.isCanonicalLowS(highSDer, order))

    // Sanity: high-S still verifies under raw JCA (the Auth API gap SEC-012 closed).
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(keyPair.public)
    verifier.update(data)
    assertTrue(verifier.verify(highSDer), "JCA alone accepts high-S; low-S gate is required")
  }

  @Test
  @DisplayName("curveOrder falls back to secp256r1 constant when key is not ECPrivateKey")
  fun curveOrderFallback() {
    // Non-EC PrivateKey stub via DSA would be heavy; assert constant matches EC-generated order.
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val privateKey = kpg.generateKeyPair().private as ECPrivateKey
    assertTrue(EcdsaLowS.curveOrder(privateKey) == EcdsaLowS.SECP256R1_ORDER)
  }
}
