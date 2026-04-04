/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: ApiKeyController
 * Description: REST controller for API key management operations.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.ApiKeyCreateRequestDto;
import org.ezkey.admin.dto.request.ApiKeyUpdateRequestDto;
import org.ezkey.admin.dto.response.ApiKeyCreateResponseDto;
import org.ezkey.admin.dto.response.ApiKeyResponseDto;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.exception.RateLimitExceededException;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.ApiKeyRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.exception.ApiKeyLimitExceededException;
import org.ezkey.integration.service.ApiKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for API key management operations.
 *
 * <p>This controller provides endpoints for the complete lifecycle of API keys including creation,
 * listing, retrieval, and revocation. All operations are protected by admin authentication.
 *
 * <p><b>Available Operations:</b>
 *
 * <ul>
 *   <li><b>POST /api/v1/api-keys:</b> Create new API key pair
 *   <li><b>GET /api/v1/api-keys/integration/{integrationId}:</b> List keys for integration
 *   <li><b>GET /api/v1/api-keys/{keyId}:</b> Get specific key details
 *   <li><b>PATCH /api/v1/api-keys/{keyId}:</b> Partially update API key config (ipWhitelist,
 *       description)
 *   <li><b>DELETE /api/v1/api-keys/{keyId}:</b> Revoke API key
 * </ul>
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li>All endpoints require admin authentication (Bearer token)
 *   <li>Secret keys shown ONLY ONCE during creation
 *   <li>Comprehensive audit logging for all operations
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyService
 */
@Validated
@RestController
@RequestMapping("/api/v1/api-keys")
@Tag(
    name = "API Keys",
    description = "API key management for machine-to-machine (Integration API) authentication")
public class ApiKeyController {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyController.class);

  private final ApiKeyService apiKeyService;
  private final ApiKeyRepository apiKeyRepository;
  private final AdminOperationsRateLimitService adminOpsRateLimitService;
  private final EzkeyAdminRepository adminRepository;
  private final AccessControlService accessControlService;
  private final AuditLogService auditLogService;
  private final AuditEntityFkResolver auditEntityFkResolver;

  /**
   * Constructs a new ApiKeyController.
   *
   * @param apiKeyService the API key service
   * @param apiKeyRepository the API key repository for paginated list queries
   * @param adminOpsRateLimitService the admin operations rate limiting service
   * @param adminRepository the admin repository for loading admin entities
   * @param accessControlService the access control service for tenant scoping validation
   * @param auditLogService the audit log service for security monitoring
   * @param auditEntityFkResolver resolves audit foreign keys only when referenced rows exist
   */
  public ApiKeyController(
      ApiKeyService apiKeyService,
      ApiKeyRepository apiKeyRepository,
      AdminOperationsRateLimitService adminOpsRateLimitService,
      EzkeyAdminRepository adminRepository,
      AccessControlService accessControlService,
      AuditLogService auditLogService,
      AuditEntityFkResolver auditEntityFkResolver) {
    this.apiKeyService = apiKeyService;
    this.apiKeyRepository = apiKeyRepository;
    this.adminOpsRateLimitService = adminOpsRateLimitService;
    this.adminRepository = adminRepository;
    this.accessControlService = accessControlService;
    this.auditLogService = auditLogService;
    this.auditEntityFkResolver = auditEntityFkResolver;
  }

  /**
   * Creates a new API key pair for an integration.
   *
   * <p>This endpoint generates a new API key pair consisting of a public integration key and a
   * private secret key. The secret key is shown ONLY ONCE in the response and must be saved
   * immediately.
   *
   * <p><b>Important:</b> The secret key cannot be retrieved later. If lost, create a new API key.
   *
   * <p><b>Security Features:</b>
   *
   * <ul>
   *   <li>Secret key generated with SecureRandom (160 bits entropy)
   *   <li>BCrypt hashed for storage
   *   <li>Optional IP whitelist for production use
   *   <li>Optional expiration for automatic rotation
   * </ul>
   *
   * @param request the API key creation request
   * @return ResponseEntity containing the new API key pair with secret shown once
   */
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  @Operation(
      summary = "Create new API key pair",
      description =
          "Generates a new API key pair for machine-to-machine authentication. "
              + "The secret key is shown ONLY ONCE and cannot be retrieved later.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "API key created successfully - SAVE THE SECRET KEY IMMEDIATELY",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyCreateResponseDto.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required"),
        @ApiResponse(responseCode = "404", description = "Integration not found or inactive"),
        @ApiResponse(
            responseCode = "409",
            description = "Maximum active keys limit reached for integration")
      })
  public ResponseEntity<ApiKeyCreateResponseDto> createApiKey(
      @Valid @RequestBody ApiKeyCreateRequestDto request, HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    logger.info(
        "Creating API key for integration: {} with description: '{}'",
        request.integrationId(),
        request.description());

    // Get authenticated admin from security context
    EzkeyAdmin currentAdmin = getCurrentAdmin();
    Integer adminTenantId =
        currentAdmin.getAdminType() == EzkeyAdmin.AdminType.TENANT_ADMIN
            ? currentAdmin.getTenant().getTenantId()
            : null;

    // Validate tenant scoping: admin must have access to the integration
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canAccessIntegration(auth, request.integrationId())) {
      logger.warn(
          "Admin {} attempted to create API key for integration {} without access",
          currentAdmin.getUsername(),
          request.integrationId());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Check rate limiting for admin operations
    String adminId = currentAdmin.getUsername();
    if (!adminOpsRateLimitService.canCreateApiKey(adminId)) {
      throw new RateLimitExceededException("API_KEY_CREATE", 5, 0, 15);
    }

    try {
      // Create API key via service
      ApiKeyService.ApiKeyCreationResult result =
          apiKeyService.createApiKey(
              request.integrationId(),
              currentAdmin,
              request.description(),
              request.expiresAt(),
              request.ipWhitelist());

      // Map to response DTO
      ApiKeyCreateResponseDto response =
          new ApiKeyCreateResponseDto(
              result.getApiKeyId(),
              result.getIntegrationKey(),
              result.getSecretKey(), // Plain text secret - SHOWN ONCE
              result.getDescription(),
              result.getCreatedAt(),
              result.getExpiresAt(),
              result.getIpWhitelist(),
              "IMPORTANT: Save the secret key now. It will not be shown again.");

      logger.info(
          "API key created successfully - ID: {}, Integration: {}, Admin: {}",
          result.getApiKeyId(),
          request.integrationId(),
          currentAdmin.getUsername());

      // Record successful operation for rate limiting
      adminOpsRateLimitService.recordCreateApiKey(adminId);

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_CREATED,
                  AdminAuditConstants.API_KEY_CREATED,
                  adminTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(currentAdmin.getAdminId())
              .integrationId(
                  auditEntityFkResolver.integrationIdForAuditOrNull(request.integrationId()))
              .eventDetails("API key ID: " + result.getApiKeyId())
              .build());

      return ResponseEntity.status(HttpStatus.CREATED).body(response);

    } catch (IllegalArgumentException e) {
      logger.warn("API key creation failed - Bad request: {}", e.getMessage());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_CREATED,
                  AdminAuditConstants.API_KEY_CREATION_FAILED,
                  adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .integrationId(
                  auditEntityFkResolver.integrationIdForAuditOrNull(request.integrationId()))
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.badRequest().build();
    } catch (TenantInactiveException | ApiKeyLimitExceededException e) {
      logger.warn("API key creation failed - Business conflict: {}", e.getMessage());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_CREATED,
                  AdminAuditConstants.API_KEY_CREATION_FAILED,
                  adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .integrationId(
                  auditEntityFkResolver.integrationIdForAuditOrNull(request.integrationId()))
              .errorMessage(e.getMessage())
              .build());
      throw e;
    }
  }

  /**
   * Lists API keys visible to the current admin with server-side pagination and optional filters.
   *
   * <p>This endpoint returns API keys based on admin type:
   *
   * <ul>
   *   <li><b>GlobalAdmin:</b> Can see all API keys across all tenants
   *   <li><b>TenantAdmin:</b> Can only see API keys for integrations in their own tenant
   * </ul>
   *
   * <p>Optional filters: integrationId, active (boolean), description (partial match,
   * case-insensitive). Sortable by apiKeyId, integrationKey, description, active, createdAt,
   * expiresAt, lastUsedAt, revokedAt.
   *
   * <p>The secret keys are never included in the response for security.
   *
   * @param integrationId optional filter by integration ID
   * @param active optional filter by active flag (true/false; omit for all)
   * @param description optional filter by description (partial match, case-insensitive)
   * @param pageable pagination and sort (default: size=20, sort=createdAt,DESC)
   * @return ResponseEntity containing paginated list (content + page metadata)
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  @Operation(
      summary = "List all API keys for current admin",
      description =
          "Returns API keys with pagination, filtered by admin type. GlobalAdmin sees all keys, "
              + "TenantAdmin sees only keys for their tenant's integrations. Optional filters: "
              + "integrationId, active, description. Use page, size, sort for pagination. "
              + "Secret keys are never included.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Paginated list of API keys (content + page metadata)",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required")
      })
  public ResponseEntity<Page<ApiKeyResponseDto>> listAllApiKeys(
      @Parameter(description = "Filter by integration ID") @RequestParam(required = false)
          Integer integrationId,
      @Parameter(description = "Filter by active flag (true/false; omit for all)")
          @RequestParam(required = false)
          Boolean active,
      @Parameter(description = "Filter by description (partial match, case-insensitive)")
          @RequestParam(required = false)
          String description,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    EzkeyAdmin currentAdmin = getCurrentAdmin();

    logger.info(
        "Listing API keys for admin: {} (type: {})",
        currentAdmin.getUsername(),
        currentAdmin.getAdminType());

    Integer tenantIdForScope = null;
    if (currentAdmin.getAdminType() == EzkeyAdmin.AdminType.TENANT_ADMIN) {
      tenantIdForScope = currentAdmin.getTenant().getTenantId();
      logger.debug("TenantAdmin - scoping API keys to tenant: {}", tenantIdForScope);
    }

    final Integer tenantId = tenantIdForScope;
    Specification<ApiKey> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          if (tenantId != null) {
            predicates.add(
                cb.equal(root.get("integration").get("tenant").get("tenantId"), tenantId));
          }
          if (integrationId != null) {
            predicates.add(cb.equal(root.get("integration").get("id"), integrationId));
          }
          if (active != null) {
            predicates.add(cb.equal(root.get("active"), active));
          }
          if (description != null && !description.isBlank()) {
            String pattern = "%" + description.trim().toLowerCase() + "%";
            predicates.add(cb.like(cb.lower(root.get("description")), pattern));
          }
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<ApiKeyResponseDto> page =
        apiKeyRepository.findAll(spec, pageable).map(this::mapToResponseDto);

    logger.info(
        "Returning page {} with {} API keys for admin: {} (type: {})",
        page.getNumber(),
        page.getNumberOfElements(),
        currentAdmin.getUsername(),
        currentAdmin.getAdminType());

    return ResponseEntity.ok(page);
  }

  /**
   * Lists API keys for a specific integration with server-side pagination.
   *
   * <p>This endpoint returns API keys for the specified integration. When {@code active} is not
   * provided, only active (non-revoked) keys are returned (backward compatible). When {@code
   * active} is provided, filters by that value. The secret keys are never included in the response
   * for security.
   *
   * @param integrationId the integration ID (path)
   * @param active optional filter by active flag (omit = active only; true/false for explicit)
   * @param pageable pagination and sort (default: size=20, sort=createdAt,DESC)
   * @return ResponseEntity containing paginated list (content + page metadata)
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/integration/{integrationId}")
  @Operation(
      summary = "List API keys for integration",
      description =
          "Returns API keys for the specified integration with pagination. "
              + "When active is omitted, returns only active keys. Use page, size, sort. "
              + "Secret keys are never included in responses.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Paginated list of API keys (content + page metadata)",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required")
      })
  public ResponseEntity<Page<ApiKeyResponseDto>> listApiKeys(
      @Parameter(description = "Integration ID", example = "123") @PathVariable("integrationId")
          Integer integrationId,
      @Parameter(description = "Filter by active flag (omit = active only)")
          @RequestParam(required = false)
          Boolean active,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    logger.debug("Listing API keys for integration: {}", integrationId);

    // When active is not provided, default to true (preserve previous behavior: active-only)
    boolean filterActive = active != null ? active : true;

    Specification<ApiKey> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();
          predicates.add(cb.equal(root.get("integration").get("id"), integrationId));
          predicates.add(cb.equal(root.get("active"), filterActive));
          return cb.and(predicates.toArray(new Predicate[0]));
        };

    Page<ApiKeyResponseDto> page =
        apiKeyRepository.findAll(spec, pageable).map(this::mapToResponseDto);

    logger.info(
        "Returning page {} with {} API keys for integration: {}",
        page.getNumber(),
        page.getNumberOfElements(),
        integrationId);

    return ResponseEntity.ok(page);
  }

  /**
   * Partially updates an API key's configuration.
   *
   * <p>Only non-null fields in the request body are applied. Supports ipWhitelist and description.
   * Only active (non-revoked) keys can be updated. Include {@code version} from the GET response
   * for optimistic locking.
   *
   * @param keyId the API key ID to update
   * @param request the partial update request
   * @param httpRequest the HTTP request for audit context
   * @return ResponseEntity containing updated API key with HTTP 200, or error status
   */
  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{keyId}")
  @Operation(
      summary = "Partially update API key configuration",
      description =
          "Updates API key config (ipWhitelist, description). Only active keys. "
              + "Include version from GET for optimistic locking.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "API key updated successfully",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyResponseDto.class))),
        @ApiResponse(
            responseCode = "400",
            description =
                "Invalid data, key revoked, or ipWhitelist validation failed (invalid CIDR)"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(
            responseCode = "409",
            description = "Optimistic lock conflict - resource was modified, re-fetch and retry"),
        @ApiResponse(
            responseCode = "429",
            description = "Too many update requests - rate limit exceeded")
      })
  public ResponseEntity<ApiKeyResponseDto> updateApiKey(
      @Parameter(description = "API key ID to update", example = "42") @PathVariable("keyId")
          Integer keyId,
      @Valid @RequestBody ApiKeyUpdateRequestDto request,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);
    EzkeyAdmin currentAdmin = getCurrentAdmin();
    Integer adminTenantId =
        currentAdmin.getAdminType() == EzkeyAdmin.AdminType.TENANT_ADMIN
            ? currentAdmin.getTenant().getTenantId()
            : null;

    Optional<ApiKey> existingOpt = apiKeyService.getApiKey(keyId);
    if (existingOpt.isEmpty()) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_UPDATED,
                  AdminAuditConstants.API_KEY_UPDATE_FAILED,
                  adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .errorMessage("API key not found: " + keyId)
              .build());
      return ResponseEntity.notFound().build();
    }

    ApiKey existing = existingOpt.get();
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canAccessIntegration(auth, existing.getIntegration().getId())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Check rate limiting for admin operations
    String adminId = currentAdmin.getUsername();
    if (!adminOpsRateLimitService.canUpdateApiKey(adminId)) {
      throw new RateLimitExceededException("API_KEY_UPDATE", 20, 0, 15);
    }

    try {
      ApiKey updated =
          apiKeyService.updateApiKey(
              keyId, request.description(), request.ipWhitelist(), request.version());

      adminOpsRateLimitService.recordUpdateApiKey(adminId);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_UPDATED,
                  AdminAuditConstants.API_KEY_UPDATED,
                  adminTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(currentAdmin.getAdminId())
              .eventDetails("API key ID: " + keyId)
              .build());

      return ResponseEntity.ok(mapToResponseDto(updated));
    } catch (org.ezkey.exception.ResourceNotFoundException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_UPDATED,
                  AdminAuditConstants.API_KEY_UPDATE_FAILED,
                  adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .errorMessage("API key not found: " + keyId)
              .build());
      throw e;
    } catch (IllegalArgumentException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_UPDATED,
                  AdminAuditConstants.API_KEY_UPDATE_FAILED,
                  adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .errorMessage(e.getMessage())
              .build());
      throw e;
    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_UPDATED,
                  AdminAuditConstants.API_KEY_UPDATE_FAILED,
                  adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .errorMessage("Optimistic lock conflict")
              .build());
      throw e;
    }
  }

  /**
   * Gets details of a specific API key.
   *
   * <p>This endpoint returns the details of a specific API key. The secret key is never included in
   * the response.
   *
   * @param keyId the API key ID
   * @return ResponseEntity containing the API key details
   */
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{keyId}")
  @Operation(
      summary = "Get API key details",
      description = "Returns details of a specific API key. Secret key is never included.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "API key details",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ApiKeyResponseDto.class))),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required"),
        @ApiResponse(responseCode = "404", description = "API key not found")
      })
  public ResponseEntity<ApiKeyResponseDto> getApiKey(
      @Parameter(description = "API key ID", example = "42") @PathVariable("keyId") Integer keyId) {

    logger.debug("Retrieving API key: {}", keyId);

    return apiKeyService
        .getApiKey(keyId)
        .map(this::mapToResponseDto)
        .map(ResponseEntity::ok)
        .orElseGet(
            () -> {
              logger.warn("API key not found: {}", keyId);
              return ResponseEntity.notFound().build();
            });
  }

  /**
   * Revokes an API key immediately.
   *
   * <p>This endpoint performs immediate revocation of an API key, making it unusable for
   * authentication. The key is preserved for audit purposes with revocation metadata.
   *
   * <p><b>Use Cases:</b>
   *
   * <ul>
   *   <li>Compromised key security incident
   *   <li>Key rotation cleanup (after new key is deployed)
   *   <li>Decommissioning an application
   * </ul>
   *
   * @param keyId the API key ID to revoke
   * @return ResponseEntity with no content on success
   */
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{keyId}")
  @Operation(
      summary = "Revoke API key",
      description =
          "Immediately revokes an API key, making it unusable for authentication. "
              + "The key is preserved for audit purposes.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "API key revoked successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - admin token required"),
        @ApiResponse(responseCode = "404", description = "API key not found"),
        @ApiResponse(
            responseCode = "429",
            description = "Too many revoke requests - rate limit exceeded")
      })
  public ResponseEntity<Void> revokeApiKey(
      @Parameter(description = "API key ID to revoke", example = "42") @PathVariable("keyId")
          Integer keyId,
      @Parameter(description = "Audit justification for the revocation (min 10 characters)")
          @RequestParam(required = false)
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    logger.info("Revoking API key: {}", keyId);

    // Get authenticated admin
    EzkeyAdmin currentAdmin = getCurrentAdmin();
    Integer adminTenantId =
        currentAdmin.getAdminType() == EzkeyAdmin.AdminType.TENANT_ADMIN
            ? currentAdmin.getTenant().getTenantId()
            : null;

    // Check rate limiting for admin operations
    String adminId = currentAdmin.getUsername();
    if (!adminOpsRateLimitService.canRevokeApiKey(adminId)) {
      throw new RateLimitExceededException("API_KEY_REVOKE", 10, 0, 15);
    }

    boolean revoked = apiKeyService.revokeApiKey(keyId, currentAdmin);

    if (revoked) {
      adminOpsRateLimitService.recordRevokeApiKey(adminId);
      logger.info(
          "API key revoked successfully: {} by admin: {}", keyId, currentAdmin.getUsername());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_REVOKED,
                  AdminAuditConstants.API_KEY_REVOKED,
                  adminTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(currentAdmin.getAdminId())
              .reason(reason)
              .eventDetails("API key ID: " + keyId)
              .build());
      return ResponseEntity.noContent().build();
    } else {
      logger.warn("API key not found for revocation: {}", keyId);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.API_KEY_REVOKED,
                  AdminAuditConstants.API_KEY_REVOCATION_NOT_FOUND,
                  adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(currentAdmin.getAdminId())
              .errorMessage("API key not found: " + keyId)
              .build());
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Maps an ApiKey entity to a response DTO.
   *
   * <p>Note: Secret key is never included in the mapping for security.
   *
   * @param apiKey the API key entity
   * @return the response DTO
   */
  private ApiKeyResponseDto mapToResponseDto(ApiKey apiKey) {
    String revokedByUsername = null;
    if (apiKey.getRevokedByAdmin() != null) {
      revokedByUsername = apiKey.getRevokedByAdmin().getUsername();
    }

    return new ApiKeyResponseDto(
        apiKey.getApiKeyId(),
        apiKey.getVersion(),
        apiKey.getIntegration().getId(),
        apiKey.getIntegrationKey(),
        apiKey.getDescription(),
        apiKey.getActive(),
        apiKey.getCreatedAt(),
        apiKey.getExpiresAt(),
        apiKey.getLastUsedAt(),
        apiKey.getIpWhitelist(),
        apiKey.getRevokedAt(),
        revokedByUsername);
  }

  /**
   * Gets the currently authenticated admin from security context.
   *
   * <p>This method extracts the EzkeyAdmin from the Spring Security context. For admin
   * authentication, the principal is now AdminPrincipal (with adminId), so we load the admin by ID.
   * For API key authentication, this method is not applicable as API keys don't have admin context.
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
   * <p>In the current architecture with a single global admin, this method loads the admin entity
   * from the database using the EzkeyAdminRepository.
   *
   * @param username the admin username
   * @return the admin entity
   * @throws IllegalStateException if admin not found
   */
  private EzkeyAdmin loadAdminByUsername(String username) {
    // Use the repository to load the admin entity
    // This ensures consistency with the rest of the authentication system
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
}
