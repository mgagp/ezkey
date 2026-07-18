/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Minimal ASN.1 DER encode/decode for ECDSA signatures (SEQUENCE of two INTEGER r, s).
 * Mirrored from ezkey-core org.ezkey.signature.EcdsaDerCodec for Auth API SEC-012 low-S parity.
 */
package org.ezkey.mobile.crypto

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Arrays

/**
 * JDK-only ECDSA signature DER helpers (secp256r1).
 *
 * Used so Android Keystore ECDSA signatures can be normalized to low-S before leaving the device.
 */
internal object EcdsaDerCodec {

  fun decodeSignature(der: ByteArray): Array<BigInteger>? {
    return try {
      if (der.size < 8 || der[0] != 0x30.toByte()) {
        return null
      }
      var pos = 1
      val seqLen = readDerLength(der, pos)
      val seqLenFieldSize = lengthFieldSize(der[pos])
      pos += seqLenFieldSize
      if (pos + seqLen > der.size) {
        return null
      }
      if (der[pos++].toInt() != 0x02) {
        return null
      }
      val rLen = readDerLength(der, pos)
      val rLenFieldSize = lengthFieldSize(der[pos])
      pos += rLenFieldSize
      val rBytes = Arrays.copyOfRange(der, pos, pos + rLen)
      pos += rLen
      val r = BigInteger(1, rBytes)
      if (der[pos++].toInt() != 0x02) {
        return null
      }
      val sLen = readDerLength(der, pos)
      val sLenFieldSize = lengthFieldSize(der[pos])
      pos += sLenFieldSize
      val sBytes = Arrays.copyOfRange(der, pos, pos + sLen)
      val s = BigInteger(1, sBytes)
      arrayOf(r, s)
    } catch (_: RuntimeException) {
      null
    }
  }

  fun encodeSignature(r: BigInteger, s: BigInteger): ByteArray {
    val rDer = encodeInteger(r)
    val sDer = encodeInteger(s)
    val contentLen = rDer.size + sDer.size
    val out = ByteArrayOutputStream(2 + contentLen)
    out.write(0x30)
    writeDerLength(out, contentLen)
    out.write(rDer)
    out.write(sDer)
    return out.toByteArray()
  }

  private fun readDerLength(der: ByteArray, idx: Int): Int {
    val b = der[idx].toInt() and 0xFF
    if (b and 0x80 == 0) {
      return b
    }
    val n = b and 0x7F
    var len = 0
    for (i in 0 until n) {
      len = (len shl 8) or (der[idx + 1 + i].toInt() and 0xFF)
    }
    return len
  }

  private fun lengthFieldSize(firstByte: Byte): Int {
    val b = firstByte.toInt() and 0xFF
    if (b and 0x80 == 0) {
      return 1
    }
    return 1 + (b and 0x7F)
  }

  private fun encodeInteger(v: BigInteger): ByteArray {
    var bits = v.toByteArray()
    if (bits[0] < 0) {
      val padded = ByteArray(bits.size + 1)
      padded[0] = 0
      System.arraycopy(bits, 0, padded, 1, bits.size)
      bits = padded
    }
    val out = ByteArrayOutputStream(2 + bits.size)
    out.write(0x02)
    writeDerLength(out, bits.size)
    out.write(bits)
    return out.toByteArray()
  }

  private fun writeDerLength(out: ByteArrayOutputStream, len: Int) {
    if (len < 0x80) {
      out.write(len)
      return
    }
    val bytes = BigInteger.valueOf(len.toLong()).toByteArray()
    val start = if (bytes[0].toInt() == 0) 1 else 0
    val blen = bytes.size - start
    out.write(0x80 or blen)
    out.write(bytes, start, blen)
  }
}
