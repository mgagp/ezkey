/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: KeysetBlobRepository
 * Description: Spring Data JPA repository for KeysetBlob entity.
 */

package org.ezkey.security.domain.repository;

import java.util.Optional;
import org.ezkey.security.domain.entity.KeysetBlob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for KeysetBlob entity.
 *
 * <p>Provides data access operations for the encrypted keyset blob stored in the database. The
 * keyset blob table is designed as a single-row table (id=1) to store the current Tink keyset.
 *
 * <p><b>Usage:</b> This repository is used by TinkKeyManager for database-backed keyset storage,
 * enabling distributed synchronization across multiple application instances.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
}
