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
import java.time.OffsetDateTime;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

  /**
   * Constructs the audit log controller with required dependencies.
   *
   * @param auditLogService the audit log service
   * @param auditLogMapper the MapStruct mapper for entity-DTO conversions
   * @param auditIntegrityService the integrity verification service
   * @param auditChainVerificationService the chain checkpoint verification service
   */
  public AuditLogController(
      AuditLogService auditLogService,
      AuditLogMapper auditLogMapper,
      AuditIntegrityService auditIntegrityService,
      AuditChainVerificationService auditChainVerificationService) {
    this.auditLogService = auditLogService;
    this.auditLogMapper = auditLogMapper;
    this.auditIntegrityService = auditIntegrityService;
    this.auditChainVerificationService = auditChainVerificationService;
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
   * @param adminId optional admin ID filter
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
      @Parameter(description = "Filter by admin ID") @RequestParam(required = false)
          Integer adminId,
      @Parameter(
              description =
                  "Filter by tenant ID (Global Admin only). Ignored for Tenant Admin "
                      + "whose scope is enforced automatically.")
          @RequestParam(required = false)
          Integer tenantId,
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
                requesterTenantId,
                filterTenantId,
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
