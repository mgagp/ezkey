/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: TenantController
 * Description: REST controller for managing tenants.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.ezkey.admin.dto.request.TenantCreateRequestDto;
import org.ezkey.admin.dto.response.TenantResponseDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for tenant management API v1.
 *
 * <p>This controller provides REST endpoints for tenant management operations. Only global
 * administrators can create and manage tenants.
 *
 * <p><b>Endpoints:</b>
 *
 * <ul>
 *   <li><b>POST /api/v1/tenants</b> - Create a new tenant (GlobalAdmin only)
 *   <li><b>GET /api/v1/tenants</b> - List all tenants (GlobalAdmin only)
 *   <li><b>GET /api/v1/tenants/{id}</b> - Get tenant by ID (GlobalAdmin only)
 *   <li><b>POST /api/v1/tenants/{id}/deactivate</b> - Deactivate a tenant (GlobalAdmin only)
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
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenants", description = "Tenant management API")
public class TenantController {

  private final AdminProvisioningService provisioningService;
  private final TenantRepository tenantRepository;

  public TenantController(
      AdminProvisioningService provisioningService, TenantRepository tenantRepository) {
    this.provisioningService = provisioningService;
    this.tenantRepository = tenantRepository;
  }

  /**
   * Creates a new tenant.
   *
   * <p>Only global administrators can create tenants. The tenant is created with the specified name
   * and description.
   *
   * @param request the tenant creation request
   * @param auth the authentication context
   * @return ResponseEntity with created tenant (201 Created)
   */
  @PostMapping
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Create a new tenant",
      description = "Creates a new tenant. GlobalAdmin only.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Tenant created successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request or tenant name already exists"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not a global administrator")
  })
  public ResponseEntity<TenantResponseDto> createTenant(
      @Valid @RequestBody TenantCreateRequestDto request, Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Tenant tenant =
        provisioningService.createTenant(
            request.tenantName(), request.tenantDescription(), principal);

    TenantResponseDto response =
        new TenantResponseDto(
            tenant.getTenantId(),
            tenant.getTenantName(),
            tenant.getTenantDescription(),
            tenant.getCreatedAt(),
            tenant.getActive());

    return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.getTenantId()))
        .body(response);
  }

  /**
   * Lists tenants.
   *
   * <p>Global administrators can list all tenants. Tenant administrators cannot access this
   * endpoint.
   *
   * @param auth the authentication context
   * @return ResponseEntity with list of tenants (200 OK)
   */
  @GetMapping
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "List tenants",
      description =
          "Lists tenants. GlobalAdmins see all tenants. TenantAdmins see only their own tenant.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "List of tenants"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized")
  })
  public ResponseEntity<List<TenantResponseDto>> listTenants(Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    List<TenantResponseDto> tenants;
    if (principal.isGlobalAdmin()) {
      // GlobalAdmin: list all tenants
      tenants =
          tenantRepository.findAll().stream()
              .map(
                  tenant ->
                      new TenantResponseDto(
                          tenant.getTenantId(),
                          tenant.getTenantName(),
                          tenant.getTenantDescription(),
                          tenant.getCreatedAt(),
                          tenant.getActive()))
              .collect(Collectors.toList());
    } else if (principal.tenantId() != null) {
      // TenantAdmin: list only their own tenant
      tenants =
          tenantRepository
              .findById(principal.tenantId())
              .map(
                  tenant ->
                      new TenantResponseDto(
                          tenant.getTenantId(),
                          tenant.getTenantName(),
                          tenant.getTenantDescription(),
                          tenant.getCreatedAt(),
                          tenant.getActive()))
              .map(List::of)
              .orElse(List.of());
    } else {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return ResponseEntity.ok(tenants);
  }

  /**
   * Gets a tenant by ID.
   *
   * <p>Only global administrators can view tenants. Tenant administrators cannot access this
   * endpoint.
   *
   * @param id the tenant ID
   * @param auth the authentication context
   * @return ResponseEntity with tenant (200 OK) or 404 Not Found
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Get tenant by ID",
      description =
          "Gets a tenant by ID. GlobalAdmins can access any tenant. TenantAdmins can only access"
              + " their own tenant.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tenant found"),
    @ApiResponse(responseCode = "404", description = "Tenant not found"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized")
  })
  public ResponseEntity<TenantResponseDto> getTenant(
      @Parameter(description = "Tenant ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // TenantAdmin can only access their own tenant
    if (!principal.isGlobalAdmin() && principal.tenantId() != null) {
      if (!principal.tenantId().equals(id)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    return tenantRepository
        .findById(id)
        .map(
            tenant ->
                new TenantResponseDto(
                    tenant.getTenantId(),
                    tenant.getTenantName(),
                    tenant.getTenantDescription(),
                    tenant.getCreatedAt(),
                    tenant.getActive()))
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Deactivates a tenant.
   *
   * <p>Only global administrators can deactivate tenants. Deactivation prevents further operations
   * for that tenant while preserving data for audit purposes.
   *
   * @param id the tenant ID
   * @param auth the authentication context
   * @return ResponseEntity with no content (204 No Content) or 404 Not Found
   */
  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Deactivate a tenant",
      description = "Deactivates a tenant. GlobalAdmin only. Required in Phase 1.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Tenant deactivated successfully"),
    @ApiResponse(responseCode = "404", description = "Tenant not found"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not a global administrator")
  })
  public ResponseEntity<Void> deactivateTenant(
      @Parameter(description = "Tenant ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    return tenantRepository
        .findById(id)
        .map(
            tenant -> {
              tenant.setActive(false);
              tenantRepository.save(tenant);
              return ResponseEntity.noContent().<Void>build();
            })
        .orElse(ResponseEntity.notFound().build());
  }
}
