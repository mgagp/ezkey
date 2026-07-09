/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AtRestEncryptionAccess
 * Description: Fail-closed read policy when ezkey.encryption.required=true.
 */

package org.ezkey.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves persisted at-rest fields to plaintext with an explicit fail-closed policy.
 *
 * <p>When {@code ezkey.encryption.required=true}, values that are not stored in the encrypted
 * {@code ENC:} format (or cannot be decrypted) must not be used — callers receive {@link
 * IllegalStateException} instead of a silent plaintext fallback.
 *
 * @since 2025
 */
public final class AtRestEncryptionAccess {

  private static final Logger logger = LoggerFactory.getLogger(AtRestEncryptionAccess.class);

  private AtRestEncryptionAccess() {}

  /**
   * Resolves a persisted sensitive field to plaintext for in-memory use.
   *
   * @param operations runtime encryption operations (may be null outside Spring/JPA)
   * @param persistedValue value loaded from the database column
   * @param fieldDescription human-readable field label for error messages
   * @return plaintext value suitable for business logic (e.g. BCrypt comparison)
   * @throws IllegalStateException when encryption is required but the value is not encrypted or
   *     cannot be decrypted
   */
  public static String resolveEncryptedField(
      EncryptionOperations operations, String persistedValue, String fieldDescription) {
    if (persistedValue == null) {
      return null;
    }

    if (operations == null) {
      return persistedValue;
    }

    if (operations.isEncryptionRequired() && !operations.isEncryptionAvailable()) {
      throw requiredViolation(fieldDescription, "encryption is required but not available");
    }

    if (!operations.isEncryptionAvailable()) {
      return persistedValue;
    }

    if (!operations.isEncrypted(persistedValue)) {
      if (operations.isEncryptionRequired()) {
        throw requiredViolation(fieldDescription, "value is not encrypted at rest");
      }
      return persistedValue;
    }

    String decrypted = operations.decrypt(persistedValue);
    if (operations.isEncrypted(decrypted)) {
      if (operations.isEncryptionRequired()) {
        throw requiredViolation(fieldDescription, "decryption failed");
      }
      logger.warn("Failed to decrypt {}. Using persisted value as-is.", fieldDescription);
      return persistedValue;
    }

    return decrypted;
  }

  /**
   * Ensures encryption is available before persisting a sensitive field when required mode is on.
   *
   * @param operations runtime encryption operations
   * @param fieldDescription human-readable field label for error messages
   * @throws IllegalStateException when encryption is required but unavailable at flush time
   */
  public static void requireEncryptionAvailableForPersist(
      EncryptionOperations operations, String fieldDescription) {
    if (operations != null
        && operations.isEncryptionRequired()
        && !operations.isEncryptionAvailable()) {
      throw requiredViolation(
          fieldDescription, "encryption is required but not available at persist time");
    }
  }

  /**
   * Propagates encrypt failures when required mode is on; otherwise allows plaintext fallback.
   *
   * @param operations runtime encryption operations
   * @param fieldDescription human-readable field label for error messages
   * @param encryptFailure the exception raised while encrypting
   * @throws IllegalStateException when encryption is required and encryption failed
   */
  public static void handleEncryptFailure(
      EncryptionOperations operations, String fieldDescription, Exception encryptFailure) {
    if (operations != null && operations.isEncryptionRequired()) {
      throw new IllegalStateException(
          "At-rest encryption is required (ezkey.encryption.required=true) but failed to encrypt "
              + fieldDescription,
          encryptFailure);
    }
  }

  private static IllegalStateException requiredViolation(String fieldDescription, String reason) {
    return new IllegalStateException(
        "At-rest encryption is required (ezkey.encryption.required=true) but "
            + fieldDescription
            + " "
            + reason);
  }
}
