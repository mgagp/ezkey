/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: KeyRotationService
 * Description: Service for scheduled encryption key rotation and lifecycle management.
 */

package org.ezkey.security;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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
import org.ezkey.security.exception.PendingEncryptionKeyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class KeyRotationService {

  private static final Logger logger = LoggerFactory.getLogger(KeyRotationService.class);

  private final KeyManagementOperations keyManagementOperations;
  private final EncryptionKeyRepository keyRepository;
  private final TinkProperties properties;
  private final AuditLogService auditLogService;
  private final EncryptionKeyMigrationScopeService migrationScopeService;
  private final KeyUsageVerificationService keyUsageVerificationService;

  public KeyRotationService(
      KeyManagementOperations keyManagementOperations,
      EncryptionKeyRepository keyRepository,
      TinkProperties properties,
      AuditLogService auditLogService,
      EncryptionKeyMigrationScopeService migrationScopeService,
      KeyUsageVerificationService keyUsageVerificationService) {
    this.keyManagementOperations = keyManagementOperations;
    this.keyRepository = keyRepository;
    this.properties = properties;
    this.auditLogService = auditLogService;
    this.migrationScopeService = migrationScopeService;
    this.keyUsageVerificationService = keyUsageVerificationService;
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
   * necessary for the system to function correctly. Constructor wiring ensures the key management
   * dependency is initialized before this method runs.
   *
   * <p><b>Transaction boundary:</b> {@code @Transactional} is on this public {@link
   * ApplicationReadyEvent} entry so the empty-table sync runs in one Spring-managed transaction.
   * {@code @PostConstruct} would run on the raw target and would not apply the annotation.
   *
   * <p><b>Startup order:</b> {@code @Order} is {@link ApplicationReadyStartupOrder#KEYSET_SYNC} so
   * this runs before Admin API MFA bootstrap. Enrollment insert writes encryption-key foreign keys;
   * those rows must exist first.
   *
   * <p><b>Writer gate:</b> only the Admin API process ({@code ezkey.encryption.keyset.writer=true})
   * materializes {@code ezkey_encryption_key} rows. Peripherals return immediately so a SELECT-only
   * role cannot mark this transactional listener rollback-only (ADR-0012).
   */
  @EventListener(ApplicationReadyEvent.class)
  @Order(ApplicationReadyStartupOrder.KEYSET_SYNC)
  @Transactional
  public void initializeKeysetSync() {
    // Check if Tink encryption is enabled (not rotation, but encryption itself)
    if (!properties.isEnabled()) {
      logger.debug("Tink encryption is disabled, skipping keyset synchronization");
      return;
    }

    if (!properties.getKeyset().isWriter()) {
      logger.debug("Skipping encryption-key metadata sync; this process is not the keyset writer");
      return;
    }

    // Wait for key management to be initialized before syncing state.
    if (!keyManagementOperations.isInitialized()) {
      logger.warn(
          "⚠️  Tink encryption not initialized yet. This may indicate a timing issue. "
              + "Keyset synchronization will be skipped. "
              + "If this persists, check key management initialization logs.");
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
    } catch (DataAccessException | IllegalStateException e) {
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
   * Scheduled job to check and promote PENDING keys that are ready.
   *
   * <p>Runs at a fixed rate configured by promotion-check-interval-seconds (default: 5 seconds).
   * This job finds PENDING keys whose effective_at timestamp has passed and promotes them to
   * PRIMARY status.
   *
   * <p><b>Distributed Synchronization:</b> This ensures all instances see the key as PRIMARY at
   * approximately the same time, after the synchronization window has expired.
   *
   * <p><b>HA Safety:</b> Uses distributed locking to ensure only one instance executes this job at
   * a time.
   *
   * <p><b>Transaction boundary:</b> this scheduled method is the Spring transaction entry. It calls
   * {@link #promotePendingToPrimary(EncryptionKey)} on {@code this}, so that method's annotation
   * would not apply (self-invocation).
   */
  @Scheduled(fixedRateString = "${ezkey.encryption.rotation.promotion-check-interval-seconds:5}000")
  @SchedulerLock(name = "KEY_PROMOTION", lockAtMostFor = "PT1M", lockAtLeastFor = "PT4S")
  @Transactional
  public void checkAndPromotePendingKeys() {
    if (!properties.isEnabled()) {
      return; // Encryption disabled
    }

    if (!keyManagementOperations.isInitialized()) {
      return; // Not initialized
    }

    try {
      List<EncryptionKey> pendingKeys =
          keyRepository.findPendingKeysReadyForPromotion(OffsetDateTime.now());

      if (pendingKeys.isEmpty()) {
        return; // No pending keys ready
      }

      // Should only be one, but handle multiple defensively
      for (EncryptionKey pendingKey : pendingKeys) {
        promotePendingToPrimary(pendingKey);
      }
    } catch (DataAccessException | IllegalStateException e) {
      logger.error("Failed to check/promote pending keys: {}", e.getMessage());
      logger.debug("Pending key promotion error", e);
    }
  }

  /**
   * Promote a PENDING key to PRIMARY status.
   *
   * <p>This is called by the scheduled promotion job when a PENDING key's effective_at timestamp
   * has passed. The promotion:
   *
   * <ol>
   *   <li>Promotes the key to PRIMARY in the Tink keyset (this is the critical step!)
   *   <li>Demotes current PRIMARY key(s) to ENABLED in database
   *   <li>Promotes PENDING key to PRIMARY in database
   *   <li>Emits audit log events
   * </ol>
   *
   * <p><b>CRITICAL:</b> The Tink keyset promotion must happen FIRST. This ensures:
   *
   * <ul>
   *   <li>The keyset file/database blob is updated with the new PRIMARY key
   *   <li>All instances that sync the keyset will use the new key for encryption
   *   <li>Database metadata stays synchronized with actual Tink state
   * </ul>
   *
   * <p><b>Transaction boundary:</b> not a Spring transaction entry. Joins the caller: {@link
   * #checkAndPromotePendingKeys()} (scheduled) or an outer test transaction. A
   * {@code @Transactional} here would not apply on the scheduled {@code this} path.
   *
   * @param pendingKey the PENDING key to promote
   */
  public void promotePendingToPrimary(EncryptionKey pendingKey) {
    logger.info(
        "🔄 Promoting PENDING key {} (unsigned: {}) to PRIMARY...",
        pendingKey.getKeyId(),
        Long.toUnsignedString(pendingKey.getKeyId()));

    // STEP 1: Promote in Tink keyset FIRST (this is the critical change!)
    // This updates the keyset file and database blob so all instances will use the new key
    Long oldPrimaryKeyId = null;
    try {
      oldPrimaryKeyId = keyManagementOperations.promoteToPrimary(pendingKey.getKeyId());
      logger.info(
          "✅ Tink keyset updated: key {} is now PRIMARY (old primary was {})",
          Long.toUnsignedString(pendingKey.getKeyId()),
          oldPrimaryKeyId != null ? Long.toUnsignedString(oldPrimaryKeyId) : "none");
    } catch (GeneralSecurityException | IOException e) {
      logger.error(
          "❌ Failed to promote key {} in Tink keyset: {}",
          Long.toUnsignedString(pendingKey.getKeyId()),
          e.getMessage());
      // Emit failure audit event
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.KEY_PROMOTED_PRIMARY)
              .eventAction("promote_pending_to_primary")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .encryptionKeyId(pendingKey.getKeyId())
                      .errorSummary("Tink keyset promotion failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
      throw new RuntimeException("Failed to promote key in Tink keyset", e);
    }

    // STEP 2: Demote all current PRIMARY keys to ENABLED in database
    List<EncryptionKey> currentPrimaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    for (EncryptionKey primaryKey : currentPrimaryKeys) {
      if (!primaryKey.getKeyId().equals(pendingKey.getKeyId())) {
        logger.debug(
            "Demoting current PRIMARY key {} to ENABLED in database",
            Long.toUnsignedString(primaryKey.getKeyId()));
        primaryKey.setKeyStatus(KeyStatus.ENABLED);
        primaryKey.setPromotedPrimaryAt(null);
        migrationScopeService.applyDemotionBaseline(primaryKey);
        keyRepository.save(primaryKey);
      }
    }

    // STEP 3: Promote PENDING key to PRIMARY in database
    pendingKey.setKeyStatus(KeyStatus.PRIMARY);
    pendingKey.setPromotedPrimaryAt(OffsetDateTime.now());
    pendingKey.setEffectiveAt(null); // Clear effective_at after promotion
    keyRepository.save(pendingKey);

    // STEP 4: Emit audit events
    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.KEY_PROMOTED_PRIMARY)
            .eventAction("promote_pending_to_primary")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .encryptionKeyId(pendingKey.getKeyId())
                    .previousPrimaryKeyId(oldPrimaryKeyId)
                    .custom("promotion_reason", "sync_window_expired")
                    .custom("tink_keyset_updated", true)
                    .toJson())
            .build());

    if (oldPrimaryKeyId != null) {
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.KEY_DEMOTED)
              .eventAction("demote_for_pending_promotion")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .encryptionKeyId(oldPrimaryKeyId)
                      .custom("reason", "PENDING key promoted to PRIMARY")
                      .toJson())
              .build());
    }

    logger.info(
        "✅ PENDING key {} (unsigned: {}) promoted to PRIMARY. "
            + "Tink keyset and database are now synchronized.",
        pendingKey.getKeyId(),
        Long.toUnsignedString(pendingKey.getKeyId()));
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
   *   <li>Performs rotation if needed (creates PENDING key)
   *   <li>Cleans up old keys if configured
   * </ul>
   *
   * <p><b>HA Safety:</b> Uses distributed locking to ensure only one instance executes this job at
   * a time.
   *
   * <p><b>Transaction boundary:</b> this scheduled method is the Spring transaction entry. It calls
   * {@link #introduceNewKey(String)} and {@link #cleanupOldKeys()} on {@code this}, so those
   * methods' annotations would not apply (self-invocation).
   */
  @Scheduled(cron = "${ezkey.encryption.rotation.schedule:0 0 2 * * ?}")
  @SchedulerLock(name = "KEY_ROTATION", lockAtMostFor = "PT10M", lockAtLeastFor = "PT0S")
  @Transactional
  public void checkAndRotate() {
    if (!properties.getRotation().isEnabled()) {
      logger.debug("Key rotation is disabled via configuration");
      return;
    }

    if (!keyManagementOperations.isInitialized()) {
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
    } catch (PendingEncryptionKeyExistsException e) {
      logger.info("Rotation skipped: {}", e.getMessage());
    } catch (GeneralSecurityException
        | IOException
        | DataAccessException
        | IllegalStateException e) {
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
   * <p><b>Defensive Measures:</b>
   *
   * <ul>
   *   <li>Detects and corrects multiple PRIMARY keys before checking rotation
   *   <li>Uses keyset primary key as source of truth if database inconsistency detected
   * </ul>
   *
   * @return true if rotation is due, false otherwise
   */
  public boolean isRotationDue() {
    if (!keyManagementOperations.isInitialized()) {
      return false;
    }

    // DEFENSIVE: Check for multiple PRIMARY keys and correct if needed
    List<EncryptionKey> primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (primaryKeys.size() > 1) {
      logger.warn(
          "⚠️  Found {} PRIMARY keys in database (expected 1). Correcting inconsistency...",
          primaryKeys.size());
      long keysetPrimaryKeyId = keyManagementOperations.getCurrentPrimaryKeyId();
      ensureSinglePrimaryKey(keysetPrimaryKeyId, "ROTATION_CHECK_CORRECTION");
      // Re-fetch after correction
      primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    }

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
   * Introduce a new encryption key with PENDING status.
   *
   * <p>This method performs the actual rotation with distributed synchronization:
   *
   * <ol>
   *   <li>Corrects multiple PRIMARY keys if detected (defensive measure)
   *   <li>Creates backup if configured
   *   <li>Rotates keyset using the configured key management implementation
   *   <li>Creates key record with PENDING status and effective_at timestamp
   *   <li>Emits audit log events
   * </ol>
   *
   * <p>The new key remains PENDING until effective_at timestamp passes, then a scheduled job
   * promotes it to PRIMARY. This allows all instances to synchronize before key activation.
   *
   * <p><b>Defensive Measures:</b>
   *
   * <ul>
   *   <li>Detects and corrects multiple PRIMARY keys before rotation
   *   <li>Uses keyset primary key as source of truth
   *   <li>Checks for existing PENDING keys before creating new one
   * </ul>
   *
   * <p><b>Transaction boundary:</b> this public overload is the Admin API proxy entry. It delegates
   * to {@link #introduceNewKey(String, boolean)} on {@code this}, so the two-arg annotation does
   * not apply on this path. When {@link #checkAndRotate()} calls this overload on {@code this},
   * both annotations are unused and the scheduler transaction is shared.
   *
   * @param createdBy identifier of who/what triggered the rotation (SYSTEM or admin username)
   * @return the new key ID (will be PRIMARY after sync window expires)
   * @throws PendingEncryptionKeyExistsException if a PENDING key already exists (default workflow)
   * @throws Exception if rotation fails
   */
  @Transactional
  public long introduceNewKey(String createdBy) throws GeneralSecurityException, IOException {
    return introduceNewKey(createdBy, false);
  }

  /**
   * Introduce a new encryption key with optional immediate promotion.
   *
   * @param createdBy identifier of who/what triggered the rotation
   * @param immediatePromotion if true, promotes to PRIMARY immediately (skip sync window)
   * @return the new key ID
   * @throws PendingEncryptionKeyExistsException if {@code immediatePromotion} is false and a
   *     PENDING key already exists
   * @throws Exception if rotation fails
   */
  @Transactional
  public long introduceNewKey(String createdBy, boolean immediatePromotion)
      throws GeneralSecurityException, IOException {
    logger.info(
        "🔄 Introducing new encryption key (triggered by: {}, immediate: {})",
        createdBy,
        immediatePromotion);

    // Check if there's already a PENDING key
    if (!immediatePromotion && keyRepository.existsPendingKey()) {
      List<EncryptionKey> pendingKeys = keyRepository.findAllPendingKeys();
      logger.warn(
          "⚠️  A PENDING key already exists (key ID: {}). Cannot introduce another key until "
              + "the pending key is promoted. Use immediate promotion or wait for sync window.",
          pendingKeys.isEmpty() ? "unknown" : Long.toUnsignedString(pendingKeys.get(0).getKeyId()));
      Long pendingKeyId = pendingKeys.isEmpty() ? null : pendingKeys.get(0).getKeyId();
      throw new PendingEncryptionKeyExistsException(
          pendingKeyId,
          "A PENDING key already exists. Wait for it to be promoted or use immediate promotion.");
    }

    // DEFENSIVE: Check for multiple PRIMARY keys and correct if needed BEFORE rotation
    List<EncryptionKey> primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    Long oldPrimaryKeyId = null;
    if (primaryKeys.size() > 1) {
      logger.warn(
          "⚠️  Found {} PRIMARY keys before rotation (expected 1). Correcting inconsistency...",
          primaryKeys.size());
      long currentKeysetPrimaryId = keyManagementOperations.getCurrentPrimaryKeyId();
      ensureSinglePrimaryKey(currentKeysetPrimaryId, "PRE_ROTATION_CORRECTION");
      // Re-fetch after correction
      primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    }
    if (!primaryKeys.isEmpty()) {
      oldPrimaryKeyId = primaryKeys.get(0).getKeyId();
    }

    // Create backup if configured
    if (properties.getRotation().isBackupBeforeRotation()) {
      createBackup("rotation");
    }

    long newKeyId;

    if (immediatePromotion) {
      // Immediate promotion: use rotateKey() which adds AND promotes in one step
      newKeyId = keyManagementOperations.rotateKey();

      // Sync keyset metadata to database with PRIMARY status
      syncKeyMetadataToDatabase(newKeyId, oldPrimaryKeyId, createdBy);
      emitRotationAuditEvents(newKeyId, oldPrimaryKeyId, createdBy);
      logger.info(
          "✅ New key introduced and promoted immediately. Primary key ID: {} (unsigned: {})",
          newKeyId,
          Long.toUnsignedString(newKeyId));
    } else {
      // PENDING workflow: add key WITHOUT promotion
      // This ensures all instances have the key before it becomes active
      newKeyId = keyManagementOperations.addKeyWithoutPromotion();

      // Create PENDING record with sync window
      int syncWindowSeconds = properties.getRotation().getSyncWindowSeconds();
      OffsetDateTime effectiveAt = OffsetDateTime.now().plusSeconds(syncWindowSeconds);
      syncKeyMetadataToDatabaseAsPending(newKeyId, oldPrimaryKeyId, createdBy, effectiveAt);
      emitPendingKeyAuditEvents(newKeyId, oldPrimaryKeyId, createdBy, effectiveAt);
      logger.info(
          "✅ New PENDING key introduced (NOT yet active for encryption). "
              + "Key ID: {} (unsigned: {}), will become PRIMARY at: {} (in {} seconds). "
              + "Current primary {} remains active until promotion.",
          newKeyId,
          Long.toUnsignedString(newKeyId),
          effectiveAt,
          syncWindowSeconds,
          oldPrimaryKeyId != null ? Long.toUnsignedString(oldPrimaryKeyId) : "none");
    }

    return newKeyId;
  }

  /**
   * Synchronize all keys from keyset to database (initial sync scenario).
   *
   * <p>This method is used when the keyset exists but the database table is empty. It creates
   * records for all keys in the keyset, marking the primary key as PRIMARY and others as ENABLED.
   *
   * <p><b>Defensive Measures:</b>
   *
   * <ul>
   *   <li>Demotes ALL existing PRIMARY keys before synchronization (prevents multiple PRIMARY)
   *   <li>Verifies exactly one PRIMARY key exists after sync
   *   <li>Logs warnings/errors if data consistency issues are detected
   * </ul>
   *
   * <p><b>Note:</b> Since we don't know the actual introduction dates, we use the current timestamp
   * and add a note indicating this is a retroactive sync.
   *
   * @param createdBy identifier of who/what triggered the sync (e.g., "STARTUP_SYNC")
   */
  private void syncAllKeysFromKeyset(String createdBy) {
    logger.info("🔄 Starting synchronization of keyset to database (triggered by: {})", createdBy);

    long primaryKeyId = keyManagementOperations.getCurrentPrimaryKeyId();
    logger.info(
        "Primary key ID from keyset: {} (unsigned: {})",
        primaryKeyId,
        Long.toUnsignedString(primaryKeyId));

    String algorithm = properties.getAlgorithm();
    OffsetDateTime now = OffsetDateTime.now();

    // DEFENSIVE: Demote ALL existing PRIMARY keys before sync (prevents multiple PRIMARY)
    List<EncryptionKey> existingPrimaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (!existingPrimaryKeys.isEmpty()) {
      logger.warn(
          "Found {} existing PRIMARY key(s) before sync. Demoting all except keyset primary {}",
          existingPrimaryKeys.size(),
          Long.toUnsignedString(primaryKeyId));
      for (EncryptionKey existingPrimary : existingPrimaryKeys) {
        if (!existingPrimary.getKeyId().equals(primaryKeyId)) {
          logger.debug(
              "Demoting existing PRIMARY key {} to ENABLED before sync",
              Long.toUnsignedString(existingPrimary.getKeyId()));
          existingPrimary.setKeyStatus(KeyStatus.ENABLED);
          existingPrimary.setPromotedPrimaryAt(null);
          migrationScopeService.applyDemotionBaseline(existingPrimary);
          keyRepository.save(existingPrimary);
        }
      }
    }

    // Get all key IDs from keyset
    var allKeyIds = keyManagementOperations.getAllKeyIds();
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
        // DEFENSIVE: Ensure non-primary keys are ENABLED, not PRIMARY
        if (key.getKeyStatus() == KeyStatus.PRIMARY) {
          logger.warn(
              "Found unexpected PRIMARY key {} during sync, demoting to ENABLED",
              Long.toUnsignedString(keyId));
          key.setKeyStatus(KeyStatus.ENABLED);
          key.setPromotedPrimaryAt(null);
        } else {
          key.setKeyStatus(KeyStatus.ENABLED);
        }
        migrationScopeService.applyDemotionBaseline(key);
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

    // DEFENSIVE: Verify exactly one PRIMARY key exists after sync
    List<EncryptionKey> finalPrimaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (finalPrimaryKeys.size() != 1) {
      logger.error(
          "❌ CRITICAL: Expected exactly 1 PRIMARY key after sync, but found {}. "
              + "This is a data consistency issue!",
          finalPrimaryKeys.size());
    } else if (!finalPrimaryKeys.get(0).getKeyId().equals(primaryKeyId)) {
      logger.error(
          "❌ CRITICAL: PRIMARY key mismatch after sync! Expected {}, but found {}",
          Long.toUnsignedString(primaryKeyId),
          Long.toUnsignedString(finalPrimaryKeys.get(0).getKeyId()));
    } else {
      logger.debug(
          "✅ Verification passed: Exactly one PRIMARY key exists: {} (unsigned: {})",
          primaryKeyId,
          Long.toUnsignedString(primaryKeyId));
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
   *   <li>Ensures only ONE key has PRIMARY status (the new primary key)
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

    // FIRST: Demote ALL existing PRIMARY keys to ENABLED (safety measure)
    // This ensures we don't have multiple PRIMARY keys
    List<EncryptionKey> allPrimaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    for (EncryptionKey existingPrimaryKey : allPrimaryKeys) {
      // Don't demote the new primary key if it already exists
      if (!existingPrimaryKey.getKeyId().equals(newPrimaryKeyId)) {
        logger.debug(
            "Demoting existing PRIMARY key {} to ENABLED (new primary: {})",
            existingPrimaryKey.getKeyId(),
            newPrimaryKeyId);
        existingPrimaryKey.setKeyStatus(KeyStatus.ENABLED);
        existingPrimaryKey.setPromotedPrimaryAt(null);
        migrationScopeService.applyDemotionBaseline(existingPrimaryKey);
        keyRepository.save(existingPrimaryKey);
      }
    }

    // SECOND: Update or create record for new primary key
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
    EncryptionKey savedNewKey = keyRepository.save(newKey);
    logger.debug(
        "✅ New primary key {} (unsigned: {}) saved with status: {}",
        newPrimaryKeyId,
        Long.toUnsignedString(newPrimaryKeyId),
        savedNewKey.getKeyStatus());

    // THIRD: Update old primary key to ENABLED (if specified and different from new)
    if (oldPrimaryKeyId != null && !oldPrimaryKeyId.equals(newPrimaryKeyId)) {
      keyRepository
          .findById(oldPrimaryKeyId)
          .ifPresent(
              oldKey -> {
                if (oldKey.getKeyStatus() == KeyStatus.PRIMARY) {
                  logger.debug(
                      "Demoting old primary key {} to ENABLED",
                      Long.toUnsignedString(oldPrimaryKeyId));
                  oldKey.setKeyStatus(KeyStatus.ENABLED);
                  oldKey.setPromotedPrimaryAt(null);
                  migrationScopeService.applyDemotionBaseline(oldKey);
                  keyRepository.save(oldKey);
                }
              });
    }

    // FOURTH: Sync all other keys in keyset (ensure they are ENABLED, not PRIMARY)
    var allKeyIds = keyManagementOperations.getAllKeyIds();
    for (Long keyId : allKeyIds) {
      if (!keyId.equals(newPrimaryKeyId)) {
        keyRepository
            .findById(keyId)
            .ifPresentOrElse(
                key -> {
                  // Update existing key - ensure it's not PRIMARY
                  if (key.getKeyStatus() == KeyStatus.PRIMARY) {
                    logger.warn(
                        "Found unexpected PRIMARY key {} during sync, demoting to ENABLED",
                        Long.toUnsignedString(keyId));
                    key.setKeyStatus(KeyStatus.ENABLED);
                    key.setPromotedPrimaryAt(null);
                    migrationScopeService.applyDemotionBaseline(key);
                    keyRepository.save(key);
                  }
                },
                () -> {
                  // Create new record for key not in database
                  EncryptionKey key =
                      new EncryptionKey(keyId, KeyStatus.ENABLED, algorithm, now, createdBy);
                  migrationScopeService.applyDemotionBaseline(key);
                  keyRepository.save(key);
                });
      }
    }

    // VERIFY: Ensure only one PRIMARY key exists
    List<EncryptionKey> finalPrimaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (finalPrimaryKeys.size() != 1) {
      logger.error(
          "❌ CRITICAL: Expected exactly 1 PRIMARY key, but found {}. This is a data consistency"
              + " issue!",
          finalPrimaryKeys.size());
    } else if (!finalPrimaryKeys.get(0).getKeyId().equals(newPrimaryKeyId)) {
      logger.error(
          "❌ CRITICAL: PRIMARY key mismatch! Expected {}, but found {}",
          Long.toUnsignedString(newPrimaryKeyId),
          Long.toUnsignedString(finalPrimaryKeys.get(0).getKeyId()));
    } else {
      logger.debug(
          "✅ Verification passed: Exactly one PRIMARY key exists: {} (unsigned: {})",
          newPrimaryKeyId,
          Long.toUnsignedString(newPrimaryKeyId));
    }

    logger.debug("Keyset metadata synced to database");
  }

  /**
   * Sync keyset metadata to database with PENDING status.
   *
   * <p>Used during distributed rotation: the new key is set to PENDING with an effective_at
   * timestamp. It will be promoted to PRIMARY by the scheduled promotion job after the sync window
   * expires.
   *
   * @param newKeyId the new key ID
   * @param oldPrimaryKeyId the old primary key ID (nullable)
   * @param createdBy who created the new key
   * @param effectiveAt when the key should become PRIMARY
   */
  private void syncKeyMetadataToDatabaseAsPending(
      Long newKeyId, Long oldPrimaryKeyId, String createdBy, OffsetDateTime effectiveAt) {
    logger.debug("Syncing new key {} as PENDING (effective at: {})...", newKeyId, effectiveAt);

    String algorithm = properties.getAlgorithm();
    OffsetDateTime now = OffsetDateTime.now();

    // Create or update record for new key with PENDING status
    EncryptionKey newKey =
        keyRepository
            .findById(newKeyId)
            .orElse(new EncryptionKey(newKeyId, KeyStatus.PENDING, algorithm, now, createdBy));
    newKey.setKeyStatus(KeyStatus.PENDING);
    newKey.setEffectiveAt(effectiveAt);
    if (newKey.getIntroducedAt() == null) {
      newKey.setIntroducedAt(now);
    }
    keyRepository.save(newKey);

    logger.debug(
        "✅ New PENDING key {} (unsigned: {}) saved, effective at: {}",
        newKeyId,
        Long.toUnsignedString(newKeyId),
        effectiveAt);
  }

  /**
   * Emit audit log events for PENDING key introduction.
   *
   * @param newKeyId the new key ID
   * @param oldPrimaryKeyId the old primary key ID (nullable)
   * @param createdBy who triggered the rotation
   * @param effectiveAt when the key will become PRIMARY
   */
  private void emitPendingKeyAuditEvents(
      Long newKeyId, Long oldPrimaryKeyId, String createdBy, OffsetDateTime effectiveAt) {
    auditLogService.log(
        AuditLog.builder()
            .eventType(EventType.KEY_INTRODUCED)
            .eventAction("introduce_pending_encryption_key")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("127.0.0.1")
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .encryptionKeyId(newKeyId)
                    .algorithm(properties.getAlgorithm())
                    .triggeredBy(createdBy)
                    .custom("status", "PENDING")
                    .custom("effective_at", effectiveAt.toString())
                    .custom("sync_window_seconds", properties.getRotation().getSyncWindowSeconds())
                    .toJson())
            .build());
  }

  /**
   * Create a backup of the keyset file.
   *
   * <p>Backup is created by the configured key management implementation, which handles timestamped
   * naming and cleanup of old backups.
   *
   * @param reason reason for backup (e.g., "rotation", "manual")
   */
  private void createBackup(String reason) {
    try {
      // Backup is handled by the key management implementation during keyset save.
      // This method is here for future extensibility if needed
      logger.debug("Backup will be created during keyset save (reason: {})", reason);
    } catch (IllegalStateException e) {
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
   *   <li>Verified drained: no ciphertext rows remain for this key on tracked targets and no
   *       incomplete re-encryption batches (same rules as {@link KeyUsageVerificationService}
   *       {@code DRAINED})
   * </ul>
   *
   * <p><b>Defensive Measures:</b>
   *
   * <ul>
   *   <li>Double-checks that key is not PRIMARY before disabling (safety net)
   *   <li>Logs warning if PRIMARY key is somehow in the disable list
   * </ul>
   *
   * <p><b>Transaction boundary:</b> not a Spring transaction entry. Joins {@link #checkAndRotate()}
   * when called on {@code this}. A {@code @Transactional} here would not apply on that path.
   */
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
      // DEFENSIVE: Double-check that key is not PRIMARY (safety net)
      if (key.getKeyStatus() == KeyStatus.PRIMARY) {
        logger.error(
            "❌ CRITICAL: Attempted to disable PRIMARY key {}! This should never happen. "
                + "Skipping disable operation for this key.",
            Long.toUnsignedString(key.getKeyId()));
        continue;
      }

      KeyUsageVerificationService.KeyUsageSnapshot snap =
          keyUsageVerificationService.computeSnapshot(key);
      if (!KeyUsageVerificationService.LIFECYCLE_DRAINED.equals(snap.lifecycleStage())) {
        logger.debug(
            "Skipping auto-disable for key {}: lifecycle {}, not DRAINED",
            key.getKeyId(),
            snap.lifecycleStage());
        continue;
      }

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
                      .custom("lifecycle_stage", snap.lifecycleStage())
                      .toJson())
              .build());
    }
  }

  /**
   * Get current primary encryption key from database.
   *
   * <p><b>Defensive Measures:</b>
   *
   * <ul>
   *   <li>Detects multiple PRIMARY keys and logs warning
   *   <li>Uses keyset primary key as source of truth if database inconsistency detected
   * </ul>
   *
   * @return Optional containing the primary key if found
   */
  public java.util.Optional<EncryptionKey> getCurrentPrimaryKey() {
    List<EncryptionKey> primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (primaryKeys.size() > 1) {
      logger.warn(
          "⚠️  Found {} PRIMARY keys (expected 1). Using keyset primary as source of truth...",
          primaryKeys.size());
      if (keyManagementOperations.isInitialized()) {
        long keysetPrimaryId = keyManagementOperations.getCurrentPrimaryKeyId();
        ensureSinglePrimaryKey(keysetPrimaryId, "GET_CURRENT_PRIMARY_CORRECTION");
        // Re-fetch after correction
        primaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
      }
    }
    return primaryKeys.isEmpty()
        ? java.util.Optional.empty()
        : java.util.Optional.of(primaryKeys.get(0));
  }

  /**
   * Ensures exactly one PRIMARY key exists, using the keyset primary key as source of truth.
   *
   * <p>This defensive method corrects data consistency issues where multiple PRIMARY keys exist in
   * the database. It demotes all PRIMARY keys except the one matching the keyset's primary key ID.
   *
   * <p><b>Use Cases:</b>
   *
   * <ul>
   *   <li>Correcting inconsistencies detected during rotation checks
   *   <li>Fixing multiple PRIMARY keys before critical operations
   *   <li>Recovery from data corruption or manual database modifications
   * </ul>
   *
   * @param correctPrimaryKeyId the key ID that should be PRIMARY (from keyset)
   * @param reason reason for correction (for logging and audit)
   */
  private void ensureSinglePrimaryKey(long correctPrimaryKeyId, String reason) {
    List<EncryptionKey> allPrimaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (allPrimaryKeys.size() <= 1) {
      // No correction needed
      return;
    }

    logger.warn(
        "Correcting {} PRIMARY keys to ensure single PRIMARY key {} (reason: {})",
        allPrimaryKeys.size(),
        Long.toUnsignedString(correctPrimaryKeyId),
        reason);

    int demotedCount = 0;
    for (EncryptionKey primaryKey : allPrimaryKeys) {
      if (!primaryKey.getKeyId().equals(correctPrimaryKeyId)) {
        logger.info(
            "Demoting PRIMARY key {} to ENABLED (correct primary: {})",
            Long.toUnsignedString(primaryKey.getKeyId()),
            Long.toUnsignedString(correctPrimaryKeyId));
        primaryKey.setKeyStatus(KeyStatus.ENABLED);
        primaryKey.setPromotedPrimaryAt(null);
        migrationScopeService.applyDemotionBaseline(primaryKey);
        keyRepository.save(primaryKey);
        demotedCount++;
      }
    }

    // Ensure correct key is PRIMARY
    keyRepository
        .findById(correctPrimaryKeyId)
        .ifPresentOrElse(
            key -> {
              if (key.getKeyStatus() != KeyStatus.PRIMARY) {
                logger.info(
                    "Promoting key {} to PRIMARY (reason: {})",
                    Long.toUnsignedString(correctPrimaryKeyId),
                    reason);
                key.setKeyStatus(KeyStatus.PRIMARY);
                if (key.getPromotedPrimaryAt() == null) {
                  key.setPromotedPrimaryAt(OffsetDateTime.now());
                }
                keyRepository.save(key);
              }
            },
            () -> {
              logger.warn(
                  "Correct primary key {} not found in database. Creating new record...",
                  Long.toUnsignedString(correctPrimaryKeyId));
              EncryptionKey newKey =
                  new EncryptionKey(
                      correctPrimaryKeyId,
                      KeyStatus.PRIMARY,
                      properties.getAlgorithm(),
                      OffsetDateTime.now(),
                      reason);
              newKey.setPromotedPrimaryAt(OffsetDateTime.now());
              keyRepository.save(newKey);
            });

    // Verify correction
    List<EncryptionKey> finalPrimaryKeys = keyRepository.findByKeyStatus(KeyStatus.PRIMARY);
    if (finalPrimaryKeys.size() == 1
        && finalPrimaryKeys.get(0).getKeyId().equals(correctPrimaryKeyId)) {
      logger.info(
          "✅ Correction successful: Exactly one PRIMARY key exists: {} (unsigned: {}, demoted: {})",
          correctPrimaryKeyId,
          Long.toUnsignedString(correctPrimaryKeyId),
          demotedCount);
    } else {
      logger.error(
          "❌ CRITICAL: Correction failed! Expected 1 PRIMARY key {}, but found {}",
          Long.toUnsignedString(correctPrimaryKeyId),
          finalPrimaryKeys.size());
    }
  }
}
