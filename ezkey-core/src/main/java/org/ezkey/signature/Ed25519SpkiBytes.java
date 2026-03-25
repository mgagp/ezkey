/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Raw Ed25519 public key (32 bytes) to/from X.509 SubjectPublicKeyInfo (44 bytes), RFC 8410.
 */
package org.ezkey.signature;

import java.util.Arrays;

/** Helpers for Ed25519 SPKI wire format (JDK KeyFactory expects SPKI, APIs expose raw 32 bytes). */
final class Ed25519SpkiBytes {

  private static final byte[] SPKI_PREFIX_44 =
      new byte[] {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

  private Ed25519SpkiBytes() {}

  static byte[] rawPublicKeyToSpki(byte[] raw32) {
    if (raw32.length != 32) {
      throw new IllegalArgumentException("Ed25519 raw public key must be 32 bytes");
    }
    byte[] spki = new byte[44];
    System.arraycopy(SPKI_PREFIX_44, 0, spki, 0, 12);
    System.arraycopy(raw32, 0, spki, 12, 32);
    return spki;
  }

  static byte[] spkiToRawPublicKey(byte[] spki) {
    if (spki == null || spki.length < 44) {
      throw new IllegalArgumentException("Invalid Ed25519 SPKI length");
    }
    if (!Arrays.equals(Arrays.copyOf(spki, 12), SPKI_PREFIX_44)) {
      throw new IllegalArgumentException("Unexpected Ed25519 SPKI prefix");
    }
    return Arrays.copyOfRange(spki, 12, 44);
  }
}
