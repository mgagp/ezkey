/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuditController
 * Description: REST controller for querying audit logs
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ezkey.admin.dto.audit.AuditLogDto;
import org.ezkey.admin.mapper.AuditLogMapper;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for audit log querying and management.
 * <p>
 * This controller provides endpoints for administrators to query and analyze
 * audit logs for security monitoring, compliance reporting, and forensic analysis.
 * Supports pagination and filtering by various criteria.
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "Audit Logs", description = "Audit log query and management API")
public class AuditController {

    private final AuditService auditService;
    private final AuditLogMapper auditLogMapper;

    public AuditController(AuditService auditService, AuditLogMapper auditLogMapper) {
        this.auditService = auditService;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * Retrieve all audit logs with pagination.
     *
     * @param page     page number (0-indexed)
     * @param size     page size
     * @return paginated list of audit logs
     */
    @GetMapping
    @Operation(summary = "Get all audit logs", 
               description = "Retrieves paginated audit logs for compliance and security monitoring")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogDto>> getAllAuditLogs(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.findAll(pageable);
        Page<AuditLogDto> response = auditLogs.map(auditLogMapper::toDto);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve audit logs filtered by event type.
     *
     * @param eventType the event type to filter by
     * @param page      page number (0-indexed)
     * @param size      page size
     * @return paginated list of filtered audit logs
     */
    @GetMapping("/by-event-type")
    @Operation(summary = "Get audit logs by event type", 
               description = "Retrieves audit logs filtered by event type (e.g., AUTH_ATTEMPT, ENROLLMENT)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogDto>> getAuditLogsByEventType(
            @Parameter(description = "Event type to filter by", example = "AUTH_ATTEMPT")
            @RequestParam String eventType,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.findByEventType(eventType, pageable);
        Page<AuditLogDto> response = auditLogs.map(auditLogMapper::toDto);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve audit logs filtered by event status.
     *
     * @param eventStatus the event status to filter by
     * @param page        page number (0-indexed)
     * @param size        page size
     * @return paginated list of filtered audit logs
     */
    @GetMapping("/by-event-status")
    @Operation(summary = "Get audit logs by event status", 
               description = "Retrieves audit logs filtered by status (SUCCESS, FAILURE, ERROR)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogDto>> getAuditLogsByEventStatus(
            @Parameter(description = "Event status to filter by", example = "FAILURE")
            @RequestParam String eventStatus,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.findByEventStatus(eventStatus, pageable);
        Page<AuditLogDto> response = auditLogs.map(auditLogMapper::toDto);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve audit logs filtered by API name.
     *
     * @param apiName the API name to filter by
     * @param page    page number (0-indexed)
     * @param size    page size
     * @return paginated list of filtered audit logs
     */
    @GetMapping("/by-api")
    @Operation(summary = "Get audit logs by API name", 
               description = "Retrieves audit logs filtered by API (ADMIN_API or AUTH_API)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogDto>> getAuditLogsByApiName(
            @Parameter(description = "API name to filter by", example = "ADMIN_API")
            @RequestParam String apiName,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.findByApiName(apiName, pageable);
        Page<AuditLogDto> response = auditLogs.map(auditLogMapper::toDto);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve audit logs for a specific enrollment.
     *
     * @param enrollmentId the enrollment ID
     * @param page         page number (0-indexed)
     * @param size         page size
     * @return paginated list of audit logs for the enrollment
     */
    @GetMapping("/by-enrollment/{enrollmentId}")
    @Operation(summary = "Get audit logs by enrollment ID", 
               description = "Retrieves audit logs for a specific enrollment")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogDto>> getAuditLogsByEnrollment(
            @Parameter(description = "Enrollment ID", example = "1")
            @PathVariable Integer enrollmentId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.findByEnrollmentId(enrollmentId, pageable);
        Page<AuditLogDto> response = auditLogs.map(auditLogMapper::toDto);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieve audit logs for a specific admin.
     *
     * @param adminId the admin ID
     * @param page    page number (0-indexed)
     * @param size    page size
     * @return paginated list of audit logs for the admin
     */
    @GetMapping("/by-admin/{adminId}")
    @Operation(summary = "Get audit logs by admin ID", 
               description = "Retrieves audit logs for a specific administrator")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Audit logs retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditLogDto>> getAuditLogsByAdmin(
            @Parameter(description = "Admin ID", example = "1")
            @PathVariable Integer adminId,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.findByAdminId(adminId, pageable);
        Page<AuditLogDto> response = auditLogs.map(auditLogMapper::toDto);
        return ResponseEntity.ok(response);
    }
}
