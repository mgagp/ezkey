/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: KeyRotationService
 * Description: Service for scheduled encryption key rotation and lifecycle management.
 */

package org.ezkey.security;

import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for scheduled encryption key rotation and lifecycle management.
 *
 * <p>This service handles:
 *
 * <ul>
 *   <li>Scheduled key rotation based on max-key-age-days configuration
 *   <li>Key introduction and promotion to PRIMARY status
 *   <li>Database synchronization of keyset metadata
 *   <li>Backup creation before rotation
 *   <li>Old key cleanup and disablement
 * </ul>
 *
 * <p><b>Rotation Algorithm:</b>
 *
 * <ol>
 *   <li>Check if rotation is enabled and due (primary key age >= max-key-age-days)
 *   <li>Create backup of keyset file
 *   <li>Generate new key using Tink KeysetManager
 *   <li>Promote new key to PRIMARY, demote old to ENABLED
 *   <li>Save encrypted keyset to disk
 *   <li>Sync key metadata to database (ezkey_encryption_key table)
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
@DependsOn("tinkKeyManager")
public class KeyRotationService {

  private static final Logger logger = LoggerFactory.getLogger(KeyRotationService.class);

  private final TinkKeyManager keyManager;
  private final EncryptionKeyRepository keyRepository;
  private final TinkProperties properties;
  private final AuditLogService auditLogService;

  public KeyRotationService(
      TinkKeyManager keyManager,
      EncryptionKeyRepository keyRepository,
      TinkProperties properties,
      AuditLogService auditLogService) {
    this.keyManager = keyManager;
    this.keyRepository = keyRepository;
    this.properties = properties;
    this.auditLogService = auditLogService;
  }

  /**
   * Initialize and synchronize keyset state with database on startup.
   *
   * <p>This method detects if a keyset exists but the database table is empty (e.g., after
   * migration or database reset) and automatically synchronizes the keyset metadata to the
   * database. This ensures that key rotation can function correctly even if the database was reset
   * while the keyset file persisted.
   *
   * <p><b>Detection Logic:</b>
   *
   * <ul>
   *   <li>If Tink encryption is enabled and initialized (keyset exists and loaded)
   *   <li>AND database table is empty (no keys found)
   *   <li>THEN synchronize all keys from keyset to database
   * </ul>
   *
   * <p><b>Note:</b> This synchronization runs independently of rotation.enabled setting, as it is
   * necessary for the system to function correctly. The @DependsOn("tinkKeyManager") annotation
   * ensures TinkKeyManager is fully initialized before this method runs.
   */
  @PostConstruct
  @Transactional
  public void initializeKeysetSync() {
    // Check if Tink encryption is enabled (not rotation, but encryption itself)
    if (!properties.isEnabled()) {
      logger.debug("Tink encryption is disabled, skipping keyset synchronization");
      return;
    }

    // Wait for TinkKeyManager to be initialized (guaranteed by @DependsOn, but double-check)
    if (!keyManager.isInitialized()) {
      logger.warn(
          "⚠️  Tink encryption not initialized yet. This may indicate a timing issue. "
              + "Keyset synchronization will be skipped. "
              + "If this persists, check TinkKeyManager initialization logs.");
      return;
    }

    try {
      // Check if database is empty
      long keyCount = keyRepository.count();
      logger.debug("Checking keyset synchronization: database contains {} keys", keyCount);

      if (keyCount == 0) {
        logger.warn(
            "⚠️  Keyset exists and is loaded, but encryption_key table is empty. "
                + "This may occur after database migration or reset. "
                + "Synchronizing keyset metadata to database...");

        // Synchronize all keys from keyset to database
        syncAllKeysFromKeyset("STARTUP_SYNC");

        logger.info("✅ Keyset synchronized to database. Key rotation service is now operational.");
      } else {
        logger.debug("Keyset and database are in sync ({} keys found in database)", keyCount);
      }
    } catch (Exception e) {
      logger.error(
          "❌ Failed to synchronize keyset on startup. Key rotation may not work correctly. "
              + "Error: {}",
          e.getMessage(),
          e);
      // Don't throw - allow application to start even if sync fails
      // Admin can manually trigger sync via API if needed
    }
  }

  /**
   * Scheduled job to check and perform key rotation if needed.
   *
   * <p>Runs according to the cron schedule configured in ezkey.encryption.rotation.schedule
   * (default: daily at 2 AM).
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Checks if rotation is enabled
   *   <li>Verifies if rotation is due (primary key age >= max-key-age-days)
   *   <li>Performs rotation if needed
   *   <li>Cleans up old keys if configured
   * </ul>
   */
  @Scheduled(cron = "${ezkey.encryption.rotation.schedule:0 0 2 * * ?}")
  @Transactional
  public void checkAndRotate() {
    if (!properties.getRotation().isEnabled()) {
      logger.debug("Key rotation is disabled via configuration");
      return;
    }

    if (!keyManager.isInitialized()) {
      logger.warn("Tink encryption not initialized, skipping rotation check");
      return;
    }

    try {
      logger.info("🔍 Checking if key rotation is due...");

      if (isRotationDue()) {
        logger.info("✅ Rotation is due, performing key rotation...");
        introduceNewKey("SCHEDULED_JOB");
        cleanupOldKeys();
      } else {
        logger.debug("Rotation not due yet, skipping");
      }
    } catch (Exception e) {
      logger.error("Failed to check/perform key rotation", e);
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("scheduled_rotation_check")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Scheduled rotation check failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
    }
  }

  /**
   * Check if key rotation is due based on primary key age.
   *
   * @return true if rotation is due, false otherwise
   */
  public boolean isRotationDue() {
    if (!keyManager.isInitialized()) {
      return false;
    }

    // Get current primary key from database
    List<EncryptionKey> primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (primaryKeys.isEmpty()) {
      logger.warn("No PRIMARY key found in database, rotation check cannot proceed");
      return false;
    }

    EncryptionKey primaryKey = primaryKeys.get(0);
    OffsetDateTime introducedAt = primaryKey.getIntroducedAt();
    int maxAgeDays = properties.getRotation().getMaxKeyAgeDays();

    OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(maxAgeDays);
    boolean isDue = introducedAt.isBefore(cutoffDate);

    if (isDue) {
      long ageDays = java.time.Duration.between(introducedAt, OffsetDateTime.now()).toDays();
      logger.info(
          "Rotation is due: Primary key age is {} days (max: {} days)", ageDays, maxAgeDays);
    }

    return isDue;
  }

  /**
   * Introduce a new encryption key and promote it to PRIMARY.
   *
   * <p>This method performs the actual rotation:
   *
   * <ol>
   *   <li>Creates backup if configured
   *   <li>Rotates keyset using TinkKeyManager
   *   <li>Syncs key metadata to database
   *   <li>Emits audit log events
   * </ol>
   *
   * @param createdBy identifier of who/what triggered the rotation (SYSTEM or admin username)
   * @return the new primary key ID
   * @throws Exception if rotation fails
   */
  @Transactional
  public long introduceNewKey(String createdBy) throws Exception {
    logger.info("🔄 Introducing new encryption key (triggered by: {})", createdBy);

    // Get current primary key before rotation
    List<EncryptionKey> primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    Long oldPrimaryKeyId = null;
    if (!primaryKeys.isEmpty()) {
      oldPrimaryKeyId = primaryKeys.get(0).getKeyId();
    }

    // Create backup if configured
    if (properties.getRotation().isBackupBeforeRotation()) {
      createBackup("rotation");
    }

    // Perform rotation using TinkKeyManager
    long newPrimaryKeyId = keyManager.rotateKey();

    // Sync keyset metadata to database
    syncKeyMetadataToDatabase(newPrimaryKeyId, oldPrimaryKeyId, createdBy);

    // Emit audit log events
    emitRotationAuditEvents(newPrimaryKeyId, oldPrimaryKeyId, createdBy);

    logger.info(
        "✅ New key introduced successfully. New primary key ID: {} (unsigned: {})",
        newPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId));
    return newPrimaryKeyId;
  }

  /**
   * Synchronize all keys from keyset to database (initial sync scenario).
   *
   * <p>This method is used when the keyset exists but the database table is empty. It creates
   * records for all keys in the keyset, marking the primary key as PRIMARY and others as ENABLED.
   *
   * <p><b>Note:</b> Since we don't know the actual introduction dates, we use the current timestamp
   * and add a note indicating this is a retroactive sync.
   *
   * @param createdBy identifier of who/what triggered the sync (e.g., "STARTUP_SYNC")
   */
  private void syncAllKeysFromKeyset(String createdBy) {
    logger.info("🔄 Starting synchronization of keyset to database (triggered by: {})", createdBy);

    long primaryKeyId = keyManager.getCurrentPrimaryKeyId();
    logger.info(
        "Primary key ID from keyset: {} (unsigned: {})",
        primaryKeyId,
        Long.toUnsignedString(primaryKeyId));

    String algorithm = properties.getAlgorithm();
    OffsetDateTime now = OffsetDateTime.now();

    // Get all key IDs from keyset
    var allKeyIds = keyManager.getAllKeyIds();
    logger.info("Found {} keys in keyset to synchronize", allKeyIds.size());

    for (Long keyId : allKeyIds) {
      EncryptionKey key =
          keyRepository
              .findById(keyId)
              .orElse(
                  new EncryptionKey(
                      keyId,
                      keyId.equals(primaryKeyId) ? KeyStatus.PRIMARY : KeyStatus.ENABLED,
                      algorithm,
                      now,
                      createdBy));

      // Update status and timestamps
      if (keyId.equals(primaryKeyId)) {
        key.setKeyStatus(KeyStatus.PRIMARY);
        if (key.getPromotedPrimaryAt() == null) {
          key.setPromotedPrimaryAt(now);
        }
        key.setNotes(
            "Retroactive sync: Keyset existed before database tracking was enabled. "
                + "Actual introduction date unknown.");
      } else {
        key.setKeyStatus(KeyStatus.ENABLED);
      }

      if (key.getIntroducedAt() == null) {
        key.setIntroducedAt(now);
      }

      EncryptionKey savedKey = keyRepository.save(key);
      logger.info(
          "✅ Synchronized key {} (unsigned: {}, status: {}) to database",
          keyId,
          Long.toUnsignedString(keyId),
          savedKey.getKeyStatus());
    }

    // Verify synchronization by counting keys in database
    long finalKeyCount = keyRepository.count();
    logger.info(
        "✅ Synchronization complete: {} keys synchronized from keyset to database "
            + "(primary key: {} / unsigned: {}). Database now contains {} keys.",
        allKeyIds.size(),
        primaryKeyId,
        Long.toUnsignedString(primaryKeyId),
        finalKeyCount);

    // Emit audit log for synchronization
    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.KEY_INTRODUCED)
            .eventAction("startup_keyset_sync")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .encryptionKeyId(primaryKeyId)
                    .algorithm(algorithm)
                    .triggeredBy(createdBy)
                    .custom("keys_synced", allKeyIds.size())
                    .custom("sync_reason", "keyset_existed_but_database_empty")
                    .toJson())
            .build());
  }

  /**
   * Sync keyset state to database (ezkey_encryption_key table).
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Creates/updates records for all keys in the keyset
   *   <li>Sets correct status (PRIMARY, ENABLED)
   *   <li>Updates timestamps (introduced_at, promoted_primary_at)
   * </ul>
   *
   * @param newPrimaryKeyId the new primary key ID
   * @param oldPrimaryKeyId the old primary key ID (nullable)
   * @param createdBy who created the new key
   */
  private void syncKeyMetadataToDatabase(
      Long newPrimaryKeyId, Long oldPrimaryKeyId, String createdBy) {
    logger.debug("Syncing keyset metadata to database...");

    String algorithm = properties.getAlgorithm();
    OffsetDateTime now = OffsetDateTime.now();

    // Update or create record for new primary key
    EncryptionKey newKey =
        keyRepository
            .findById(newPrimaryKeyId)
            .orElse(
                new EncryptionKey(newPrimaryKeyId, KeyStatus.PRIMARY, algorithm, now, createdBy));
    newKey.setKeyStatus(KeyStatus.PRIMARY);
    newKey.setPromotedPrimaryAt(now);
    if (newKey.getIntroducedAt() == null) {
      newKey.setIntroducedAt(now);
    }
    keyRepository.save(newKey);

    // Update old primary key to ENABLED
    if (oldPrimaryKeyId != null) {
      keyRepository
          .findById(oldPrimaryKeyId)
          .ifPresent(
              oldKey -> {
                oldKey.setKeyStatus(KeyStatus.ENABLED);
                oldKey.setPromotedPrimaryAt(null); // Clear promotion timestamp
                keyRepository.save(oldKey);
              });
    }

    // Sync all other keys in keyset (mark as ENABLED if not already PRIMARY)
    var allKeyIds = keyManager.getAllKeyIds();
    for (Long keyId : allKeyIds) {
      if (!keyId.equals(newPrimaryKeyId)) {
        keyRepository
            .findById(keyId)
            .ifPresentOrElse(
                key -> {
                  // Update existing key
                  if (key.getKeyStatus() == KeyStatus.PRIMARY) {
                    key.setKeyStatus(KeyStatus.ENABLED);
                    key.setPromotedPrimaryAt(null);
                    keyRepository.save(key);
                  }
                },
                () -> {
                  // Create new record for key not in database
                  EncryptionKey key =
                      new EncryptionKey(keyId, KeyStatus.ENABLED, algorithm, now, createdBy);
                  keyRepository.save(key);
                });
      }
    }

    logger.debug("Keyset metadata synced to database");
  }

  /**
   * Create a backup of the keyset file.
   *
   * <p>Backup is created by TinkKeyManager.createBackup() which handles timestamped naming and
   * cleanup of old backups.
   *
   * @param reason reason for backup (e.g., "rotation", "manual")
   */
  private void createBackup(String reason) {
    try {
      // Backup is handled by TinkKeyManager.saveKeyset() which calls createBackup()
      // This method is here for future extensibility if needed
      logger.debug("Backup will be created during keyset save (reason: {})", reason);
    } catch (Exception e) {
      logger.warn("Failed to create backup: {}", e.getMessage());
      // Don't throw - backup failure shouldn't block rotation
    }
  }

  /**
   * Emit audit log events for key rotation.
   *
   * @param newPrimaryKeyId the new primary key ID
   * @param oldPrimaryKeyId the old primary key ID (nullable)
   * @param createdBy who triggered the rotation
   */
  private void emitRotationAuditEvents(
      Long newPrimaryKeyId, Long oldPrimaryKeyId, String createdBy) {
    // Log KEY_INTRODUCED event
    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.KEY_INTRODUCED)
            .eventAction("introduce_encryption_key")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .encryptionKeyId(newPrimaryKeyId)
                    .algorithm(properties.getAlgorithm())
                    .triggeredBy(createdBy)
                    .toJson())
            .build());

    // Log KEY_PROMOTED_PRIMARY event
    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.KEY_PROMOTED_PRIMARY)
            .eventAction("promote_key_to_primary")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .encryptionKeyId(newPrimaryKeyId)
                    .previousPrimaryKeyId(oldPrimaryKeyId)
                    .algorithm(properties.getAlgorithm())
                    .toJson())
            .build());

    // Log KEY_DEMOTED event for old primary (if exists)
    if (oldPrimaryKeyId != null) {
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.KEY_DEMOTED)
              .eventAction("demote_key_from_primary")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .encryptionKeyId(oldPrimaryKeyId)
                      .custom("reason", "Key rotation - new primary key introduced")
                      .toJson())
              .build());
    }
  }

  /**
   * Cleanup old keys that can be disabled.
   *
   * <p>Disables keys that are:
   *
   * <ul>
   *   <li>ENABLED status (not PRIMARY)
   *   <li>Older than auto-disable-days
   *   <li>All data has been re-encrypted (records_reencrypted >= records_encrypted)
   * </ul>
   */
  @Transactional
  public void cleanupOldKeys() {
    int autoDisableDays = properties.getRotation().getAutoDisableDays();
    if (autoDisableDays <= 0) {
      logger.debug(
          "Auto-disable disabled (auto-disable-days: {}), skipping cleanup", autoDisableDays);
      return;
    }

    OffsetDateTime cutoffDate = OffsetDateTime.now().minusDays(autoDisableDays);
    List<EncryptionKey> keysToDisable = keyRepository.findKeysEligibleForDisable(cutoffDate);

    for (EncryptionKey key : keysToDisable) {
      // Only disable if all data has been re-encrypted
      if (key.getRecordsReencrypted() >= key.getRecordsEncrypted()) {
        key.setKeyStatus(KeyStatus.DISABLED);
        key.setDisabledAt(OffsetDateTime.now());
        keyRepository.save(key);

        logger.info(
            "Disabled old key: {} (age: {} days)",
            key.getKeyId(),
            java.time.Duration.between(key.getIntroducedAt(), OffsetDateTime.now()).toDays());

        // Emit audit log
        auditLogService.log(
            AuditLog.builder()
                .eventType(EventType.KEY_DISABLED)
                .eventAction("disable_old_key")
                .eventStatus(EventStatus.SUCCESS)
                .apiName(ApiName.ADMIN_API)
                .ipAddress("127.0.0.1")
                .eventDetails(
                    AuditDetailsBuilder.builder()
                        .encryptionKeyId(key.getKeyId())
                        .custom("records_encrypted", key.getRecordsEncrypted())
                        .custom("records_reencrypted", key.getRecordsReencrypted())
                        .toJson())
                .build());
      }
    }
  }

  /**
   * Get current primary encryption key from database.
   *
   * @return Optional containing the primary key if found
   */
  public java.util.Optional<EncryptionKey> getCurrentPrimaryKey() {
    List<EncryptionKey> primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    return primaryKeys.isEmpty()
        ? java.util.Optional.empty()
        : java.util.Optional.of(primaryKeys.get(0));
  }
}
