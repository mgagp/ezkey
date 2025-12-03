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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.stream.Collectors;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
 *   <li><b>GET /api/v1/reencryption-batches:</b> List re-encryption batches
 *   <li><b>POST /api/v1/reencryption-batches/{batchId}/resume:</b> Resume failed batch
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
   * List all encryption keys.
   *
   * @return list of all encryption keys
   */
  @Operation(
      summary = "List all encryption keys",
      description = "Returns all encryption keys in the system")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Keys retrieved successfully"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<List<EncryptionKeyResponse>> listKeys() {
    List<EncryptionKey> keys = keyRepository.findAll();
    List<EncryptionKeyResponse> responses =
        keys.stream().map(this::toResponse).collect(Collectors.toList());
    return ResponseEntity.ok(responses);
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
  public ResponseEntity<KeyRotationResponse> rotateKey() {
    try {
      logger.info("Manual key rotation triggered by admin");
      long newPrimaryKeyId = rotationService.introduceNewKey("ADMIN_MANUAL");
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
}
