/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: EncryptionKeyRepository
 * Description: Spring Data JPA repository for EncryptionKey entity.
 */

package org.ezkey.security.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for EncryptionKey entity.
 *
 * <p>This repository provides data access methods for encryption key lifecycle management,
 * including key lookup by status, primary key identification, and key rotation queries.
 *
 * <p><b>Supported Operations:</b>
 *
 * <ul>
 *   <li><b>CRUD Operations:</b> Standard JPA repository operations
 *   <li><b>Status Queries:</b> Find keys by status (PRIMARY, ENABLED, DISABLED)
 *   <li><b>Primary Key Lookup:</b> Find current primary key for encryption
 *   <li><b>Rotation Queries:</b> Find keys eligible for rotation or cleanup
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EncryptionKey
 */
@Repository
public interface EncryptionKeyRepository extends JpaRepository<EncryptionKey, Long> {

  /**
   * Find encryption keys by status.
   *
   * @param status the key status to search for
   * @return list of encryption keys with the specified status
   */
  List<EncryptionKey> findByKeyStatus(KeyStatus status);

  /**
   * Find all enabled keys (PRIMARY and ENABLED status).
   *
   * <p>These are keys that can be used for decryption operations. Used for key rotation and cleanup
   * decisions.
   *
   * @return list of enabled encryption keys
   */
  @Query(
      "SELECT k FROM EncryptionKey k WHERE k.keyStatus IN ('PRIMARY', 'ENABLED') ORDER BY"
          + " k.introducedAt DESC")
  List<EncryptionKey> findEnabledKeys();

  /**
   * Find the primary encryption key.
   *
   * <p>There should be exactly one PRIMARY key at any time.
   *
   * @return Optional containing the primary key if found
   */
  Optional<EncryptionKey> findOneByKeyStatus(KeyStatus status);

  /**
   * Find keys older than specified date that are eligible for rotation.
   *
   * <p>Used by rotation service to determine if rotation is due based on max-key-age-days
   * configuration.
   *
   * @param cutoffDate keys introduced before this date are eligible for rotation
   * @param status key status to filter (typically PRIMARY)
   * @return list of keys eligible for rotation
   */
  @Query(
      "SELECT k FROM EncryptionKey k WHERE k.introducedAt < :cutoffDate AND k.keyStatus = :status"
          + " ORDER BY k.introducedAt ASC")
  List<EncryptionKey> findKeysEligibleForRotation(
      @Param("cutoffDate") OffsetDateTime cutoffDate, @Param("status") KeyStatus status);

  /**
   * Find keys older than specified date that can be disabled.
   *
   * <p>Used for cleanup of old keys after all data has been re-encrypted. Keys must be ENABLED (not
   * PRIMARY) and older than the cutoff date.
   *
   * @param cutoffDate keys introduced before this date can be disabled
   * @return list of keys eligible for disablement
   */
  @Query(
      "SELECT k FROM EncryptionKey k WHERE k.keyStatus = 'ENABLED' AND k.introducedAt < :cutoffDate"
          + " ORDER BY k.introducedAt ASC")
  List<EncryptionKey> findKeysEligibleForDisable(@Param("cutoffDate") OffsetDateTime cutoffDate);

  /**
   * Count keys by status.
   *
   * @param status the key status to count
   * @return number of keys with the specified status
   */
  long countByKeyStatus(KeyStatus status);
}
