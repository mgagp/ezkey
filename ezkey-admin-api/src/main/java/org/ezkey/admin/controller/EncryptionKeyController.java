/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EncryptionKeyController
 * Description: REST controller for encryption key management operations.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for encryption key management operations.
 *
 * <p>This controller provides endpoints for manual encryption key operations including rotation,
 * status queries, and batch management. All operations require admin authentication and are
 * audited.
 *
 * <p><b>Available Operations:</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/encryption-keys:</b> List all encryption keys
 *   <li><b>GET /api/v1/encryption-keys/primary:</b> Get current primary key
 *   <li><b>POST /api/v1/encryption-keys/rotate:</b> Manually trigger key rotation
 *   <li><b>GET /api/v1/encryption-keys/{keyId}:</b> Get key details
 *   <li><b>GET /api/v1/encryption-keys/reencryption-batches:</b> List re-encryption batches
 *   <li><b>POST /api/v1/encryption-keys/reencryption-batches/{batchId}/resume:</b> Resume failed
 *       batch
 *   <li><b>POST /api/v1/encryption-keys/reencrypt/trigger:</b> Trigger full re-encryption
 *   <li><b>POST /api/v1/encryption-keys/{keyId}/reencrypt:</b> Trigger re-encryption for specific
 *       key
 *   <li><b>POST /api/v1/encryption-keys/reencrypt/create-batches:</b> Create re-encryption batches
 *       only
 * </ul>
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li>All endpoints require admin authentication (Bearer token)
 *   <li>Comprehensive audit logging for all operations
 *   <li>Rate limiting applied via AdminOperationsRateLimitService
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Validated
@RestController
@RequestMapping("/api/v1/encryption-keys")
@Tag(
    name = "Encryption Keys",
    description = "Encryption key lifecycle management and rotation operations")
public class EncryptionKeyController {

  private static final Logger logger = LoggerFactory.getLogger(EncryptionKeyController.class);

  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final KeyRotationService rotationService;
  private final ReencryptionService reencryptionService;
  private final AuditLogService auditLogService;

  public EncryptionKeyController(
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      KeyRotationService rotationService,
      ReencryptionService reencryptionService,
      AuditLogService auditLogService) {
    this.keyRepository = keyRepository;
    this.batchRepository = batchRepository;
    this.rotationService = rotationService;
    this.reencryptionService = reencryptionService;
    this.auditLogService = auditLogService;
  }

  /**
   * List encryption keys with server-side pagination and optional filter by status.
   *
   * @param keyStatus optional filter by key status (PRIMARY, ENABLED, DISABLED, PENDING); omit for
   *     all
   * @param pageable page, size, and sort (e.g. sort=introducedAt,DESC)
   * @return paginated list of encryption keys (content + page metadata)
   */
  @Operation(
      summary = "List encryption keys",
      description =
          "Returns encryption keys with pagination and optional keyStatus filter. Use page, size,"
              + " sort for pagination. Optional keyStatus: PRIMARY, ENABLED, DISABLED, PENDING."
              + " Sortable: keyId, keyStatus, algorithm, introducedAt, promotedPrimaryAt,"
              + " disabledAt, recordsEncrypted, recordsReencrypted, createdBy, createdAt.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paginated list of keys (content + page)"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<Page<EncryptionKeyResponse>> listKeys(
      @Parameter(
              description =
                  "Filter by key status (PRIMARY, ENABLED, DISABLED, PENDING); omit for all")
          @RequestParam(required = false)
          String keyStatus,
      @ParameterObject
          @PageableDefault(size = 20, sort = "introducedAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Specification<EncryptionKey> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (keyStatus != null && !keyStatus.isBlank()) {
            try {
              KeyStatus status = KeyStatus.valueOf(keyStatus.trim().toUpperCase());
              predicates.add(cb.equal(root.get("keyStatus"), status));
            } catch (IllegalArgumentException ignored) {
              // Invalid enum value: ignore filter and return all keys
            }
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };
    Page<EncryptionKeyResponse> page = keyRepository.findAll(spec, pageable).map(this::toResponse);
    return ResponseEntity.ok(page);
  }

  /**
   * Get current primary encryption key.
   *
   * @return the primary encryption key
   */
  @Operation(
      summary = "Get primary encryption key",
      description = "Returns the current primary encryption key used for new encryption operations")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Primary key retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "No primary key found"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/primary")
  public ResponseEntity<EncryptionKeyResponse> getPrimaryKey() {
    return rotationService
        .getCurrentPrimaryKey()
        .map(key -> ResponseEntity.ok(toResponse(key)))
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Get encryption key by ID.
   *
   * @param keyId the key ID
   * @return the encryption key
   */
  @Operation(
      summary = "Get encryption key by ID",
      description = "Returns details for a specific encryption key")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Key retrieved successfully"),
    @ApiResponse(responseCode = "404", description = "Key not found"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{keyId}")
  public ResponseEntity<EncryptionKeyResponse> getKey(@PathVariable Long keyId) {
    return keyRepository
        .findById(keyId)
        .map(key -> ResponseEntity.ok(toResponse(key)))
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Manually trigger key rotation.
   *
   * <p>This endpoint allows administrators to manually trigger key rotation outside of the
   * scheduled job. Useful for emergency rotations or testing.
   *
   * @return the new primary key ID
   */
  @Operation(
      summary = "Manually trigger key rotation",
      description = "Immediately rotates the encryption key, creating a new primary key")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Key rotation completed successfully"),
    @ApiResponse(responseCode = "500", description = "Key rotation failed")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/rotate")
  public ResponseEntity<KeyRotationResponse> rotateKey(
      @RequestParam(required = false)
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    try {
      logger.info("Manual key rotation triggered by admin");
      long newPrimaryKeyId = rotationService.introduceNewKey("ADMIN_MANUAL");
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.KEY_INTRODUCED,
                  AdminAuditConstants.ENCRYPTION_KEY_ROTATION_MANUAL)
              .eventStatus(EventStatus.SUCCESS)
              .reason(reason)
              .eventDetails(AuditDetailsBuilder.builder().encryptionKeyId(newPrimaryKeyId).toJson())
              .build());
      return ResponseEntity.ok(
          new KeyRotationResponse(newPrimaryKeyId, "Key rotation completed successfully"));
    } catch (Exception e) {
      logger.error("Manual key rotation failed", e);
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("manual_key_rotation")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Manual key rotation failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new KeyRotationResponse(null, "Key rotation failed: " + e.getMessage()));
    }
  }

  /**
   * List all re-encryption batches.
   *
   * @return list of re-encryption batches
   */
  @Operation(
      summary = "List re-encryption batches",
      description = "Returns all re-encryption batches with their status and progress")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Batches retrieved successfully"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/reencryption-batches")
  public ResponseEntity<List<ReencryptionBatchResponse>> listBatches() {
    List<ReencryptionBatch> batches = batchRepository.findAll();
    List<ReencryptionBatchResponse> responses =
        batches.stream().map(this::toBatchResponse).collect(Collectors.toList());
    return ResponseEntity.ok(responses);
  }

  /**
   * Resume a failed or paused re-encryption batch.
   *
   * @param batchId the batch ID
   * @return success response
   */
  @Operation(
      summary = "Resume re-encryption batch",
      description = "Resumes processing of a failed or paused re-encryption batch")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Batch resumed successfully"),
    @ApiResponse(responseCode = "404", description = "Batch not found"),
    @ApiResponse(responseCode = "500", description = "Failed to resume batch")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/reencryption-batches/{batchId}/resume")
  public ResponseEntity<BatchResumeResponse> resumeBatch(@PathVariable Integer batchId) {
    return batchRepository
        .findById(batchId)
        .map(
            batch -> {
              try {
                reencryptionService.processBatch(batch);
                return ResponseEntity.ok(
                    new BatchResumeResponse(batchId, "Batch resumed successfully"));
              } catch (Exception e) {
                logger.error("Failed to resume batch {}", batchId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                        new BatchResumeResponse(
                            batchId, "Failed to resume batch: " + e.getMessage()));
              }
            })
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Manually trigger full re-encryption process.
   *
   * <p>This endpoint creates batches for all old keys and processes them immediately. Useful for
   * testing or emergency re-encryption operations.
   *
   * @return summary of re-encryption operation
   */
  @Operation(
      summary = "Trigger full re-encryption",
      description =
          "Manually triggers full re-encryption process. Creates batches for all old keys and"
              + " processes them immediately.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Re-encryption triggered successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request (encryption not available)"),
    @ApiResponse(responseCode = "500", description = "Re-encryption failed")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/reencrypt/trigger")
  public ResponseEntity<ReencryptionTriggerResponse> triggerFullReencryption() {
    try {
      logger.info("Manual full re-encryption triggered by admin");
      org.ezkey.security.ReencryptionService.ReencryptionSummary summary =
          reencryptionService.triggerFullReencryption();

      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_STARTED)
              .eventAction("manual_full_reencryption")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("batches_created", summary.batchesCreated())
                      .custom("batches_processed", summary.batchesProcessed())
                      .custom("batches_failed", summary.batchesFailed())
                      .toJson())
              .build());

      return ResponseEntity.ok(
          new ReencryptionTriggerResponse(
              summary.batchesCreated(),
              summary.batchesProcessed(),
              summary.batchesFailed(),
              "Full re-encryption completed successfully"));
    } catch (IllegalStateException e) {
      logger.error("Full re-encryption failed: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              new ReencryptionTriggerResponse(0, 0, 0, "Re-encryption failed: " + e.getMessage()));
    } catch (Exception e) {
      logger.error("Full re-encryption failed", e);
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("manual_full_reencryption")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Manual full re-encryption failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              new ReencryptionTriggerResponse(0, 0, 0, "Re-encryption failed: " + e.getMessage()));
    }
  }

  /**
   * Manually trigger re-encryption for a specific old key.
   *
   * <p>This endpoint creates and processes re-encryption batches for a specific old key. Useful for
   * targeted re-encryption operations.
   *
   * @param keyId the old key ID to re-encrypt
   * @return summary of re-encryption operation for this key
   */
  @Operation(
      summary = "Trigger re-encryption for specific key",
      description =
          "Creates and processes re-encryption batches for a specific old key. The key must not be"
              + " PRIMARY.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Re-encryption triggered successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request (key not found or is PRIMARY)"),
    @ApiResponse(responseCode = "404", description = "Key not found"),
    @ApiResponse(responseCode = "500", description = "Re-encryption failed")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{keyId}/reencrypt")
  public ResponseEntity<ReencryptionKeyResponse> triggerReencryptionForKey(
      @PathVariable Long keyId) {
    try {
      logger.info("Manual re-encryption triggered for key {} by admin", keyId);
      org.ezkey.security.ReencryptionService.ReencryptionSummary summary =
          reencryptionService.triggerReencryptionForKey(keyId);

      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_STARTED)
              .eventAction("manual_key_reencryption")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("key_id", keyId)
                      .custom("batches_created", summary.batchesCreated())
                      .custom("batches_processed", summary.batchesProcessed())
                      .custom("batches_failed", summary.batchesFailed())
                      .toJson())
              .build());

      return ResponseEntity.ok(
          new ReencryptionKeyResponse(
              keyId,
              summary.batchesCreated(),
              summary.batchesProcessed(),
              summary.batchesFailed(),
              "Re-encryption for key " + keyId + " completed successfully"));
    } catch (IllegalArgumentException e) {
      logger.error("Re-encryption for key {} failed: {}", keyId, e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              new ReencryptionKeyResponse(
                  keyId, 0, 0, 0, "Re-encryption failed: " + e.getMessage()));
    } catch (IllegalStateException e) {
      logger.error("Re-encryption for key {} failed: {}", keyId, e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              new ReencryptionKeyResponse(
                  keyId, 0, 0, 0, "Re-encryption failed: " + e.getMessage()));
    } catch (Exception e) {
      logger.error("Re-encryption for key {} failed", keyId, e);
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("manual_key_reencryption")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("key_id", keyId)
                      .errorSummary("Manual re-encryption for key failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              new ReencryptionKeyResponse(
                  keyId, 0, 0, 0, "Re-encryption failed: " + e.getMessage()));
    }
  }

  /**
   * Create re-encryption batches for all old keys without processing them.
   *
   * <p>This endpoint creates re-encryption batches for all old keys without processing them. The
   * batches will be processed by the scheduled job. Useful for preparing batches before scheduled
   * processing.
   *
   * @return summary of batch creation operation
   */
  @Operation(
      summary = "Create re-encryption batches",
      description =
          "Creates re-encryption batches for all old keys without processing them. Batches will be"
              + " processed by the scheduled job.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Batches created successfully"),
    @ApiResponse(responseCode = "500", description = "Batch creation failed")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/reencrypt/create-batches")
  public ResponseEntity<BatchCreationResponse> createBatches() {
    try {
      logger.info("Manual batch creation triggered by admin");

      // Count existing batches before creation
      long batchesBefore = batchRepository.count();

      // Create batches for all old keys
      reencryptionService.createBatchesForOldKeys();

      // Count batches after creation
      long batchesAfter = batchRepository.count();
      int batchesCreated = (int) (batchesAfter - batchesBefore);

      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_STARTED)
              .eventAction("manual_batch_creation")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder().custom("batches_created", batchesCreated).toJson())
              .build());

      return ResponseEntity.ok(
          new BatchCreationResponse(
              batchesCreated, "Created " + batchesCreated + " re-encryption batches"));
    } catch (Exception e) {
      logger.error("Batch creation failed", e);
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.REENCRYPTION_FAILED)
              .eventAction("manual_batch_creation")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress("127.0.0.1")
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Manual batch creation failed: " + e.getMessage())
                      .toJson())
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new BatchCreationResponse(0, "Batch creation failed: " + e.getMessage()));
    }
  }

  private EncryptionKeyResponse toResponse(EncryptionKey key) {
    return new EncryptionKeyResponse(
        key.getKeyId(),
        key.getKeyStatus().name(),
        key.getAlgorithm(),
        key.getIntroducedAt(),
        key.getPromotedPrimaryAt(),
        key.getDisabledAt(),
        key.getRecordsEncrypted(),
        key.getRecordsReencrypted(),
        key.getCreatedBy(),
        key.getNotes());
  }

  private ReencryptionBatchResponse toBatchResponse(ReencryptionBatch batch) {
    return new ReencryptionBatchResponse(
        batch.getBatchId(),
        batch.getTargetTable(),
        batch.getTargetColumn(),
        batch.getOldKey().getKeyId(),
        batch.getNewKey().getKeyId(),
        batch.getStatus().name(),
        batch.getRecordsTotal(),
        batch.getRecordsDone(),
        batch.getRecordsFailed(),
        batch.getRecordsSkipped(),
        batch.getProgressPct(),
        batch.getStartedAt(),
        batch.getCompletedAt(),
        batch.getErrorMessage(),
        batch.getRetryCount());
  }

  // Response DTOs

  /**
   * Response DTO for encryption key information.
   *
   * @param keyId the unique identifier of the encryption key
   * @param keyStatus the current status of the key (PRIMARY, ENABLED, DISABLED)
   * @param algorithm the encryption algorithm used (e.g., "AES256_GCM")
   * @param introducedAt timestamp when the key was first introduced
   * @param promotedPrimaryAt timestamp when the key was promoted to PRIMARY status (null if never
   *     primary)
   * @param disabledAt timestamp when the key was disabled (null if still enabled)
   * @param recordsEncrypted total number of records encrypted with this key
   * @param recordsReencrypted total number of records that have been re-encrypted from this key
   * @param createdBy identifier of who/what created the key (SYSTEM or admin username)
   * @param notes optional notes about the key
   */
  public record EncryptionKeyResponse(
      Long keyId,
      String keyStatus,
      String algorithm,
      java.time.OffsetDateTime introducedAt,
      java.time.OffsetDateTime promotedPrimaryAt,
      java.time.OffsetDateTime disabledAt,
      Long recordsEncrypted,
      Long recordsReencrypted,
      String createdBy,
      String notes) {}

  /**
   * Response DTO for key rotation operation.
   *
   * @param newPrimaryKeyId the ID of the newly created primary key after rotation
   * @param message human-readable message describing the rotation result
   */
  public record KeyRotationResponse(Long newPrimaryKeyId, String message) {}

  /**
   * Response DTO for re-encryption batch information.
   *
   * @param batchId the unique identifier of the re-encryption batch
   * @param targetTable the database table being re-encrypted
   * @param targetColumn the column within the table being re-encrypted
   * @param oldKeyId the ID of the encryption key being replaced
   * @param newKeyId the ID of the new encryption key being used
   * @param status the current batch status (PENDING, IN_PROGRESS, COMPLETED, FAILED, PAUSED)
   * @param recordsTotal total number of records to be re-encrypted
   * @param recordsDone number of records successfully re-encrypted
   * @param recordsFailed number of records that failed to re-encrypt
   * @param recordsSkipped number of records skipped (e.g., already encrypted with new key)
   * @param progressPct percentage of batch completion (0.00 to 100.00)
   * @param startedAt timestamp when batch processing started (null if not started)
   * @param completedAt timestamp when batch processing completed (null if not completed)
   * @param errorMessage error message if batch failed (null if successful)
   * @param retryCount number of times the batch has been retried after failure
   */
  public record ReencryptionBatchResponse(
      Integer batchId,
      String targetTable,
      String targetColumn,
      Long oldKeyId,
      Long newKeyId,
      String status,
      Integer recordsTotal,
      Integer recordsDone,
      Integer recordsFailed,
      Integer recordsSkipped,
      java.math.BigDecimal progressPct,
      java.time.OffsetDateTime startedAt,
      java.time.OffsetDateTime completedAt,
      String errorMessage,
      Integer retryCount) {}

  /**
   * Response DTO for batch resume operation.
   *
   * @param batchId the ID of the batch that was resumed
   * @param message human-readable message describing the resume operation result
   */
  public record BatchResumeResponse(Integer batchId, String message) {}

  /**
   * Response DTO for full re-encryption trigger operation.
   *
   * @param batchesCreated number of batches created
   * @param batchesProcessed number of batches successfully processed
   * @param batchesFailed number of batches that failed
   * @param message human-readable message describing the operation result
   */
  public record ReencryptionTriggerResponse(
      int batchesCreated, int batchesProcessed, int batchesFailed, String message) {}

  /**
   * Response DTO for key-specific re-encryption operation.
   *
   * @param keyId the key ID that was re-encrypted
   * @param batchesCreated number of batches created for this key
   * @param batchesProcessed number of batches successfully processed
   * @param batchesFailed number of batches that failed
   * @param message human-readable message describing the operation result
   */
  public record ReencryptionKeyResponse(
      Long keyId, int batchesCreated, int batchesProcessed, int batchesFailed, String message) {}

  /**
   * Response DTO for batch creation operation.
   *
   * @param batchesCreated number of batches created
   * @param message human-readable message describing the operation result
   */
  public record BatchCreationResponse(int batchesCreated, String message) {}
}
