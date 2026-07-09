/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Interface: EncryptionOperations
 * Description: Minimal encryption contract used by core entities and JPA listeners.
 */

package org.ezkey.security;

/**
 * Minimal encryption contract needed by {@code ezkey-core}.
 *
 * <p>This interface exists to keep entities and JPA listeners decoupled from the concrete Tink
 * implementation so that the implementation can move to a dedicated module without breaking the
 * core domain model.
 *
 * @since 2025
 */
public interface EncryptionOperations {

  /**
   * Returns whether runtime encryption is currently available.
   *
   * @return true when runtime encryption is available
   */
  boolean isEncryptionAvailable();

  /**
   * Returns whether at-rest encryption is mandatory for this deployment.
   *
   * <p>When {@code true} ( {@code ezkey.encryption.required=true} ), callers must fail closed if a
   * sensitive column is stored without the encrypted format or cannot be decrypted.
   *
   * @return true when at-rest encryption is required
   */
  default boolean isEncryptionRequired() {
    return false;
  }

  /**
   * Returns whether a value already matches the encrypted storage format.
   *
   * @param value candidate encrypted value
   * @return true when the value matches the encrypted storage format
   */
  boolean isEncrypted(String value);

  /**
   * Encrypts a plaintext value when runtime encryption is available.
   *
   * @param plaintext plaintext value
   * @return encrypted value, or plaintext when encryption is unavailable
   */
  String encrypt(String plaintext);

  /**
   * Decrypts a stored encrypted value when runtime encryption is available.
   *
   * @param encryptedValue encrypted value or plaintext fallback
   * @return decrypted plaintext, or the original value when decryption is unavailable
   */
  String decrypt(String encryptedValue);
}
