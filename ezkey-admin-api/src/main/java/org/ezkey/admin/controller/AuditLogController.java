/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuditLogController
 * Description: REST controller for audit log query API.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.dto.ArchiveSealRequest;
import org.ezkey.audit.dto.ArchiveSealResult;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.ezkey.audit.dto.GapDeclarationRequest;
import org.ezkey.audit.dto.GapDeclarationResult;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for audit log query API with tenant-scoped visibility.
 *
 * <p>This controller provides REST endpoints for querying audit logs for security monitoring,
 * compliance reporting, and forensic analysis. Supports filtering, pagination, and tenant-based
 * visibility enforcement for proper multi-tenant isolation.
 *
 * <p><b>Tenant Visibility Rules:</b>
 *
 * <ul>
 *   <li><b>Global Admin:</b> Sees all audit logs across all tenants. May optionally filter by a
 *       specific tenant using the {@code tenantId} query parameter.
 *   <li><b>Tenant Admin:</b> Sees only audit logs where {@code tenant_id} matches their own tenant.
 *       Audit entries with {@code tenant_id = NULL} (system-level events like encryption key
 *       operations) are excluded from Tenant Admin results.
 * </ul>
 *
 * <p><b>Admin API Endpoints:</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/audit-logs</b> - Query audit logs with filters, tenant scoping, and
 *       pagination
 * </ul>
 *
 * <p><b>Usage Context:</b> Part of the admin-api for security monitoring and SOC2 compliance
 * reporting. Provides comprehensive audit trail access with proper tenant isolation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(
    name = "Audit Logs",
    description = "Audit log query and reporting API for security monitoring and compliance")
public class AuditLogController {

  private final AuditLogService auditLogService;
  private final AuditLogMapper auditLogMapper;
  private final AuditIntegrityService auditIntegrityService;
  private final AuditChainVerificationService auditChainVerificationService;
  private final AuditLifecycleService auditLifecycleService;

  /**
   * Constructs the audit log controller with required dependencies.
   *
   * @param auditLogService the audit log service
   * @param auditLogMapper the MapStruct mapper for entity-DTO conversions
   * @param auditIntegrityService the integrity verification service
   * @param auditChainVerificationService the chain checkpoint verification service
   * @param auditLifecycleService the chain lifecycle service for archive sealing and gap
   *     declaration
   */
  public AuditLogController(
      AuditLogService auditLogService,
      AuditLogMapper auditLogMapper,
      AuditIntegrityService auditIntegrityService,
      AuditChainVerificationService auditChainVerificationService,
      AuditLifecycleService auditLifecycleService) {
    this.auditLogService = auditLogService;
    this.auditLogMapper = auditLogMapper;
    this.auditIntegrityService = auditIntegrityService;
    this.auditChainVerificationService = auditChainVerificationService;
    this.auditLifecycleService = auditLifecycleService;
  }

  /**
   * Query audit logs with optional filters, tenant-scoped visibility, and pagination.
   *
   * <p>Retrieves audit logs matching the specified criteria with pagination support and automatic
   * tenant-based visibility enforcement. All filter parameters are optional - if none are provided,
   * returns audit logs visible to the caller (paginated).
   *
   * <p><b>Tenant Visibility:</b>
   *
   * <ul>
   *   <li><b>Global Admin:</b> Sees all audit logs. May optionally filter by {@code tenantId}.
   *   <li><b>Tenant Admin:</b> Sees only audit logs where {@code tenant_id} matches their tenant.
   *       System-level events ({@code tenant_id = NULL}) are excluded.
   * </ul>
   *
   * <p><b>Pagination and Sorting:</b>
   *
   * <ul>
   *   <li>Use <code>?page=0&size=20</code> for pagination (zero-based page numbers)
   *   <li>Use <code>?sort=field,direction</code> for sorting (e.g., <code>?sort=auditLogId,asc
   *       </code> or <code>?sort=createdAt,desc</code>)
   *   <li>Default: page=0, size=20, sort=createdAt,DESC
   *   <li>Sortable fields: auditLogId, createdAt, eventType, eventStatus, apiName
   * </ul>
   *
   * @param eventType optional event type filter
   * @param eventStatus optional event status filter
   * @param apiName optional API name filter
   * @param enrollmentId optional enrollment ID filter
   * @param adminId optional admin ID filter (actor who performed the action)
   * @param targetAdminId optional target admin ID filter (admin who is the subject of the event,
   *     e.g. created, deactivated, or activated)
   * @param tenantId optional tenant ID filter (Global Admin only; ignored for Tenant Admin whose
   *     scope is enforced automatically)
   * @param pageable pagination and sorting parameters (default: page=0, size=20,
   *     sort=createdAt,DESC)
   * @return ResponseEntity containing page of audit logs visible to the caller
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  @Operation(
      summary = "Query audit logs",
      description =
          "Retrieves audit logs with optional filters, tenant-scoped visibility, and pagination "
              + "for security monitoring and compliance reporting. Tenant Admins automatically "
              + "see only their tenant's audit logs. Global Admins see all logs and may "
              + "optionally filter by tenantId. Supports dynamic sorting via "
              + "?sort=field,direction (e.g., ?sort=auditLogId,asc). Default sort is by "
              + "creation date descending (newest first).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<Page<AuditLogResponseDto>> getAuditLogs(
      @Parameter(description = "Filter by event type") @RequestParam(required = false)
          EventType eventType,
      @Parameter(description = "Filter by event status") @RequestParam(required = false)
          EventStatus eventStatus,
      @Parameter(description = "Filter by API name") @RequestParam(required = false)
          ApiName apiName,
      @Parameter(description = "Filter by enrollment ID") @RequestParam(required = false)
          Integer enrollmentId,
      @Parameter(description = "Filter by admin ID (actor)") @RequestParam(required = false)
          Integer adminId,
      @Parameter(
              description =
                  "Filter by target admin ID (subject of event, e.g."
                      + " created/deactivated/activated)")
          @RequestParam(required = false)
          Integer targetAdminId,
      @Parameter(
              description =
                  "Filter by tenant ID (Global Admin only). Ignored for Tenant Admin "
                      + "whose scope is enforced automatically.")
          @RequestParam(required = false)
          Integer tenantId,
      @Parameter(description = "Filter by creation time (inclusive start), ISO-8601")
          @RequestParam(required = false)
          OffsetDateTime createdAfter,
      @Parameter(description = "Filter by creation time (inclusive end), ISO-8601")
          @RequestParam(required = false)
          OffsetDateTime createdBefore,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    // Extract tenant scope from authenticated principal
    Integer requesterTenantId = extractRequesterTenantId();

    // For Tenant Admin, the tenantId filter parameter is ignored;
    // their own tenantId is enforced unconditionally.
    // For Global Admin (requesterTenantId == null), the optional tenantId param is passed
    // as a filter.
    Integer filterTenantId = (requesterTenantId == null) ? tenantId : null;

    Page<AuditLogResponseDto> auditLogs =
        auditLogService
            .findByFilters(
                eventType,
                eventStatus,
                apiName,
                enrollmentId,
                adminId,
                targetAdminId,
                requesterTenantId,
                filterTenantId,
                createdAfter,
                createdBefore,
                pageable)
            .map(auditLogMapper::toResponseDto);

    return ResponseEntity.ok(auditLogs);
  }

  /**
   * Verifies HMAC integrity of audit log entries within a date range.
   *
   * <p>Recomputes the HMAC-SHA256 signature for each entry and compares it to the stored value.
   * Returns a summary report indicating whether any tampered entries were detected. This endpoint
   * is restricted to Global Admins only, as it is a system-level security operation.
   *
   * @param from start of the verification window (inclusive, ISO-8601)
   * @param to end of the verification window (exclusive, ISO-8601)
   * @return integrity verification report
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/integrity-check")
  @Operation(
      summary = "Verify audit log integrity",
      description =
          "Recomputes HMAC-SHA256 signatures for audit log entries in the specified date "
              + "range and reports any tampered or unsigned entries. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Integrity check completed"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<AuditIntegrityService.IntegrityReport> checkIntegrity(
      @Parameter(description = "Start of verification window (inclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @Parameter(description = "End of verification window (exclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to) {

    AuditIntegrityService.IntegrityReport report = auditIntegrityService.verifyRange(from, to);
    return ResponseEntity.ok(report);
  }

  /**
   * Verifies the HMAC integrity of a single audit log entry by its ID.
   *
   * <p>Recomputes the HMAC-SHA256 signature for the entry and compares it to the stored value.
   * Intended for targeted forensic analysis and developer experimentation -- pass any audit log ID
   * to instantly verify its integrity or confirm a tamper is detected.
   *
   * <p>Response status values:
   *
   * <ul>
   *   <li>{@code OK} - HMAC matches; entry is intact
   *   <li>{@code INTEGRITY_VIOLATION_DETECTED} - HMAC mismatch; entry was modified
   *   <li>{@code UNSIGNED} - entry has no HMAC (created before signing was enabled)
   *   <li>{@code NOT_FOUND} - no entry with the given ID (totalEntries=0)
   * </ul>
   *
   * @param id the audit log entry ID to verify
   * @return integrity verification report (totalEntries=1 when found, 0 when not found)
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/{id}/integrity-check")
  @Operation(
      summary = "Verify integrity of a single audit log entry",
      description =
          "Recomputes the HMAC-SHA256 signature for a single audit log entry and compares "
              + "it to the stored value. Returns OK if the entry is intact, "
              + "INTEGRITY_VIOLATION_DETECTED if modified, UNSIGNED if not signed, "
              + "or NOT_FOUND if the ID does not exist. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Integrity check completed"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<AuditIntegrityService.IntegrityReport> checkSingleEntryIntegrity(
      @Parameter(description = "Audit log entry ID to verify", required = true, example = "42")
          @PathVariable
          Long id) {

    AuditIntegrityService.IntegrityReport report = auditIntegrityService.verifySingle(id);
    return ResponseEntity.ok(report);
  }

  /**
   * Verifies the chain checkpoint integrity for a date range.
   *
   * <p>Recomputes entries_digest for each checkpoint window and verifies chain linkage between
   * consecutive checkpoints. Detects entry insertion, deletion, reordering, and checkpoint
   * tampering. This endpoint is restricted to Global Admins only.
   *
   * @param from start of the verification range (inclusive, ISO-8601)
   * @param to end of the verification range (exclusive, ISO-8601)
   * @return chain verification report
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/chain-integrity")
  @Operation(
      summary = "Verify audit chain checkpoint integrity",
      description =
          "Verifies chain checkpoint integrity by recomputing entry digests and validating "
              + "chain linkage. Detects entry insertion, deletion, reordering, and checkpoint "
              + "tampering. Global Admin only.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Chain verification completed"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<AuditChainVerificationService.ChainVerificationReport> checkChainIntegrity(
      @Parameter(description = "Start of verification range (inclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime from,
      @Parameter(description = "End of verification range (exclusive, ISO-8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          OffsetDateTime to) {

    AuditChainVerificationService.ChainVerificationReport report =
        auditChainVerificationService.verifyChain(from, to);
    return ResponseEntity.ok(report);
  }

  /**
   * Seals an audit chain period prior to archiving and dropping the corresponding DB partition.
   *
   * <p>Marks all chain checkpoints in the specified period as {@code ARCHIVE_SEAL}. Future {@code
   * verifyChain()} calls will skip entries_digest re-computation for sealed checkpoints (entries no
   * longer in DB by design) while still verifying the chain_hmac linkage.
   *
   * <p><b>Pre-condition:</b> All checkpoints in the period must pass integrity verification. The
   * operation is rejected if any violation is detected.
   *
   * <p><b>Post-condition:</b> The DBA may safely {@code DROP} the audit log partition. Include the
   * returned {@code sealChainHmac} in the Git archive manifest for long-term provenance.
   *
   * @param request period and justification for the archive seal
   * @return seal result with the last chain HMAC and metadata
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping("/lifecycle/seal-archive")
  @Operation(
      summary = "Seal an audit chain period for archival",
      description =
          "Marks all chain checkpoints in the specified period as ARCHIVE_SEAL prior to dropping "
              + "the corresponding DB partition. Runs a mandatory pre-flight integrity check — "
              + "rejected if any violation is detected. Returns the seal HMAC to include in the "
              + "Git archive manifest. Global Admin only.\n\n"
              + "**Period identification — two alternative modes:**\n"
              + "- **Timestamp mode**: provide `periodStart` (inclusive) and `periodEnd` "
              + "(exclusive) as ISO-8601 timestamps.\n"
              + "- **Checkpoint ID mode**: provide `checkpointIdFrom` and `checkpointIdTo` "
              + "(both inclusive integers). The service resolves the effective time range from "
              + "those checkpoints automatically. Ergonomic when working directly with the "
              + "database — short IDs are easier to read than full timestamps.\n\n"
              + "Exactly one mode must be used. Providing both is an error.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Period sealed successfully"),
        @ApiResponse(
            responseCode = "400",
            description =
                "Invalid request: conflicting modes, missing required fields, or no checkpoints "
                    + "found for the given ID range"),
        @ApiResponse(
            responseCode = "409",
            description = "Chain integrity violation detected — resolve before sealing"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<ArchiveSealResult> sealArchive(
      @Valid @RequestBody ArchiveSealRequest request) {
    try {
      ArchiveSealResult result = auditLifecycleService.sealArchive(request);
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    }
  }

  /**
   * Formally declares a downtime gap in the audit chain.
   *
   * <p>Creates a single {@code GAP_DECLARATION} checkpoint spanning the full gap period and signs
   * it into the chain with the provided justification. The next scheduler tick will chain its
   * regular checkpoints from the gap declaration, restoring continuity.
   *
   * <p><b>Operational constraint:</b> Must be called before the scheduler creates regular
   * checkpoints for the gap period (i.e., within the lookback window after system restart). The
   * request is rejected if conflicting checkpoints already exist.
   *
   * @param request gap period boundaries and downtime justification
   * @return gap declaration result with the new checkpoint ID and chain HMAC
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @PostMapping("/lifecycle/declare-gap")
  @Operation(
      summary = "Declare a downtime gap in the audit chain",
      description =
          "Creates a single GAP_DECLARATION checkpoint covering the specified period and signs "
              + "it into the chain with the admin's justification. Use when the system was "
              + "offline longer than the scheduler lookback window. Must be called before the "
              + "scheduler fills the gap with regular checkpoints. Global Admin only.\n\n"
              + "**Gap start — two alternative modes:**\n"
              + "- **Timestamp mode**: provide `gapStart` as an ISO-8601 timestamp (the moment "
              + "the system went offline).\n"
              + "- **Anchor checkpoint mode**: provide `anchorCheckpointId`, the "
              + "`checkpoint_id` of the last checkpoint recorded before the downtime. The "
              + "service derives `gapStart = anchorCheckpoint.window_end` automatically. "
              + "Ergonomic when working directly with the database — look up the last "
              + "checkpoint ID before the gap and pass it directly, no timestamp extraction "
              + "needed.\n\n"
              + "`gapEnd` is always a timestamp when provided. In anchor checkpoint mode it is "
              + "**optional**: if omitted, the service auto-derives it as the `window_start` of "
              + "the first checkpoint that exists after the anchor (the boundary between the "
              + "undeclared gap and the scheduler's catch-up checkpoints). If no such checkpoint "
              + "exists yet, falls back to the start of the current 5-minute window. Exactly one "
              + "of `gapStart` or `anchorCheckpointId` must be provided.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Gap declared successfully"),
        @ApiResponse(
            responseCode = "400",
            description =
                "Invalid request: conflicting modes, missing required fields, audit entries "
                    + "found in gap period, or anchor checkpoint not found"),
        @ApiResponse(
            responseCode = "409",
            description = "Conflicting regular checkpoints already exist in the gap period"),
        @ApiResponse(responseCode = "401", description = "Not authenticated"),
        @ApiResponse(responseCode = "403", description = "Not a Global Admin")
      })
  public ResponseEntity<GapDeclarationResult> declareGap(
      @Valid @RequestBody GapDeclarationRequest request) {
    try {
      GapDeclarationResult result = auditLifecycleService.declareGap(request);
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
    } catch (IllegalStateException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
    }
  }

  /**
   * Extracts the tenant ID of the authenticated administrator for tenant scoping.
   *
   * <p>Returns the tenant ID from the {@link AdminPrincipal} if the caller is a Tenant Admin, or
   * {@code null} for Global Admin (who can see all tenants).
   *
   * @return tenant ID for Tenant Admin, {@code null} for Global Admin
   */
  private Integer extractRequesterTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth.getPrincipal() == null) {
      return null;
    }
    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      return adminPrincipal.tenantId();
    }
    return null;
  }
}
