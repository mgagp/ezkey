/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.ezkey.admin.audit.RecoveryAuditDetails;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.AdminCreateRequestDto;
import org.ezkey.admin.dto.request.AdminUpdateRequestDto;
import org.ezkey.admin.dto.response.AdminOnboardingResponseDto;
import org.ezkey.admin.dto.response.AdminProvisioningResponseDto;
import org.ezkey.admin.dto.response.AdminRecoveryCodesRegenerationResponseDto;
import org.ezkey.admin.dto.response.AdminResponseDto;
import org.ezkey.admin.exception.AdminLimitException;
import org.ezkey.admin.exception.AdminNotAllowedException;
import org.ezkey.admin.exception.GlobalAdminLimitException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminProvisioningService.OnboardingCredentialsResult;
import org.ezkey.admin.service.AdminProvisioningService.ProvisioningResult;
import org.ezkey.admin.service.AdminProvisioningService.RecoveryCodesRegenerationResult;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Validated
@RestController
@RequestMapping("/api/v1/admins")
@Tag(name = "Administrator Provisioning", description = "Administrator provisioning API")
public class AdminProvisioningController {

  private final AdminProvisioningService provisioningService;
  private final QrCodeGeneratorService qrCodeGeneratorService;
  private final QrCodePayloadService qrCodePayloadService;
  private final AuditLogService auditLogService;

  public AdminProvisioningController(
      AdminProvisioningService provisioningService,
      QrCodeGeneratorService qrCodeGeneratorService,
      QrCodePayloadService qrCodePayloadService,
      AuditLogService auditLogService) {
    this.provisioningService = provisioningService;
    this.qrCodeGeneratorService = qrCodeGeneratorService;
    this.qrCodePayloadService = qrCodePayloadService;
    this.auditLogService = auditLogService;
  }

  /**
   * Builds an RFC 9457 Problem Detail response for bad request (400).
   *
   * @param request the HTTP request (for path)
   * @param detail human-readable detail message
   * @param typeSuffix suffix for type URI (e.g. "missing-tenant-id")
   * @param title short title (e.g. "Invalid Request")
   * @return ResponseEntity with status 400 and ProblemDetail body
   */
  private static ResponseEntity<ProblemDetail> badRequest(
      HttpServletRequest request, String detail, String typeSuffix, String title) {
    return badRequest(request.getRequestURI(), detail, typeSuffix, title);
  }

  private static ResponseEntity<ProblemDetail> badRequest(
      String path, String detail, String typeSuffix, String title) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setType(URI.create("https://ezkey.io/problems/admin-provisioning/" + typeSuffix));
    problem.setTitle(title);
    problem.setProperty("path", path);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  private static AdminResponseDto toAdminResponseDto(EzkeyAdmin admin) {
    Integer enrollmentId =
        admin.getEnrollment() != null ? admin.getEnrollment().getEnrollmentId() : null;
    boolean operational =
        Boolean.TRUE.equals(admin.getActive())
            && admin.getLifecycleStatus() == EzkeyAdmin.AdminLifecycleStatus.ACTIVE
            && (admin.getAdminType() == EzkeyAdmin.AdminType.GLOBAL_ADMIN
                || (admin.getTenant() != null
                    && Boolean.TRUE.equals(admin.getTenant().getActive())));
    return new AdminResponseDto(
        admin.getAdminId(),
        admin.getVersion(),
        admin.getUsername(),
        admin.getEmail(),
        admin.getPhoneNumber(),
        admin.getFirstName(),
        admin.getLastName(),
        admin.getAdminType().name(),
        admin.getTenant() != null ? admin.getTenant().getTenantId() : null,
        enrollmentId,
        admin.getActive(),
        admin.getLifecycleStatus().name(),
        admin.getCreatedAt(),
        admin.getLastLoginAt(),
        operational);
  }

  /**
   * Creates a new global administrator (peer admin).
   *
   * <p>Only global administrators can create other global administrators. The operation enforces
   * maximum limit. The default onboarding mode creates enrollment + recovery codes immediately. The
   * activation-code mode creates a pending admin and returns a one-time activation code instead.
   *
   * @param request the admin creation request
   * @param auth the authentication context
   * @return ResponseEntity with provisioning result including onboarding credentials (201 Created)
   */
  @PostMapping("/global")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Create a peer global administrator",
      description =
          "Creates a new global administrator. GlobalAdmin only. Enforces max limit."
              + " onboardingMode=IMMEDIATE returns enrollment/recovery bootstrap data;"
              + " onboardingMode=ACTIVATION_CODE returns a one-time activation code and leaves"
              + " the admin pending activation.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Global administrator created successfully"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Invalid request, username exists, or global administrator limit reached. When the"
                + " configured maximum active global administrators is reached, the response is RFC"
                + " 9457 application/problem+json with type"
                + " https://ezkey.io/problems/admin-provisioning/global-admin-limit-reached and"
                + " extension property parameters.maxGlobalAdmins (integer)."),
    @ApiResponse(responseCode = "403", description = "Forbidden - not a global administrator")
  })
  public ResponseEntity<?> createGlobalAdmin(
      @Valid @RequestBody AdminCreateRequestDto request,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Validate required fields for global admin (SOC 2 compliance)
    if (request.email() == null || request.email().isBlank()) {
      return badRequest(
          httpRequest,
          "Email is required for global administrator creation.",
          "missing-email",
          "Invalid Request");
    }
    if (request.firstName() == null || request.firstName().isBlank()) {
      return badRequest(
          httpRequest,
          "First name is required for global administrator creation.",
          "missing-first-name",
          "Invalid Request");
    }
    if (request.lastName() == null || request.lastName().isBlank()) {
      return badRequest(
          httpRequest,
          "Last name is required for global administrator creation.",
          "missing-last-name",
          "Invalid Request");
    }

    try {
      ProvisioningResult result =
          provisioningService.createGlobalAdmin(
              request.username(),
              request.email(),
              request.phoneNumber(),
              request.firstName(),
              request.lastName(),
              request.onboardingMode(),
              principal);

      AdminProvisioningResponseDto response =
          new AdminProvisioningResponseDto(
              result.admin().getAdminId(),
              result.admin().getUsername(),
              result.admin().getEmail(),
              result.admin().getPhoneNumber(),
              result.admin().getFirstName(),
              result.admin().getLastName(),
              result.admin().getAdminType().name(),
              result.admin().getTenant() != null ? result.admin().getTenant().getTenantId() : null,
              result.enrollment() != null ? result.enrollment().getEnrollmentId() : null,
              result.admin().getLifecycleStatus().name(),
              result.onboardingMode().name(),
              result.activationCode(),
              result.activationCodeExpiresAt(),
              result.admin().getCreatedAt(),
              result.recoveryCodes());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_CREATED,
                  AdminAuditConstants.ADMIN_GLOBAL_CREATED,
                  principal.tenantId())
              .eventStatus(EventStatus.SUCCESS)
              .adminId(principal.adminId())
              .targetAdminId(result.admin().getAdminId())
              .eventDetails(
                  "Admin ID: "
                      + result.admin().getAdminId()
                      + ", username: "
                      + result.admin().getUsername()
                      + ", onboardingMode: "
                      + result.onboardingMode().name())
              .build());

      return ResponseEntity.created(URI.create("/api/v1/admins/" + result.admin().getAdminId()))
          .body(response);
    } catch (IllegalArgumentException | AdminLimitException | GlobalAdminLimitException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_CREATED,
                  AdminAuditConstants.ADMIN_GLOBAL_CREATION_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .errorMessage(e.getMessage())
              .build());
      throw e;
    }
  }

  /**
   * Creates a new tenant administrator (peer admin).
   *
   * <p>Global administrators can create tenant admins for any tenant. Tenant administrators can
   * create peer tenant admins for their own tenant only. The operation enforces maximum limit and
   * supports both immediate and activation-code onboarding modes.
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
              + " can create for same tenant only. Enforces max limit."
              + " onboardingMode=IMMEDIATE returns enrollment/recovery bootstrap data;"
              + " onboardingMode=ACTIVATION_CODE returns a one-time activation code and leaves"
              + " the admin pending activation.")
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
  public ResponseEntity<?> createTenantAdmin(
      @Valid @RequestBody AdminCreateRequestDto request,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // When a TenantAdmin creates a peer admin, tenantId is implicitly their own tenant.
    // This avoids requiring the caller to repeat information already present in their JWT.
    // GlobalAdmin must still supply tenantId explicitly (they can target any tenant).
    Integer effectiveTenantId = request.tenantId();
    if (effectiveTenantId == null && principal.isTenantAdmin()) {
      effectiveTenantId = principal.tenantId();
    }
    if (effectiveTenantId == null) {
      return badRequest(
          httpRequest,
          "Tenant ID is required for tenant administrator creation. Global admins must supply"
              + " tenantId in the request body.",
          "missing-tenant-id",
          "Invalid Request");
    }

    try {
      ProvisioningResult result =
          provisioningService.createTenantAdmin(
              request.username(),
              request.email(),
              request.phoneNumber(),
              request.firstName(),
              request.lastName(),
              effectiveTenantId,
              request.onboardingMode(),
              principal);

      AdminProvisioningResponseDto response =
          new AdminProvisioningResponseDto(
              result.admin().getAdminId(),
              result.admin().getUsername(),
              result.admin().getEmail(),
              result.admin().getPhoneNumber(),
              result.admin().getFirstName(),
              result.admin().getLastName(),
              result.admin().getAdminType().name(),
              result.admin().getTenant() != null ? result.admin().getTenant().getTenantId() : null,
              result.enrollment() != null ? result.enrollment().getEnrollmentId() : null,
              result.admin().getLifecycleStatus().name(),
              result.onboardingMode().name(),
              result.activationCode(),
              result.activationCodeExpiresAt(),
              result.admin().getCreatedAt(),
              result.recoveryCodes());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_CREATED,
                  AdminAuditConstants.ADMIN_TENANT_CREATED,
                  effectiveTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .targetAdminId(result.admin().getAdminId())
              .adminId(principal.adminId())
              .eventDetails(
                  "Admin ID: "
                      + result.admin().getAdminId()
                      + ", username: "
                      + result.admin().getUsername()
                      + ", tenantId: "
                      + request.tenantId()
                      + ", onboardingMode: "
                      + result.onboardingMode().name())
              .build());

      return ResponseEntity.created(URI.create("/api/v1/admins/" + result.admin().getAdminId()))
          .body(response);
    } catch (IllegalArgumentException | AdminLimitException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_CREATED,
                  AdminAuditConstants.ADMIN_TENANT_CREATION_FAILED,
                  effectiveTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .errorMessage(e.getMessage())
              .build());
      throw e;
    }
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
          Pageable pageable,
      @Parameter(
              description =
                  "Filter by tenant ID. GlobalAdmin only; when provided, limits results to that"
                      + " tenant. Ignored for TenantAdmin.")
          @RequestParam(required = false)
          Integer tenantId) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Integer authTenantId = extractTenantId(auth);
    // TenantAdmin: always scope to their tenant (ignore request tenantId). GlobalAdmin: use request
    // tenantId when provided.
    Integer effectiveTenantId = (authTenantId != null) ? authTenantId : tenantId;

    Page<EzkeyAdmin> admins = provisioningService.listAdmins(effectiveTenantId, pageable);

    Page<AdminResponseDto> response = admins.map(AdminProvisioningController::toAdminResponseDto);

    return ResponseEntity.ok(response);
  }

  /**
   * Retrieves a single administrator by ID.
   *
   * <p><b>Authorization:</b> GlobalAdmin can retrieve any admin. TenantAdmin can only retrieve
   * admins from their own tenant.
   *
   * @param id the administrator ID
   * @param auth the authentication context
   * @return ResponseEntity with administrator details (200 OK) or 404 Not Found
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Get administrator by ID",
      description =
          "Returns a single administrator by ID. GlobalAdmin can access any admin. "
              + "TenantAdmin can only access admins in their tenant.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Administrator retrieved successfully"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
    @ApiResponse(responseCode = "404", description = "Administrator not found")
  })
  public ResponseEntity<AdminResponseDto> getAdminById(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      EzkeyAdmin admin = provisioningService.getAdminById(id, principal);
      return ResponseEntity.ok(toAdminResponseDto(admin));
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
  }

  /**
   * Partially updates an administrator profile.
   *
   * <p>Only non-null fields are applied. GlobalAdmin can update any admin. TenantAdmin can update
   * admins in their own tenant. Admins can update their own profile (firstName, lastName, email).
   *
   * <p><b>Updatable Fields:</b> firstName, lastName, email, challengeRequired
   *
   * <p><b>Optimistic Locking:</b> Include version from GET response. Stale version returns 409.
   *
   * @param id the administrator ID
   * @param request the partial update request
   * @param auth the authentication context
   * @param httpRequest the HTTP request for audit context
   * @return ResponseEntity with updated admin (200 OK)
   */
  @PatchMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Update administrator profile",
      description =
          "Partial update of administrator profile (firstName, lastName, email, challengeRequired)."
              + " GlobalAdmin can update any admin. TenantAdmin can update admins in their tenant."
              + " Admins can update their own profile. Include version for optimistic locking.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Administrator profile updated successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid data or email already exists"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
    @ApiResponse(responseCode = "404", description = "Administrator not found"),
    @ApiResponse(
        responseCode = "409",
        description = "Optimistic lock conflict - resource was modified, re-fetch and retry")
  })
  public ResponseEntity<AdminResponseDto> updateAdmin(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      @Valid @RequestBody AdminUpdateRequestDto request,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      EzkeyAdmin existingAdmin = provisioningService.getAdminById(id, principal);
      String previousFirstName = existingAdmin.getFirstName();
      String previousLastName = existingAdmin.getLastName();
      String previousEmail = existingAdmin.getEmail();
      String previousPhoneNumber = existingAdmin.getPhoneNumber();
      Boolean previousChallengeRequired = existingAdmin.getChallengeRequired();

      EzkeyAdmin admin = provisioningService.updateAdmin(id, request, principal);
      AdminResponseDto response = toAdminResponseDto(admin);

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_PROFILE_UPDATED,
                  AdminAuditConstants.ADMIN_PROFILE_UPDATED,
                  principal.tenantId())
              .eventStatus(EventStatus.SUCCESS)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .eventDetails(
                  buildAdminUpdateAuditEventDetailsJson(
                      id,
                      request,
                      previousFirstName,
                      previousLastName,
                      previousEmail,
                      previousPhoneNumber,
                      previousChallengeRequired,
                      admin))
              .build());

      return ResponseEntity.ok(response);
    } catch (ResourceNotFoundException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_PROFILE_UPDATED,
                  AdminAuditConstants.ADMIN_PROFILE_UPDATE_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage("Admin not found: " + id)
              .build());
      throw e;
    } catch (IllegalArgumentException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_PROFILE_UPDATED,
                  AdminAuditConstants.ADMIN_PROFILE_UPDATE_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage(e.getMessage())
              .build());
      throw e;
    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_PROFILE_UPDATED,
                  AdminAuditConstants.ADMIN_PROFILE_UPDATE_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage("Optimistic lock conflict")
              .build());
      throw e;
    }
  }

  private static String buildAdminUpdateAuditEventDetailsJson(
      Integer adminId,
      AdminUpdateRequestDto request,
      String previousFirstName,
      String previousLastName,
      String previousEmail,
      String previousPhoneNumber,
      Boolean previousChallengeRequired,
      EzkeyAdmin updated) {
    org.ezkey.audit.util.AuditDetailsBuilder builder =
        org.ezkey.audit.util.AuditDetailsBuilder.builder();
    builder.custom("admin_id", adminId);

    List<java.util.Map<String, Object>> changes = new ArrayList<>();
    if (request.firstName() != null && !Objects.equals(previousFirstName, updated.getFirstName())) {
      changes.add(AuditHelper.changeEntry("firstName", previousFirstName, updated.getFirstName()));
    }
    if (request.lastName() != null && !Objects.equals(previousLastName, updated.getLastName())) {
      changes.add(AuditHelper.changeEntry("lastName", previousLastName, updated.getLastName()));
    }
    if (request.email() != null && !Objects.equals(previousEmail, updated.getEmail())) {
      changes.add(AuditHelper.changeEntry("email", previousEmail, updated.getEmail()));
    }
    if (request.phoneNumber() != null
        && !Objects.equals(previousPhoneNumber, updated.getPhoneNumber())) {
      changes.add(
          AuditHelper.maskedPhoneChangeEntry(
              "phoneNumber", previousPhoneNumber, updated.getPhoneNumber()));
    }
    if (request.challengeRequired() != null
        && !Objects.equals(previousChallengeRequired, updated.getChallengeRequired())) {
      changes.add(
          AuditHelper.changeEntry(
              "challengeRequired", previousChallengeRequired, updated.getChallengeRequired()));
    }

    builder.custom("changes", changes);
    return builder.toJson();
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
  public ResponseEntity<?> getAdminOnboarding(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth,
      HttpServletRequest httpRequest) {
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
      return badRequest(
          httpRequest,
          e.getMessage() != null ? e.getMessage() : "Invalid request.",
          "invalid-request",
          "Invalid Request");
    }
  }

  /**
   * Regenerates recovery codes for an administrator.
   *
   * <p>GlobalAdmin can regenerate recovery codes for any admin. TenantAdmin can regenerate recovery
   * codes for admins in their tenant. The operation invalidates any remaining unused recovery codes
   * and returns a new plain-text set once.
   *
   * @param id the administrator ID
   * @param auth the authentication context
   * @param httpRequest the HTTP request for audit context
   * @return ResponseEntity with the new recovery codes (200 OK)
   */
  @PostMapping("/{id}/recovery-codes/regenerate")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(
      summary = "Regenerate administrator recovery codes",
      description =
          "Generates a new set of single-use recovery codes for an administrator. Previous unused"
              + " codes are invalidated immediately. GlobalAdmin can access any admin; TenantAdmin"
              + " can access admins in their tenant.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Recovery codes regenerated successfully"),
    @ApiResponse(responseCode = "400", description = "Target administrator is inactive"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
    @ApiResponse(responseCode = "404", description = "Administrator not found")
  })
  public ResponseEntity<?> regenerateRecoveryCodes(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      RecoveryCodesRegenerationResult result =
          provisioningService.regenerateRecoveryCodes(id, principal);
      int newCodesCount = result.recoveryCodes() != null ? result.recoveryCodes().size() : 0;
      boolean selfService = principal.adminId().equals(result.admin().getAdminId());

      AdminRecoveryCodesRegenerationResponseDto response =
          new AdminRecoveryCodesRegenerationResponseDto(
              result.admin().getAdminId(),
              result.admin().getUsername(),
              result.recoveryCodes(),
              newCodesCount,
              true,
              "New recovery codes generated. Previous unused codes are no longer valid.");

      Integer tenantId =
          result.admin().getTenant() != null ? result.admin().getTenant().getTenantId() : null;
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_CODES_REGENERATED,
                  AdminAuditConstants.RECOVERY_CODES_REGENERATED,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(principal.adminId())
              .targetAdminId(result.admin().getAdminId())
              .eventDetails(
                  RecoveryAuditDetails.recoveryCodesRegenerated(
                      principal.adminId(),
                      result.admin().getAdminId(),
                      result.admin().getUsername(),
                      tenantId,
                      result.previousCodesCount(),
                      newCodesCount,
                      selfService))
              .build());

      return ResponseEntity.ok(response);
    } catch (ResourceNotFoundException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_CODES_REGENERATED,
                  AdminAuditConstants.RECOVERY_CODES_REGENERATION_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage("Administrator not found: " + id)
              .eventDetails(
                  RecoveryAuditDetails.recoveryCodesRegenerationRejected(
                      principal.adminId(),
                      id,
                      principal.tenantId(),
                      "admin_not_found",
                      "Administrator not found"))
              .build());
      throw e;
    } catch (AccessDeniedException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_CODES_REGENERATED,
                  AdminAuditConstants.RECOVERY_CODES_REGENERATION_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage(e.getMessage())
              .eventDetails(
                  RecoveryAuditDetails.recoveryCodesRegenerationRejected(
                      principal.adminId(),
                      id,
                      principal.tenantId(),
                      "access_denied",
                      e.getMessage()))
              .build());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    } catch (IllegalStateException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_CODES_REGENERATED,
                  AdminAuditConstants.RECOVERY_CODES_REGENERATION_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage(e.getMessage())
              .eventDetails(
                  RecoveryAuditDetails.recoveryCodesRegenerationRejected(
                      principal.adminId(),
                      id,
                      principal.tenantId(),
                      "admin_inactive",
                      e.getMessage()))
              .build());
      return badRequest(
          httpRequest,
          e.getMessage() != null ? e.getMessage() : "Invalid request.",
          "admin-inactive",
          "Invalid Request");
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
   * <p><b>QR Code Format (JSON):</b>
   *
   * <pre>
   * {"enrollmentId":"...","enrollmentProofToken":"...","authUrl":"..."}
   * </pre>
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
          "Returns a PNG QR code image containing enrollment credentials as JSON"
              + " ({enrollmentId, enrollmentProofToken, authUrl}) for passwordless enrollment"
              + " binding.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "QR code generated successfully"),
    @ApiResponse(responseCode = "400", description = "Enrollment missing proof token"),
    @ApiResponse(responseCode = "403", description = "Forbidden - not authorized"),
    @ApiResponse(responseCode = "404", description = "Administrator or enrollment not found")
  })
  public ResponseEntity<?> getAdminOnboardingQrCode(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      Authentication auth,
      HttpServletRequest httpRequest) {
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || (!principal.isGlobalAdmin() && !principal.isTenantAdmin())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      OnboardingCredentialsResult result = provisioningService.getAdminOnboarding(id, principal);

      // Validate enrollment has proof token
      if (result.enrollmentProofToken() == null || result.enrollmentProofToken().isEmpty()) {
        return badRequest(
            httpRequest,
            "Enrollment proof token is not available for this administrator.",
            "missing-proof-token",
            "Invalid Request");
      }

      // Compose JSON payload with optional authUrl
      String qrContent =
          qrCodePayloadService.composePayload(result.enrollmentId(), result.enrollmentProofToken());

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
      return badRequest(
          httpRequest,
          e.getMessage() != null ? e.getMessage() : "Invalid request.",
          "invalid-request",
          "Invalid Request");
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Deactivates an administrator.
   *
   * <p>Only global administrators can deactivate administrators. Deactivation enforces minimum
   * limits to prevent lockout and revokes all active tokens. /** Deactivates an administrator
   * (passwordless-only global admin operation).
   *
   * <p>This endpoint deactivates an administrator account, revoking all active authentication
   * tokens. Only global administrators can deactivate other admins. The operation enforces minimum
   * global admin limits to prevent the system from being left without any administrators.
   *
   * <p><b>Authorization:</b> Global Admin only (via @PreAuthorize)
   *
   * <p><b>Business Rules:</b>
   *
   * <ul>
   *   <li>Admin cannot deactivate their own account
   *   <li>Deactivation must not violate minimum global admin limits
   *   <li>All active tokens for the admin are automatically revoked
   *   <li>Operation is idempotent if admin is already inactive
   * </ul>
   *
   * <p><b>Success Response:</b> 204 No Content (no response body)
   *
   * <p><b>Error Responses:</b>
   *
   * <ul>
   *   <li><b>400 Bad Request (RFC 9457 Problem Detail):</b> Operation not allowed or limit violated
   *   <li><b>403 Forbidden:</b> Caller is not a global administrator
   *   <li><b>404 Not Found:</b> Administrator ID does not exist
   * </ul>
   *
   * @param id the administrator ID to deactivate
   * @param auth the authentication context
   * @return ResponseEntity with 204 No Content on success
   * @throws AdminNotAllowedException if self-deactivation attempted (400, RFC 9457)
   * @throws AdminLimitException if minimum limits would be violated (400, RFC 9457)
   * @throws ResourceNotFoundException if admin not found (404)
   */
  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Deactivate an administrator",
      description =
          "Deactivates an administrator account and revokes all tokens. GlobalAdmin only. "
              + "Enforces minimum limits and prevents self-deactivation.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Administrator deactivated successfully"),
    @ApiResponse(
        responseCode = "400",
        description =
            "Cannot deactivate: self-deactivation attempted or would violate limits (RFC 9457"
                + " Problem Detail)"),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - caller is not a global administrator"),
    @ApiResponse(responseCode = "404", description = "Administrator not found")
  })
  public ResponseEntity<Void> deactivateAdmin(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      @Parameter(description = "Audit justification for the deactivation (min 10 characters)")
          @RequestParam(required = false)
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      boolean stateChanged = provisioningService.deactivateAdmin(id, principal);
      if (stateChanged) {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ADMIN_DEACTIVATED,
                    AdminAuditConstants.ADMIN_DEACTIVATED,
                    principal.tenantId())
                .eventStatus(EventStatus.SUCCESS)
                .adminId(principal.adminId())
                .targetAdminId(id)
                .reason(reason)
                .eventDetails("Admin ID: " + id)
                .build());
      }
      return ResponseEntity.noContent().build();
    } catch (AdminNotAllowedException | AdminLimitException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_DEACTIVATED,
                  AdminAuditConstants.ADMIN_DEACTIVATION_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage(e.getMessage())
              .build());
      throw e;
    } catch (ResourceNotFoundException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_DEACTIVATED,
                  AdminAuditConstants.ADMIN_DEACTIVATION_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage("Admin not found: " + id)
              .build());
      throw e;
    }
  }

  /**
   * Activates (reactivates) an administrator account.
   *
   * <p>Only global administrators can activate. Sets {@code active = true}. Idempotent if already
   * active. The admin must log in again to obtain a new bearer token.
   *
   * @param id the administrator ID to activate
   * @param auth the authentication context
   * @param httpRequest the HTTP request for audit context
   * @return ResponseEntity with 204 No Content on success
   */
  @PostMapping("/{id}/activate")
  @PreAuthorize("hasRole('ROLE_GLOBAL_ADMIN')")
  @Operation(
      summary = "Activate an administrator",
      description =
          "Reactivates a deactivated administrator account. GlobalAdmin only. Idempotent if already"
              + " active. Admin must log in again to obtain a new token.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Administrator activated successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Forbidden - caller is not a global administrator"),
    @ApiResponse(responseCode = "404", description = "Administrator not found")
  })
  public ResponseEntity<Void> activateAdmin(
      @Parameter(description = "Administrator ID", example = "1") @PathVariable("id") Integer id,
      @Parameter(description = "Audit justification for the activation (min 10 characters)")
          @RequestParam(required = false)
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      Authentication auth,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (principal == null || !principal.isGlobalAdmin()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      boolean stateChanged = provisioningService.activateAdmin(id, principal);
      if (stateChanged) {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ADMIN_ACTIVATED,
                    AdminAuditConstants.ADMIN_ACTIVATED,
                    principal.tenantId())
                .eventStatus(EventStatus.SUCCESS)
                .adminId(principal.adminId())
                .targetAdminId(id)
                .reason(reason)
                .eventDetails("Admin ID: " + id)
                .build());
      }
      return ResponseEntity.noContent().build();
    } catch (ResourceNotFoundException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_ACTIVATED,
                  AdminAuditConstants.ADMIN_ACTIVATION_FAILED,
                  principal.tenantId())
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal.adminId())
              .targetAdminId(id)
              .errorMessage("Admin not found: " + id)
              .build());
      throw e;
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
