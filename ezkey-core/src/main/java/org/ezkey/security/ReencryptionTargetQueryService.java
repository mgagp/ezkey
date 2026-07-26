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
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.repository.ApiKeyRepository;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.springframework.stereotype.Service;

/**
 * Count and fetch helpers for re-encryption targets (enrollment / auth attempt / API key columns).
 *
 * <p>Isolated from orchestration so batch creation and processing share one implementation.
 */
@Service
public class ReencryptionTargetQueryService {

  private final EnrollmentRepository enrollmentRepository;
  private final AuthAttemptRepository authAttemptRepository;
  private final ApiKeyRepository apiKeyRepository;

  public ReencryptionTargetQueryService(
      EnrollmentRepository enrollmentRepository,
      AuthAttemptRepository authAttemptRepository,
      ApiKeyRepository apiKeyRepository) {
    this.enrollmentRepository = enrollmentRepository;
    this.authAttemptRepository = authAttemptRepository;
    this.apiKeyRepository = apiKeyRepository;
  }

  public int countRecordsEncryptedWithKey(String table, String column, Long keyId) {
    return countRecordsEncryptedWithKey(table, column, keyId, null, null);
  }

  /**
   * Counts rows still encrypted with {@code keyId} for the target, optionally restricted to a shard
   * ({@code mod(auth_attempt_id, shardCount) = shardIndex}) for {@code ezkey_auth_attempt}.
   *
   * <p>Uses an indexed equality lookup on the target's {@code *_encryption_key_id} column
   * (I-2026-0029), replacing the historical {@code LIKE 'ENC:{keyId}:%'} prefix scan on the
   * ciphertext column.
   *
   * @param shardIndex shard index when {@code shardCount != null}; otherwise ignored
   * @param shardCount when non-null ({@code >= 2}), applies shard filter on auth attempts; null for
   *     full-table counts (lifecycle / enrollment)
   */
  public int countRecordsEncryptedWithKey(
      String table, String column, Long keyId, Integer shardIndex, Integer shardCount) {
    return switch (table) {
      case "ezkey_enrollment" -> countEnrollmentRecords(column, keyId);
      case "ezkey_auth_attempt" -> countAuthAttemptRecords(column, keyId, shardIndex, shardCount);
      case "ezkey_api_key" -> countApiKeyRecords(column, keyId);
      default -> 0;
    };
  }

  private int countEnrollmentRecords(String column, Long keyId) {
    return switch (column) {
      case "integration_private_key" ->
          enrollmentRepository.countByIntegrationPrivateKeyEncryptionKeyId(keyId);
      case "enrollment_proof_token" ->
          enrollmentRepository.countByEnrollmentProofTokenEncryptionKeyId(keyId);
      default -> 0;
    };
  }

  private int countAuthAttemptRecords(
      String column, Long keyId, Integer shardIndex, Integer shardCount) {
    return switch (column) {
      case "auth_attempt_proof_token" ->
          authAttemptRepository.countByAuthAttemptProofTokenEncryptionKeyId(
              keyId, shardIndex, shardCount);
      default -> 0;
    };
  }

  private int countApiKeyRecords(String column, Long keyId) {
    return switch (column) {
      case "secret_key_hash" -> apiKeyRepository.countBySecretKeyHashEncryptionKeyId(keyId);
      default -> 0;
    };
  }

  /**
   * Fetches a page of records still encrypted with {@code batch.getOldKey()} for the batch target.
   *
   * <p>Uses an indexed equality lookup on the target's {@code *_encryption_key_id} column
   * (I-2026-0029), replacing the historical {@code LIKE 'ENC:{keyId}:%'} prefix scan.
   */
  public List<? extends Reencryptable> fetchRecords(
      ReencryptionBatch batch, Long lastRecordId, int batchSize) {
    Long keyId = batch.getOldKey().getKeyId();
    Integer lastId = lastRecordId != null ? lastRecordId.intValue() : null;
    Integer shardIndex = batch.getShardIndex();
    Integer shardCount = batch.getShardCount();

    return switch (batch.getTargetTable()) {
      case "ezkey_enrollment" ->
          fetchEnrollmentRecords(batch.getTargetColumn(), keyId, lastId, batchSize);
      case "ezkey_auth_attempt" ->
          fetchAuthAttemptRecords(
              batch.getTargetColumn(), keyId, lastId, batchSize, shardIndex, shardCount);
      case "ezkey_api_key" -> fetchApiKeyRecords(batch.getTargetColumn(), keyId, lastId, batchSize);
      default -> List.of();
    };
  }

  private List<Enrollment> fetchEnrollmentRecords(
      String column, Long keyId, Integer lastId, int limit) {
    return switch (column) {
      case "integration_private_key" ->
          enrollmentRepository.findByIntegrationPrivateKeyEncryptionKeyId(keyId, lastId, limit);
      case "enrollment_proof_token" ->
          enrollmentRepository.findByEnrollmentProofTokenEncryptionKeyId(keyId, lastId, limit);
      default -> List.of();
    };
  }

  private List<AuthAttempt> fetchAuthAttemptRecords(
      String column,
      Long keyId,
      Integer lastId,
      int limit,
      Integer shardIndex,
      Integer shardCount) {
    return switch (column) {
      case "auth_attempt_proof_token" ->
          authAttemptRepository.findByAuthAttemptProofTokenEncryptionKeyId(
              keyId, lastId, shardIndex, shardCount, limit);
      default -> List.of();
    };
  }

  private List<ApiKey> fetchApiKeyRecords(String column, Long keyId, Integer lastId, int limit) {
    return switch (column) {
      case "secret_key_hash" ->
          apiKeyRepository.findBySecretKeyHashEncryptionKeyId(keyId, lastId, limit);
      default -> List.of();
    };
  }
}
