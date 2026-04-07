/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: ReencryptionBatchRepository
 * Description: Spring Data JPA repository for ReencryptionBatch entity.
 */

package org.ezkey.security.domain.repository;

import java.util.List;
import java.util.Optional;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for ReencryptionBatch entity.
 *
 * <p>This repository provides data access methods for re-encryption batch operations, including
 * batch lookup by status, resumability queries, and progress tracking.
 *
 * <p><b>Supported Operations:</b>
 *
 * <ul>
 *   <li><b>CRUD Operations:</b> Standard JPA repository operations
 *   <li><b>Status Queries:</b> Find batches by status (PENDING, IN_PROGRESS, COMPLETED, FAILED,
 *       PAUSED)
 *   <li><b>Resumability:</b> Find batches that can be resumed after failure
 *   <li><b>Progress Tracking:</b> Query batches by target table/column
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ReencryptionBatch
 */
@Repository
public interface ReencryptionBatchRepository
    extends JpaRepository<ReencryptionBatch, Integer>, JpaSpecificationExecutor<ReencryptionBatch> {

  /**
   * Find re-encryption batches by status.
   *
   * @param status the batch status to search for
   * @return list of batches with the specified status
   */
  List<ReencryptionBatch> findByStatus(BatchStatus status);

  /**
   * Find batches that can be resumed (FAILED or PAUSED with retries remaining).
   *
   * <p>Used by re-encryption service to identify batches that should be retried.
   *
   * @return list of batches eligible for resumption
   */
  @Query(
      "SELECT b FROM ReencryptionBatch b WHERE b.status IN ('FAILED', 'PAUSED') AND b.retryCount <"
          + " b.maxRetries ORDER BY b.createdAt ASC")
  List<ReencryptionBatch> findBatchesEligibleForResume();

  /**
   * Find batches for a specific table and column combination.
   *
   * <p>Used to check if there's already a batch in progress for a target table/column before
   * creating a new one.
   *
   * @param targetTable the target table name
   * @param targetColumn the target column name
   * @return list of batches for the specified target
   */
  List<ReencryptionBatch> findByTargetTableAndTargetColumn(String targetTable, String targetColumn);

  /**
   * Find active batches (PENDING or IN_PROGRESS) for a specific table and column.
   *
   * <p>Used to prevent duplicate batch creation for the same target.
   *
   * @param targetTable the target table name
   * @param targetColumn the target column name
   * @return list of active batches for the specified target
   */
  @Query(
      "SELECT b FROM ReencryptionBatch b WHERE b.targetTable = :targetTable AND b.targetColumn ="
          + " :targetColumn AND b.status IN ('PENDING', 'IN_PROGRESS')")
  List<ReencryptionBatch> findActiveBatchesByTarget(
      @Param("targetTable") String targetTable, @Param("targetColumn") String targetColumn);

  /**
   * Find active batches (PENDING or IN_PROGRESS) for a specific table, column, and old key.
   *
   * <p>Used to prevent duplicate batch creation for the same target and old key combination. This
   * allows multiple batches for the same table/column if they target different old keys.
   *
   * @param targetTable the target table name
   * @param targetColumn the target column name
   * @param oldKeyId the old encryption key ID
   * @return list of active batches for the specified target and old key
   */
  @Query(
      "SELECT b FROM ReencryptionBatch b WHERE b.targetTable = :targetTable "
          + "AND b.targetColumn = :targetColumn AND b.oldKey.keyId = :oldKeyId "
          + "AND b.status IN ('PENDING', 'IN_PROGRESS')")
  List<ReencryptionBatch> findActiveBatchesByTargetAndOldKey(
      @Param("targetTable") String targetTable,
      @Param("targetColumn") String targetColumn,
      @Param("oldKeyId") Long oldKeyId);

  /**
   * Find batches by old and new key IDs.
   *
   * <p>Used to track all batches involved in migrating from one key to another.
   *
   * @param oldKeyId the old encryption key ID
   * @param newKeyId the new encryption key ID
   * @return list of batches for the specified key migration
   */
  @Query(
      "SELECT b FROM ReencryptionBatch b WHERE b.oldKey.keyId = :oldKeyId AND b.newKey.keyId ="
          + " :newKeyId ORDER BY b.createdAt DESC")
  List<ReencryptionBatch> findByOldKeyIdAndNewKeyId(
      @Param("oldKeyId") Long oldKeyId, @Param("newKeyId") Long newKeyId);

  /**
   * Counts batches for an old key whose status is not the given value (for example, all batches not
   * yet {@code COMPLETED}).
   *
   * <p>Used by key lifecycle verification to detect migration work still in flight or needing
   * attention.
   *
   * @param oldKeyId the old encryption key id
   * @param status status to exclude from the count (typically {@link
   *     org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus#COMPLETED})
   * @return number of matching batches
   */
  long countByOldKey_KeyIdAndStatusNot(Long oldKeyId, BatchStatus status);

  /**
   * Find the most recent batch for a target table/column that can be resumed.
   *
   * <p>Used to resume from the last batch if service was interrupted.
   *
   * @param targetTable the target table name
   * @param targetColumn the target column name
   * @return Optional containing the most recent resumable batch if found
   */
  @Query(
      "SELECT b FROM ReencryptionBatch b WHERE b.targetTable = :targetTable AND b.targetColumn ="
          + " :targetColumn AND b.status IN ('IN_PROGRESS', 'FAILED', 'PAUSED') ORDER BY"
          + " b.createdAt DESC")
  Optional<ReencryptionBatch> findMostRecentResumableBatch(
      @Param("targetTable") String targetTable, @Param("targetColumn") String targetColumn);
}
