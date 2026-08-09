/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.security.KeyRotationService;
import org.ezkey.security.KeyUsageVerificationService;
import org.ezkey.security.ReencryptionService;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.entity.ReencryptionBatch;
import org.ezkey.security.domain.entity.ReencryptionBatch.BatchStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.domain.repository.ReencryptionBatchRepository;
import org.ezkey.security.exception.PendingEncryptionKeyExistsException;
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
import org.springframework.security.core.Authentication;
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
 * status queries, and batch management. Encryption keys are instance-wide platform assets; all
 * operations require {@code GLOBAL_ADMIN} and are audited with the acting administrator and client
 * context.
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
 *   <li>Global Admin only ({@code ROLE_GLOBAL_ADMIN}); Tenant Admins and API keys receive 403
 *   <li>Manual mutations record adminId, client IP, and user-agent
 *   <li>Scheduled crypto jobs remain distinct (system actor / triggeredBy in core services)
 *   <li>Rate limiting applied via AdminOperationsRateLimitService
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Validated
@RestController
@RequestMapping("/api/v1/encryption-keys")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
@Tag(
    name = "Encryption Keys",
    description =
        "Encryption key lifecycle management and rotation (Global Admin only). Instance-wide"
            + " platform encryption keys; Tenant Admins receive 403.")
public class EncryptionKeyController {

  private static final Logger logger = LoggerFactory.getLogger(EncryptionKeyController.class);

  private final EncryptionKeyRepository keyRepository;
  private final ReencryptionBatchRepository batchRepository;
  private final KeyRotationService rotationService;
  private final ReencryptionService reencryptionService;
  private final AuditLogService auditLogService;
  private final KeyUsageVerificationService keyUsageVerificationService;

  public EncryptionKeyController(
      EncryptionKeyRepository keyRepository,
      ReencryptionBatchRepository batchRepository,
      KeyRotationService rotationService,
      ReencryptionService reencryptionService,
      AuditLogService auditLogService,
      KeyUsageVerificationService keyUsageVerificationService) {
    this.keyRepository = keyRepository;
    this.batchRepository = batchRepository;
    this.rotationService = rotationService;
    this.reencryptionService = reencryptionService;
    this.auditLogService = auditLogService;
    this.keyUsageVerificationService = keyUsageVerificationService;
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
              + " disabledAt, recordsEncrypted, recordsReencrypted, createdBy, createdAt."
              + " Global Admin only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Paginated list of keys (content + page)"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
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
        (root, _, cb) -> {
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
      description =
          "Returns the current primary encryption key used for new encryption operations. Global"
              + " Admin only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Primary key retrieved successfully"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "404", description = "No primary key found"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
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
      description = "Returns details for a specific encryption key. Global Admin only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Key retrieved successfully"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "404", description = "Key not found"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
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
   * <p>This endpoint allows Global Admins to manually trigger key rotation outside of the scheduled
   * job. Useful for emergency rotations or testing.
   *
   * @param reason optional audit reason (10–500 characters)
   * @param auth the authenticated Global Admin
   * @param httpRequest the HTTP request (client IP / user-agent)
   * @return the new primary key ID
   */
  @Operation(
      summary = "Manually trigger key rotation",
      description =
          "Introduces a new encryption key (typically PENDING until the sync window elapses, then"
              + " promoted to PRIMARY). Global Admin only.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "New key introduced with PENDING status; it will be promoted to primary"
                + " automatically once the synchronization window elapses"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(
        responseCode = "409",
        description =
            "A PENDING encryption key already exists; wait for promotion or use immediate"
                + " promotion",
        content =
            @io.swagger.v3.oas.annotations.media.Content(
                schema =
                    @io.swagger.v3.oas.annotations.media.Schema(
                        implementation = org.springframework.http.ProblemDetail.class))),
    @ApiResponse(responseCode = "500", description = "Key rotation failed")
  })
  @PostMapping("/rotate")
  public ResponseEntity<KeyRotationResponse> rotateKey(
      @RequestParam(required = false)
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    Integer adminId = resolveAdminId(auth);
    try {
      logger.info("Manual key rotation triggered by adminId={}", adminId);
      long newPrimaryKeyId = rotationService.introduceNewKey("ADMIN_MANUAL");
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.KEY_INTRODUCED,
                  AdminAuditConstants.ENCRYPTION_KEY_ROTATION_MANUAL)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(adminId)
              .reason(reason)
              .eventDetails(AuditDetailsBuilder.builder().encryptionKeyId(newPrimaryKeyId).toJson())
              .build());
      return ResponseEntity.ok(
          new KeyRotationResponse(
              newPrimaryKeyId,
              "New key introduced with PENDING status; it will be promoted to primary"
                  + " automatically once the synchronization window elapses"));
    } catch (PendingEncryptionKeyExistsException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.KEY_INTRODUCED,
                  AdminAuditConstants.ENCRYPTION_KEY_ROTATION_MANUAL)
              .eventStatus(EventStatus.FAILURE)
              .adminId(adminId)
              .reason(reason)
              .errorMessage(e.getMessage())
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom(
                          "pending_key_id",
                          e.getPendingKeyId() != null
                              ? Long.toUnsignedString(e.getPendingKeyId())
                              : "unknown")
                      .toJson())
              .build());
      throw e;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Manual key rotation failed", e);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.REENCRYPTION_FAILED,
                  AdminAuditConstants.ENCRYPTION_KEY_ROTATION_MANUAL)
              .eventStatus(EventStatus.ERROR)
              .adminId(adminId)
              .reason(reason)
              .errorMessage(e.getMessage())
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Manual key rotation failed: " + e.getMessage())
                      .toJson())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new KeyRotationResponse(null, "Key rotation failed: " + e.getMessage()));
    }
  }

  /**
   * List re-encryption batches with pagination and optional filters.
   *
   * @param status optional filter by batch status (invalid values ignored)
   * @param targetTable optional exact match on target table name
   * @param targetColumn optional exact match on target column name
   * @param oldKeyId optional filter by old encryption key id
   * @param newKeyId optional filter by new encryption key id
   * @param createdAfter optional inclusive lower bound on {@code createdAt} (ISO-8601)
   * @param createdBefore optional inclusive upper bound on {@code createdAt} (ISO-8601)
   * @param pageable pagination and sort
   * @return page of batch rows
   */
  @Operation(
      summary = "List re-encryption batches",
      description =
          "Returns re-encryption batches with pagination and optional filters. Use page, size, sort"
              + " (default sort createdAt,DESC). Optional filters: status (PENDING, IN_PROGRESS,"
              + " COMPLETED, FAILED, PAUSED), targetTable, targetColumn, oldKeyId, newKeyId,"
              + " createdAfter, createdBefore (ISO-8601, inclusive bounds on createdAt). Sortable:"
              + " batchId, status, targetTable, targetColumn, createdAt, startedAt, completedAt,"
              + " progressPct, recordsTotal, recordsDone, oldKey.keyId, newKey.keyId. Global Admin"
              + " only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Batches retrieved successfully"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
  })
  @GetMapping("/reencryption-batches")
  public ResponseEntity<Page<ReencryptionBatchResponse>> listBatches(
      @Parameter(
              description =
                  "Filter by batch status (PENDING, IN_PROGRESS, COMPLETED, FAILED, PAUSED); omit"
                      + " for all")
          @RequestParam(required = false)
          String status,
      @Parameter(description = "Filter by target table name (exact match)")
          @RequestParam(required = false)
          String targetTable,
      @Parameter(description = "Filter by target column name (exact match)")
          @RequestParam(required = false)
          String targetColumn,
      @Parameter(description = "Filter by old encryption key id") @RequestParam(required = false)
          Long oldKeyId,
      @Parameter(description = "Filter by new encryption key id") @RequestParam(required = false)
          Long newKeyId,
      @Parameter(
              description =
                  "Inclusive lower bound on createdAt (ISO-8601); aligns with Admin UI date range")
          @RequestParam(required = false)
          OffsetDateTime createdAfter,
      @Parameter(
              description =
                  "Inclusive upper bound on createdAt (ISO-8601); aligns with Admin UI date range")
          @RequestParam(required = false)
          OffsetDateTime createdBefore,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    Specification<ReencryptionBatch> spec =
        (root, _, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (status != null && !status.isBlank()) {
            try {
              BatchStatus batchStatus = BatchStatus.valueOf(status.trim().toUpperCase());
              predicates.add(cb.equal(root.get("status"), batchStatus));
            } catch (IllegalArgumentException ignored) {
              // Invalid enum: ignore filter (same as listKeys keyStatus)
            }
          }
          if (targetTable != null && !targetTable.isBlank()) {
            predicates.add(cb.equal(root.get("targetTable"), targetTable.trim()));
          }
          if (targetColumn != null && !targetColumn.isBlank()) {
            predicates.add(cb.equal(root.get("targetColumn"), targetColumn.trim()));
          }
          if (oldKeyId != null) {
            predicates.add(cb.equal(root.join("oldKey").get("keyId"), oldKeyId));
          }
          if (newKeyId != null) {
            predicates.add(cb.equal(root.join("newKey").get("keyId"), newKeyId));
          }
          if (createdAfter != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
          }
          if (createdBefore != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<ReencryptionBatchResponse> page =
        batchRepository.findAll(spec, pageable).map(this::toBatchResponse);
    return ResponseEntity.ok(page);
  }

  /**
   * Resume a failed or paused re-encryption batch.
   *
   * @param batchId the batch ID
   * @param auth the authenticated Global Admin
   * @param httpRequest the HTTP request (client IP / user-agent)
   * @return success response
   */
  @Operation(
      summary = "Resume re-encryption batch",
      description =
          "Resumes processing of a failed or paused re-encryption batch. Global Admin only.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Batch resume accepted for background processing"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "404", description = "Batch not found"),
    @ApiResponse(responseCode = "500", description = "Failed to enqueue batch resume")
  })
  @PostMapping("/reencryption-batches/{batchId}/resume")
  public ResponseEntity<BatchResumeResponse> resumeBatch(
      @PathVariable Integer batchId, Authentication auth, HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    Integer adminId = resolveAdminId(auth);
    return batchRepository
        .findById(batchId)
        .map(
            batch -> {
              try {
                reencryptionService.enqueueBatchProcessing(List.of(batch));
                auditLogService.log(
                    AuditHelper.createAdminAudit(
                            context,
                            EventType.REENCRYPTION_STARTED,
                            AdminAuditConstants.ENCRYPTION_BATCH_RESUME)
                        .eventStatus(EventStatus.SUCCESS)
                        .adminId(adminId)
                        .eventDetails(
                            AuditDetailsBuilder.builder().custom("batch_id", batchId).toJson())
                        .build());
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(
                        new BatchResumeResponse(
                            batchId, "Batch resume accepted; progress in batches table"));
              } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
                logger.error("Failed to enqueue resume for batch {}", batchId, e);
                auditLogService.log(
                    AuditHelper.createAdminAudit(
                            context,
                            EventType.REENCRYPTION_FAILED,
                            AdminAuditConstants.ENCRYPTION_BATCH_RESUME)
                        .eventStatus(EventStatus.ERROR)
                        .adminId(adminId)
                        .errorMessage(e.getMessage())
                        .eventDetails(
                            AuditDetailsBuilder.builder()
                                .custom("batch_id", batchId)
                                .errorSummary("Failed to resume batch: " + e.getMessage())
                                .toJson())
                        .build());
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
   * <p>This endpoint creates batches for all old keys and enqueues background processing. Useful
   * for testing or emergency re-encryption operations.
   *
   * @param auth the authenticated Global Admin
   * @param httpRequest the HTTP request (client IP / user-agent)
   * @return summary of re-encryption operation
   */
  @Operation(
      summary = "Trigger full re-encryption",
      description =
          "Accepts a manual full re-encryption request: creates batches for old keys and enqueues"
              + " background processing. Returns immediately; use the batches list for progress."
              + " Global Admin only.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Re-encryption accepted for background processing"),
    @ApiResponse(responseCode = "400", description = "Invalid request (encryption not available)"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "500", description = "Failed to enqueue re-encryption")
  })
  @PostMapping("/reencrypt/trigger")
  public ResponseEntity<ReencryptionTriggerResponse> triggerFullReencryption(
      Authentication auth, HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    Integer adminId = resolveAdminId(auth);
    try {
      logger.info("Manual full re-encryption accepted by adminId={}", adminId);
      ReencryptionService.ManualReencryptionEnqueueResult result =
          reencryptionService.enqueueFullReencryption();

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.REENCRYPTION_STARTED,
                  AdminAuditConstants.ENCRYPTION_FULL_REENCRYPTION_ACCEPTED)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(adminId)
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("batches_enqueued", result.batchesEnqueued())
                      .custom("batch_ids", result.batchIds())
                      .toJson())
              .build());

      String message =
          result.batchesEnqueued() > 0
              ? "Full re-encryption accepted; " + result.batchesEnqueued() + " batch(es) enqueued"
              : "Full re-encryption accepted; no batches to process";

      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(
              new ReencryptionTriggerResponse(
                  result.batchesEnqueued(), result.batchIds(), message));
    } catch (IllegalStateException e) {
      logger.error("Full re-encryption rejected: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              new ReencryptionTriggerResponse(
                  0, List.of(), "Re-encryption failed: " + e.getMessage()));
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Full re-encryption enqueue failed", e);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.REENCRYPTION_FAILED,
                  AdminAuditConstants.ENCRYPTION_FULL_REENCRYPTION_ACCEPTED)
              .eventStatus(EventStatus.ERROR)
              .adminId(adminId)
              .errorMessage(e.getMessage())
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Manual full re-encryption enqueue failed: " + e.getMessage())
                      .toJson())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              new ReencryptionTriggerResponse(
                  0, List.of(), "Re-encryption failed: " + e.getMessage()));
    }
  }

  /**
   * Manually trigger re-encryption for a specific old key.
   *
   * <p>This endpoint creates and enqueues re-encryption batches for a specific old key. Useful for
   * targeted re-encryption operations.
   *
   * @param keyId the old key ID to re-encrypt
   * @param auth the authenticated Global Admin
   * @param httpRequest the HTTP request (client IP / user-agent)
   * @return summary of re-encryption operation for this key
   */
  @Operation(
      summary = "Trigger re-encryption for specific key",
      description =
          "Accepts re-encryption for a specific old key: creates batches and enqueues background"
              + " processing. The key must not be PRIMARY. Returns immediately. Global Admin only.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "202",
        description = "Re-encryption accepted for background processing"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request (key not found or is PRIMARY)"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "404", description = "Key not found"),
    @ApiResponse(responseCode = "500", description = "Failed to enqueue re-encryption")
  })
  @PostMapping("/{keyId}/reencrypt")
  public ResponseEntity<ReencryptionKeyResponse> triggerReencryptionForKey(
      @PathVariable Long keyId, Authentication auth, HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    Integer adminId = resolveAdminId(auth);
    try {
      logger.info("Manual re-encryption accepted for key {} by adminId={}", keyId, adminId);
      ReencryptionService.ManualReencryptionEnqueueResult result =
          reencryptionService.enqueueReencryptionForKey(keyId);

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.REENCRYPTION_STARTED,
                  AdminAuditConstants.ENCRYPTION_KEY_REENCRYPTION_ACCEPTED)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(adminId)
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("key_id", keyId)
                      .custom("batches_enqueued", result.batchesEnqueued())
                      .custom("batch_ids", result.batchIds())
                      .toJson())
              .build());

      String message =
          result.batchesEnqueued() > 0
              ? "Re-encryption for key "
                  + keyId
                  + " accepted; "
                  + result.batchesEnqueued()
                  + " batch(es) enqueued"
              : "Re-encryption for key " + keyId + " accepted; no batches to process";

      return ResponseEntity.status(HttpStatus.ACCEPTED)
          .body(
              new ReencryptionKeyResponse(
                  keyId, result.batchesEnqueued(), result.batchIds(), message));
    } catch (IllegalArgumentException e) {
      logger.error("Re-encryption for key {} rejected: {}", keyId, e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              new ReencryptionKeyResponse(
                  keyId, 0, List.of(), "Re-encryption failed: " + e.getMessage()));
    } catch (IllegalStateException e) {
      logger.error("Re-encryption for key {} rejected: {}", keyId, e.getMessage());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body(
              new ReencryptionKeyResponse(
                  keyId, 0, List.of(), "Re-encryption failed: " + e.getMessage()));
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Re-encryption for key {} enqueue failed", keyId, e);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.REENCRYPTION_FAILED,
                  AdminAuditConstants.ENCRYPTION_KEY_REENCRYPTION_ACCEPTED)
              .eventStatus(EventStatus.ERROR)
              .adminId(adminId)
              .errorMessage(e.getMessage())
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .custom("key_id", keyId)
                      .errorSummary(
                          "Manual re-encryption for key enqueue failed: " + e.getMessage())
                      .toJson())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(
              new ReencryptionKeyResponse(
                  keyId, 0, List.of(), "Re-encryption failed: " + e.getMessage()));
    }
  }

  /**
   * Create re-encryption batches for all old keys without processing them.
   *
   * <p>This endpoint creates re-encryption batches for all old keys without processing them. The
   * batches will be processed by the scheduled job. Useful for preparing batches before scheduled
   * processing.
   *
   * @param auth the authenticated Global Admin
   * @param httpRequest the HTTP request (client IP / user-agent)
   * @return summary of batch creation operation
   */
  @Operation(
      summary = "Create re-encryption batches",
      description =
          "Creates re-encryption batches for all old keys without processing them. Batches will be"
              + " processed by the scheduled job. Global Admin only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Batches created successfully"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "500", description = "Batch creation failed")
  })
  @PostMapping("/reencrypt/create-batches")
  public ResponseEntity<BatchCreationResponse> createBatches(
      Authentication auth, HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    Integer adminId = resolveAdminId(auth);
    try {
      logger.info("Manual batch creation triggered by adminId={}", adminId);

      // Count existing batches before creation
      long batchesBefore = batchRepository.count();

      // Create batches for all old keys
      reencryptionService.createBatchesForOldKeys();

      // Count batches after creation
      long batchesAfter = batchRepository.count();
      int batchesCreated = (int) (batchesAfter - batchesBefore);

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.REENCRYPTION_STARTED,
                  AdminAuditConstants.ENCRYPTION_BATCH_CREATION_MANUAL)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(adminId)
              .eventDetails(
                  AuditDetailsBuilder.builder().custom("batches_created", batchesCreated).toJson())
              .build());

      return ResponseEntity.ok(
          new BatchCreationResponse(
              batchesCreated, "Created " + batchesCreated + " re-encryption batches"));
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Batch creation failed", e);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.REENCRYPTION_FAILED,
                  AdminAuditConstants.ENCRYPTION_BATCH_CREATION_MANUAL)
              .eventStatus(EventStatus.ERROR)
              .adminId(adminId)
              .errorMessage(e.getMessage())
              .eventDetails(
                  AuditDetailsBuilder.builder()
                      .errorSummary("Manual batch creation failed: " + e.getMessage())
                      .toJson())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new BatchCreationResponse(0, "Batch creation failed: " + e.getMessage()));
    }
  }

  /**
   * Resolves the acting administrator id from the security context.
   *
   * @param auth Spring Security authentication
   * @return admin id when the principal is an {@link AdminPrincipal}; otherwise {@code null}
   */
  private static Integer resolveAdminId(Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    return principal != null ? principal.adminId() : null;
  }

  private EncryptionKeyResponse toResponse(EncryptionKey key) {
    KeyUsageVerificationService.KeyUsageSnapshot snap =
        keyUsageVerificationService.computeSnapshot(key);
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
        key.getNotes(),
        snap.lifecycleStage(),
        snap.remainingRecords(),
        snap.remainingTargets(),
        snap.lastVerifiedAt(),
        snap.verificationState(),
        snap.decommissionEligible(),
        snap.incompleteMigrationBatches(),
        snap.reencryptionWallClockSeconds());
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
        batch.getRetryCount(),
        batch.getShardIndex(),
        batch.getShardCount());
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
   * @param recordsEncrypted migration-scope baseline: ciphertext units when this key became ENABLED
   *     (demotion); zero while PRIMARY until demotion
   * @param recordsReencrypted cumulative ciphertext units re-encrypted off this key (completed
   *     batches; reset at demotion)
   * @param createdBy identifier of who/what created the key (SYSTEM or admin username)
   * @param notes optional notes about the key
   * @param lifecycleStage derived operator-facing lifecycle (e.g. PRIMARY, ENABLED_IN_USE, DRAINED)
   * @param remainingRecords derived sum of ciphertext units for this key (prefix scan): migration
   *     backlog for ENABLED; live volume on PRIMARY; null when not applicable (PENDING / DISABLED)
   * @param remainingTargets number of targets with count &gt; 0; null when not applicable
   * @param lastVerifiedAt when the lifecycle snapshot was computed
   * @param verificationState machine-readable verification outcome
   * @param decommissionEligible true when drained and eligible for a future decommission workflow
   * @param incompleteMigrationBatches true when non-completed migration batches exist for this old
   *     key
   * @param reencryptionWallClockSeconds retrospective wall-clock duration in seconds for a fully
   *     drained migration, computed by merging completed batches' actual time windows (overlapping
   *     batches count once); null unless {@code lifecycleStage} is {@code DRAINED} and at least one
   *     completed batch has timing
   */
  @Schema(description = "Encryption key row with derived lifecycle fields for operators.")
  public record EncryptionKeyResponse(
      @Schema(description = "Unique key identifier (Tink keyset id)") Long keyId,
      @Schema(description = "PRIMARY, ENABLED, DISABLED, or PENDING") String keyStatus,
      @Schema(description = "Algorithm label, e.g. AES256_GCM") String algorithm,
      @Schema(description = "When the key was introduced") java.time.OffsetDateTime introducedAt,
      @Schema(description = "When promoted to PRIMARY, if applicable")
          java.time.OffsetDateTime promotedPrimaryAt,
      @Schema(description = "When disabled, if applicable") java.time.OffsetDateTime disabledAt,
      @Schema(
              description =
                  "Migration baseline: ciphertext units when key became ENABLED (demotion); 0 while"
                      + " PRIMARY")
          Long recordsEncrypted,
      @Schema(
              description =
                  "Cumulative ciphertext units re-encrypted off this key (completed batches; reset"
                      + " at demotion)")
          Long recordsReencrypted,
      @Schema(description = "Creator label (SYSTEM or admin)") String createdBy,
      @Schema(description = "Optional operator notes") String notes,
      @Schema(
              description =
                  "Derived lifecycle stage (e.g. PRIMARY, ENABLED_IN_USE, DRAINED, DISABLED)")
          String lifecycleStage,
      @Schema(
              description =
                  "Derived ciphertext units (prefix scan): ENABLED = migration backlog; PRIMARY ="
                      + " current live volume; null when not applicable")
          Long remainingRecords,
      @Schema(description = "Targets with rows counted for this key; null when not applicable")
          Integer remainingTargets,
      @Schema(description = "When this snapshot was computed")
          java.time.OffsetDateTime lastVerifiedAt,
      @Schema(description = "Verification outcome (e.g. NOT_APPLICABLE, VERIFIED_ZERO)")
          String verificationState,
      @Schema(description = "True when drained and ready for a future decommission workflow")
          boolean decommissionEligible,
      @Schema(description = "True when migration batches for this key are not all completed")
          boolean incompleteMigrationBatches,
      @Schema(
              description =
                  "Retrospective wall-clock seconds for a fully drained migration, computed by"
                      + " merging completed batches' actual time windows (overlapping work counts"
                      + " once); null unless lifecycleStage is DRAINED and timing is available")
          Long reencryptionWallClockSeconds) {}

  /**
   * Response DTO for key rotation operation.
   *
   * <p>Rotation is asynchronous: the returned key is introduced with {@code PENDING} status and is
   * promoted to {@code PRIMARY} by a scheduled job once the synchronization window elapses (see
   * {@code ezkey.encryption.rotation.sync-window-seconds}). Callers should not assume the key is
   * already primary from a 200 response alone.
   *
   * @param newPrimaryKeyId the ID of the newly introduced key (PENDING; becomes PRIMARY
   *     automatically after the synchronization delay)
   * @param message human-readable message describing the current state and next step
   */
  public record KeyRotationResponse(
      @Schema(
              description =
                  "ID of the newly introduced key. Status is PENDING at response time; the key"
                      + " becomes PRIMARY automatically once the synchronization window elapses.")
          Long newPrimaryKeyId,
      @Schema(description = "Human-readable message describing the current state and next step")
          String message) {}

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
   * @param shardIndex parallel shard index for {@code ezkey_auth_attempt} when {@code shardCount
   *     &gt; 1}; null for non-sharded batches
   * @param shardCount number of parallel shards when sharding auth attempts; null for non-sharded
   *     batches
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
      Integer retryCount,
      Integer shardIndex,
      Integer shardCount) {}

  /**
   * Response DTO for batch resume operation.
   *
   * @param batchId the ID of the batch that was resumed
   * @param message human-readable message describing the resume operation result
   */
  public record BatchResumeResponse(Integer batchId, String message) {}

  /**
   * Response DTO for full re-encryption trigger acceptance (202 Accepted).
   *
   * @param batchesEnqueued number of batches submitted to the background executor
   * @param batchIds identifiers of enqueued batches (may be empty)
   * @param message human-readable acceptance message
   */
  public record ReencryptionTriggerResponse(
      int batchesEnqueued, List<Integer> batchIds, String message) {}

  /**
   * Response DTO for key-specific re-encryption acceptance (202 Accepted).
   *
   * @param keyId the key ID that was accepted for re-encryption
   * @param batchesEnqueued number of batches submitted to the background executor
   * @param batchIds identifiers of enqueued batches (may be empty)
   * @param message human-readable acceptance message
   */
  public record ReencryptionKeyResponse(
      Long keyId, int batchesEnqueued, List<Integer> batchIds, String message) {}

  /**
   * Response DTO for batch creation operation.
   *
   * @param batchesCreated number of batches created
   * @param message human-readable message describing the operation result
   */
  public record BatchCreationResponse(int batchesCreated, String message) {}
}
