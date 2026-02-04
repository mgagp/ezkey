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
import org.ezkey.admin.dto.response.AdminOnboardingResponseDto;
import org.ezkey.admin.dto.response.AdminProvisioningResponseDto;
import org.ezkey.admin.dto.response.AdminResponseDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminProvisioningService.OnboardingCredentialsResult;
import org.ezkey.admin.service.AdminProvisioningService.ProvisioningResult;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final QrCodeGeneratorService qrCodeGeneratorService;

  public AdminProvisioningController(
      AdminProvisioningService provisioningService, QrCodeGeneratorService qrCodeGeneratorService) {
    this.provisioningService = provisioningService;
    this.qrCodeGeneratorService = qrCodeGeneratorService;
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
            result.admin().getCreatedAt());

    return ResponseEntity.created(URI.create("/api/v1/admins/" + result.admin().getAdminId()))
        .body(response);
  }

  /**
   * Lists administrators with tenant-based filtering.
   *
   * <p>Retrieves a paginated list of administrators. GlobalAdmin sees all administrators across all
   * tenants. TenantAdmin sees only administrators from their tenant. Results are ordered by
   * creation date descending (newest first) by default.
   *
   * <p><b>Tenant Filtering:</b>
   *
   * <ul>
   *   <li><b>GlobalAdmin:</b> Returns all administrators (all tenants)
   *   <li><b>TenantAdmin:</b> Returns only administrators from their tenant (automatic filtering)
   * </ul>
   *
   * <p><b>Pagination and Sorting:</b>
   *
   * <ul>
   *   <li>Use <code>?page=0&size=20</code> for pagination (zero-based page numbers)
   *   <li>Use <code>?sort=field,direction</code> for sorting (e.g., <code>?sort=adminId,asc</code>
   *       or <code>?sort=createdAt,desc</code>)
   *   <li>Default: page=0, size=20, sort=createdAt,DESC
   *   <li>Sortable fields: adminId, username, adminType, tenantId, active, createdAt
   * </ul>
   *
   * @param pageable pagination and sorting parameters (default: page=0, size=20,
   *     sort=createdAt,DESC)
   * @return ResponseEntity containing page of administrator response DTOs (200 OK)
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "List administrators",
      description =
          "Lists administrators with tenant-based filtering. GlobalAdmin sees all admins. "
              + "TenantAdmin sees only admins from their tenant. Supports pagination and sorting.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "List of administrators retrieved successfully"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not an administrator")
  })
  public ResponseEntity<Page<AdminResponseDto>> listAdmins(
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    // Extract tenant ID from authentication for tenant scoping
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Integer tenantId = extractTenantId(auth);

    Page<EzkeyAdmin> admins = provisioningService.listAdmins(tenantId, pageable);

    Page<AdminResponseDto> response =
        admins.map(
            admin ->
                new AdminResponseDto(
                    admin.getAdminId(),
                    admin.getUsername(),
                    admin.getEmail(),
                    admin.getFirstName(),
                    admin.getLastName(),
                    admin.getAdminType().name(),
                    admin.getTenant() != null ? admin.getTenant().getTenantId() : null,
                    admin.getActive(),
                    admin.getCreatedAt()));

    return ResponseEntity.ok(response);
  }

  /**
   * Retrieves onboarding credentials for an administrator.
   *
   * <p>This endpoint returns sensitive onboarding credentials (enrollment proof token, challenge
   * code) for an administrator. Recovery codes cannot be retrieved after initial provisioning as
   * they are stored as BCrypt hashes.
   *
   * <p><b>Authorization:</b>
   *
   * <ul>
   *   <li>GlobalAdmin can retrieve onboarding credentials for any admin
   *   <li>TenantAdmin can only retrieve onboarding credentials for admins in their tenant
   * </ul>
   *
   * <p><b>Security:</b> This endpoint follows the same pattern as the enrollment API, where
   * sensitive credentials are separated from the creation response. This prevents credentials from
   * appearing in logs and provides better control over credential access.
   *
   * @param id the administrator ID
   * @param auth the authentication context
   * @return ResponseEntity with onboarding credentials (200 OK) or 404 Not Found
   */
  @GetMapping("/{id}/onboarding")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Retrieve onboarding credentials",
      description =
          "Retrieves onboarding credentials (enrollment proof token, challenge code) for an"
              + " administrator. Recovery codes cannot be retrieved after initial provisioning.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Onboarding credentials retrieved successfully"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
    @ApiResponse(responseCode = "404", description = "Administrator or enrollment not found")
  })
  public ResponseEntity<AdminOnboardingResponseDto> getAdminOnboarding(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      OnboardingCredentialsResult result = provisioningService.getAdminOnboarding(id, principal);

      AdminOnboardingResponseDto response =
          new AdminOnboardingResponseDto(
              result.enrollmentId(),
              result.enrollmentProofToken(),
              result.enrollmentChallenge(),
              result.recoveryCodes());

      return ResponseEntity.ok(response);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  /**
   * Generates a QR code for administrator onboarding.
   *
   * <p>This endpoint generates a PNG QR code image containing enrollment credentials
   * (enrollmentId|enrollmentProofToken) that can be scanned by the mobile application for
   * passwordless enrollment binding.
   *
   * <p><b>Authorization:</b>
   *
   * <ul>
   *   <li>GlobalAdmin can generate QR codes for any admin
   *   <li>TenantAdmin can only generate QR codes for admins in their tenant
   * </ul>
   *
   * <p><b>QR Code Format:</b> {@code enrollmentId|enrollmentProofToken}
   *
   * @param id the administrator ID
   * @param auth the authentication context
   * @return ResponseEntity containing PNG image bytes (200 OK) or 404 Not Found
   */
  @GetMapping("/{id}/onboarding/qrcode")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Generate QR code for onboarding",
      description =
          "Returns a PNG QR code image containing enrollment credentials"
              + " (enrollmentId|enrollmentProofToken) for passwordless enrollment binding.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "QR code generated successfully"),
    @ApiResponse(responseCode = "400", description = "Enrollment missing proof token"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
    @ApiResponse(responseCode = "404", description = "Administrator or enrollment not found")
  })
  public ResponseEntity<byte[]> getAdminOnboardingQrCode(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      OnboardingCredentialsResult result = provisioningService.getAdminOnboarding(id, principal);

      // Validate enrollment has proof token
      if (result.enrollmentProofToken() == null || result.enrollmentProofToken().isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      // Format: enrollmentId|enrollmentProofToken
      String qrContent = result.enrollmentId() + "|" + result.enrollmentProofToken();

      // Generate QR code (300x300 pixels)
      byte[] qrCodeImage = qrCodeGeneratorService.generateQrCodeImage(qrContent, 300, 300);

      // Return as PNG image
      HttpHeaders headers = new HttpHeaders();
      headers.add("Content-Type", "image/png");
      headers.add("Content-Disposition", "inline; filename=admin-" + id + "-onboarding-qrcode.png");

      return ResponseEntity.ok().headers(headers).body(qrCodeImage);

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
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

    try {
      provisioningService.deactivateAdmin(id, principal);
      return ResponseEntity.noContent().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      // Cannot deactivate yourself
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    } catch (IllegalStateException e) {
      // Would violate minimum limits
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
  }

  /**
   * Extracts tenant ID from authentication context for tenant scoping.
   *
   * <p>This method extracts the tenant ID from the AdminPrincipal in the authentication context.
   * Returns null for GlobalAdmin (who can access all tenants) and the tenant ID for TenantAdmin.
   *
   * @param auth the authentication context
   * @return tenant ID if TenantAdmin, null if GlobalAdmin
   */
  private Integer extractTenantId(Authentication auth) {
    if (auth == null || auth.getPrincipal() == null) {
      return null;
    }

    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      return adminPrincipal.tenantId(); // null for GlobalAdmin, tenantId for TenantAdmin
    }

    return null;
  }
}
