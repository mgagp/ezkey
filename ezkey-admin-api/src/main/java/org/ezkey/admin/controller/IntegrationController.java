/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the project root for full
 * license information.
 *
 * Controller: IntegrationController Description: REST controller for managing Integration entities through HTTP
 * endpoints.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.OffsetDateTime;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.response.BulkEnrollmentOperationResultDto;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.admin.service.EnrollmentRevocationService.BulkEnrollmentOperationResult;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.SystemTenantNotConfiguredException;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.IntegrationCreateResponse;
import org.ezkey.integration.domain.IntegrationLifecycleStatus;
import org.ezkey.integration.domain.IntegrationResponse;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.dto.IntegrationCreateResponseDto;
import org.ezkey.integration.dto.IntegrationResponseDto;
import org.ezkey.integration.exception.IntegrationCodeAlreadyExistsException;
import org.ezkey.integration.exception.IntegrationCreateValidationException;
import org.ezkey.integration.exception.SystemIntegrationLifecycleException;
import org.ezkey.integration.mapper.IntegrationControllerMapper;
import org.ezkey.integration.service.IntegrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for integration administration API v1.
 *
 * <p>This controller provides REST endpoints for integration management operations in the admin API
 * (internal). It handles CRUD operations for integrations, which represent applications or systems
 * to be protected by MFA. Uses JPA-based service and DTOs for clean API responses with proper HTTP
 * status codes.
 *
 * <p><b>Admin API Endpoints (Internal):</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/integrations</b> - List all integrations
 *   <li><b>GET /api/v1/integrations/{id}</b> - Get integration by ID
 *   <li><b>POST /api/v1/integrations</b> - Create new integration
 *   <li><b>DELETE /api/v1/integrations/{id}</b> - Delete integration
 * </ul>
 *
 * <p><b>Usage Context:</b> This is part of the admin-api (port 9080) for internal administration
 * purposes. Integrations represent applications that will use Ezkey for MFA.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see IntegrationService
 * @see IntegrationResponse
 * @see IntegrationCreateRequest
 * @see IntegrationResponseDto
 * @see IntegrationCreateRequestDto
 */
@Validated
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "Integration management API")
public class IntegrationController {

  private static final Logger logger = LoggerFactory.getLogger(IntegrationController.class);

  private final IntegrationService service;
  private final IntegrationControllerMapper mapper;
  private final EzkeyAdminRepository adminRepository;
  private final AccessControlService accessControlService;
  private final AuditLogService auditLogService;
  private final EnrollmentRevocationService enrollmentRevocationService;

  /**
   * Constructs the controller with required dependencies.
   *
   * @param service the service layer for Integration operations
   * @param mapper the mapper for converting between entities and DTOs
   * @param adminRepository the admin repository for loading admin entities
   * @param accessControlService the access control service for tenant scoping validation
   * @param auditLogService the audit log service for security monitoring
   * @param enrollmentRevocationService the service for bulk enrollment revocation
   */
  public IntegrationController(
      IntegrationService service,
      IntegrationControllerMapper mapper,
      EzkeyAdminRepository adminRepository,
      AccessControlService accessControlService,
      AuditLogService auditLogService,
      EnrollmentRevocationService enrollmentRevocationService) {
    this.service = service;
    this.mapper = mapper;
    this.adminRepository = adminRepository;
    this.accessControlService = accessControlService;
    this.auditLogService = auditLogService;
    this.enrollmentRevocationService = enrollmentRevocationService;
  }

  /**
   * Searches integrations with optional filters and pagination.
   *
   * <p>Retrieves integrations matching the specified criteria with pagination support. All filter
   * parameters are optional - if none are provided, returns all integrations (paginated). Results
   * are ordered by creation date descending (newest first) by default.
   *
   * <p><b>Use Case:</b> Administrators managing integrations, searching for specific applications,
   * and compliance reporting.
   *
   * <p><b>Pagination and Sorting:</b>
   *
   * <ul>
   *   <li>Use <code>?page=0&size=20</code> for pagination (zero-based page numbers)
   *   <li>Use <code>?sort=field,direction</code> for sorting (e.g., <code>?sort=id,asc</code> or
   *       <code>?sort=createdAt,desc</code>)
   *   <li>Default: page=0, size=20, sort=createdAt,DESC
   *   <li>Sortable fields: id, createdAt, lifecycleStatus
   * </ul>
   *
   * @param integrationName optional filter by integration name (partial match, case-insensitive)
   * @param active optional compatibility filter by active flag
   * @param lifecycleStatus optional exact lifecycle filter
   * @param includeRetired when true, retired integrations are included in default listings
   * @param createdAfter optional filter for integrations created after this timestamp
   * @param createdBefore optional filter for integrations created before this timestamp
   * @param tenantId optional filter by tenant ID (GlobalAdmin only; ignored for TenantAdmin)
   * @param pageable pagination and sorting parameters (default: page=0, size=20,
   *     sort=createdAt,DESC)
   * @return ResponseEntity containing page of integration response DTOs with HTTP 200 status
   */
  @Operation(
      summary = "Search integrations",
      description =
          "Retrieves integrations with optional filters and pagination for administration and"
              + " compliance reporting. Supports dynamic sorting via ?sort=field,direction (e.g.,"
              + " ?sort=id,asc). Default sort is by creation date descending (newest first)."
              + " Retired integrations are excluded by default unless explicitly requested."
              + " Optional tenantId filter: GlobalAdmin only; TenantAdmin scope is always their"
              + " tenant.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class)))
      })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<Page<IntegrationResponseDto>> search(
      @Parameter(description = "Filter by integration name (partial match, case-insensitive)")
          @RequestParam(required = false)
          String integrationName,
      @Parameter(description = "Compatibility filter by active flag")
          @RequestParam(required = false)
          Boolean active,
      @Parameter(description = "Exact lifecycle filter (ACTIVE, RETIRED)")
          @RequestParam(required = false)
          IntegrationLifecycleStatus lifecycleStatus,
      @Parameter(
              description =
                  "Include retired integrations in results when no exact lifecycle is requested")
          @RequestParam(required = false, defaultValue = "false")
          Boolean includeRetired,
      @Parameter(description = "Filter integrations created after this timestamp (ISO-8601)")
          @RequestParam(required = false)
          OffsetDateTime createdAfter,
      @Parameter(description = "Filter integrations created before this timestamp (ISO-8601)")
          @RequestParam(required = false)
          OffsetDateTime createdBefore,
      @Parameter(
              description =
                  "Filter by tenant ID. GlobalAdmin only; when provided limits results to that"
                      + " tenant. Ignored for TenantAdmin.")
          @RequestParam(required = false)
          Integer tenantId,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Integer authTenantId = extractTenantId(auth);
    // TenantAdmin: always scope to their tenant (ignore request tenantId). GlobalAdmin: use request
    // tenantId when provided.
    Integer effectiveTenantId = (authTenantId != null) ? authTenantId : tenantId;
    IntegrationLifecycleStatus effectiveLifecycleStatus =
        lifecycleStatus != null
            ? lifecycleStatus
            : (active == null
                ? null
                : (Boolean.TRUE.equals(active)
                    ? IntegrationLifecycleStatus.ACTIVE
                    : IntegrationLifecycleStatus.RETIRED));

    Page<IntegrationResponseDto> integrations =
        service
            .findByFilters(
                integrationName,
                effectiveLifecycleStatus,
                Boolean.TRUE.equals(includeRetired),
                createdAfter,
                createdBefore,
                effectiveTenantId,
                pageable)
            .map(mapper::toResponse);

    return ResponseEntity.ok(integrations);
  }

  /**
   * Retrieves a specific integration by its unique identifier for administrative purposes.
   *
   * <p>Returns the integration if found, or throws ResourceNotFoundException if not found. This
   * endpoint provides detailed information about a specific application integration.
   *
   * @param id the unique identifier of the Integration to retrieve
   * @return ResponseEntity containing the IntegrationResponse with HTTP 200 status if found
   * @throws ResourceNotFoundException if the Integration with the given id is not found (returns
   *     HTTP 404)
   */
  @Operation(
      summary = "Retrieve integration by ID",
      description = "Returns details of a specific integration")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Integration found",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = IntegrationResponseDto.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Integration not found",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class)))
      })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{id}")
  public ResponseEntity<IntegrationResponseDto> getById(
      @Parameter(description = "Unique integration ID", example = "1") @PathVariable("id")
          Integer id) {
    // Validate tenant scoping: admin must have access to this integration
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canAccessIntegration(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Integration integration =
        service.getById(id).orElseThrow(() -> new ResourceNotFoundException("Integration", id));
    return ResponseEntity.ok(mapper.toResponse(integration));
  }

  /**
   * Creates a new integration entity for administrative purposes.
   *
   * <p>Accepts an IntegrationCreateRequestDto and creates a new Integration representing an
   * application or system to be protected by Ezkey MFA. The service layer handles all business
   * logic including default values, cryptographic key generation, and validation. Returns 201
   * Created with Location header pointing to the created resource.
   *
   * @param request the request DTO containing integration data to create
   * @return ResponseEntity containing the created IntegrationCreateResponse with HTTP 201 status
   *     and Location header
   */
  @Operation(
      summary = "Create new integration",
      description =
          "Creates a new integration with the provided data. Integration code must be unique per"
              + " tenant.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Integration created successfully",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = IntegrationCreateResponseDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid data (validation errors: invalid code format, blank code, etc.)",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Integration code already exists for this tenant",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class)))
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<IntegrationCreateResponseDto> create(
      @Parameter(
              description = "Integration creation data including code, name, and description",
              required = true)
          @RequestBody
          @jakarta.validation.Valid
          IntegrationCreateRequestDto request,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    EzkeyAdmin currentAdmin = getCurrentAdmin();
    Integer requestedTenantId = resolveRequestedIntegrationTenantId(currentAdmin);
    String requestAuditDetails = integrationCreateAuditDetails(request, currentAdmin).toJson();

    try {
      IntegrationCreateResponse savedIntegration =
          service.createIntegration(mapper.toCreateRequest(request), currentAdmin);

      Integer tenantId =
          service
              .getById(savedIntegration.getId())
              .map(i -> i.getTenant() != null ? i.getTenant().getTenantId() : null)
              .orElse(null);

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.INTEGRATION_CREATED,
                  AdminAuditConstants.INTEGRATION_CREATED,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(currentAdmin.getAdminId())
              .integrationId(savedIntegration.getId())
              .eventDetails(
                  integrationCreateAuditDetails(request, currentAdmin)
                      .custom("created_integration_id", savedIntegration.getId())
                      .custom("created_integration_code", savedIntegration.getCode())
                      .toJson())
              .build());

      URI location = URI.create("/api/v1/integrations/" + savedIntegration.getId());
      return ResponseEntity.created(location).body(mapper.toCreateResponseDto(savedIntegration));

    } catch (IntegrationCreateValidationException
        | IntegrationCodeAlreadyExistsException
        | TenantInactiveException e) {
      logger.warn("Integration creation rejected: {}", e.getMessage());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.INTEGRATION_CREATED,
                  AdminAuditConstants.INTEGRATION_CREATION_FAILED,
                  requestedTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .errorMessage(e.getMessage())
              .eventDetails(integrationCreateFailureAuditDetails(request, currentAdmin, e).toJson())
              .build());
      throw e;

    } catch (SystemTenantNotConfiguredException e) {
      logger.error(
          "Integration creation failed due to system configuration error: {}", e.getMessage());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.INTEGRATION_CREATED,
                  AdminAuditConstants.INTEGRATION_CREATION_ERROR)
              .eventStatus(EventStatus.ERROR)
              .adminId(currentAdmin.getAdminId())
              .errorMessage(e.getMessage())
              .eventDetails(
                  integrationCreateAuditDetails(request, currentAdmin)
                      .errorType(e.getClass().getSimpleName())
                      .errorSummary(
                          "System tenant configuration unavailable during integration creation")
                      .toJson())
              .build());
      throw e;

    } catch (Exception e) {
      logger.error("Integration creation failed unexpectedly", e);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.INTEGRATION_CREATED,
                  AdminAuditConstants.INTEGRATION_CREATION_ERROR,
                  requestedTenantId)
              .eventStatus(EventStatus.ERROR)
              .adminId(currentAdmin.getAdminId())
              .errorMessage(e.getMessage())
              .eventDetails(
                  integrationCreateAuditDetails(request, currentAdmin)
                      .errorType(e.getClass().getSimpleName())
                      .errorSummary("Unexpected exception during integration creation")
                      .toJson())
              .build());
      throw e;
    }
  }

  /**
   * Retires an integration while preserving history.
   *
   * <p>Retirement is the normal end-of-life posture for integrations that should disappear from
   * day-to-day operations while preserving historical traceability.
   *
   * @param id the integration ID to retire
   * @return ResponseEntity with HTTP 204 No Content on success
   */
  @Operation(
      summary = "Retire integration",
      description =
          "Retires an integration from day-to-day use while preserving historical data. The"
              + " operation bulk-revokes revocable enrollments before marking the integration"
              + " RETIRED.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "204",
            description = "Integration retired successfully",
            content = @io.swagger.v3.oas.annotations.media.Content()),
        @ApiResponse(
            responseCode = "403",
            description = "System integration cannot be retired",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Integration not found",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class)))
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/retire")
  public ResponseEntity<Void> retire(
      @Parameter(description = "Integration ID to retire", example = "1") @PathVariable("id")
          Integer id,
      @Parameter(description = "Audit justification for the retirement (min 10 characters)")
          @RequestParam
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    EzkeyAdmin currentAdmin = getCurrentAdmin();

    // Check if integration exists
    Integration integration =
        service.getById(id).orElseThrow(() -> new ResourceNotFoundException("Integration", id));

    // Validate tenant access for TenantAdmin
    if (currentAdmin.getAdminType() == EzkeyAdmin.AdminType.TENANT_ADMIN) {
      if (!integration.getTenant().getTenantId().equals(currentAdmin.getTenant().getTenantId())) {
        // Return 404 to hide existence of cross-tenant resource
        throw new ResourceNotFoundException("Integration", id);
      }
    }

    Integer tenantId =
        integration.getTenant() != null ? integration.getTenant().getTenantId() : null;

    if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
      throw new SystemIntegrationLifecycleException(
          "System integration cannot be retired because it is reserved for administrator MFA.");
    }

    AdminPrincipal principal =
        AdminProvisioningService.extractAdminPrincipal(
            SecurityContextHolder.getContext().getAuthentication());
    BulkEnrollmentOperationResult revokeResult =
        enrollmentRevocationService.revokeAllByIntegration(
            id, principal, reason, context, tenantId);
    service.retire(id);

    auditLogService.log(
        AuditHelper.createAdminAudit(
                context,
                EventType.INTEGRATION_RETIRED,
                AdminAuditConstants.INTEGRATION_RETIRED,
                tenantId)
            .eventStatus(EventStatus.SUCCESS)
            .adminId(currentAdmin.getAdminId())
            .integrationId(id)
            .eventDetails(
                AuditDetailsBuilder.builder()
                    .custom("integration_id", id)
                    .custom("revoked_enrollments", revokeResult.affectedCount())
                    .custom("skipped_enrollments", revokeResult.skippedCount())
                    .custom("lifecycle_status", IntegrationLifecycleStatus.RETIRED.name())
                    .toJson())
            .reason(reason)
            .build());

    return ResponseEntity.noContent().build();
  }

  /**
   * Deletes an integration entity by its ID for administrative purposes.
   *
   * <p>Deletion is exceptional and only allowed after retirement, when no enrollments remain.
   */
  @Operation(
      summary = "Delete integration",
      description =
          "Permanently deletes an already-retired integration that no longer has enrollments.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "204",
            description = "Integration deleted successfully",
            content = @io.swagger.v3.oas.annotations.media.Content()),
        @ApiResponse(
            responseCode = "403",
            description = "System integration cannot be deleted",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(
            responseCode = "404",
            description = "Integration not found",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Integration must be retired first and must not have enrollments",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.springframework.http.ProblemDetail.class)))
      })
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Integration ID to delete", example = "1") @PathVariable("id")
          Integer id,
      @Parameter(description = "Audit justification for the deletion (min 10 characters)")
          @RequestParam
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      HttpServletRequest httpRequest) {
    ClientContext context = ClientContext.from(httpRequest);
    EzkeyAdmin currentAdmin = getCurrentAdmin();

    // Check if integration exists
    Integration integration =
        service.getById(id).orElseThrow(() -> new ResourceNotFoundException("Integration", id));

    // Validate tenant access for TenantAdmin
    if (currentAdmin.getAdminType() == EzkeyAdmin.AdminType.TENANT_ADMIN) {
      if (!integration.getTenant().getTenantId().equals(currentAdmin.getTenant().getTenantId())) {
        throw new ResourceNotFoundException("Integration", id);
      }
    }

    Integer tenantId =
        integration.getTenant() != null ? integration.getTenant().getTenantId() : null;

    service.delete(id);

    auditLogService.log(
        AuditHelper.createAdminAudit(
                context,
                EventType.INTEGRATION_DELETED,
                AdminAuditConstants.INTEGRATION_DELETED,
                tenantId)
            .eventStatus(EventStatus.SUCCESS)
            .adminId(currentAdmin.getAdminId())
            .eventDetails(
                AuditDetailsBuilder.builder().custom("deleted_integration_id", id).toJson())
            .reason(reason)
            .build());

    return ResponseEntity.noContent().build();
  }

  /**
   * Gets the currently authenticated admin from security context.
   *
   * <p>This method extracts the admin entity from the Spring Security context. For admin
   * authentication, the principal is now AdminPrincipal (with adminId), so we load the admin by ID.
   * For API key authentication, this method should not be called.
   *
   * @return the authenticated admin
   * @throws IllegalStateException if no authentication found or not an admin authentication
   */
  private EzkeyAdmin getCurrentAdmin() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !authentication.isAuthenticated()) {
      throw new IllegalStateException("No authenticated admin found");
    }

    // Check if this is admin authentication (not API key)
    if (!hasRole(authentication, "ROLE_ADMIN")) {
      throw new IllegalStateException(
          "Current authentication is not an admin - API keys cannot access this operation");
    }

    // Extract AdminPrincipal from authentication (new multi-tenant auth flow)
    Object principal = authentication.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      // Load admin by ID from AdminPrincipal
      return adminRepository
          .findById(adminPrincipal.adminId())
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Admin not found with ID: " + adminPrincipal.adminId()));
    }

    // Fallback for backward compatibility (should not happen with new auth flow)
    // Try to extract username from principal
    String username = authentication.getName();
    return loadAdminByUsername(username);
  }

  /**
   * Loads admin entity from database by username.
   *
   * <p>Fallback method for backward compatibility. In the new multi-tenant architecture, admins are
   * loaded by ID from AdminPrincipal.
   *
   * @param username the admin username
   * @return the admin entity
   * @throws IllegalStateException if admin not found
   */
  private EzkeyAdmin loadAdminByUsername(String username) {
    return adminRepository
        .findByUsername(username)
        .orElseThrow(() -> new IllegalStateException("Admin not found: " + username));
  }

  /**
   * Checks if the authentication has the specified role.
   *
   * @param authentication the authentication context
   * @param role the role to check
   * @return true if the role is present
   */
  private boolean hasRole(Authentication authentication, String role) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> authority.getAuthority().equals(role));
  }

  private Integer resolveRequestedIntegrationTenantId(EzkeyAdmin currentAdmin) {
    if (currentAdmin.getTenant() != null) {
      return currentAdmin.getTenant().getTenantId();
    }
    return null;
  }

  private AuditDetailsBuilder integrationCreateAuditDetails(
      IntegrationCreateRequestDto request, EzkeyAdmin currentAdmin) {
    AuditDetailsBuilder builder =
        AuditDetailsBuilder.builder()
            .custom("requested_integration_code", request.code())
            .custom("requested_integration_name", request.name())
            .custom(
                "requested_description_present",
                request.description() != null && !request.description().isBlank())
            .custom("admin_id", currentAdmin.getAdminId())
            .custom("admin_type", currentAdmin.getAdminType().name());

    if (currentAdmin.getTenant() != null) {
      builder
          .custom("requested_tenant_id", currentAdmin.getTenant().getTenantId())
          .custom("requested_tenant_name", currentAdmin.getTenant().getTenantName());
    } else if (currentAdmin.getAdminType() == EzkeyAdmin.AdminType.GLOBAL_ADMIN) {
      builder.custom("requested_scope", "system_tenant");
    }

    return builder;
  }

  private AuditDetailsBuilder integrationCreateFailureAuditDetails(
      IntegrationCreateRequestDto request, EzkeyAdmin currentAdmin, Exception exception) {
    AuditDetailsBuilder builder =
        integrationCreateAuditDetails(request, currentAdmin)
            .errorType(exception.getClass().getSimpleName())
            .errorSummary("Integration creation rejected by business or validation rules");

    if (exception instanceof IntegrationCodeAlreadyExistsException duplicateCodeException) {
      builder
          .custom("conflicting_integration_code", duplicateCodeException.getCode())
          .custom("conflicting_tenant_name", duplicateCodeException.getTenantName());
    }

    return builder;
  }

  /**
   * Bulk-revokes all revocable enrollments for an integration.
   *
   * <p>Designed for incident response: when an integration's API key is compromised, all associated
   * enrollments can be immediately and permanently revoked in a single operation.
   *
   * <p><b>System integration guard:</b> Cannot be applied to system integrations — they host all
   * administrator MFA enrollments and a bulk revocation would lock out all admins.
   *
   * <p><b>Self-revocation skip:</b> If the calling admin's own enrollment is encountered during
   * bulk revocation (unlikely for non-system integrations), it is skipped rather than throwing.
   *
   * @param id the integration ID whose enrollments should be bulk-revoked
   * @param reason optional justification (min 10, max 500 characters when provided)
   * @param httpRequest the HTTP request
   * @return 200 OK with a compact result summary
   */
  @Operation(
      summary = "Bulk-revoke all enrollments for an integration",
      description =
          "Permanently revokes all revocable enrollments for the specified integration, including"
              + " deactivated VERIFIED enrollments and in-flight CREATED or BOUND enrollments."
              + " Intended for incident response (e.g., compromised API key)."
              + " Cannot be applied to system integrations.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Bulk revocation completed successfully with result summary"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(
            responseCode = "403",
            description = "Access denied or system integration guard triggered"),
        @ApiResponse(responseCode = "404", description = "Integration not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/enrollments/revoke-all")
  public ResponseEntity<BulkEnrollmentOperationResultDto> revokeAllEnrollments(
      @Parameter(description = "Integration ID", example = "5") @PathVariable("id") Integer id,
      @Parameter(
              description =
                  "Optional justification for bulk revocation (min 10 characters when provided)")
          @RequestParam(required = false)
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canAccessIntegration(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    Integer tenantId = extractTenantId(auth);

    BulkEnrollmentOperationResult result =
        enrollmentRevocationService.revokeAllByIntegration(
            id, principal, reason, context, tenantId);
    return ResponseEntity.ok(
        new BulkEnrollmentOperationResultDto(
            result.affectedCount(), result.skippedCount(), result.noOp()));
  }

  /**
   * Bulk-deactivates all active VERIFIED enrollments for an integration (reversible lockdown).
   *
   * <p>Use for precautionary lockdowns (suspected threat, emergency maintenance). Status remains
   * VERIFIED; enrollments can be restored via {@code POST .../enrollments/reactivate-all}.
   *
   * <p><b>System integration guard:</b> Cannot be applied to system integrations.
   *
   * @param id the integration ID whose enrollments should be bulk-deactivated
   * @param reason optional justification (max 500 characters)
   * @param httpRequest the HTTP request
   * @return 200 OK with a compact result summary
   */
  @Operation(
      summary = "Bulk-deactivate all enrollments for an integration",
      description =
          "Reversibly deactivates all active VERIFIED enrollments for the specified integration."
              + " Use for precautionary lockdowns. Restore with POST"
              + " .../enrollments/reactivate-all. Cannot be applied to system integrations.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Bulk deactivation completed successfully with result summary"),
        @ApiResponse(
            responseCode = "403",
            description = "Access denied or system integration guard triggered"),
        @ApiResponse(responseCode = "404", description = "Integration not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/enrollments/deactivate-all")
  public ResponseEntity<BulkEnrollmentOperationResultDto> deactivateAllEnrollments(
      @Parameter(description = "Integration ID", example = "5") @PathVariable("id") Integer id,
      @Parameter(description = "Optional justification for bulk deactivation")
          @RequestParam(required = false)
          @Size(max = 500, message = "Reason must be at most 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canAccessIntegration(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    Integer tenantId = extractTenantId(auth);

    BulkEnrollmentOperationResult result =
        enrollmentRevocationService.deactivateAllByIntegration(
            id, principal, reason, context, tenantId);
    return ResponseEntity.ok(
        new BulkEnrollmentOperationResultDto(
            result.affectedCount(), result.skippedCount(), result.noOp()));
  }

  /**
   * Bulk-reactivates all inactive VERIFIED enrollments for an integration.
   *
   * <p>Use after a precautionary deactivate-all when the threat is cleared or maintenance ends.
   * Only VERIFIED + active=false enrollments are reactivated; REVOKED enrollments are unaffected.
   *
   * @param id the integration ID whose enrollments should be bulk-reactivated
   * @param reason optional justification (max 500 characters)
   * @param httpRequest the HTTP request
   * @return 200 OK with a compact result summary
   */
  @Operation(
      summary = "Bulk-reactivate all enrollments for an integration",
      description =
          "Reactivates all inactive VERIFIED enrollments for the specified integration."
              + " Use after a precautionary deactivate-all.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Bulk reactivation completed successfully with result summary"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Integration not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/enrollments/reactivate-all")
  public ResponseEntity<BulkEnrollmentOperationResultDto> reactivateAllEnrollments(
      @Parameter(description = "Integration ID", example = "5") @PathVariable("id") Integer id,
      @Parameter(description = "Optional justification for bulk reactivation")
          @RequestParam(required = false)
          @Size(max = 500, message = "Reason must be at most 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canAccessIntegration(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    Integer tenantId = extractTenantId(auth);

    BulkEnrollmentOperationResult result =
        enrollmentRevocationService.reactivateAllByIntegration(
            id, principal, reason, context, tenantId);
    return ResponseEntity.ok(
        new BulkEnrollmentOperationResultDto(
            result.affectedCount(), result.skippedCount(), result.noOp()));
  }

  /**
   * Extracts tenant ID from authentication context for tenant scoping.
   *
   * <p>Returns the tenant ID from AdminPrincipal if present (for TenantAdmin), or null for
   * GlobalAdmin (who can access all tenants).
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
