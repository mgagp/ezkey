/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AlertController
 * Description: REST controller exposing the operator-facing alert subsystem (Global Admin only).
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.dto.AlertResponseDto;
import org.ezkey.alert.mapper.AlertMapper;
import org.ezkey.alert.service.AlertService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the operator-facing alert subsystem.
 *
 * <p>Alerts are instance-level and surface internal Ezkey signals (e.g. undeclared audit chain
 * gaps). Endpoints are restricted to {@code GLOBAL_ADMIN}; tenant administrators receive 403.
 *
 * <p>This iteration exposes read-only endpoints. Resolution happens automatically through the
 * matching producer workflow (e.g. declaring an audit chain gap auto-resolves the {@code
 * AUDIT_CHAIN_GAP_PENDING} alert). Manual resolve / acknowledge endpoints will be added when a
 * non-auto-closing alert type is introduced.
 *
 * @since 2026
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(
    name = "Alerts",
    description =
        "Operator-facing alert subsystem (Global Admin only). Surfaces internal Ezkey signals"
            + " such as undeclared audit chain gaps; resolved automatically by the matching"
            + " producer workflow.")
public class AlertController {

  private final AlertService alertService;
  private final AlertMapper alertMapper;

  /**
   * Constructs the controller.
   *
   * @param alertService the alert service
   * @param alertMapper entity → DTO mapper
   */
  public AlertController(AlertService alertService, AlertMapper alertMapper) {
    this.alertService = alertService;
    this.alertMapper = alertMapper;
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
}
