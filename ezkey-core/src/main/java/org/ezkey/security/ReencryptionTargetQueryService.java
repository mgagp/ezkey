/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import java.util.List;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.springframework.stereotype.Service;

/**
 * Count and fetch helpers for re-encryption targets (enrollment / auth attempt columns).
 *
 * <p>Isolated from orchestration so batch creation and processing share one implementation.
 */
@Service
public class ReencryptionTargetQueryService {

  private final EnrollmentRepository enrollmentRepository;
  private final AuthAttemptRepository authAttemptRepository;

  public ReencryptionTargetQueryService(
      EnrollmentRepository enrollmentRepository, AuthAttemptRepository authAttemptRepository) {
    this.enrollmentRepository = enrollmentRepository;
    this.authAttemptRepository = authAttemptRepository;
  }

  public int countRecordsEncryptedWithKey(String table, String column, Long keyId) {
    return countRecordsEncryptedWithKey(table, column, keyId, null, null);
  }

  /**
   * Counts rows still encrypted with {@code keyId} for the target, optionally restricted to a shard
   * ({@code mod(auth_attempt_id, shardCount) = shardIndex}) for {@code ezkey_auth_attempt}.
   *
   * @param shardIndex shard index when {@code shardCount != null}; otherwise ignored
   * @param shardCount when non-null ({@code >= 2}), applies shard filter on auth attempts; null for
   *     full-table counts (lifecycle / enrollment)
   */
  public int countRecordsEncryptedWithKey(
      String table, String column, Long keyId, Integer shardIndex, Integer shardCount) {
    String keyPrefix = "ENC:" + keyId + ":%";
    return switch (table) {
      case "ezkey_enrollment" -> countEnrollmentRecords(column, keyPrefix);
      case "ezkey_auth_attempt" ->
          countAuthAttemptRecords(column, keyPrefix, shardIndex, shardCount);
      default -> 0;
    };
  }

  private int countEnrollmentRecords(String column, String keyPrefix) {
    return switch (column) {
      case "integration_private_key" ->
          enrollmentRepository.countByEncryptedIntegrationPrivateKeyLike(keyPrefix);
      case "enrollment_proof_token" ->
          enrollmentRepository.countByEncryptedEnrollmentProofTokenLike(keyPrefix);
      default -> 0;
    };
  }

  private int countAuthAttemptRecords(
      String column, String keyPrefix, Integer shardIndex, Integer shardCount) {
    return switch (column) {
      case "auth_attempt_proof_token" ->
          authAttemptRepository.countByEncryptedAuthAttemptProofTokenLike(
              keyPrefix, shardIndex, shardCount);
      default -> 0;
    };
  }

  public List<? extends Reencryptable> fetchRecords(
      ReencryptionBatch batch, Long lastRecordId, int batchSize) {
    String keyPrefix = "ENC:" + batch.getOldKey().getKeyId() + ":%";
    Integer lastId = lastRecordId != null ? lastRecordId.intValue() : null;
    Integer shardIndex = batch.getShardIndex();
    Integer shardCount = batch.getShardCount();

    return switch (batch.getTargetTable()) {
      case "ezkey_enrollment" ->
          fetchEnrollmentRecords(batch.getTargetColumn(), keyPrefix, lastId, batchSize);
      case "ezkey_auth_attempt" ->
          fetchAuthAttemptRecords(
              batch.getTargetColumn(), keyPrefix, lastId, batchSize, shardIndex, shardCount);
      default -> List.of();
    };
  }

  private List<Enrollment> fetchEnrollmentRecords(
      String column, String keyPrefix, Integer lastId, int limit) {
    return switch (column) {
      case "integration_private_key" ->
          enrollmentRepository.findEncryptedIntegrationPrivateKeyLike(keyPrefix, lastId, limit);
      case "enrollment_proof_token" ->
          enrollmentRepository.findEncryptedEnrollmentProofTokenLike(keyPrefix, lastId, limit);
      default -> List.of();
    };
  }

  private List<AuthAttempt> fetchAuthAttemptRecords(
      String column,
      String keyPrefix,
      Integer lastId,
      int limit,
      Integer shardIndex,
      Integer shardCount) {
    return switch (column) {
      case "auth_attempt_proof_token" ->
          authAttemptRepository.findEncryptedAuthAttemptProofTokenLike(
              keyPrefix, lastId, shardIndex, shardCount, limit);
      default -> List.of();
    };
  }
}
