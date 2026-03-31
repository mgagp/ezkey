/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Interface: Reencryptable
 * Description: Interface for entities that contain encrypted fields that can be re-encrypted.
 */

package org.ezkey.security;

import java.util.Map;

/**
 * Interface for entities that contain encrypted fields that can be re-encrypted.
 *
 * <p>This interface provides a generic contract for re-encryption operations, allowing the
 * ReencryptionService to work with any entity type without reflection.
 *
 * <p><b>Usage:</b> Entities that store encrypted data should implement this interface to support
 * batch re-encryption operations during key rotation.
 *
 * <p><b>Design:</b>
 *
 * <ul>
 *   <li>Uses Map to handle multiple encrypted fields per entity
 *   <li>Column names match database column names (e.g., "integration_private_key")
 *   <li>setEncryptedField must clear transient decrypted fields to force re-decryption
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public interface Reencryptable {

  /**
   * Gets all encrypted field values mapped by database column name.
   *
   * <p>Returns a map where:
   *
   * <ul>
   *   <li>Key: database column name (e.g., "integration_private_key")
   *   <li>Value: encrypted value as stored in database (with ENC: prefix)
   * </ul>
   *
   * <p>Only fields that are actually encrypted (non-null) should be included.
   *
   * @return map of column names to encrypted values
   */
  Map<String, String> getEncryptedFields();

  /**
   * Sets an encrypted field value by database column name.
   *
   * <p>This method should:
   *
   * <ul>
   *   <li>Update the encrypted field directly (bypassing automatic encryption)
   *   <li>Clear any transient decrypted fields to force re-decryption on next access
   * </ul>
   *
   * @param columnName database column name (e.g., "integration_private_key")
   * @param encryptedValue the encrypted value (with ENC: prefix)
   * @throws IllegalArgumentException if column name is not supported
   */
  void setEncryptedField(String columnName, String encryptedValue);

  /**
   * Gets the entity ID for progress tracking during batch re-encryption.
   *
   * @return entity ID (as Long for consistency)
   */
  Long getEntityId();

  /**
   * Gets the table name for this entity type.
   *
   * @return database table name
   */
  String getTableName();
}
