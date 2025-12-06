/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: ReencryptionService
 * Description: Service for batch re-encryption of data encrypted with old keys.
 */

package org.ezkey.security;

import java.time.OffsetDateTime;
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
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.TinkProperties;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for batch re-encryption of data encrypted with old keys.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Scheduled batch re-encryption of data encrypted with old keys
 *   <li>Fault-tolerant batch processing with resumability
 *   <li>Progress tracking and monitoring
 *   <li>Error handling and retry logic
 *   <li>Statistics updates (records_encrypted, records_reencrypted)
 * </ul>
 *
 * <p><b>Re-encryption Process:</b>
 *
 * <ol>
 *   <li>Identify records encrypted with old key (using SQL LIKE 'ENC:oldKeyId:%')
 *   <li>Create batch record in database
 *   <li>Process records in configurable batch size
 *   <li>Decrypt with old key, encrypt with new key
 *   <li>Update database record
 *   <li>Update batch progress and statistics
 *   <li>Emit audit log events
 * </ol>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class ReencryptionService {

  private static final Logger logger = LoggerFactory.getLogger(ReencryptionService.class);

  private final EncryptionService encryptionService;
  private final TinkKeyManager keyManager;
  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final AuthAttemptRepository authAttemptRepository;
  private final TinkProperties properties;
  private final AuditLogService auditLogService;

  public ReencryptionService(
      EncryptionService encryptionService,
      TinkKeyManager keyManager,
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      EnrollmentRepository enrollmentRepository,
      AuthAttemptRepository authAttemptRepository,
      TinkProperties properties,
      AuditLogService auditLogService) {
    this.encryptionService = encryptionService;
    this.keyManager = keyManager;
    this.keyRepository = keyRepository;
    this.batchRepository = batchRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.authAttemptRepository = authAttemptRepository;
    this.properties = properties;
    this.auditLogService = auditLogService;
  }

  /**
   * Scheduled job to process re-encryption batches.
   *
   * <p>Runs according to the cron schedule configured in ezkey.encryption.reencryption.schedule
   * (default: daily at 3 AM, after rotation).
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Finds batches that need processing (PENDING, IN_PROGRESS, FAILED with retries)
   *   <li>Processes batches up to max-batches-per-run limit
   *   <li>Respects max-duration-minutes timeout
   *   <li>Creates new batches for old keys that need re-encryption
   * </ul>
   */
  @Scheduled(cron = "${ezkey.encryption.reencryption.schedule:0 0 3 * * ?}")
  @Transactional
  public void processReencryptionBatches() {
    if (!properties.getReencryption().isEnabled()) {
      logger.debug("Re-encryption is disabled via configuration");
      return;
    }

    if (!encryptionService.isEncryptionAvailable()) {
      logger.warn("Encryption not available, skipping re-encryption batch processing");
      return;
    }

    try {
      logger.info("🔄 Starting re-encryption batch processing...");

      OffsetDateTime startTime = OffsetDateTime.now();
      int maxBatches = properties.getReencryption().getMaxBatchesPerRun();
      int maxDurationMinutes = properties.getReencryption().getMaxDurationMinutes();
      int batchesProcessed = 0;

      // Find batches that need processing
      List<ReencryptionBatch> batchesToProcess = batchRepository.findBatchesEligibleForResume();
      batchesToProcess.addAll(batchRepository.findByStatus(BatchStatus.PENDING));

      // Also create new batches for old keys that need re-encryption
      createBatchesForOldKeys();

      // Process batches
      for (ReencryptionBatch batch : batchesToProcess) {
        if (batchesProcessed >= maxBatches) {
          logger.info("Reached max batches per run limit: {}", maxBatches);
          break;
        }

        long elapsedMinutes =
            java.time.Duration.between(startTime, OffsetDateTime.now()).toMinutes();
        if (elapsedMinutes >= maxDurationMinutes) {
          logger.info("Reached max duration limit: {} minutes", maxDurationMinutes);
          break;
        }

        try {
          processBatch(batch);
          batchesProcessed++;
        } catch (Exception e) {
          logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
          markBatchFailed(batch, e.getMessage());
        }
      }

      logger.info(
          "✅ Re-encryption batch processing completed. Processed {} batches", batchesProcessed);
    } catch (Exception e) {
      logger.error("Failed to process re-encryption batches", e);
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("scheduled_batch_processing")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Scheduled batch processing failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
    }
  }

  /**
   * Create batches for old keys that need re-encryption.
   *
   * <p>For each ENABLED key older than rotation threshold, creates batches for all tables/columns
   * that contain encrypted data. Targets are discovered dynamically from entities implementing
   * Reencryptable.
   */
  @Transactional
  public void createBatchesForOldKeys() {
    // Get current primary key from keyset Tink (source of truth for SOC2 compliance)
    EncryptionKey primaryKey = getPrimaryKeyFromKeyset();
    if (primaryKey == null) {
      logger.debug("No PRIMARY key found, skipping batch creation");
      return;
    }

    // Find ENABLED keys that are not primary (old keys)
    List<EncryptionKey> enabledKeys = keyRepository.findEnabledKeys();
    enabledKeys.removeIf(key -> key.getKeyStatus() == EncryptionKey.KeyStatus.PRIMARY);

    // Discover re-encryptable targets dynamically
    List<Target> targets = discoverReencryptableTargets();

    for (EncryptionKey oldKey : enabledKeys) {
      for (Target target : targets) {
        String table = target.table();
        String column = target.column();

        // Check if active batch already exists for this specific old key
        // Note: We check by (table, column, oldKeyId) to allow multiple batches for different old
        // keys
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

        // Count records encrypted with old key
        int recordCount = countRecordsEncryptedWithKey(table, column, oldKey.getKeyId());
        if (recordCount == 0) {
          logger.debug(
              "No records found encrypted with key {} in {}.{}", oldKey.getKeyId(), table, column);
          continue;
        }

        // Create batch
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

        // Emit audit log
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

  /**
   * Count records encrypted with a specific key.
   *
   * @param table the table name
   * @param column the column name
   * @param keyId the encryption key ID
   * @return number of records encrypted with the key
   */
  private int countRecordsEncryptedWithKey(String table, String column, Long keyId) {
    String keyPrefix = "ENC:" + keyId + ":%";
    return switch (table) {
      case "ezkey_enrollment" -> countEnrollmentRecords(column, keyPrefix);
      case "ezkey_auth_attempt" -> countAuthAttemptRecords(column, keyPrefix);
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

  private int countAuthAttemptRecords(String column, String keyPrefix) {
    return switch (column) {
      case "auth_attempt_proof_token" ->
          authAttemptRepository.countByEncryptedAuthAttemptProofTokenLike(keyPrefix);
      case "device_proof_token" ->
          authAttemptRepository.countByEncryptedDeviceProofTokenLike(keyPrefix);
      default -> 0;
    };
  }

  /**
   * Process a single re-encryption batch.
   *
   * @param batch the batch to process
   */
  @Transactional
  public void processBatch(ReencryptionBatch batch) {
    logger.info(
        "Processing re-encryption batch {} ({}.{})",
        batch.getBatchId(),
        batch.getTargetTable(),
        batch.getTargetColumn());

    if (batch.getStatus() == BatchStatus.COMPLETED) {
      logger.debug("Batch {} already completed, skipping", batch.getBatchId());
      return;
    }

    // Mark batch as IN_PROGRESS
    if (batch.getStatus() == BatchStatus.PENDING) {
      batch.setStatus(BatchStatus.IN_PROGRESS);
      batch.setStartedAt(OffsetDateTime.now());
      batchRepository.save(batch);
    }

    int batchSize = properties.getReencryption().getBatchSize();
    int recordsDone = batch.getRecordsDone();
    int recordsFailed = batch.getRecordsFailed();
    int recordsSkipped = batch.getRecordsSkipped();
    Long lastRecordId = batch.getLastRecordId();

    OffsetDateTime batchStartTime = OffsetDateTime.now();

    // Process records
    while (recordsDone + recordsFailed + recordsSkipped < batch.getRecordsTotal()) {
      List<? extends Reencryptable> records = fetchRecords(batch, lastRecordId, batchSize);
      if (records.isEmpty()) {
        break; // No more records
      }

      // Collect modified records for batch save (optimize N+1 problem)
      List<Enrollment> modifiedEnrollments = new java.util.ArrayList<>();
      List<AuthAttempt> modifiedAuthAttempts = new java.util.ArrayList<>();

      for (Reencryptable record : records) {
        try {
          ReencryptResult result = reencryptRecord(batch, record);
          // Always update lastRecordId to ensure we don't reprocess the same record
          // This prevents infinite loops and ensures all records are processed
          lastRecordId = record.getEntityId();

          if (result.reencrypted()) {
            recordsDone++;
            // Collect modified record for batch save
            if (result.modifiedRecord() instanceof Enrollment e) {
              modifiedEnrollments.add(e);
            } else if (result.modifiedRecord() instanceof AuthAttempt a) {
              modifiedAuthAttempts.add(a);
            }
          } else {
            recordsSkipped++;
          }
        } catch (Exception e) {
          logger.warn(
              "Failed to re-encrypt record in batch {}: {}", batch.getBatchId(), e.getMessage());
          recordsFailed++;
          // Still update lastRecordId even on failure to avoid reprocessing
          lastRecordId = record.getEntityId();
        }
      }

      // Batch save all modified records (optimize N+1 problem)
      if (!modifiedEnrollments.isEmpty()) {
        enrollmentRepository.saveAll(modifiedEnrollments);
      }
      if (!modifiedAuthAttempts.isEmpty()) {
        authAttemptRepository.saveAll(modifiedAuthAttempts);
      }

      // Update batch progress
      batch.updateProgress(recordsDone, recordsFailed, recordsSkipped);
      batch.setLastRecordId(lastRecordId);
      batch.setLastBatchAt(OffsetDateTime.now());
      batchRepository.save(batch);

      // Throttle to prevent DB overload
      try {
        Thread.sleep(properties.getReencryption().getThrottleMs());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // Mark batch as completed if all records processed
    if (recordsDone + recordsFailed + recordsSkipped >= batch.getRecordsTotal()) {
      batch.setStatus(BatchStatus.COMPLETED);
      batch.setCompletedAt(OffsetDateTime.now());
      batchRepository.save(batch);

      // Update key statistics
      updateKeyStatistics(batch, recordsDone);

      logger.info(
          "✅ Batch {} completed. Done: {}, Failed: {}, Skipped: {}",
          batch.getBatchId(),
          recordsDone,
          recordsFailed,
          recordsSkipped);

      // Emit audit log
      long durationMs = java.time.Duration.between(batchStartTime, OffsetDateTime.now()).toMillis();
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_COMPLETED)
              .eventAction("complete_reencryption_batch")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .reencryptionBatchId(batch.getBatchId())
                      .targetTable(batch.getTargetTable())
                      .targetColumn(batch.getTargetColumn())
                      .oldKeyId(batch.getOldKey().getKeyId())
                      .newKeyId(batch.getNewKey().getKeyId())
                      .recordsTotal(batch.getRecordsTotal())
                      .recordsDone(recordsDone)
                      .recordsFailed(recordsFailed)
                      .custom("records_skipped", recordsSkipped)
                      .progressPct(batch.getProgressPct().doubleValue())
                      .durationMs(durationMs)
                      .toJson())
              .build());
    }
  }

  /**
   * Fetch records for re-encryption.
   *
   * @param batch the batch
   * @param lastRecordId last processed record ID (for resumability)
   * @param batchSize number of records to fetch
   * @return list of records to process
   */
  private List<? extends Reencryptable> fetchRecords(
      ReencryptionBatch batch, Long lastRecordId, int batchSize) {
    String keyPrefix = "ENC:" + batch.getOldKey().getKeyId() + ":%";
    Integer lastId = lastRecordId != null ? lastRecordId.intValue() : null;

    return switch (batch.getTargetTable()) {
      case "ezkey_enrollment" ->
          fetchEnrollmentRecords(batch.getTargetColumn(), keyPrefix, lastId, batchSize);
      case "ezkey_auth_attempt" ->
          fetchAuthAttemptRecords(batch.getTargetColumn(), keyPrefix, lastId, batchSize);
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
      String column, String keyPrefix, Integer lastId, int limit) {
    return switch (column) {
      case "auth_attempt_proof_token" ->
          authAttemptRepository.findEncryptedAuthAttemptProofTokenLike(keyPrefix, lastId, limit);
      case "device_proof_token" ->
          authAttemptRepository.findEncryptedDeviceProofTokenLike(keyPrefix, lastId, limit);
      default -> List.of();
    };
  }

  /**
   * Result of re-encryption operation.
   *
   * <p>Package-private for testing purposes.
   *
   * @param reencrypted whether the record was re-encrypted
   * @param modifiedRecord the modified record (null if skipped)
   */
  record ReencryptResult(boolean reencrypted, Reencryptable modifiedRecord) {}

  /**
   * Re-encrypt a single record.
   *
   * <p>Note: This method modifies the record in memory but does NOT save it. The caller is
   * responsible for batch saving all modified records using saveAll() to optimize N+1 queries.
   *
   * @param batch the batch
   * @param record the record to re-encrypt
   * @return ReencryptResult indicating if re-encrypted and the modified record
   */
  private ReencryptResult reencryptRecord(ReencryptionBatch batch, Reencryptable record) {
    String column = batch.getTargetColumn();
    String oldKeyPrefix = "ENC:" + batch.getOldKey().getKeyId() + ":";
    String newKeyPrefix = "ENC:" + batch.getNewKey().getKeyId() + ":";

    // Get encrypted fields from record
    Map<String, String> encryptedFields = record.getEncryptedFields();
    String encryptedValue = encryptedFields.get(column);

    if (encryptedValue == null) {
      logger.debug(
          "Skipping record {} ({}): encrypted field {} is null",
          record.getEntityId(),
          record.getTableName(),
          column);
      return new ReencryptResult(false, null); // Skip - field is null
    }

    if (!encryptedValue.startsWith(oldKeyPrefix)) {
      logger.debug(
          "Skipping record {} ({}): encrypted field {} does not start with old key prefix {} (value"
              + " starts with: {})",
          record.getEntityId(),
          record.getTableName(),
          column,
          oldKeyPrefix,
          encryptedValue.length() > 20 ? encryptedValue.substring(0, 20) + "..." : encryptedValue);
      return new ReencryptResult(false, null); // Skip - not encrypted with old key
    }

    // Check if already encrypted with new key
    if (encryptedValue.startsWith(newKeyPrefix)) {
      logger.debug(
          "Skipping record {} ({}): already encrypted with new key {}",
          record.getEntityId(),
          record.getTableName(),
          batch.getNewKey().getKeyId());
      return new ReencryptResult(false, null); // Skip - already encrypted with new key
    }

    // Decrypt with old key, encrypt with new key
    String plaintext = encryptionService.decrypt(encryptedValue);
    String reencrypted = encryptionService.encrypt(plaintext);

    // Update record using interface method
    record.setEncryptedField(column, reencrypted);

    return new ReencryptResult(true, record);
  }

  /**
   * Update key statistics after batch completion.
   *
   * @param batch the completed batch
   * @param recordsReencrypted number of records re-encrypted
   */
  private void updateKeyStatistics(ReencryptionBatch batch, int recordsReencrypted) {
    EncryptionKey oldKey = batch.getOldKey();
    oldKey.setRecordsReencrypted(oldKey.getRecordsReencrypted() + recordsReencrypted);
    oldKey.setLastReencryptAt(OffsetDateTime.now());
    keyRepository.save(oldKey);
  }

  /**
   * Mark batch as failed.
   *
   * @param batch the batch
   * @param errorMessage error message
   */
  private void markBatchFailed(ReencryptionBatch batch, String errorMessage) {
    batch.setStatus(BatchStatus.FAILED);
    batch.setErrorMessage(errorMessage);
    batch.setErrorCount(batch.getErrorCount() + 1);
    batch.setRetryCount(batch.getRetryCount() + 1);
    batch.setCompletedAt(OffsetDateTime.now());
    batchRepository.save(batch);

    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.REENCRYPTION_FAILED)
            .eventAction("batch_processing_failed")
            .eventStatus(EventStatus.ERROR)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .reencryptionBatchId(batch.getBatchId())
                    .errorSummary(errorMessage)
                    .retryCount(batch.getRetryCount())
                    .toJson())
            .errorMessage(errorMessage)
            .build());
  }

  /**
   * Manually trigger full re-encryption process.
   *
   * <p>Creates batches for all old keys and processes them immediately. This method is useful for
   * testing or emergency re-encryption operations.
   *
   * <p><b>Process:</b>
   *
   * <ol>
   *   <li>Create batches for all old keys that need re-encryption
   *   <li>Process all created batches immediately
   *   <li>Return summary of batches created and processed
   * </ol>
   *
   * @return summary containing number of batches created and processed
   * @throws IllegalStateException if encryption is not available or re-encryption is disabled
   */
  @Transactional
  public ReencryptionSummary triggerFullReencryption() {
    if (!encryptionService.isEncryptionAvailable()) {
      throw new IllegalStateException("Encryption not available");
    }

    logger.info("🔄 Manual full re-encryption triggered");

    // Create batches for all old keys
    createBatchesForOldKeys();

    // Find all batches that need processing
    List<ReencryptionBatch> batchesToProcess = batchRepository.findBatchesEligibleForResume();
    batchesToProcess.addAll(batchRepository.findByStatus(BatchStatus.PENDING));

    int batchesCreated = batchesToProcess.size();
    int batchesProcessed = 0;
    int batchesFailed = 0;

    // Process all batches
    for (ReencryptionBatch batch : batchesToProcess) {
      try {
        processBatch(batch);
        if (batch.getStatus() == BatchStatus.COMPLETED) {
          batchesProcessed++;
        } else if (batch.getStatus() == BatchStatus.FAILED) {
          batchesFailed++;
        }
      } catch (Exception e) {
        logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
        markBatchFailed(batch, e.getMessage());
        batchesFailed++;
      }
    }

    logger.info(
        "✅ Full re-encryption completed. Created: {}, Processed: {}, Failed: {}",
        batchesCreated,
        batchesProcessed,
        batchesFailed);

    return new ReencryptionSummary(batchesCreated, batchesProcessed, batchesFailed);
  }

  /**
   * Manually trigger re-encryption for a specific old key.
   *
   * <p>Creates and processes re-encryption batches for a specific old key. Useful for targeted
   * re-encryption operations.
   *
   * <p><b>Process:</b>
   *
   * <ol>
   *   <li>Validate that the key exists and is not PRIMARY
   *   <li>Create batches for all tables/columns encrypted with this key
   *   <li>Process all created batches immediately
   *   <li>Return summary of batches created and processed
   * </ol>
   *
   * @param keyId the old key ID to re-encrypt
   * @return summary containing number of batches created and processed for this key
   * @throws IllegalArgumentException if key not found or key is PRIMARY
   * @throws IllegalStateException if encryption is not available
   */
  @Transactional
  public ReencryptionSummary triggerReencryptionForKey(Long keyId) {
    if (!encryptionService.isEncryptionAvailable()) {
      throw new IllegalStateException("Encryption not available");
    }

    EncryptionKey oldKey =
        keyRepository
            .findById(keyId)
            .orElseThrow(() -> new IllegalArgumentException("Key not found: " + keyId));

    if (oldKey.getKeyStatus() == EncryptionKey.KeyStatus.PRIMARY) {
      throw new IllegalArgumentException("Cannot re-encrypt PRIMARY key: " + keyId);
    }

    logger.info("🔄 Manual re-encryption triggered for key: {}", keyId);

    // Get current primary key from keyset Tink (source of truth for SOC2 compliance)
    EncryptionKey primaryKey = getPrimaryKeyFromKeyset();
    if (primaryKey == null) {
      throw new IllegalStateException("No PRIMARY key found");
    }

    // Discover re-encryptable targets dynamically
    List<Target> targets = discoverReencryptableTargets();

    int batchesCreated = 0;
    int batchesProcessed = 0;
    int batchesFailed = 0;

    for (Target target : targets) {
      String table = target.table();
      String column = target.column();

      // Check if active batch already exists for this specific old key
      // Note: We check by (table, column, oldKeyId) to allow multiple batches for different old
      // keys
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

      // Count records encrypted with old key
      int recordCount = countRecordsEncryptedWithKey(table, column, oldKey.getKeyId());
      if (recordCount == 0) {
        logger.debug(
            "No records found encrypted with key {} in {}.{}", oldKey.getKeyId(), table, column);
        continue;
      }

      // Create batch
      ReencryptionBatch batch =
          new ReencryptionBatch(table, column, oldKey, primaryKey, recordCount, "ADMIN_MANUAL");
      batchRepository.save(batch);
      batchesCreated++;

      logger.info(
          "Created re-encryption batch {} for {}.{} (old key: {}, new key: {}, records: {})",
          batch.getBatchId(),
          table,
          column,
          oldKey.getKeyId(),
          primaryKey.getKeyId(),
          recordCount);

      // Emit audit log
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

      // Process batch immediately
      try {
        processBatch(batch);
        if (batch.getStatus() == BatchStatus.COMPLETED) {
          batchesProcessed++;
        } else if (batch.getStatus() == BatchStatus.FAILED) {
          batchesFailed++;
        }
      } catch (Exception e) {
        logger.error("Failed to process batch {}: {}", batch.getBatchId(), e.getMessage(), e);
        markBatchFailed(batch, e.getMessage());
        batchesFailed++;
      }
    }

    logger.info(
        "✅ Re-encryption for key {} completed. Created: {}, Processed: {}, Failed: {}",
        keyId,
        batchesCreated,
        batchesProcessed,
        batchesFailed);

    return new ReencryptionSummary(batchesCreated, batchesProcessed, batchesFailed);
  }

  /**
   * Discovers all re-encryptable targets dynamically from entities implementing Reencryptable.
   *
   * <p>This method creates sample instances of each known Reencryptable entity type, initializes
   * their encrypted fields with placeholder values, and queries their getEncryptedFields() method
   * to discover all encrypted columns. This eliminates the need to manually maintain a list of
   * tables/columns when new encrypted fields are added.
   *
   * <p><b>Benefits:</b>
   *
   * <ul>
   *   <li>Single source of truth: getEncryptedFields() defines what needs re-encryption
   *   <li>Automatic discovery: New encrypted fields are automatically included
   *   <li>No duplication: No need to maintain lists in multiple places
   *   <li>Type-safe: Uses the Reencryptable interface contract
   * </ul>
   *
   * @return list of discovered targets (table, column pairs)
   */
  private List<Target> discoverReencryptableTargets() {
    Set<Target> targets = new LinkedHashSet<>();

    // Create sample instances of each known Reencryptable entity type
    // Initialize encrypted fields with placeholder values so getEncryptedFields() returns them
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
   * Gets the current PRIMARY key from the Tink keyset (source of truth) and validates consistency
   * with database.
   *
   * <p>This method ensures SOC2 compliance by using the keyset Tink as the authoritative source for
   * the PRIMARY key, while validating that the database is synchronized. Any inconsistencies are
   * logged for audit purposes.
   *
   * <p><b>80-20 Principle:</b> Simple validation without complex synchronization logic. If
   * inconsistency is detected, it's logged but the operation continues using the keyset value
   * (which is what EncryptionService.encrypt() will use anyway).
   *
   * @return EncryptionKey entity from database, or null if keyset not initialized or key not found
   *     in database
   */
  private EncryptionKey getPrimaryKeyFromKeyset() {
    if (!keyManager.isInitialized()) {
      logger.warn("Tink keyset not initialized, cannot determine PRIMARY key");
      return null;
    }

    // Get PRIMARY key ID from keyset Tink (source of truth)
    long primaryKeyId = keyManager.getCurrentPrimaryKeyId();

    // Get corresponding entity from database
    EncryptionKey primaryKey = keyRepository.findById(primaryKeyId).orElse(null);
    if (primaryKey == null) {
      logger.warn(
          "PRIMARY key {} from keyset not found in database. Database may need synchronization.",
          Long.toUnsignedString(primaryKeyId));
      return null;
    }

    // Validate consistency (SOC2 audit requirement)
    if (primaryKey.getKeyStatus() != EncryptionKey.KeyStatus.PRIMARY) {
      logger.warn(
          "⚠️ SOC2 AUDIT: PRIMARY key mismatch detected! "
              + "Keyset PRIMARY: {} (unsigned: {}), "
              + "Database status: {}. "
              + "Database may need synchronization. Using keyset value (source of truth).",
          primaryKeyId,
          Long.toUnsignedString(primaryKeyId),
          primaryKey.getKeyStatus());

      // Log to audit trail for SOC2 compliance
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
   * Represents a re-encryption target (table and column pair).
   *
   * @param table database table name
   * @param column database column name
   */
  private record Target(String table, String column) {}

  /**
   * Summary of re-encryption operation.
   *
   * @param batchesCreated number of batches created
   * @param batchesProcessed number of batches successfully processed
   * @param batchesFailed number of batches that failed
   */
  public record ReencryptionSummary(int batchesCreated, int batchesProcessed, int batchesFailed) {}
}
