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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.TenantActivateRequestDto;
import org.ezkey.admin.dto.request.TenantCreateRequestDto;
import org.ezkey.admin.dto.request.TenantDeactivateRequestDto;
import org.ezkey.admin.dto.request.TenantUpdateRequestDto;
import org.ezkey.admin.dto.response.TenantResponseDto;
import org.ezkey.admin.mapper.TenantMapper;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.TenantService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 *   <li><b>POST /api/v1/tenants</b> - Create a new tenant
 *   <li><b>GET /api/v1/tenants</b> - List all tenants
 *   <li><b>GET /api/v1/tenants/{id}</b> - Get tenant by ID
 *   <li><b>PUT /api/v1/tenants/{id}</b> - Update a tenant
 *   <li><b>POST /api/v1/tenants/{id}/deactivate</b> - Deactivate a tenant
 *   <li><b>POST /api/v1/tenants/{id}/activate</b> - Activate a tenant (reactivation)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Validated
@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenants", description = "Tenant management API")
public class TenantController {

  private static final Logger logger = LoggerFactory.getLogger(TenantController.class);

  private final AdminProvisioningService provisioningService;
  private final TenantRepository tenantRepository;
  private final TenantService tenantService;
  private final TenantMapper tenantMapper;
  private final AuditLogService auditLogService;

  public TenantController(
      AdminProvisioningService provisioningService,
      TenantRepository tenantRepository,
      TenantService tenantService,
      TenantMapper tenantMapper,
      AuditLogService auditLogService) {
    this.provisioningService = provisioningService;
    this.tenantRepository = tenantRepository;
    this.tenantService = tenantService;
    this.tenantMapper = tenantMapper;
    this.auditLogService = auditLogService;
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
      @Valid @RequestBody TenantCreateRequestDto request,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      Tenant tenant =
          provisioningService.createTenant(
              request.tenantName(),
              request.tenantDescription(),
              request.organizationName(),
              request.organizationDomain(),
              request.countryCode(),
              request.timezone(),
              request.primaryContactName(),
              request.primaryContactEmail(),
              principal);

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.TENANT_CREATED,
                  AdminAuditConstants.TENANT_CREATED,
                  tenant.getTenantId())
              .eventStatus(EventStatus.SUCCESS)
              .adminId(principal.adminId())
              .eventDetails("Tenant name: " + tenant.getTenantName())
              .build());

      TenantResponseDto response = tenantMapper.toResponseDto(tenant);
      return ResponseEntity.created(URI.create("/api/v1/tenants/" + tenant.getTenantId()))
          .body(response);

    } catch (Exception e) {
      logger.warn("Tenant creation failed: {}", e.getMessage());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.TENANT_CREATED, AdminAuditConstants.TENANT_CREATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal != null ? principal.adminId() : null)
              .errorMessage(e.getMessage())
              .build());
      throw e;
    }
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
      tenants = tenantMapper.toResponseDtoList(tenantRepository.findAll());
    } else if (principal.tenantId() != null) {
      tenants =
          tenantRepository
              .findById(principal.tenantId())
              .map(tenantMapper::toResponseDto)
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
        .map(tenantMapper::toResponseDto)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /**
   * Updates a tenant with partial-update semantics.
   *
   * <p>Only global administrators can update tenants. Only non-null fields in the request body are
   * applied.
   *
   * @param id the tenant ID
   * @param request the update request (partial fields)
   * @param auth the authentication context
   * @return ResponseEntity with updated tenant (200 OK)
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Update a tenant",
      description = "Updates a tenant (partial update). GlobalAdmin only.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Tenant updated"),
    @ApiResponse(responseCode = "400", description = "Invalid request"),
    @ApiResponse(responseCode = "403", description = "Forbidden"),
    @ApiResponse(responseCode = "404", description = "Tenant not found")
  })
  public ResponseEntity<TenantResponseDto> updateTenant(
      @Parameter(description = "Tenant ID", example = "1") @PathVariable("id") Integer id,
      @Valid @RequestBody TenantUpdateRequestDto request,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Tenant updated = tenantService.updateTenant(id, request, principal);

    auditLogService.log(
        AuditHelper.createAdminAudit(
                context, EventType.TENANT_UPDATED, AdminAuditConstants.TENANT_UPDATED, id)
            .eventStatus(EventStatus.SUCCESS)
            .adminId(principal.adminId())
            .eventDetails("Tenant ID: " + id)
            .build());

    return ResponseEntity.ok(tenantMapper.toResponseDto(updated));
  }

  /**
   * Deactivates a tenant and revokes all active admin tokens.
   *
   * <p>Only global administrators can deactivate tenants. The system tenant cannot be deactivated.
   * Deactivation prevents further operations for that tenant while preserving data for audit
   * purposes. All active admin tokens for the tenant are immediately revoked.
   *
   * @param id the tenant ID
   * @param auth the authentication context
   * @return ResponseEntity with no content (204 No Content)
   * @throws org.ezkey.admin.exception.TenantNotAllowedException if attempting to deactivate the
   *     system tenant (400, RFC 9457)
   * @throws org.ezkey.exception.ResourceNotFoundException if the tenant is not found (404)
   */
  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Deactivate a tenant",
      description =
          "Deactivates a tenant and revokes all active admin tokens for that tenant. GlobalAdmin"
              + " only. The system tenant cannot be deactivated.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Tenant deactivated successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Bad request - cannot deactivate the system tenant (RFC 9457 ProblemDetail)"),
    @ApiResponse(responseCode = "404", description = "Tenant not found"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not a global administrator")
  })
  public ResponseEntity<Void> deactivateTenant(
      @Parameter(description = "Tenant ID", example = "1") @PathVariable("id") Integer id,
      @RequestBody(required = false) @Valid TenantDeactivateRequestDto body,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    tenantService.deactivateTenant(id, principal);

    String reason = body != null ? body.reason() : null;
    auditLogService.log(
        AuditHelper.createAdminAudit(
                context, EventType.TENANT_DEACTIVATED, AdminAuditConstants.TENANT_DEACTIVATED, id)
            .eventStatus(EventStatus.SUCCESS)
            .adminId(principal.adminId())
            .reason(reason)
            .eventDetails("Tenant ID: " + id)
            .build());

    return ResponseEntity.noContent().build();
  }

  /**
   * Activates a tenant (reactivation after deactivation).
   *
   * <p>Only global administrators can activate tenants. Activation is idempotent: if the tenant is
   * already active, the request succeeds with no change. Deactivation metadata (deactivatedAt,
   * deactivatedByAdmin) is preserved for audit traceability.
   *
   * @param id the tenant ID
   * @param body optional request body with reason for audit
   * @param auth the authentication context
   * @return ResponseEntity with no content (204 No Content)
   * @throws org.ezkey.exception.ResourceNotFoundException if the tenant is not found (404)
   */
  @PostMapping("/{id}/activate")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Activate a tenant",
      description =
          "Activates a previously deactivated tenant. GlobalAdmin only. Idempotent if already"
              + " active.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Tenant activated successfully"),
    @ApiResponse(responseCode = "404", description = "Tenant not found"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not a global administrator")
  })
  public ResponseEntity<Void> activateTenant(
      @Parameter(description = "Tenant ID", example = "1") @PathVariable("id") Integer id,
      @RequestBody(required = false) @Valid TenantActivateRequestDto body,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    tenantService.activateTenant(id, principal);

    String reason = body != null ? body.reason() : null;
    auditLogService.log(
        AuditHelper.createAdminAudit(
                context, EventType.TENANT_ACTIVATED, AdminAuditConstants.TENANT_ACTIVATED, id)
            .eventStatus(EventStatus.SUCCESS)
            .adminId(principal.adminId())
            .reason(reason)
            .eventDetails("Tenant ID: " + id)
            .build());

    return ResponseEntity.noContent().build();
  }
}
