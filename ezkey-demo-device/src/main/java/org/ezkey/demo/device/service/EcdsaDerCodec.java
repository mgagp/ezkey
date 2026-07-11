/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Minimal ASN.1 DER encode/decode for ECDSA signatures (SEQUENCE of two INTEGER r, s).
 * JDK-only helper so the Demo Device can emit low-S signatures matching Auth API SEC-012.
 */
package org.ezkey.demo.device.service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * JDK-only ECDSA signature DER helpers (secp256r1).
 *
 * <p>Mirrored from ezkey-core {@code org.ezkey.signature.EcdsaDerCodec} so the standalone Demo Device
 * module stays free of an ezkey-core dependency.
 */
final class EcdsaDerCodec {

  private EcdsaDerCodec() {}

  static BigInteger[] decodeSignature(byte[] der) {
    try {
      if (der.length < 8 || der[0] != 0x30) {
        return null;
      }
      int pos = 1;
      int seqLen = readDerLength(der, pos);
      int seqLenFieldSize = lengthFieldSize(der[pos]);
      pos += seqLenFieldSize;
      if (pos + seqLen > der.length) {
        return null;
      }
      if (der[pos++] != 0x02) {
        return null;
      }
      int rLen = readDerLength(der, pos);
      int rLenFieldSize = lengthFieldSize(der[pos]);
      pos += rLenFieldSize;
      byte[] rBytes = Arrays.copyOfRange(der, pos, pos + rLen);
      pos += rLen;
      BigInteger r = new BigInteger(1, rBytes);
      if (der[pos++] != 0x02) {
        return null;
      }
      int sLen = readDerLength(der, pos);
      int sLenFieldSize = lengthFieldSize(der[pos]);
      pos += sLenFieldSize;
      byte[] sBytes = Arrays.copyOfRange(der, pos, pos + sLen);
      BigInteger s = new BigInteger(1, sBytes);
      return new BigInteger[] {r, s};
    } catch (RuntimeException e) {
      return null;
    }
  }

  static byte[] encodeSignature(BigInteger r, BigInteger s) {
    byte[] rDer = encodeInteger(r);
    byte[] sDer = encodeInteger(s);
    int contentLen = rDer.length + sDer.length;
    ByteArrayOutputStream out = new ByteArrayOutputStream(2 + contentLen);
    out.write(0x30);
    writeDerLength(out, contentLen);
    out.writeBytes(rDer);
    out.writeBytes(sDer);
    return out.toByteArray();
  }

  private static int readDerLength(byte[] der, int idx) {
    int b = der[idx] & 0xFF;
    if ((b & 0x80) == 0) {
      return b;
    }
    int n = b & 0x7F;
    int len = 0;
    for (int i = 0; i < n; i++) {
      len = (len << 8) | (der[idx + 1 + i] & 0xFF);
    }
    return len;
  }

  private static int lengthFieldSize(int firstByte) {
    int b = firstByte & 0xFF;
    if ((b & 0x80) == 0) {
      return 1;
    }
    return 1 + (b & 0x7F);
  }

  private static byte[] encodeInteger(BigInteger v) {
    byte[] bits = v.toByteArray();
    if (bits[0] < 0) {
      byte[] padded = new byte[bits.length + 1];
      padded[0] = 0;
      System.arraycopy(bits, 0, padded, 1, bits.length);
      bits = padded;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream(2 + bits.length);
    out.write(0x02);
    writeDerLength(out, bits.length);
    out.writeBytes(bits);
    return out.toByteArray();
  }

  private static void writeDerLength(ByteArrayOutputStream out, int len) {
    if (len < 0x80) {
      out.write(len);
      return;
    }
    byte[] bytes = BigInteger.valueOf(len).toByteArray();
    int start = bytes[0] == 0 ? 1 : 0;
    int blen = bytes.length - start;
    out.write(0x80 | blen);
    out.write(bytes, start, blen);
  }
}
