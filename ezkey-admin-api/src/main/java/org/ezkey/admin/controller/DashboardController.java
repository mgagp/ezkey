/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: DashboardController
 * Description: REST controller for dashboard overview and aggregated stats.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ezkey.admin.dto.response.DashboardOverviewDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the Admin UI dashboard.
 *
 * <p>Provides a single overview endpoint that consolidates integration, enrollment, auth-24h stats,
 * recent activity, and (for Global Admin) instance-level alerts (e.g. audit chain gap pending).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Dashboard overview and aggregated stats for Admin UI")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  /**
   * Returns the aggregated dashboard overview for the current admin.
   *
   * <p>Tenant Admin: tenant-scoped stats and recent activity. Global Admin: instance-wide stats,
   * recent activity, and optional alerts (e.g. AUDIT_CHAIN_GAP_PENDING).
   *
   * @return dashboard overview DTO
   */
  @Operation(
      summary = "Get dashboard overview",
      description =
          "Returns aggregated stats (integrations, enrollments, auth 24h terminal-outcome health),"
              + " recent activity, and for Global Admin only: instance-level alerts (e.g. audit"
              + " chain gap pending). Designed for 60s refresh interval.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Dashboard overview"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
      })
  @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/overview")
  public ResponseEntity<DashboardOverviewDto> getOverview() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null) {
      return ResponseEntity.status(401).build();
    }
    DashboardOverviewDto overview = dashboardService.buildOverview(principal);
    return ResponseEntity.ok(overview);
  }
}
