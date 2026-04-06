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

  private final TinkKeyManager keyManager;
  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final AuditLogService auditLogService;
  private final ReencryptionTargetQueryService targetQueryService;

  public ReencryptionBatchCreationService(
      TinkKeyManager keyManager,
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      AuditLogService auditLogService,
      ReencryptionTargetQueryService targetQueryService) {
    this.keyManager = keyManager;
    this.keyRepository = keyRepository;
    this.batchRepository = batchRepository;
    this.auditLogService = auditLogService;
    this.targetQueryService = targetQueryService;
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
        String table = target.table();
        String column = target.column();

        List<ReencryptionBatch> activeBatches =
            batchRepository.findActiveBatchesByTargetAndOldKey(table, column, oldKey.getKeyId());
        if (!activeBatches.isEmpty()) {
          logger.debug(
              "Active batch already exists for {}.{} with old key {}",
              table,
              column,
              oldKey.getKeyId());
          continue;
        }

        int recordCount =
            targetQueryService.countRecordsEncryptedWithKey(table, column, oldKey.getKeyId());
        if (recordCount == 0) {
          logger.debug(
              "No records found encrypted with key {} in {}.{}", oldKey.getKeyId(), table, column);
          continue;
        }

        ReencryptionBatch batch =
            new ReencryptionBatch(table, column, oldKey, primaryKey, recordCount, "SYSTEM");
        batchRepository.save(batch);

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
    }
  }

  public EncryptionKey getPrimaryKeyFromKeyset() {
    if (!keyManager.isInitialized()) {
      logger.warn("Tink keyset not initialized, cannot determine PRIMARY key");
      return null;
    }

    long primaryKeyId = keyManager.getCurrentPrimaryKeyId();

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
