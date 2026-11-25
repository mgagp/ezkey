/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AlertController
 * Description: REST controller exposing the operator-facing alert subsystem.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.ezkey.admin.dto.request.AlertResolveRequestDto;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.dto.AlertResponseDto;
import org.ezkey.alert.mapper.AlertMapper;
import org.ezkey.alert.service.AlertService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the operator-facing alert subsystem.
 *
 * <p>List and detail endpoints are instance-level and restricted to {@code GLOBAL_ADMIN}. Manual
 * resolution allows any authenticated admin when the target alert is visible in their scope.
 *
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(
    name = "Alerts",
    description =
        "Operator-facing alert subsystem. List/detail are Global Admin only; manual resolution"
            + " is available for supported alert types within caller visibility.")
public class AlertController {

  private final AlertService alertService;
  private final AlertMapper alertMapper;
  private final AccessControlService accessControlService;

  /**
   * Constructs the controller.
   *
   * @param alertService the alert service
   * @param alertMapper entity → DTO mapper
   * @param accessControlService tenant access checks
   */
  public AlertController(
      AlertService alertService,
      AlertMapper alertMapper,
      AccessControlService accessControlService) {
    this.alertService = alertService;
    this.alertMapper = alertMapper;
    this.accessControlService = accessControlService;
  }

  /**
   * Paginated alert search with optional filters.
   *
   * @param status optional status filter ({@code OPEN} / {@code RESOLVED})
   * @param alertType optional alert-type filter
   * @param severity optional severity filter
   * @param dedupeKey optional exact dedupe-key match (debug / forensic aid)
   * @param createdAfter inclusive lower bound on {@code createdAt}
   * @param createdBefore inclusive upper bound on {@code createdAt}
   * @param pageable pagination/sort parameters (default: {@code createdAt,DESC}, size 20)
   * @return matching alerts as a page of response DTOs
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping
  @Operation(
      summary = "List alerts",
      description =
          "Returns a paginated list of operator-facing alerts. Supports filtering by status,"
              + " alert type, severity, dedupe key, and creation-time range. Default sort is by"
              + " createdAt descending (newest first). Sortable fields: createdAt, lastSeenAt,"
              + " severity, status. Restricted to Global Admin.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Alerts retrieved"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)")
  })
  public ResponseEntity<Page<AlertResponseDto>> listAlerts(
      @Parameter(description = "Filter by alert status") @RequestParam(required = false)
          AlertStatus status,
      @Parameter(description = "Filter by alert type") @RequestParam(required = false)
          AlertType alertType,
      @Parameter(description = "Filter by severity") @RequestParam(required = false)
          AlertSeverity severity,
      @Parameter(description = "Exact dedupe-key match (debug / forensic aid)")
          @RequestParam(required = false)
          String dedupeKey,
      @Parameter(description = "Filter by creation time (inclusive start), ISO-8601")
          @RequestParam(required = false)
          OffsetDateTime createdAfter,
      @Parameter(description = "Filter by creation time (inclusive end), ISO-8601")
          @RequestParam(required = false)
          OffsetDateTime createdBefore,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<AlertResponseDto> page =
        alertService
            .search(status, alertType, severity, dedupeKey, createdAfter, createdBefore, pageable)
            .map(alertMapper::toResponseDto);
    return ResponseEntity.ok(page);
  }

  /**
   * Returns a single alert by id.
   *
   * @param alertId database identifier
   * @return 200 with the alert when present, 404 otherwise
   */
  @PreAuthorize("hasRole('GLOBAL_ADMIN')")
  @GetMapping("/{alertId}")
  @Operation(
      summary = "Get alert by id",
      description = "Returns the alert with the given id. Restricted to Global Admin.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Alert returned"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "403", description = "Forbidden (not a Global Admin)"),
    @ApiResponse(responseCode = "404", description = "Alert not found")
  })
  public ResponseEntity<AlertResponseDto> getAlert(@PathVariable Long alertId) {
    return alertService
        .findById(alertId)
        .map(alertMapper::toResponseDto)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Manually resolves an open alert when the type supports manual closure.
   *
   * @param alertId database identifier
   * @param request optional request body carrying a short operator note
   * @param authentication authenticated admin context
   * @return 200 with resolved alert, 404 when missing/not visible, 409 when not resolvable
   */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{alertId}/resolve")
  @Operation(
      summary = "Manually resolve alert",
      description =
          "Resolves an OPEN alert when its type supports manual resolution. Returns 409 for"
              + " automatic-resolution types or already-resolved alerts. Tenant Admins can resolve"
              + " only alerts visible in their tenant scope.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Alert resolved"),
    @ApiResponse(responseCode = "401", description = "Not authenticated"),
    @ApiResponse(responseCode = "404", description = "Alert not found or not visible"),
    @ApiResponse(responseCode = "409", description = "Alert cannot be manually resolved")
  })
  public ResponseEntity<AlertResponseDto> resolveAlert(
      @PathVariable Long alertId,
      @Valid @RequestBody(required = false) AlertResolveRequestDto request,
      Authentication authentication) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(authentication);
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    Alert alert =
        alertService
            .findById(alertId)
            .filter(existing -> canSeeAlert(authentication, principal, existing))
            .orElse(null);
    if (alert == null) {
      return ResponseEntity.notFound().build();
    }
    if (alert.getStatus() != AlertStatus.OPEN || !alert.getAlertType().supportsManualResolution()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    String note = normalizeNote(request != null ? request.note() : null);
    return alertService
        .resolveOpenById(alertId, AlertResolutionReason.MANUAL, principal.adminId(), note)
        .map(alertMapper::toResponseDto)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).build());
  }

  private boolean canSeeAlert(
      Authentication authentication, AdminPrincipal principal, Alert alert) {
    if (principal.isGlobalAdmin()) {
      return true;
    }
    if (!principal.isTenantAdmin()) {
      return false;
    }
    if (alert.getAlertType() != AlertType.AUTH_ATTEMPT_REJECTED) {
      return false;
    }
    Integer enrollmentId = enrollmentIdFromAuthAttemptRejectedDedupeKey(alert.getDedupeKey());
    return enrollmentId != null
        && accessControlService.canAccessEnrollment(authentication, enrollmentId);
  }

  private Integer enrollmentIdFromAuthAttemptRejectedDedupeKey(String dedupeKey) {
    if (dedupeKey == null || !dedupeKey.startsWith("AUTH_ATTEMPT_REJECTED:")) {
      return null;
    }
    String[] parts = dedupeKey.split(":", 2);
    if (parts.length != 2 || parts[1].isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(parts[1]);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static String normalizeNote(String note) {
    if (note == null) {
      return null;
    }
    String trimmed = note.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
