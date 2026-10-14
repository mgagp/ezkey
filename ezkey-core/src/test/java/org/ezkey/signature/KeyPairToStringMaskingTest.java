/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyPairToStringMaskingTest
 * Description: Ed25519/ECP256 key-pair toString() must mask private keys (#754 review).
 */

package org.ezkey.signature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards {@link Ed25519KeyPair} and {@link ECP256KeyPair} {@code toString()} masking.
 *
 * @since 2026
 */
class KeyPairToStringMaskingTest {

  private static final String PRIVATE_KEY = "PRIV_KEY_SECRET_RAW_VALUE_do-not-log";
  private static final String PUBLIC_KEY = "pub-key-VISIBLE-ok-to-log";

  @Test
  @DisplayName("Ed25519KeyPair toString masks private key and keeps public key")
  void ed25519ToStringMasksPrivateKey() {
    Ed25519KeyPair pair = new Ed25519KeyPair(PRIVATE_KEY, PUBLIC_KEY);

    String rendered = pair.toString();

    assertThat(rendered).doesNotContain(PRIVATE_KEY);
    assertThat(rendered).contains("[PROTECTED]");
    assertThat(rendered).contains(PUBLIC_KEY);
  }

  @Test
  @DisplayName("ECP256KeyPair toString masks private key and keeps public key")
  void ecp256ToStringMasksPrivateKey() {
    ECP256KeyPair pair = new ECP256KeyPair(PRIVATE_KEY, PUBLIC_KEY);

    String rendered = pair.toString();

    assertThat(rendered).doesNotContain(PRIVATE_KEY);
    assertThat(rendered).contains("[PROTECTED]");
    assertThat(rendered).contains(PUBLIC_KEY);
  }
}
