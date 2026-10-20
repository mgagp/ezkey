/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: KeysetBlobRepository
 * Description: Spring Data JPA repository for KeysetBlob entity.
 */

package org.ezkey.security.domain.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for KeysetBlob entity.
 *
 * <p>Provides data access operations for the encrypted keyset blob stored in the database. The
 * keyset blob table is designed as a single-row table (id=1) to store the current Tink keyset.
 *
 * <p><b>Usage:</b> This repository is used by the key management implementation for database-backed
 * keyset storage, enabling distributed synchronization across multiple application instances.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Repository
public interface KeysetBlobRepository extends JpaRepository<KeysetBlob, Integer> {

  /**
   * Find the singleton keyset blob record.
   *
   * <p>Since the table is designed as single-row (id=1), this method returns the keyset if it
   * exists.
   *
   * @return Optional containing the keyset blob if it exists
   */
  default Optional<KeysetBlob> findKeyset() {
    return findById(KeysetBlob.SINGLETON_ID);
  }

  /**
   * Check if keyset blob exists in database.
   *
   * @return true if keyset blob exists, false otherwise
   */
  default boolean keysetExists() {
    return existsById(KeysetBlob.SINGLETON_ID);
  }

  /**
   * Inserts the singleton keyset row only when the table is empty.
   *
   * <p>Used for first-boot materialization so two Admin writers cannot overwrite each other's newly
   * generated keysets. Returns {@code 1} when this process claimed the row, or {@code 0} when a
   * peer already inserted {@code id=1}.
   *
   * @param keysetData encrypted Tink keyset envelope
   * @param lastUpdatedAt claim timestamp
   * @param updatedBy actor recorded on the new row
   * @return {@code 1} if inserted, {@code 0} if the singleton already existed
   */
  @Modifying
  @Transactional
  @Query(
      value =
          """
          INSERT INTO ezkey_keyset_blob (id, keyset_data, last_updated_at, updated_by, version)
          VALUES (1, :keysetData, :lastUpdatedAt, :updatedBy, 1)
          ON CONFLICT (id) DO NOTHING
          """,
      nativeQuery = true)
  int insertSingletonIfAbsent(
      @Param("keysetData") byte[] keysetData,
      @Param("lastUpdatedAt") OffsetDateTime lastUpdatedAt,
      @Param("updatedBy") String updatedBy);

  /**
   * Get the last updated timestamp of the keyset blob.
   *
   * <p>Used for change detection without loading the full keyset data.
   *
   * @return Optional containing the last updated timestamp if keyset exists
   */
  @Query("SELECT k.lastUpdatedAt FROM KeysetBlob k WHERE k.id = 1")
  Optional<java.time.OffsetDateTime> findLastUpdatedAt();

  /**
   * Get the version of the keyset blob for optimistic locking.
   *
   * @return Optional containing the version if keyset exists
   */
  @Query("SELECT k.version FROM KeysetBlob k WHERE k.id = 1")
  Optional<Long> findVersion();

  /**
   * Conditionally updates the singleton keyset blob only when the row still has {@code
   * expectedVersion} (CAS write for HA multi-writer safety — #920).
   *
   * <p>Returns {@code 1} when the row was updated (caller should treat the new version as {@code
   * expectedVersion + 1}), or {@code 0} when another writer advanced the version first.
   *
   * @param keysetData encrypted Tink keyset envelope
   * @param lastUpdatedAt update timestamp
   * @param updatedBy actor recorded on the row
   * @param expectedVersion version read before the mutation (CAS predicate)
   * @return {@code 1} if updated, {@code 0} if version mismatch
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query(
      value =
          """
          UPDATE ezkey_keyset_blob
          SET keyset_data = :keysetData,
              last_updated_at = :lastUpdatedAt,
              updated_by = :updatedBy,
              version = version + 1
          WHERE id = 1 AND version = :expectedVersion
          """,
      nativeQuery = true)
  int updateKeysetIfVersionMatches(
      @Param("keysetData") byte[] keysetData,
      @Param("lastUpdatedAt") OffsetDateTime lastUpdatedAt,
      @Param("updatedBy") String updatedBy,
      @Param("expectedVersion") long expectedVersion);
}
