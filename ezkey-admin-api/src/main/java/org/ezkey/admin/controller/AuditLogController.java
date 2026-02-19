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
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
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

  /**
   * Constructs the audit log controller with required dependencies.
   *
   * @param auditLogService the audit log service
   * @param auditLogMapper the MapStruct mapper for entity-DTO conversions
   */
  public AuditLogController(AuditLogService auditLogService, AuditLogMapper auditLogMapper) {
    this.auditLogService = auditLogService;
    this.auditLogMapper = auditLogMapper;
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
