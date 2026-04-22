/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.config.TinkProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates {@link ReencryptionBatch} rows for ENABLED non-PRIMARY keys. Runs in {@link
 * Propagation#REQUIRES_NEW} so inserts commit before batch processing.
 */
@Service
public class ReencryptionBatchCreationService {

  private static final Logger logger =
      LoggerFactory.getLogger(ReencryptionBatchCreationService.class);

  /** Table name for {@link org.ezkey.authattempt.domain.entity.AuthAttempt}. */
  public static final String EZKEY_AUTH_ATTEMPT_TABLE = "ezkey_auth_attempt";

  private final KeyManagementOperations keyManagementOperations;
  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final AuditLogService auditLogService;
  private final ReencryptionTargetQueryService targetQueryService;
  private final TinkProperties tinkProperties;

  public ReencryptionBatchCreationService(
      KeyManagementOperations keyManagementOperations,
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      AuditLogService auditLogService,
      ReencryptionTargetQueryService targetQueryService,
      TinkProperties tinkProperties) {
    this.keyManagementOperations = keyManagementOperations;
    this.keyRepository = keyRepository;
    this.batchRepository = batchRepository;
    this.auditLogService = auditLogService;
    this.targetQueryService = targetQueryService;
    this.tinkProperties = tinkProperties;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void createBatchesForOldKeys() {
    EncryptionKey primaryKey = getPrimaryKeyFromKeyset();
    if (primaryKey == null) {
      logger.debug("No PRIMARY key found, skipping batch creation");
      return;
    }

    List<EncryptionKey> enabledKeys = keyRepository.findEnabledKeys();
    enabledKeys.removeIf(key -> key.getKeyStatus() == EncryptionKey.KeyStatus.PRIMARY);

    List<Target> targets = discoverReencryptableTargets();

    for (EncryptionKey oldKey : enabledKeys) {
      for (Target target : targets) {
        createBatchesForTarget(oldKey, primaryKey, target, "SYSTEM");
      }
    }
  }

  /**
   * Creates one or more {@link ReencryptionBatch} rows for a single (old key, target) pair when
   * needed. For {@link #EZKEY_AUTH_ATTEMPT_TABLE} and {@code authAttemptShardCount &gt; 1}, creates
   * one batch per shard; otherwise a single non-sharded batch.
   *
   * @return batches inserted in this call (empty if nothing created)
   */
  public List<ReencryptionBatch> createBatchesForTarget(
      EncryptionKey oldKey, EncryptionKey primaryKey, Target target, String createdBy) {
    String table = target.table();
    String column = target.column();
    List<ReencryptionBatch> created = new ArrayList<>();

    int configuredShards = tinkProperties.getReencryption().getAuthAttemptShardCount();
    boolean useSharding = EZKEY_AUTH_ATTEMPT_TABLE.equals(table) && configuredShards > 1;

    if (useSharding) {
      for (int shardIndex = 0; shardIndex < configuredShards; shardIndex++) {
        List<ReencryptionBatch> activeBatches =
            batchRepository.findActiveBatchesByTargetAndOldKey(
                table, column, oldKey.getKeyId(), shardIndex, configuredShards);
        if (!activeBatches.isEmpty()) {
          logger.debug(
              "Active batch already exists for {}.{} shard {}/{} with old key {}",
              table,
              column,
              shardIndex,
              configuredShards,
              oldKey.getKeyId());
          continue;
        }

        int recordCount =
            targetQueryService.countRecordsEncryptedWithKey(
                table, column, oldKey.getKeyId(), shardIndex, configuredShards);
        if (recordCount == 0) {
          logger.debug(
              "No records for shard {}/{} encrypted with key {} in {}.{}",
              shardIndex,
              configuredShards,
              oldKey.getKeyId(),
              table,
              column);
          continue;
        }

        ReencryptionBatch batch =
            new ReencryptionBatch(
                table,
                column,
                oldKey,
                primaryKey,
                recordCount,
                createdBy,
                shardIndex,
                configuredShards);
        batchRepository.save(batch);
        created.add(batch);
        logAndAuditBatchCreated(batch, table, column, oldKey, primaryKey, recordCount);
      }
      return created;
    }

    List<ReencryptionBatch> activeBatches =
        batchRepository.findActiveBatchesByTargetAndOldKey(
            table, column, oldKey.getKeyId(), null, null);
    if (!activeBatches.isEmpty()) {
      logger.debug(
          "Active batch already exists for {}.{} with old key {}",
          table,
          column,
          oldKey.getKeyId());
      return created;
    }

    int recordCount =
        targetQueryService.countRecordsEncryptedWithKey(table, column, oldKey.getKeyId());
    if (recordCount == 0) {
      logger.debug(
          "No records found encrypted with key {} in {}.{}", oldKey.getKeyId(), table, column);
      return created;
    }

    ReencryptionBatch batch =
        new ReencryptionBatch(table, column, oldKey, primaryKey, recordCount, createdBy);
    batchRepository.save(batch);
    created.add(batch);
    logAndAuditBatchCreated(batch, table, column, oldKey, primaryKey, recordCount);
    return created;
  }

  private void logAndAuditBatchCreated(
      ReencryptionBatch batch,
      String table,
      String column,
      EncryptionKey oldKey,
      EncryptionKey primaryKey,
      int recordCount) {
    logger.info(
        "Created re-encryption batch {} for {}.{} (old key: {}, new key: {}, records: {})",
        batch.getBatchId(),
        table,
        column,
        oldKey.getKeyId(),
        primaryKey.getKeyId(),
        recordCount);

    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.REENCRYPTION_STARTED)
            .eventAction("create_reencryption_batch")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .reencryptionBatchId(batch.getBatchId())
                    .targetTable(table)
                    .targetColumn(column)
                    .oldKeyId(oldKey.getKeyId())
                    .newKeyId(primaryKey.getKeyId())
                    .recordsTotal(recordCount)
                    .toJson())
            .build());
  }

  public EncryptionKey getPrimaryKeyFromKeyset() {
    if (!keyManagementOperations.isInitialized()) {
      logger.warn("Tink keyset not initialized, cannot determine PRIMARY key");
      return null;
    }

    long primaryKeyId = keyManagementOperations.getCurrentPrimaryKeyId();

    EncryptionKey primaryKey = keyRepository.findById(primaryKeyId).orElse(null);
    if (primaryKey == null) {
      logger.warn(
          "PRIMARY key {} from keyset not found in database. Database may need synchronization.",
          Long.toUnsignedString(primaryKeyId));
      return null;
    }

    if (primaryKey.getKeyStatus() != EncryptionKey.KeyStatus.PRIMARY) {
      logger.warn(
          "⚠️ SOC2 AUDIT: PRIMARY key mismatch detected! "
              + "Keyset PRIMARY: {} (unsigned: {}), "
              + "Database status: {}. "
              + "Database may need synchronization. Using keyset value (source of truth).",
          primaryKeyId,
          Long.toUnsignedString(primaryKeyId),
          primaryKey.getKeyStatus());

      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("primary_key_mismatch_detected")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("keyset_primary_key_id", Long.toUnsignedString(primaryKeyId))
                      .custom("database_key_status", primaryKey.getKeyStatus().toString())
                      .errorSummary(
                          "PRIMARY key status mismatch between keyset and database. "
                              + "Keyset is source of truth. "
                              + "Database synchronization may be needed.")
                      .toJson())
              .errorMessage(
                  "PRIMARY key status mismatch: keyset="
                      + Long.toUnsignedString(primaryKeyId)
                      + ", database_status="
                      + primaryKey.getKeyStatus())
              .build());
    }

    return primaryKey;
  }

  /**
   * Discovers all re-encryptable targets dynamically from entities implementing Reencryptable.
   *
   * @return list of discovered targets (table, column pairs)
   */
  public List<Target> discoverReencryptableTargets() {
    Set<Target> targets = new LinkedHashSet<>();

    Enrollment enrollmentSample = new Enrollment();
    enrollmentSample.setEncryptedField("integration_private_key", "PLACEHOLDER");
    enrollmentSample.setEncryptedField("enrollment_proof_token", "PLACEHOLDER");

    AuthAttempt authAttemptSample = new AuthAttempt();
    authAttemptSample.setEncryptedField("auth_attempt_proof_token", "PLACEHOLDER");
    authAttemptSample.setEncryptedField("device_proof_token", "PLACEHOLDER");

    List<Reencryptable> sampleEntities = List.of(enrollmentSample, authAttemptSample);

    for (Reencryptable entity : sampleEntities) {
      String table = entity.getTableName();
      Map<String, String> encryptedFields = entity.getEncryptedFields();

      for (String column : encryptedFields.keySet()) {
        targets.add(new Target(table, column));
      }
    }

    List<Target> result = new ArrayList<>(targets);
    logger.debug("Discovered {} re-encryptable targets: {}", result.size(), result);
    return result;
  }

  /**
   * Represents a re-encryption target (table and column pair).
   *
   * @param table target table name
   * @param column target column name
   */
  public record Target(String table, String column) {}
}
