/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AdminProvisioningController
 * Description: REST controller for provisioning administrators.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.ezkey.admin.dto.request.AdminCreateRequestDto;
import org.ezkey.admin.dto.response.AdminProvisioningResponseDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminProvisioningService.ProvisioningResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for administrator provisioning API v1.
 *
 * <p>This controller provides REST endpoints for creating administrators (global and tenant admins)
 * with proper limits enforcement and onboarding credentials.
 *
 * <p><b>Endpoints:</b>
 *
 * <ul>
 *   <li><b>POST /api/v1/admins/global</b> - Create a peer global administrator (GlobalAdmin only)
 *   <li><b>POST /api/v1/admins/tenant</b> - Create a peer tenant administrator (GlobalAdmin or
 *       TenantAdmin for same tenant)
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
@RequestMapping("/api/v1/admins")
@Tag(name = "Administrator Provisioning", description = "Administrator provisioning API")
public class AdminProvisioningController {

  private final AdminProvisioningService provisioningService;

  public AdminProvisioningController(AdminProvisioningService provisioningService) {
    this.provisioningService = provisioningService;
  }

  /**
   * Creates a new global administrator (peer admin).
   *
   * <p>Only global administrators can create other global administrators. The operation enforces
   * maximum limit and returns onboarding credentials (enrollment proof token, challenge code, and
   * recovery codes).
   *
   * @param request the admin creation request
   * @param auth the authentication context
   * @return ResponseEntity with provisioning result including onboarding credentials (201 Created)
   */
  @PostMapping("/global")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Create a peer global administrator",
      description = "Creates a new global administrator. GlobalAdmin only. Enforces max limit.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Global administrator created successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request, limit exceeded, or username exists"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not a global administrator")
  })
  public ResponseEntity<AdminProvisioningResponseDto> createGlobalAdmin(
      @Valid @RequestBody AdminCreateRequestDto request, Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Validate required fields for global admin (SOC 2 compliance)
    if (request.email() == null || request.email().isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
    if (request.firstName() == null || request.firstName().isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
    if (request.lastName() == null || request.lastName().isBlank()) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    ProvisioningResult result =
        provisioningService.createGlobalAdmin(
            request.username(),
            request.email(),
            request.firstName(),
            request.lastName(),
            principal);

    AdminProvisioningResponseDto response =
        new AdminProvisioningResponseDto(
            result.admin().getAdminId(),
            result.admin().getUsername(),
            result.admin().getEmail(),
            result.admin().getFirstName(),
            result.admin().getLastName(),
            result.admin().getAdminType().name(),
            result.admin().getTenant() != null ? result.admin().getTenant().getTenantId() : null,
            result.enrollment().getEnrollmentId(),
            result.enrollmentProofToken(),
            result.enrollmentChallenge(),
            result.recoveryCodes(),
            result.admin().getCreatedAt());

    return ResponseEntity.created(URI.create("/api/v1/admins/" + result.admin().getAdminId()))
        .body(response);
  }

  /**
   * Creates a new tenant administrator (peer admin).
   *
   * <p>Global administrators can create tenant admins for any tenant. Tenant administrators can
   * create peer tenant admins for their own tenant only. The operation enforces maximum limit and
   * returns onboarding credentials.
   *
   * @param request the admin creation request (must include tenantId)
   * @param auth the authentication context
   * @return ResponseEntity with provisioning result including onboarding credentials (201 Created)
   */
  @PostMapping("/tenant")
  @PreAuthorize("hasAnyRole('ROLE_GLOBAL_ADMIN', 'ROLE_TENANT_ADMIN')")
  @Operation(
      summary = "Create a peer tenant administrator",
      description =
          "Creates a new tenant administrator. GlobalAdmin can create for any tenant. TenantAdmin"
              + " can create for same tenant only. Enforces max limit.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tenant administrator created successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request, limit exceeded, username exists, or tenant not found"),
    @ApiResponse(
        responseCode = "403",
        description =
            "Forbidden - not authorized or tenant admin trying to create for different tenant")
  })
  public ResponseEntity<AdminProvisioningResponseDto> createTenantAdmin(
      @Valid @RequestBody AdminCreateRequestDto request, Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Validate tenantId is provided
    if (request.tenantId() == null) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    ProvisioningResult result =
        provisioningService.createTenantAdmin(
            request.username(),
            request.email(),
            request.firstName(),
            request.lastName(),
            request.tenantId(),
            principal);

    AdminProvisioningResponseDto response =
        new AdminProvisioningResponseDto(
            result.admin().getAdminId(),
            result.admin().getUsername(),
            result.admin().getEmail(),
            result.admin().getFirstName(),
            result.admin().getLastName(),
            result.admin().getAdminType().name(),
            result.admin().getTenant() != null ? result.admin().getTenant().getTenantId() : null,
            result.enrollment().getEnrollmentId(),
            result.enrollmentProofToken(),
            result.enrollmentChallenge(),
            result.recoveryCodes(),
            result.admin().getCreatedAt());

    return ResponseEntity.created(URI.create("/api/v1/admins/" + result.admin().getAdminId()))
        .body(response);
  }

  /**
   * Deactivates an administrator.
   *
   * <p>Only global administrators can deactivate administrators. Deactivation enforces minimum
   * limits to prevent lockout and revokes all active tokens.
   *
   * @param id the administrator ID
   * @param auth the authentication context
   * @return ResponseEntity with no content (204 No Content) or 404 Not Found
   */
  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Deactivate an administrator",
      description =
          "Deactivates an administrator. GlobalAdmin only. Enforces min limits and revokes tokens.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Administrator deactivated successfully"),
    @ApiResponse(responseCode = "400", description = "Cannot deactivate below minimum limit"),
    @ApiResponse(responseCode = "404", description = "Administrator not found"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not a global administrator")
  })
  public ResponseEntity<Void> deactivateAdmin(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // TODO: Implement deactivation logic with min limit enforcement and token revocation
    // This will be implemented in the deactivation-revocation todo
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }
}
