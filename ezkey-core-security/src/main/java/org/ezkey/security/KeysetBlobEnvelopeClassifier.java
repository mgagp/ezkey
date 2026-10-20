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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.crypto.tink.Aead;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
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
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<PosixFilePermission> OWNER_RW =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

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
   * Structural check: Tink cleartext keyset JSON after a successful master-AEAD decrypt.
   *
   * @param clearJson decrypted UTF-8 payload
   * @return {@code true} only when the payload parses as a Tink cleartext keyset object
   */
  static boolean looksLikeTinkCleartextKeysetJson(String clearJson) {
    if (clearJson == null || !clearJson.startsWith("{")) {
      return false;
    }
    try {
      JsonNode root = JSON.readTree(clearJson);
      if (!root.isObject() || root.has("encryptedKeyset") || !root.has("primaryKeyId")) {
        return false;
      }
      JsonNode keys = root.get("key");
      if (keys == null || !keys.isArray() || keys.isEmpty()) {
        return false;
      }
      for (JsonNode key : keys) {
        if (!key.isObject()
            || !key.has("keyData")
            || !key.has("outputPrefixType")
            || !key.has("status")) {
          return false;
        }
      }
      return true;
    } catch (JsonProcessingException e) {
      return false;
    }
  }

  /**
   * Atomically creates a unique legacy backup with POSIX 600 at create time when supported.
   *
   * @param keysetPath normalized keyset file path (backup is a sibling sidecar)
   * @param liveVersion blob version included in the filename
   * @param legacyBytes old row bytes
   * @return backup path
   * @throws IOException if create-new or write fails
   */
  static Path writeUniqueLegacyBackup(String keysetPath, long liveVersion, byte[] legacyBytes)
      throws IOException {
    Path backupPath =
        Path.of(keysetPath + ".legacy-backup.v" + liveVersion + "." + System.currentTimeMillis());
    try {
      Files.createFile(backupPath, PosixFilePermissions.asFileAttribute(OWNER_RW));
    } catch (UnsupportedOperationException e) {
      logger.debug("POSIX create attrs unsupported for {}: {}", backupPath, e.getMessage());
      Files.createFile(backupPath);
    }
    try (var out = Files.newOutputStream(backupPath, StandardOpenOption.WRITE)) {
      out.write(legacyBytes);
    }
    return backupPath;
  }

  /**
   * Best-effort delete of a local keyset file cache after fail-closed clear (Christophe orphan
   * rule).
   *
   * @param path absolute or normalized keyset path; ignored when null/blank
   * @param context fail-closed reason for logs
   */
  static void deleteFileCacheBestEffort(String path, String context) {
    if (path == null || path.isBlank()) {
      return;
    }
    try {
      if (Files.deleteIfExists(Path.of(path))) {
        logger.warn("Deleted orphan keyset file cache after fail-closed ({}): {}", context, path);
      }
    } catch (IOException | SecurityException e) {
      logger.warn(
          "Could not delete orphan keyset file cache after fail-closed ({}): {} — {}",
          context,
          path,
          e.getMessage());
    }
  }
}
