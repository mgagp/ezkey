/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeysetBlobEnvelopeClassifierTest
 * Description: Positive cleartext-keyset markers required for LEGACY_ENVELOPE classification.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tightens ADR-0011 legacy detection beyond “not encryptedKeyset”.
 *
 * @since 2026
 */
class KeysetBlobEnvelopeClassifierTest {

  @Test
  @DisplayName("cleartext Tink keyset JSON with positive markers is accepted")
  void looksLikeTinkCleartextKeysetJson_positiveMarkers_true() {
    String json =
        "{\"primaryKeyId\":1,\"key\":[{\"keyData\":{\"typeUrl\":\"x\"},\"outputPrefixType\":\"TINK\",\"status\":\"ENABLED\",\"keyId\":1}]}";
    assertTrue(KeysetBlobEnvelopeClassifier.looksLikeTinkCleartextKeysetJson(json));
  }

  @Test
  @DisplayName("absence of encryptedKeyset alone is not enough")
  void looksLikeTinkCleartextKeysetJson_onlyKeyField_false() {
    assertFalse(KeysetBlobEnvelopeClassifier.looksLikeTinkCleartextKeysetJson("{\"key\":[]}"));
    assertFalse(
        KeysetBlobEnvelopeClassifier.looksLikeTinkCleartextKeysetJson(
            "{\"encryptedKeyset\":\"abc\",\"primaryKeyId\":1}"));
  }

  @Test
  @DisplayName("Tink-native envelope shape classifies as LOADED shape (caller parses)")
  void classify_tinkNativeJson_loadedShape() {
    byte[] data =
        "{\"encryptedKeyset\":\"abc\",\"keysetInfo\":{}}"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(KeysetLoadResult.LOADED, KeysetBlobEnvelopeClassifier.classify(data, null));
  }
}
