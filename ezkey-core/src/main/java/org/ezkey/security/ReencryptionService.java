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
import java.util.List;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final AuthAttemptRepository authAttemptRepository;
  private final TinkProperties properties;
  private final AuditLogService auditLogService;

  public ReencryptionService(
      EncryptionService encryptionService,
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      EnrollmentRepository enrollmentRepository,
      AuthAttemptRepository authAttemptRepository,
      TinkProperties properties,
      AuditLogService auditLogService) {
    this.encryptionService = encryptionService;
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
   * that contain encrypted data.
   */
  @Transactional
  public void createBatchesForOldKeys() {
    // Get current primary key
    List<EncryptionKey> primaryKeys =
        keyRepository.findByKeyStatus(EncryptionKey.KeyStatus.PRIMARY);
    if (primaryKeys.isEmpty()) {
      logger.debug("No PRIMARY key found, skipping batch creation");
      return;
    }
    EncryptionKey primaryKey = primaryKeys.get(0);

    // Find ENABLED keys that are not primary (old keys)
    List<EncryptionKey> enabledKeys = keyRepository.findEnabledKeys();
    enabledKeys.removeIf(key -> key.getKeyStatus() == EncryptionKey.KeyStatus.PRIMARY);

    for (EncryptionKey oldKey : enabledKeys) {

      // Define tables/columns that need re-encryption
      String[][] targets = {
        {"ezkey_enrollment", "integration_private_key"},
        {"ezkey_enrollment", "enrollment_proof_token"},
        {"ezkey_auth_attempt", "auth_attempt_proof_token"},
        {"ezkey_auth_attempt", "device_proof_token"}
      };

      for (String[] target : targets) {
        String table = target[0];
        String column = target[1];

        // Check if active batch already exists
        List<ReencryptionBatch> activeBatches =
            batchRepository.findActiveBatchesByTarget(table, column);
        if (!activeBatches.isEmpty()) {
          logger.debug("Active batch already exists for {}.{}", table, column);
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
    String keyPrefix = "ENC:" + keyId + ":";
    return switch (table) {
      case "ezkey_enrollment" -> countEnrollmentRecords(column, keyPrefix);
      case "ezkey_auth_attempt" -> countAuthAttemptRecords(column, keyPrefix);
      default -> 0;
    };
  }

  private int countEnrollmentRecords(String column, String keyPrefix) {
    String fieldName =
        switch (column) {
          case "integration_private_key" -> "encryptedIntegrationPrivateKey";
          case "enrollment_proof_token" -> "encryptedEnrollmentProofToken";
          default -> null;
        };
    if (fieldName == null) {
      return 0;
    }
    return enrollmentRepository.findAll().stream()
        .mapToInt(
            e -> {
              String value = getFieldValue(e, fieldName);
              return (value != null && value.startsWith(keyPrefix)) ? 1 : 0;
            })
        .sum();
  }

  private int countAuthAttemptRecords(String column, String keyPrefix) {
    String fieldName =
        switch (column) {
          case "auth_attempt_proof_token" -> "encryptedAuthAttemptProofToken";
          case "device_proof_token" -> "encryptedDeviceProofToken";
          default -> null;
        };
    if (fieldName == null) {
      return 0;
    }
    return authAttemptRepository.findAll().stream()
        .mapToInt(
            a -> {
              String value = getFieldValue(a, fieldName);
              return (value != null && value.startsWith(keyPrefix)) ? 1 : 0;
            })
        .sum();
  }

  private String getFieldValue(Object entity, String fieldName) {
    try {
      java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      return (String) field.get(entity);
    } catch (Exception e) {
      logger.debug("Failed to read field {}: {}", fieldName, e.getMessage());
      return null;
    }
  }

  private void setFieldValue(Object entity, String fieldName, String value) {
    try {
      java.lang.reflect.Field field = entity.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(entity, value);
    } catch (Exception e) {
      logger.error("Failed to write field {}: {}", fieldName, e.getMessage());
    }
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
      List<?> records = fetchRecords(batch, lastRecordId, batchSize);
      if (records.isEmpty()) {
        break; // No more records
      }

      for (Object record : records) {
        try {
          boolean reencrypted = reencryptRecord(batch, record);
          if (reencrypted) {
            recordsDone++;
            // Update lastRecordId based on record type
            if (record instanceof Enrollment e) {
              lastRecordId = Long.valueOf(e.getEnrollmentId());
            } else if (record instanceof AuthAttempt a) {
              lastRecordId = Long.valueOf(a.getAuthAttemptId());
            }
          } else {
            recordsSkipped++;
          }
        } catch (Exception e) {
          logger.warn(
              "Failed to re-encrypt record in batch {}: {}", batch.getBatchId(), e.getMessage());
          recordsFailed++;
        }
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
  private List<?> fetchRecords(ReencryptionBatch batch, Long lastRecordId, int batchSize) {
    String keyPrefix = "ENC:" + batch.getOldKey().getKeyId() + ":";
    Pageable pageable = PageRequest.of(0, batchSize);

    return switch (batch.getTargetTable()) {
      case "ezkey_enrollment" ->
          fetchEnrollmentRecords(batch.getTargetColumn(), keyPrefix, lastRecordId, pageable);
      case "ezkey_auth_attempt" ->
          fetchAuthAttemptRecords(batch.getTargetColumn(), keyPrefix, lastRecordId, pageable);
      default -> List.of();
    };
  }

  private List<Enrollment> fetchEnrollmentRecords(
      String column, String keyPrefix, Long lastRecordId, Pageable pageable) {
    // Simple implementation - fetch all and filter in memory
    // TODO: Optimize with native SQL query using LIKE
    String fieldName =
        switch (column) {
          case "integration_private_key" -> "encryptedIntegrationPrivateKey";
          case "enrollment_proof_token" -> "encryptedEnrollmentProofToken";
          default -> null;
        };
    if (fieldName == null) {
      return List.of();
    }
    return enrollmentRepository.findAll(pageable).stream()
        .filter(
            e -> {
              String encrypted = getFieldValue(e, fieldName);
              return encrypted != null && encrypted.startsWith(keyPrefix);
            })
        .filter(e -> lastRecordId == null || e.getEnrollmentId() > lastRecordId.intValue())
        .toList();
  }

  private List<AuthAttempt> fetchAuthAttemptRecords(
      String column, String keyPrefix, Long lastRecordId, Pageable pageable) {
    // Simple implementation - fetch all and filter in memory
    // TODO: Optimize with native SQL query using LIKE
    String fieldName =
        switch (column) {
          case "auth_attempt_proof_token" -> "encryptedAuthAttemptProofToken";
          case "device_proof_token" -> "encryptedDeviceProofToken";
          default -> null;
        };
    if (fieldName == null) {
      return List.of();
    }
    return authAttemptRepository.findAll(pageable).stream()
        .filter(
            a -> {
              String encrypted = getFieldValue(a, fieldName);
              return encrypted != null && encrypted.startsWith(keyPrefix);
            })
        .filter(a -> lastRecordId == null || a.getAuthAttemptId() > lastRecordId.intValue())
        .toList();
  }

  /**
   * Re-encrypt a single record.
   *
   * @param batch the batch
   * @param record the record to re-encrypt
   * @return true if re-encrypted, false if skipped
   */
  private boolean reencryptRecord(ReencryptionBatch batch, Object record) {
    String column = batch.getTargetColumn();
    String oldKeyPrefix = "ENC:" + batch.getOldKey().getKeyId() + ":";
    String newKeyPrefix = "ENC:" + batch.getNewKey().getKeyId() + ":";

    String fieldName =
        switch (column) {
          case "integration_private_key" -> "encryptedIntegrationPrivateKey";
          case "enrollment_proof_token" -> "encryptedEnrollmentProofToken";
          case "auth_attempt_proof_token" -> "encryptedAuthAttemptProofToken";
          case "device_proof_token" -> "encryptedDeviceProofToken";
          default -> null;
        };
    if (fieldName == null) {
      return false;
    }

    String encryptedValue = getFieldValue(record, fieldName);
    if (encryptedValue == null || !encryptedValue.startsWith(oldKeyPrefix)) {
      return false; // Skip - not encrypted with old key
    }

    // Check if already encrypted with new key
    if (encryptedValue.startsWith(newKeyPrefix)) {
      return false; // Skip - already encrypted with new key
    }

    // Decrypt with old key, encrypt with new key
    String plaintext = encryptionService.decrypt(encryptedValue);
    String reencrypted = encryptionService.encrypt(plaintext);

    // Update record
    setFieldValue(record, fieldName, reencrypted);
    if (record instanceof Enrollment e) {
      enrollmentRepository.save(e);
    } else if (record instanceof AuthAttempt a) {
      authAttemptRepository.save(a);
    }

    return true;
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
}
