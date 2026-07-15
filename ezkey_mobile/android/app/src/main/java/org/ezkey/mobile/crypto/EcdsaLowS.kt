/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * ECDSA low-S normalization for Auth API SEC-012 parity (mirrors ezkey-core SignatureService /
 * Demo Device DeviceCryptoService).
 */
package org.ezkey.mobile.crypto

import java.math.BigInteger
import java.security.PrivateKey
import java.security.interfaces.ECPrivateKey

/**
 * Normalizes ECDSA DER signatures to canonical low-S form required by Auth API SEC-012.
 *
 * Android Keystore {@code SHA256withECDSA} emits high-S roughly half the time; the Auth API rejects
 * those after SEC-012. Same posture as Demo Device {@code DeviceCryptoService} and backend
 * {@code SignatureService#signEcdsaSha256}.
 */
internal object EcdsaLowS {

  /**
   * secp256r1 / P-256 curve order. Fallback when the Keystore private key does not expose EC params
   * (Ezkey device keys are always P-256).
   */
  val SECP256R1_ORDER: BigInteger =
      BigInteger(
          "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551",
          16,
      )

  fun curveOrder(privateKey: PrivateKey): BigInteger {
    if (privateKey is ECPrivateKey) {
      return privateKey.params.order
    }
    return SECP256R1_ORDER
  }

  fun normalizeDerSignature(der: ByteArray, curveOrder: BigInteger): ByteArray {
    val rs =
        EcdsaDerCodec.decodeSignature(der)
            ?: throw IllegalStateException("Invalid ECDSA DER from provider")
    val normalizedS = normalizeSToLowS(rs[1], curveOrder)
    if (normalizedS == rs[1]) {
      return der
    }
    return EcdsaDerCodec.encodeSignature(rs[0], normalizedS)
  }

  fun isCanonicalLowS(der: ByteArray, curveOrder: BigInteger): Boolean {
    val rs = EcdsaDerCodec.decodeSignature(der) ?: return false
    val halfN = curveOrder.shiftRight(1)
    return rs[1].compareTo(halfN) <= 0
  }

  fun normalizeSToLowS(s: BigInteger, n: BigInteger): BigInteger {
    val halfN = n.shiftRight(1)
    return if (s.compareTo(halfN) > 0) {
      n.subtract(s)
    } else {
      s
    }
  }
}
