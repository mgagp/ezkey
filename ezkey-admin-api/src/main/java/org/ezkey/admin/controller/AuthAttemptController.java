/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuthAttemptController
 * Description: REST controller for authorization attempt API v1 using JPA service.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.security.RateLimitService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.admin.util.ClientContext;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptAdminApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.RateLimitExceededException;
import org.ezkey.exception.ResourceNotFoundException;
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
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authorization attempt administration API v1.
 *
 * <p>This controller provides REST endpoints for authorization attempt management operations in the
 * admin API (internal). It handles CRUD operations for authentication attempts, allowing
 * administrators to create, view, and delete authentication requests. Uses JPA-based service and
 * DTOs for clean API responses with proper HTTP status codes.
 *
 * <p><b>Admin API Endpoints (Internal):</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/auth-attempts</b> - List all authorization attempts
 *   <li><b>GET /api/v1/auth-attempts/{id}</b> - Get authorization attempt by ID
 *   <li><b>GET /api/v1/auth-attempts/{id}/wait</b> - Wait for authentication response
 *   <li><b>POST /api/v1/auth-attempts</b> - Create new authorization attempt
 *   <li><b>DELETE /api/v1/auth-attempts/{id}</b> - Delete authorization attempt
 * </ul>
 *
 * <p><b>Usage Context:</b> This is part of the admin-api (port 9080) for internal administration
 * purposes. For mobile authentication consumption, see auth-api endpoints.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptService
 * @see AuthAttemptDto
 * @see AuthAttemptCreateRequestDto
 * @see AuthAttemptCreateResponseDto
 * @see AuthAttemptWaitRequestDto
 * @see AuthAttemptWaitResponseDto
 */
@RestController
@RequestMapping("/api/v1/auth-attempts")
@Tag(name = "Auth Attempts", description = "Authentication attempt management API")
public class AuthAttemptController {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptController.class);

  private final AuthAttemptService authAttemptService;
  private final AuthAttemptAdminApiMapper authAttemptMapper;
  private final AuditLogService auditLogService;
  private final RateLimitService rateLimitService;
  private final EnrollmentRepository enrollmentRepository;
  private final AccessControlService accessControlService;

  /**
   * Constructs the authorization attempt controller with required dependencies.
   *
   * @param authAttemptService the JPA-based authorization attempt service
   * @param authAttemptMapper the MapStruct mapper for entity-DTO conversions
   * @param auditLogService the audit log service for security monitoring
   * @param rateLimitService the rate limiting service for API key operations
   * @param enrollmentRepository the enrollment repository for ownership checks
   * @param accessControlService the access control service for tenant scoping validation
   */
  public AuthAttemptController(
      AuthAttemptService authAttemptService,
      AuthAttemptAdminApiMapper authAttemptMapper,
      AuditLogService auditLogService,
      RateLimitService rateLimitService,
      EnrollmentRepository enrollmentRepository,
      AccessControlService accessControlService) {
    this.authAttemptService = authAttemptService;
    this.authAttemptMapper = authAttemptMapper;
    this.auditLogService = auditLogService;
    this.rateLimitService = rateLimitService;
    this.enrollmentRepository = enrollmentRepository;
    this.accessControlService = accessControlService;
  }

  /**
   * Searches authentication attempts with optional filters and pagination.
   *
   * <p>Retrieves authentication attempts matching the specified criteria with pagination support.
   * All filter parameters are optional - if none are provided, returns all authentication attempts
   * (paginated). Results are ordered by creation date descending (newest first) by default.
   *
   * <p><b>Use Case:</b> Security operators monitoring authentication attempts, forensic analysis,
   * incident investigation, and compliance reporting.
   *
   * <p><b>Pagination and Sorting:</b>
   *
   * <ul>
   *   <li>Use <code>?page=0&size=20</code> for pagination (zero-based page numbers)
   *   <li>Use <code>?sort=field,direction</code> for sorting (e.g., <code>?sort=authAttemptId,asc
   *       </code> or <code>?sort=createdAt,desc</code>)
   *   <li>Default: page=0, size=20, sort=createdAt,DESC
   *   <li>Sortable fields: authAttemptId, createdAt, expiresAt, enrollmentId
   * </ul>
   *
   * @param status optional filter by authentication attempt status
   * @param enrollmentId optional filter by enrollment ID
   * @param integrationId optional filter by integration ID
   * @param createdAfter optional filter for attempts created after this timestamp
   * @param createdBefore optional filter for attempts created before this timestamp
   * @param pageable pagination and sorting parameters (default: page=0, size=20,
   *     sort=createdAt,DESC)
   * @return ResponseEntity containing page of authorization attempt DTOs with HTTP 200 status
   */
  @Operation(
      summary = "Search auth attempts",
      description =
          "Retrieves authentication attempts with optional filters and pagination for security "
              + "monitoring, forensic analysis, and compliance reporting. Supports dynamic sorting "
              + "via ?sort=field,direction (e.g., ?sort=authAttemptId,asc). Default sort is by "
              + "creation date descending (newest first).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<Page<AuthAttemptDto>> search(
      @Parameter(
              description =
                  "Filter by authentication attempt status (PENDING, READ, ACCEPTED, "
                      + "REJECTED, INVALID, EXPIRED)")
          @RequestParam(required = false)
          AuthAttemptStatus status,
      @Parameter(description = "Filter by enrollment ID") @RequestParam(required = false)
          Integer enrollmentId,
      @Parameter(description = "Filter by integration ID") @RequestParam(required = false)
          Integer integrationId,
      @Parameter(description = "Filter attempts created after this timestamp (ISO-8601)")
          @RequestParam(required = false)
          OffsetDateTime createdAfter,
      @Parameter(description = "Filter attempts created before this timestamp (ISO-8601)")
          @RequestParam(required = false)
          OffsetDateTime createdBefore,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    // Extract tenant ID from authentication for tenant scoping
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Integer tenantId = extractTenantId(auth);

    Page<AuthAttemptDto> authAttempts =
        authAttemptService
            .findByFilters(
                status,
                enrollmentId,
                integrationId,
                createdAfter,
                createdBefore,
                tenantId,
                pageable)
            .map(authAttemptMapper::toDto);

    return ResponseEntity.ok(authAttempts);
  }

  /**
   * Retrieves an authorization attempt by its ID for administrative purposes.
   *
   * <p>Returns the authorization attempt data as a DTO for administrative review. Returns 404 if
   * the authorization attempt is not found.
   *
   * @param id the authorization attempt ID
   * @return ResponseEntity containing authorization attempt DTO with HTTP 200 status, or 404 if not
   *     found
   */
  @Operation(
      summary = "Retrieve auth attempt by ID",
      description = "Returns details of a specific authentication attempt")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Auth attempt found"),
        @ApiResponse(responseCode = "404", description = "Auth attempt not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("@accessControlService.canAccessAuthAttempt(authentication, #id)")
  @GetMapping("/{id}")
  public ResponseEntity<AuthAttemptDto> getById(
      @Parameter(description = "Unique auth attempt ID", example = "1") @PathVariable("id")
          Integer id) {
    try {
      AuthAttempt authAttempt = authAttemptService.getById(id);
      AuthAttemptDto response = authAttemptMapper.toDto(authAttempt);
      return ResponseEntity.ok(response);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Creates a new authorization attempt for administrative purposes.
   *
   * <p>Creates a new authorization attempt with the provided data and returns the created attempt.
   * This is typically used by integrating applications to initiate MFA authentication requests.
   * Returns 201 Created with the created authorization attempt data.
   *
   * @param request the authorization attempt creation request DTO
   * @return ResponseEntity containing created authorization attempt response with HTTP 201 status
   */
  @Operation(
      summary = "Create new auth attempt",
      description = "Creates a new authentication attempt for MFA validation")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Auth attempt created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasAnyRole('ADMIN', 'API_KEY')")
  @PostMapping
  public ResponseEntity<AuthAttemptCreateResponseDto> create(
      @Parameter(description = "Auth attempt creation data", required = true) @RequestBody
          AuthAttemptCreateRequestDto request,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    // Check rate limiting for API keys
    String apiKeyId = extractApiKeyId(httpRequest);
    if (apiKeyId != null && !rateLimitService.canCreateAuthAttempt(apiKeyId)) {
      throw new RateLimitExceededException("CREATE_AUTH_ATTEMPT", 100, 0, 15);
    }

    // Check enrollment ownership for API keys
    if (apiKeyId != null) {
      validateEnrollmentOwnership(request.enrollmentId(), apiKeyId);
    }

    // Validate tenant scoping for admins: admin must have access to the enrollment
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
      // Admin authentication - validate access to enrollment
      if (!accessControlService.canAccessEnrollment(auth, request.enrollmentId())) {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.AUTH_ATTEMPT_CREATED,
                    AdminAuditConstants.AUTH_ATTEMPT_CREATION_FAILED)
                .eventStatus(EventStatus.FAILURE)
                .errorMessage(
                    "Access denied: admin does not have access to enrollment "
                        + request.enrollmentId())
                .build());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }
    }

    try {
      AuthAttemptCreateResponse response =
          authAttemptService.create(authAttemptMapper.toAuthAttemptCreateRequest(request));

      // Record successful operation for rate limiting
      if (apiKeyId != null) {
        rateLimitService.recordCreateAuthAttempt(apiKeyId);
      }

      // Audit successful auth attempt creation
      String authType = apiKeyId != null ? "API_KEY" : "BEARER_TOKEN";
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.AUTH_ATTEMPT_CREATED, AdminAuditConstants.AUTH_ATTEMPT_CREATED)
              .eventStatus(EventStatus.SUCCESS)
              .authAttemptId(response.getAuthAttemptId())
              .authAttemptCreatedAt(response.getCreatedAt())
              .enrollmentId(request.enrollmentId())
              .eventDetails(
                  "Auth Type: "
                      + authType
                      + ", Challenge: "
                      + (response.getAuthAttemptChallenge() != null ? "required" : "not required"))
              .build());

      return ResponseEntity.status(HttpStatus.CREATED)
          .body(authAttemptMapper.toAuthAttemptCreateResponseDto(response));
    } catch (IllegalArgumentException e) {
      // Audit validation failure - do not include enrollmentId as it may not exist
      // (would violate FK constraint if enrollment doesn't exist)
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CREATED,
                  AdminAuditConstants.AUTH_ATTEMPT_CREATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              // enrollmentId omitted - may not exist, would violate FK constraint
              .errorMessage(e.getMessage() + " (enrollmentId: " + request.enrollmentId() + ")")
              .build());

      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      // Audit error - do not include enrollmentId as it may not exist
      // (would violate FK constraint if enrollment doesn't exist)
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CREATED,
                  AdminAuditConstants.AUTH_ATTEMPT_CREATION_ERROR)
              .eventStatus(EventStatus.ERROR)
              // enrollmentId omitted - may not exist, would violate FK constraint
              .errorMessage(e.getMessage() + " (enrollmentId: " + request.enrollmentId() + ")")
              .build());

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Deletes an authorization attempt by its ID for administrative purposes.
   *
   * <p>Removes an authorization attempt from the system. Returns 204 No Content on successful
   * deletion, or 404 if not found.
   *
   * @param id the authorization attempt ID to delete
   * @return ResponseEntity with HTTP 204 No Content on success, or 404 if not found
   */
  @Operation(
      summary = "Delete auth attempt",
      description = "Removes an authentication attempt from the system")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Auth attempt deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Auth attempt not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Auth attempt ID to delete", example = "1") @PathVariable("id")
          Integer id) {
    try {
      authAttemptService.delete(id);
      return ResponseEntity.noContent().build();
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Cancels a pending or read authentication attempt by marking it as expired.
   *
   * <p>This endpoint allows client applications to proactively cancel authentication requests that
   * are still waiting for user response. Only attempts in PENDING or READ status can be cancelled;
   * attempts that are already in a final state (ACCEPTED, REJECTED, INVALID, EXPIRED) cannot be
   * cancelled.
   *
   * <p><b>Use Case:</b> When a user decides to abort the authentication flow (e.g., clicks "Cancel"
   * or navigates away), the client application can call this endpoint to immediately mark the
   * attempt as expired, allowing any waiting threads to terminate promptly.
   *
   * <p><b>Security:</b> This endpoint supports both Bearer token (admin) and API key (M2M)
   * authentication. Access control ensures that users can only cancel attempts they have access to.
   *
   * @param id the authentication attempt ID to cancel
   * @param httpRequest the HTTP request for audit logging
   * @return ResponseEntity containing the updated authentication attempt DTO with HTTP 200 status,
   *     or 400 if already completed, or 404 if not found
   */
  @Operation(
      summary = "Cancel auth attempt",
      description =
          "Cancels a pending or read authentication attempt by marking it as expired. "
              + "Allows client applications to proactively abort authentication requests.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Auth attempt cancelled successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Auth attempt already in final state (cannot be cancelled)"),
        @ApiResponse(responseCode = "404", description = "Auth attempt not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("@accessControlService.canAccessAuthAttempt(authentication, #id)")
  @PostMapping("/{id}/cancel")
  public ResponseEntity<AuthAttemptDto> cancel(
      @Parameter(description = "Auth attempt ID to cancel", example = "1") @PathVariable("id")
          Integer id,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    // Check rate limiting for API keys
    String apiKeyId = extractApiKeyId(httpRequest);
    if (apiKeyId != null && !rateLimitService.canWaitAuthAttempt(apiKeyId)) {
      throw new RateLimitExceededException("CANCEL_AUTH_ATTEMPT", 200, 0, 15);
    }

    try {
      AuthAttempt cancelledAttempt = authAttemptService.cancel(id);

      // Record successful operation for rate limiting
      if (apiKeyId != null) {
        rateLimitService.recordWaitAuthAttempt(apiKeyId);
      }

      // Audit successful cancellation
      String authType = apiKeyId != null ? "API_KEY" : "BEARER_TOKEN";
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  AdminAuditConstants.AUTH_ATTEMPT_CANCELLED)
              .eventStatus(EventStatus.SUCCESS)
              .authAttemptId(id)
              .enrollmentId(cancelledAttempt.getEnrollmentId())
              .eventDetails("Auth Type: " + authType + ", Status: EXPIRED")
              .build());

      AuthAttemptDto response = authAttemptMapper.toDto(cancelledAttempt);
      return ResponseEntity.ok(response);

    } catch (ResourceNotFoundException e) {
      // Audit not found
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  AdminAuditConstants.AUTH_ATTEMPT_CANCELLATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .authAttemptId(id)
              .errorMessage("Authentication attempt not found")
              .build());

      return ResponseEntity.notFound().build();

    } catch (IllegalArgumentException e) {
      // Audit validation failure (already in final state)
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  AdminAuditConstants.AUTH_ATTEMPT_CANCELLATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .authAttemptId(id)
              .errorMessage(e.getMessage())
              .build());

      return ResponseEntity.badRequest().build();

    } catch (Exception e) {
      // Audit error
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  AdminAuditConstants.AUTH_ATTEMPT_CANCELLATION_FAILED)
              .eventStatus(EventStatus.ERROR)
              .authAttemptId(id)
              .errorMessage(e.getMessage())
              .build());

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Waits for authentication response completion with configurable timeout and polling.
   *
   * <p>This endpoint allows applications to wait for mobile device responses to authentication
   * requests. It implements a polling mechanism that checks the authentication status at regular
   * intervals until either the device responds or the timeout is reached.
   *
   * <p><b>MFA Integration Context:</b> This endpoint enables synchronous-like behavior in the
   * asynchronous MFA authentication flow. Applications can wait for user responses without
   * implementing their own polling logic, simplifying integration.
   *
   * <p><b>Status Calculation:</b> The response includes a calculated status based on the rules
   * defined in ENDPOINT.md:
   *
   * <ul>
   *   <li><b>PENDING:</b> Authentication request created but not yet read by device
   *   <li><b>READ:</b> Device has read the request but not yet responded
   *   <li><b>INVALID:</b> Authentication was invalid (wrong signature, challenge, etc.)
   *   <li><b>REJECTED:</b> User rejected the authentication request
   *   <li><b>ACCEPTED:</b> User accepted the authentication request
   * </ul>
   *
   * <p><b>Security Note:</b> This endpoint is part of the admin API and should only be accessible
   * to authorized applications. The polling mechanism prevents excessive resource consumption while
   * providing responsive authentication status updates.
   *
   * @param id the authentication attempt ID to wait for
   * @param timeoutSeconds maximum duration to wait in seconds (default: 30, max: 300)
   * @param pollingSeconds interval between status checks in seconds (default: 2, max: 60)
   * @return ResponseEntity containing authentication status with HTTP 200 for completion, 408 for
   *     timeout, 404 for not found, or 400 for invalid parameters
   */
  @PreAuthorize("@accessControlService.canAccessAuthAttempt(authentication, #id)")
  @GetMapping("/{id}/wait")
  @Operation(
      summary = "Wait for authentication response",
      description =
          "Blocks until authentication attempt is completed or timeout is reached. "
              + "Provides polling mechanism for synchronous-like behavior in MFA flow.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Authentication completed",
            content =
                @Content(schema = @Schema(implementation = AuthAttemptWaitResponseDto.class))),
        @ApiResponse(
            responseCode = "408",
            description = "Timeout reached, authentication still pending"),
        @ApiResponse(responseCode = "404", description = "Auth attempt not found"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters (timeout, polling)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  public ResponseEntity<AuthAttemptWaitResponseDto> waitForResponse(
      @Parameter(description = "Authentication attempt ID to wait for", example = "1")
          @PathVariable("id")
          Integer id,
      @Parameter(
              description = "Maximum wait duration in seconds",
              example = "30",
              schema = @Schema(defaultValue = "30", minimum = "1", maximum = "300"))
          @RequestParam(value = "timeout", defaultValue = "30")
          Integer timeoutSeconds,
      @Parameter(
              description = "Polling interval in seconds",
              example = "2",
              schema = @Schema(defaultValue = "2", minimum = "1", maximum = "60"))
          @RequestParam(value = "polling", defaultValue = "2")
          Integer pollingSeconds,
      HttpServletRequest httpRequest) {

    // Check rate limiting for API keys
    String apiKeyId = extractApiKeyId(httpRequest);
    if (apiKeyId != null && !rateLimitService.canWaitAuthAttempt(apiKeyId)) {
      throw new RateLimitExceededException("WAIT_AUTH_ATTEMPT", 200, 0, 15);
    }

    try {
      // Build request DTO from parameters using record constructor
      AuthAttemptWaitRequestDto requestDto =
          new AuthAttemptWaitRequestDto(timeoutSeconds, pollingSeconds);

      // Convert to domain object
      AuthAttemptWaitRequest request = authAttemptMapper.toAuthAttemptWaitRequest(requestDto);

      // Call service for polling logic
      AuthAttemptWaitResponse response = authAttemptService.waitForResponse(id, request);

      // Convert to response DTO
      AuthAttemptWaitResponseDto responseDto =
          authAttemptMapper.toAuthAttemptWaitResponseDto(response);

      // Record successful operation for rate limiting
      if (apiKeyId != null) {
        rateLimitService.recordWaitAuthAttempt(apiKeyId);
      }

      return ResponseEntity.ok(responseDto);

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Extracts API key ID from the authentication context for rate limiting.
   *
   * @param request the HTTP request
   * @return the API key ID if present, null otherwise
   */
  private String extractApiKeyId(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    // Check if this is an API key authentication
    if (authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_API_KEY"))) {

      // For rate limiting, we can use the integration ID as a unique identifier
      // In a more sophisticated implementation, you'd extract the actual API key ID
      Object principal = authentication.getPrincipal();
      if (principal instanceof Integer) {
        return "api_key_integration_" + principal;
      }
    }

    return null;
  }

  /**
   * Extracts the integration ID from the authentication context.
   *
   * @return the integration ID if this is an API key authentication, null otherwise
   */
  private Integer extractIntegrationId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    // Check if this is an API key authentication
    if (authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_API_KEY"))) {

      Object principal = authentication.getPrincipal();
      if (principal instanceof Integer) {
        return (Integer) principal;
      }
    }

    return null;
  }

  /**
   * Validates that an enrollment belongs to the API key's integration.
   *
   * <p>This method ensures that API keys can only create auth attempts for enrollments that belong
   * to their associated integration, preventing cross-integration access.
   *
   * @param enrollmentId the enrollment ID to validate
   * @param apiKeyId the API key ID (for logging purposes)
   * @throws AuthorizationDeniedException if the enrollment doesn't belong to the API key's
   *     integration
   */
  private void validateEnrollmentOwnership(Integer enrollmentId, String apiKeyId) {
    // Extract the integration ID from the API key authentication context
    Integer apiKeyIntegrationId = extractIntegrationId();

    if (apiKeyIntegrationId == null) {
      // This should not happen for API key requests, but handle gracefully
      logger.warn("API key authentication found but integration ID could not be extracted");
      throw new AuthorizationDeniedException("Unable to verify API key integration ownership");
    }

    // Fetch the enrollment and verify it belongs to the API key's integration
    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

    // Check ownership
    if (!enrollment.getIntegrationId().equals(apiKeyIntegrationId)) {
      logger.warn(
          "API key from integration {} attempted to create auth attempt for enrollment {} belonging"
              + " to integration {}",
          apiKeyIntegrationId,
          enrollmentId,
          enrollment.getIntegrationId());
      throw new AuthorizationDeniedException(
          "API key cannot create auth attempts for enrollments belonging to other integrations");
    }

    logger.debug(
        "API key from integration {} validated for enrollment {} (integration {})",
        apiKeyIntegrationId,
        enrollmentId,
        enrollment.getIntegrationId());
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
