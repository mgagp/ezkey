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
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
 *   <li><b>Status Queries:</b> Find keys by status (PENDING, PRIMARY, ENABLED, DISABLED)
 *   <li><b>Primary Key Lookup:</b> Find current primary key for encryption
 *   <li><b>Pending Key Queries:</b> Find PENDING keys ready for promotion
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
public interface EncryptionKeyRepository
    extends JpaRepository<EncryptionKey, Long>, JpaSpecificationExecutor<EncryptionKey> {

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

  /**
   * Find PENDING keys that are ready for promotion to PRIMARY.
   *
   * <p>A key is ready for promotion when:
   *
   * <ul>
   *   <li>Status is PENDING
   *   <li>effective_at timestamp is less than or equal to current time
   * </ul>
   *
   * <p>This query is used by the scheduled promotion job to find keys whose synchronization window
   * has expired.
   *
   * @param now current timestamp to compare against effective_at
   * @return list of PENDING keys ready for promotion, ordered by effective_at (oldest first)
   */
  @Query(
      "SELECT k FROM EncryptionKey k WHERE k.keyStatus = 'PENDING' AND k.effectiveAt <= :now "
          + "ORDER BY k.effectiveAt ASC")
  List<EncryptionKey> findPendingKeysReadyForPromotion(@Param("now") OffsetDateTime now);

  /**
   * Find all PENDING keys (regardless of effective_at).
   *
   * <p>Used for administrative queries and status checks.
   *
   * @return list of all PENDING keys
   */
  @Query("SELECT k FROM EncryptionKey k WHERE k.keyStatus = 'PENDING' ORDER BY k.effectiveAt ASC")
  List<EncryptionKey> findAllPendingKeys();

  /**
   * Check if any PENDING key exists.
   *
   * <p>Used to determine if a rotation is in progress (waiting for synchronization window).
   *
   * @return true if at least one PENDING key exists
   */
  @Query("SELECT COUNT(k) > 0 FROM EncryptionKey k WHERE k.keyStatus = 'PENDING'")
  boolean existsPendingKey();
}
