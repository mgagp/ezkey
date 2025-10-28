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
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.dto.AuditLogResponseDto;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for audit log query API.
 *
 * <p>This controller provides REST endpoints for querying audit logs for security monitoring,
 * compliance reporting, and forensic analysis. Supports filtering and pagination for efficient
 * access to audit data.
 *
 * <p><b>Admin API Endpoints:</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/audit-logs</b> - Query audit logs with filters and pagination
 * </ul>
 *
 * <p><b>Usage Context:</b> Part of the admin-api for security monitoring and SOC2 compliance
 * reporting. Provides comprehensive audit trail access for administrators.
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
   * Query audit logs with optional filters and pagination.
   *
   * <p>Retrieves audit logs matching the specified criteria with pagination support. All filter
   * parameters are optional - if none are provided, returns all audit logs (paginated).
   *
   * @param eventType optional event type filter
   * @param eventStatus optional event status filter
   * @param apiName optional API name filter
   * @param enrollmentId optional enrollment ID filter
   * @param adminId optional admin ID filter
   * @param page page number (zero-based, default 0)
   * @param size page size (default 20, max 100)
   * @return ResponseEntity containing page of audit logs
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  @Operation(
      summary = "Query audit logs",
      description =
          "Retrieves audit logs with optional filters and pagination for security monitoring and"
              + " compliance reporting")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
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
      @Parameter(description = "Page number (zero-based)") @RequestParam(defaultValue = "0")
          int page,
      @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {

    // Validate page size
    if (size < 1 || size > 100) {
      size = 20;
    }

    Pageable pageable = PageRequest.of(page, size);

    Page<AuditLogResponseDto> auditLogs =
        auditLogService
            .findByFilters(eventType, eventStatus, apiName, enrollmentId, adminId, pageable)
            .map(auditLogMapper::toResponseDto);

    return ResponseEntity.ok(auditLogs);
  }
}
