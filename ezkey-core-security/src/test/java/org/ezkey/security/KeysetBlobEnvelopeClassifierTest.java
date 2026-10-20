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
import org.junit.jupiter.api.io.TempDir;

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
    // Substring markers without structure must not count as legacy cleartext.
    assertFalse(
        KeysetBlobEnvelopeClassifier.looksLikeTinkCleartextKeysetJson(
            "{\"primaryKeyId\":1,\"key\":\"not-an-array\",\"keyData\":1,"
                + "\"outputPrefixType\":\"TINK\",\"status\":\"ENABLED\"}"));
  }

  @Test
  @DisplayName("writeUniqueLegacyBackup creates exactly one file with POSIX 600 when supported")
  void writeUniqueLegacyBackup_atomicOwnerRw(@TempDir java.nio.file.Path tempDir) throws Exception {
    java.nio.file.Path keyset = tempDir.resolve("keyset.json.encrypted");
    java.nio.file.Files.writeString(keyset, "placeholder");
    byte[] payload = new byte[] {1, 2, 3};
    java.nio.file.Path backup =
        KeysetBlobEnvelopeClassifier.writeUniqueLegacyBackup(keyset.toString(), 9L, payload);
    assertTrue(java.nio.file.Files.exists(backup));
    assertEquals(3, java.nio.file.Files.size(backup));
    try {
      assertEquals(
          java.util.Set.of(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE),
          java.nio.file.Files.getPosixFilePermissions(backup));
    } catch (UnsupportedOperationException e) {
      // non-POSIX
    }
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
