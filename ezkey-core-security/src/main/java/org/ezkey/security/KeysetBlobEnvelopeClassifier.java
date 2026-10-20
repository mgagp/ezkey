/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: KeysetBlobEnvelopeClassifier
 * Description: Positive identification of Tink-native vs ADR-0011 legacy keyset blob bytes (B3).
 */

package org.ezkey.security;

import com.google.crypto.tink.Aead;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classifies raw {@code ezkey_keyset_blob.keyset_data} without treating decrypt failures as
 * “absent”.
 *
 * @since 2026
 */
final class KeysetBlobEnvelopeClassifier {

  private static final Logger logger = LoggerFactory.getLogger(KeysetBlobEnvelopeClassifier.class);

  private KeysetBlobEnvelopeClassifier() {}

  /**
   * Distinguishes Tink-native envelopes from positively identified ADR-0011 legacy outer-AEAD
   * blobs. Ambiguous or wrong-master-key Tink-native bytes are {@link
   * KeysetLoadResult#FAILED_CLOSED}, never legacy.
   *
   * @param encryptedData raw {@code keyset_data} bytes
   * @param masterAead master AEAD used for legacy outer decrypt probe
   * @return {@link KeysetLoadResult#LOADED} when Tink-native shape is present (caller parses),
   *     {@link KeysetLoadResult#LEGACY_ENVELOPE} when outer-AEAD+cleartext JSON is confirmed, else
   *     {@link KeysetLoadResult#FAILED_CLOSED}
   */
  static KeysetLoadResult classify(byte[] encryptedData, Aead masterAead) {
    if (encryptedData == null || encryptedData.length == 0) {
      return KeysetLoadResult.FAILED_CLOSED;
    }
    String asUtf8 = new String(encryptedData, StandardCharsets.UTF_8).trim();
    if (asUtf8.startsWith("{") && asUtf8.contains("\"encryptedKeyset\"")) {
      return KeysetLoadResult.LOADED;
    }
    if (masterAead == null) {
      return KeysetLoadResult.FAILED_CLOSED;
    }
    try {
      byte[] clear = masterAead.decrypt(encryptedData, null);
      String clearJson = new String(clear, StandardCharsets.UTF_8).trim();
      // Positive Tink cleartext-keyset markers (not merely absence of encryptedKeyset).
      if (looksLikeTinkCleartextKeysetJson(clearJson)) {
        return KeysetLoadResult.LEGACY_ENVELOPE;
      }
    } catch (GeneralSecurityException e) {
      logger.debug("Blob is not a decryptable legacy outer envelope: {}", e.getMessage());
    }
    return KeysetLoadResult.FAILED_CLOSED;
  }

  /**
   * Requires several Tink cleartext keyset field names after a successful master-AEAD decrypt.
   *
   * @param clearJson decrypted UTF-8 payload
   * @return {@code true} only when the payload looks like a Tink cleartext keyset JSON
   */
  static boolean looksLikeTinkCleartextKeysetJson(String clearJson) {
    if (clearJson == null || !clearJson.startsWith("{")) {
      return false;
    }
    if (clearJson.contains("\"encryptedKeyset\"")) {
      return false;
    }
    return clearJson.contains("\"primaryKeyId\"")
        && clearJson.contains("\"key\"")
        && clearJson.contains("\"keyData\"")
        && clearJson.contains("\"outputPrefixType\"")
        && clearJson.contains("\"status\"");
  }

  /**
   * Atomically creates a unique legacy backup (CREATE_NEW) and sets POSIX 600 when supported.
   *
   * @param keysetPath normalized keyset file path (backup is a sibling sidecar)
   * @param liveVersion blob version included in the filename
   * @param legacyBytes old row bytes
   * @return backup path
   * @throws IOException if create-new or permission set fails
   */
  static Path writeUniqueLegacyBackup(String keysetPath, long liveVersion, byte[] legacyBytes)
      throws IOException {
    Path backupPath =
        Path.of(keysetPath + ".legacy-backup.v" + liveVersion + "." + System.currentTimeMillis());
    try (var out =
        Files.newOutputStream(
            backupPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      out.write(legacyBytes);
    }
    try {
      Files.setPosixFilePermissions(
          backupPath, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException e) {
      logger.debug("POSIX 600 not supported for legacy backup {}: {}", backupPath, e.getMessage());
    }
    return backupPath;
  }
}
