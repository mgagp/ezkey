/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: IntegrationApiAuthAttemptController
 * Description: REST controller for machine-to-machine authentication attempt operations.
 */

package org.ezkey.integration.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptIntegrationApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.RateLimitExceededException;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.ezkey.integration.api.constants.IntegrationApiAuditConstants;
import org.ezkey.integration.api.security.AccessControlService;
import org.ezkey.integration.api.security.RateLimitService;
import org.ezkey.integration.api.util.AuditHelper;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Integration API authentication attempt operations (API key /
 * machine-to-machine).
 *
 * <p>This controller exposes the Integration API surface that allows backend integrations to
 * initiate, wait for, and cancel MFA authentication attempts on behalf of their users. All
 * operations require API key authentication ({@code ROLE_API_KEY}). The integration identity is
 * always derived from the API key credential — no admin-level or bearer-token paths exist in this
 * controller.
 *
 * <p><b>Integration API endpoints (port 7080):</b>
 *
 * <ul>
 *   <li><b>POST /api/v1/auth-attempts</b> – Create a new authentication attempt
 *   <li><b>GET /api/v1/auth-attempts/{id}/wait</b> – Wait for device response (long-poll)
 *   <li><b>POST /api/v1/auth-attempts/{id}/cancel</b> – Cancel a pending/read attempt
 * </ul>
 *
 * <p><b>Virtual Threads:</b> This module is configured with {@code
 * spring.threads.virtual.enabled=true}, so all request threads are virtual threads. The blocking
 * long-poll in the wait endpoint will park the virtual thread rather than occupy a platform thread,
 * enabling high concurrency at low cost.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptService
 * @see AuthAttemptIntegrationApiMapper
 * @see RateLimitService
 * @see AccessControlService
 */
@RestController
@RequestMapping("/api/v1/auth-attempts")
@Tag(name = "Auth Attempts", description = "Integration API — authentication attempt operations")
public class IntegrationApiAuthAttemptController {

  private static final Logger logger =
      LoggerFactory.getLogger(IntegrationApiAuthAttemptController.class);

  private final AuthAttemptService authAttemptService;
  private final AuthAttemptIntegrationApiMapper authAttemptMapper;
  private final AuditLogService auditLogService;
  private final RateLimitService rateLimitService;
  private final EnrollmentRepository enrollmentRepository;
  private final AccessControlService accessControlService;
  private final IntegrationRepository integrationRepository;

  /**
   * Constructs the Integration API auth attempt controller with required dependencies.
   *
   * @param authAttemptService the auth attempt service
   * @param authAttemptMapper the MapStruct mapper for entity/DTO conversions
   * @param auditLogService the audit log service for security monitoring
   * @param rateLimitService the rate limiting service for API key operations
   * @param enrollmentRepository the enrollment repository for ownership validation
   * @param accessControlService the access control service for auth attempt access checks
   * @param integrationRepository the integration repository for tenant resolution in audit logs
   */
  public IntegrationApiAuthAttemptController(
      AuthAttemptService authAttemptService,
      AuthAttemptIntegrationApiMapper authAttemptMapper,
      AuditLogService auditLogService,
      RateLimitService rateLimitService,
      EnrollmentRepository enrollmentRepository,
      AccessControlService accessControlService,
      IntegrationRepository integrationRepository) {
    this.authAttemptService = authAttemptService;
    this.authAttemptMapper = authAttemptMapper;
    this.auditLogService = auditLogService;
    this.rateLimitService = rateLimitService;
    this.enrollmentRepository = enrollmentRepository;
    this.accessControlService = accessControlService;
    this.integrationRepository = integrationRepository;
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /api/v1/auth-attempts — Create authentication attempt
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Creates a new authentication attempt for an enrolled user.
   *
   * <p>Initiates an MFA authentication request targeted at the mobile device registered for the
   * specified enrollment. The integration is automatically derived from the API key credential;
   * callers do NOT need to supply an integration ID.
   *
   * <p><b>Enrollment resolution:</b>
   *
   * <ul>
   *   <li>Supply {@code enrollmentId} for direct, unambiguous targeting
   *   <li>Supply {@code userIdentifier} to resolve an active VERIFIED enrollment within the API
   *       key's integration (fails if zero or multiple matches)
   *   <li>Supply both for a consistency cross-check
   * </ul>
   *
   * @param request the create request containing enrollment/user identifier and challenge flag
   * @param httpRequest the HTTP request for audit logging
   * @return HTTP 201 with {@link AuthAttemptCreateResponseDto}, or 400/403/429/500 on error
   */
  @Operation(
      summary = "Create authentication attempt",
      description =
          "Creates a new MFA authentication attempt. The integration is derived from the API key.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Auth attempt created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
        @ApiResponse(
            responseCode = "403",
            description = "Enrollment does not belong to this integration"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('API_KEY')")
  @PostMapping
  public ResponseEntity<?> create(
      @Parameter(description = "Auth attempt creation parameters", required = true) @RequestBody
          AuthAttemptCreateRequestDto request,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);
    String apiKeyId = extractApiKeyId();

    // Rate limiting check
    if (apiKeyId != null && !rateLimitService.canCreateAuthAttempt(apiKeyId)) {
      throw new RateLimitExceededException("CREATE_AUTH_ATTEMPT", 100, 0, 15);
    }

    // Resolve effective enrollment ID from the request
    Integer effectiveEnrollmentId;
    try {
      effectiveEnrollmentId = resolveEnrollmentId(request);
    } catch (AuthAttemptCreateValidationException e) {
      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CREATED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CREATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .errorMessage(e.getMessage())
              .build());
      throw e;
    }

    // Validate enrollment ownership against the API key's integration
    try {
      validateEnrollmentOwnership(effectiveEnrollmentId, apiKeyId);
    } catch (AuthorizationDeniedException e) {
      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CREATED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CREATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .enrollmentId(effectiveEnrollmentId)
              .integrationId(resolveIntegrationIdFromEnrollment(effectiveEnrollmentId))
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    Integer auditTenantId = resolveTenantIdFromEnrollment(effectiveEnrollmentId);

    try {
      AuthAttemptCreateRequest createRequest = new AuthAttemptCreateRequest();
      createRequest.setEnrollmentId(effectiveEnrollmentId);
      createRequest.setChallengeRequested(request.challengeRequested());
      createRequest.setContextTitle(request.contextTitle());
      createRequest.setContextMessage(request.contextMessage());
      createRequest.setDemoMitmSignatureRequested(
          Boolean.TRUE.equals(request.demoMitmSignatureRequested()));

      AuthAttemptCreateResponse response = authAttemptService.create(createRequest);

      // Record for rate limiting
      if (apiKeyId != null) {
        rateLimitService.recordCreateAuthAttempt(apiKeyId);
      }

      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CREATED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CREATED,
                  auditTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .authAttemptId(response.getAuthAttemptId())
              .authAttemptCreatedAt(response.getCreatedAt())
              .enrollmentId(effectiveEnrollmentId)
              .integrationId(resolveIntegrationIdFromEnrollment(effectiveEnrollmentId))
              .eventDetails(
                  "Challenge: "
                      + (response.getAuthAttemptChallenge() != null ? "required" : "not required"))
              .build());

      AuthAttemptCreateResponseDto responseDto =
          authAttemptMapper.toAuthAttemptCreateResponseDto(response);
      return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);

    } catch (AuthAttemptCreateValidationException e) {
      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CREATED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CREATION_FAILED,
                  auditTenantId)
              .eventStatus(EventStatus.FAILURE)
              .enrollmentId(effectiveEnrollmentId)
              .integrationId(resolveIntegrationIdFromEnrollment(effectiveEnrollmentId))
              .errorMessage(e.getMessage())
              .build());
      throw e;

    } catch (Exception e) {
      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CREATED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CREATION_ERROR,
                  auditTenantId)
              .eventStatus(EventStatus.ERROR)
              .enrollmentId(effectiveEnrollmentId)
              .integrationId(resolveIntegrationIdFromEnrollment(effectiveEnrollmentId))
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // GET /api/v1/auth-attempts/{id}/wait — Long-poll for device response
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Waits for a device response to an authentication attempt with configurable timeout and polling.
   *
   * <p>This endpoint blocks the request (parking the virtual thread) until the mobile device
   * responds or the specified timeout elapses. It is the primary mechanism for integrating
   * applications to achieve synchronous-like MFA without implementing their own polling loop.
   *
   * <p><b>Virtual Thread Note:</b> With {@code spring.threads.virtual.enabled=true}, the blocking
   * sleep in the service layer parks the virtual thread, not a platform thread. High concurrency is
   * therefore achievable without thread pool exhaustion.
   *
   * @param id the authentication attempt ID to wait for
   * @param timeoutSeconds maximum seconds to wait (default 30, max 300)
   * @param pollingSeconds polling interval in seconds (default 2, max 60)
   * @param httpRequest the HTTP request for audit logging
   * @return HTTP 200 with {@link AuthAttemptWaitResponseDto}, or 404/408/500 on error
   */
  @Operation(
      summary = "Wait for device response",
      description =
          "Blocks until device responds or timeout elapses. Uses virtual threads for high"
              + " concurrency.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Response received (or timeout reached with status still pending)",
            content =
                @Content(schema = @Schema(implementation = AuthAttemptWaitResponseDto.class))),
        @ApiResponse(responseCode = "404", description = "Auth attempt not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("@accessControlService.canAccessAuthAttempt(authentication, #id)")
  @GetMapping("/{id}/wait")
  public ResponseEntity<AuthAttemptWaitResponseDto> waitForResponse(
      @Parameter(description = "Auth attempt ID to wait for", example = "42") @PathVariable("id")
          Integer id,
      @Parameter(
              description = "Maximum wait duration in seconds",
              schema = @Schema(defaultValue = "30", minimum = "1", maximum = "300"))
          @RequestParam(value = "timeout", defaultValue = "30")
          Integer timeoutSeconds,
      @Parameter(
              description = "Polling interval in seconds",
              schema = @Schema(defaultValue = "2", minimum = "1", maximum = "60"))
          @RequestParam(value = "polling", defaultValue = "2")
          Integer pollingSeconds,
      HttpServletRequest httpRequest) {

    String apiKeyId = extractApiKeyId();

    // Rate limiting check
    if (apiKeyId != null && !rateLimitService.canWaitAuthAttempt(apiKeyId)) {
      throw new RateLimitExceededException("WAIT_AUTH_ATTEMPT", 200, 0, 15);
    }

    try {
      AuthAttemptWaitRequestDto requestDto =
          new AuthAttemptWaitRequestDto(timeoutSeconds, pollingSeconds);
      AuthAttemptWaitRequest waitRequest = authAttemptMapper.toAuthAttemptWaitRequest(requestDto);
      AuthAttemptWaitResponse waitResponse = authAttemptService.waitForResponse(id, waitRequest);

      // Record for rate limiting
      if (apiKeyId != null) {
        rateLimitService.recordWaitAuthAttempt(apiKeyId);
      }

      AuthAttemptWaitResponseDto responseDto =
          authAttemptMapper.toAuthAttemptWaitResponseDto(waitResponse);
      return ResponseEntity.ok(responseDto);

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (AuthAttemptWaitValidationException e) {
      throw e;
    } catch (Exception e) {
      logger.error("Unexpected error waiting for auth attempt {}", id, e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // POST /api/v1/auth-attempts/{id}/cancel — Cancel attempt
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Cancels a pending or read authentication attempt by marking it as expired.
   *
   * <p>Only attempts in {@code PENDING} or {@code READ} status can be cancelled. Attempts that have
   * already reached a final state ({@code ACCEPTED}, {@code REJECTED}, {@code INVALID}, {@code
   * EXPIRED}) will return 400. Returns the updated attempt DTO on success.
   *
   * @param id the authentication attempt ID to cancel
   * @param httpRequest the HTTP request for audit logging
   * @return HTTP 200 with updated {@link AuthAttemptDto}, or 400/404/500 on error
   */
  @Operation(
      summary = "Cancel authentication attempt",
      description = "Marks a pending or read authentication attempt as expired (cancelled).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Auth attempt cancelled successfully"),
        @ApiResponse(
            responseCode = "409",
            description = "Auth attempt already in final state (cannot be cancelled)"),
        @ApiResponse(responseCode = "404", description = "Auth attempt not found"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("@accessControlService.canAccessAuthAttempt(authentication, #id)")
  @PostMapping("/{id}/cancel")
  public ResponseEntity<AuthAttemptDto> cancel(
      @Parameter(description = "Auth attempt ID to cancel", example = "42") @PathVariable("id")
          Integer id,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);
    String apiKeyId = extractApiKeyId();

    // Rate limiting check (reuses wait bucket — cancel is similar in cost)
    if (apiKeyId != null && !rateLimitService.canWaitAuthAttempt(apiKeyId)) {
      throw new RateLimitExceededException("CANCEL_AUTH_ATTEMPT", 200, 0, 15);
    }

    Integer cancelTenantId = resolveTenantIdFromAuthAttempt(id);

    try {
      AuthAttempt cancelled = authAttemptService.cancel(id);

      // Record for rate limiting
      if (apiKeyId != null) {
        rateLimitService.recordWaitAuthAttempt(apiKeyId);
      }

      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CANCELLED,
                  cancelTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .authAttemptId(id)
              .enrollmentId(cancelled.getEnrollmentId())
              .integrationId(resolveIntegrationIdFromEnrollment(cancelled.getEnrollmentId()))
              .eventDetails("Status: EXPIRED")
              .build());

      return ResponseEntity.ok(authAttemptMapper.toDto(cancelled));

    } catch (ResourceNotFoundException e) {
      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CANCELLATION_FAILED,
                  cancelTenantId)
              .eventStatus(EventStatus.FAILURE)
              .authAttemptId(id)
              .integrationId(resolveIntegrationIdFromAuthAttempt(id))
              .errorMessage("Authentication attempt not found")
              .build());
      return ResponseEntity.notFound().build();

    } catch (AuthAttemptStateConflictException e) {
      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CANCELLATION_FAILED,
                  cancelTenantId)
              .eventStatus(EventStatus.FAILURE)
              .authAttemptId(id)
              .integrationId(resolveIntegrationIdFromAuthAttempt(id))
              .errorMessage(e.getMessage())
              .build());
      throw e;

    } catch (Exception e) {
      auditLogService.log(
          AuditHelper.createIntegrationApiAudit(
                  context,
                  EventType.AUTH_ATTEMPT_CANCELLED,
                  IntegrationApiAuditConstants.AUTH_ATTEMPT_CANCELLATION_ERROR,
                  cancelTenantId)
              .eventStatus(EventStatus.ERROR)
              .authAttemptId(id)
              .integrationId(resolveIntegrationIdFromAuthAttempt(id))
              .errorMessage(e.getMessage())
              .build());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Private helpers
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * Extracts a stable API key identifier from the security context for rate limiting.
   *
   * <p>Uses the integration ID stored as the principal (set by {@code ApiKeyAuthenticationFilter})
   * to derive a unique, stable key for the token bucket.
   *
   * @return a rate-limit key string, or {@code null} if no API key auth is present
   */
  private String extractApiKeyId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_API_KEY"))) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof Integer) {
        return "api_key_integration_" + principal;
      }
    }
    return null;
  }

  /**
   * Extracts the integration ID from the API key principal stored in the security context.
   *
   * @return the integration ID, or {@code null} if not available
   */
  private Integer extractIntegrationId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_API_KEY"))) {
      Object principal = authentication.getPrincipal();
      if (principal instanceof Integer integrationId) {
        return integrationId;
      }
    }
    return null;
  }

  /**
   * Resolves the effective enrollment ID from the request.
   *
   * <p>Supports three resolution strategies:
   *
   * <ul>
   *   <li><b>enrollmentId only:</b> returned as-is
   *   <li><b>userIdentifier only:</b> looked up in the API key's integration (fails if zero or
   *       multiple VERIFIED active enrollments)
   *   <li><b>both:</b> consistency cross-check — must resolve to the same enrollment
   * </ul>
   *
   * @param request the create request DTO
   * @return the resolved enrollment ID
   * @throws AuthAttemptCreateValidationException if resolution fails (missing identifiers,
   *     ambiguous, or consistency mismatch)
   */
  private Integer resolveEnrollmentId(AuthAttemptCreateRequestDto request) {
    Integer enrollmentId = request.enrollmentId();
    String userIdentifier =
        request.userIdentifier() != null && !request.userIdentifier().isBlank()
            ? request.userIdentifier().trim()
            : null;

    if (enrollmentId == null && userIdentifier == null) {
      throw new AuthAttemptCreateValidationException(
          "Either enrollmentId or userIdentifier is required.");
    }

    // Path 1: enrollmentId only — validate enrollment belongs to API key's integration
    // and is active + VERIFIED. This prevents cross-integration probing and ensures that
    // revoked/deactivated enrollments are rejected at the earliest possible point.
    if (enrollmentId != null && userIdentifier == null) {
      Integer integrationIdForPath1 = extractIntegrationId();
      if (integrationIdForPath1 == null) {
        throw new AuthAttemptCreateValidationException(
            "Unable to determine integration from API key. Contact support.");
      }
      Enrollment enrollmentForPath1 =
          enrollmentRepository
              .findById(enrollmentId)
              .orElseThrow(
                  () ->
                      new AuthAttemptCreateValidationException(
                          "Enrollment not found for ID: " + enrollmentId));
      if (!integrationIdForPath1.equals(enrollmentForPath1.getIntegrationId())) {
        logger.warn(
            "Integration API cross-integration access attempt: API key from integration {} tried to"
                + " use enrollment {} belonging to integration {}",
            integrationIdForPath1,
            enrollmentId,
            enrollmentForPath1.getIntegrationId());
        throw new AuthAttemptCreateValidationException(
            "Enrollment does not belong to this integration.");
      }
      if (!Boolean.TRUE.equals(enrollmentForPath1.getActive())
          || !EnrollmentStatus.VERIFIED.equals(enrollmentForPath1.getStatus())) {
        logger.warn(
            "Integration API auth attempt rejected: enrollment {} is not active or not VERIFIED"
                + " (status={}, active={})",
            enrollmentId,
            enrollmentForPath1.getStatus(),
            enrollmentForPath1.getActive());
        throw new org.ezkey.exception.EnrollmentInactiveException(
            "Enrollment is not active for authentication. Enrollment ID: " + enrollmentId);
      }
      return enrollmentId;
    }

    // Resolve integration for user identifier lookup
    Integer integrationId = extractIntegrationId();
    if (integrationId == null) {
      throw new AuthAttemptCreateValidationException(
          "Unable to determine integration from API key. Contact support.");
    }

    // Path 2: userIdentifier only
    if (enrollmentId == null) {
      List<Enrollment> enrollments =
          enrollmentRepository.findByIntegrationIdAndUserIdentifierAndStatusAndActive(
              integrationId, userIdentifier, EnrollmentStatus.VERIFIED, true);

      if (enrollments.isEmpty()) {
        throw new AuthAttemptCreateValidationException(
            "No verified enrollment found for userIdentifier '" + userIdentifier + "'.");
      }
      if (enrollments.size() > 1) {
        throw new AuthAttemptCreateValidationException(
            "Multiple enrollments for this user. Please specify enrollmentId.");
      }
      return enrollments.get(0).getEnrollmentId();
    }

    // Path 3: both provided — consistency check
    List<Enrollment> byUserIdentifier =
        enrollmentRepository.findByIntegrationIdAndUserIdentifierAndStatusAndActive(
            integrationId, userIdentifier, EnrollmentStatus.VERIFIED, true);

    if (byUserIdentifier.isEmpty()) {
      throw new AuthAttemptCreateValidationException(
          "No verified enrollment found for userIdentifier '" + userIdentifier + "'.");
    }

    if (byUserIdentifier.size() > 1) {
      boolean matches =
          byUserIdentifier.stream().anyMatch(e -> e.getEnrollmentId().equals(enrollmentId));
      if (!matches) {
        throw new AuthAttemptCreateValidationException(
            "enrollmentId and userIdentifier resolve to different enrollments.");
      }
      return enrollmentId;
    }

    Enrollment resolved = byUserIdentifier.get(0);
    if (!resolved.getEnrollmentId().equals(enrollmentId)) {
      throw new AuthAttemptCreateValidationException(
          "enrollmentId "
              + enrollmentId
              + " and userIdentifier map to different enrollments (expected "
              + resolved.getEnrollmentId()
              + ").");
    }
    return enrollmentId;
  }

  /**
   * Validates that the given enrollment belongs to the API key's integration.
   *
   * @param enrollmentId enrollment to validate
   * @param apiKeyId API key identifier (for log context)
   * @throws AuthorizationDeniedException if ownership check fails
   * @throws ResourceNotFoundException if the enrollment does not exist
   */
  private void validateEnrollmentOwnership(Integer enrollmentId, String apiKeyId) {
    Integer apiKeyIntegrationId = extractIntegrationId();

    if (apiKeyIntegrationId == null) {
      logger.warn("API key auth present but integration ID could not be extracted");
      throw new AuthorizationDeniedException("Unable to verify API key integration ownership");
    }

    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));

    if (!enrollment.getIntegrationId().equals(apiKeyIntegrationId)) {
      logger.warn(
          "API key from integration {} attempted to create auth attempt for enrollment {}"
              + " belonging to integration {}",
          apiKeyIntegrationId,
          enrollmentId,
          enrollment.getIntegrationId());
      throw new AuthorizationDeniedException(
          "API key cannot create auth attempts for enrollments belonging to other integrations");
    }
  }

  /**
   * Resolves the tenant ID from an enrollment by traversing enrollment → integration → tenant.
   *
   * @param enrollmentId the enrollment ID to resolve from
   * @return the tenant ID, or {@code null} if not resolvable
   */
  private Integer resolveTenantIdFromEnrollment(Integer enrollmentId) {
    if (enrollmentId == null) {
      return null;
    }
    return enrollmentRepository
        .findById(enrollmentId)
        .map(Enrollment::getIntegrationId)
        .flatMap(integrationRepository::findById)
        .map(Integration::getTenant)
        .map(tenant -> tenant.getTenantId())
        .orElse(null);
  }

  /**
   * Resolves the integration ID for an enrollment (for audit log enrichment).
   *
   * @param enrollmentId the enrollment ID
   * @return the integration ID, or null if not found
   */
  private Integer resolveIntegrationIdFromEnrollment(Integer enrollmentId) {
    if (enrollmentId == null) {
      return null;
    }
    return enrollmentRepository
        .findById(enrollmentId)
        .map(Enrollment::getIntegrationId)
        .orElse(null);
  }

  /**
   * Resolves the integration ID for an auth attempt (for audit log enrichment).
   *
   * @param authAttemptId the auth attempt ID
   * @return the integration ID, or null if not found
   */
  private Integer resolveIntegrationIdFromAuthAttempt(Integer authAttemptId) {
    if (authAttemptId == null) {
      return null;
    }
    try {
      AuthAttempt attempt = authAttemptService.getById(authAttemptId);
      return resolveIntegrationIdFromEnrollment(attempt.getEnrollmentId());
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Resolves the tenant ID from an auth attempt ID by traversing attempt → enrollment → integration
   * → tenant.
   *
   * <p>Safe to call before an operation that may fail; returns {@code null} if the chain cannot be
   * resolved.
   *
   * @param authAttemptId the auth attempt ID to resolve from
   * @return the tenant ID, or {@code null} if not resolvable
   */
  private Integer resolveTenantIdFromAuthAttempt(Integer authAttemptId) {
    if (authAttemptId == null) {
      return null;
    }
    try {
      AuthAttempt attempt = authAttemptService.getById(authAttemptId);
      return resolveTenantIdFromEnrollment(attempt.getEnrollmentId());
    } catch (Exception e) {
      return null;
    }
  }
}
