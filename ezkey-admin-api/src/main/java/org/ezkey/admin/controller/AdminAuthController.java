/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AdminAuthController
 * Description: REST controller for administrator authentication.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.audit.RecoveryAuditDetails;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.AdminAuthAuditContext;
import org.ezkey.admin.dto.request.AdminActivationRequestDto;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordlessWaitRequestDto;
import org.ezkey.admin.dto.request.AdminRecoveryRequestDto;
import org.ezkey.admin.dto.response.AdminActivationResponseDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminRecoveryResponseDto;
import org.ezkey.admin.dto.response.AdminSessionResponseDto;
import org.ezkey.admin.exception.AdminAuthenticationException;
import org.ezkey.admin.exception.AdminAuthenticationExpiredException;
import org.ezkey.admin.exception.AdminAuthenticationRejectedException;
import org.ezkey.admin.exception.AdminAuthenticationTimeoutException;
import org.ezkey.admin.exception.AdminDeviceSignatureInvalidException;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.security.AdminAuthRequestAttributes;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for administrator authentication.
 *
 * <p>This controller provides endpoints for administrator authentication including login and logout
 * functionality. It handles the generation and validation of bearer tokens for API access.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(
    name = "Admin Authentication",
    description =
        "Administrator authentication and session management for passwordless login and recovery")
public class AdminAuthController {

  private static final Logger logger = LoggerFactory.getLogger(AdminAuthController.class);

  private final AdminAuthService authService;
  private final AdminProvisioningService provisioningService;

  private final org.ezkey.admin.service.AdminRecoveryService recoveryService;

  private final AuditLogService auditLogService;

  private final AdminRateLimitFilter rateLimitFilter;

  private final AdminRecoveryProperties recoveryProperties;

  private final EzkeyAdminRepository adminRepository;

  private final AdminBrowserSessionCookieProperties browserSessionCookieProperties;

  private final AdminSessionCookieService sessionCookieService;

  private final AdminCsrfTokenService csrfTokenService;

  public AdminAuthController(
      AdminAuthService authService,
      AdminProvisioningService provisioningService,
      org.ezkey.admin.service.AdminRecoveryService recoveryService,
      AuditLogService auditLogService,
      AdminRateLimitFilter rateLimitFilter,
      AdminRecoveryProperties recoveryProperties,
      EzkeyAdminRepository adminRepository,
      AdminBrowserSessionCookieProperties browserSessionCookieProperties,
      AdminSessionCookieService sessionCookieService,
      AdminCsrfTokenService csrfTokenService) {
    this.authService = authService;
    this.provisioningService = provisioningService;
    this.recoveryService = recoveryService;
    this.auditLogService = auditLogService;
    this.rateLimitFilter = rateLimitFilter;
    this.recoveryProperties = recoveryProperties;
    this.adminRepository = adminRepository;
    this.browserSessionCookieProperties = browserSessionCookieProperties;
    this.sessionCookieService = sessionCookieService;
    this.csrfTokenService = csrfTokenService;
  }

  /**
   * Activates a pending administrator with a one-time activation code.
   *
   * <p>This public endpoint consumes the activation code issued during deferred onboarding and
   * returns the first enrollment credentials. Recovery codes are generated server-side but remain
   * deferred from this unauthenticated bootstrap response.
   *
   * @param request the activation request containing the one-time activation code
   * @param httpRequest the HTTP servlet request for IP extraction
   * @return ResponseEntity containing first-time enrollment credentials or an error response
   */
  @Operation(
      summary = "Activate pending administrator with one-time activation code",
      description =
          "Consumes a one-time activation code issued during deferred onboarding. Returns the first"
              + " enrollment binding credentials while keeping recovery codes deferred from this"
              + " unauthenticated bootstrap response.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Activation successful"),
        @ApiResponse(
            responseCode = "400",
            description = "Validation error or activation cannot proceed in the current state"),
        @ApiResponse(
            responseCode = "403",
            description = "Invalid, expired, or unusable activation code"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping("/activate")
  public ResponseEntity<AdminActivationResponseDto> activate(
      @Valid @RequestBody AdminActivationRequestDto request, HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    try {
      AdminProvisioningService.ProvisioningResult result =
          provisioningService.activatePendingAdmin(request.activationCode());

      AdminActivationResponseDto response =
          AdminActivationResponseDto.success(
              result.admin().getUsername(),
              result.enrollment() != null ? result.enrollment().getEnrollmentId() : null,
              result.enrollmentProofToken(),
              result.enrollmentChallenge(),
              null);

      rateLimitFilter.recordSuccessfulAttempt(context.clientIp());

      Integer tenantId =
          result.admin().getTenant() != null ? result.admin().getTenant().getTenantId() : null;
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_ACTIVATION,
                  AdminAuditConstants.ACTIVATION_CODE_USED,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(result.admin().getAdminId())
              .eventDetails(
                  "Activation completed for adminId: "
                      + result.admin().getAdminId()
                      + ", username: "
                      + result.admin().getUsername()
                      + ", enrollmentId: "
                      + (result.enrollment() != null
                          ? result.enrollment().getEnrollmentId()
                          : null))
              .build());

      return ResponseEntity.ok(response);
    } catch (AuthenticationException e) {
      rateLimitFilter.recordFailedAttempt(context.clientIp());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_ACTIVATION,
                  AdminAuditConstants.ACTIVATION_CODE_FAILED,
                  null)
              .eventStatus(EventStatus.FAILURE)
              .errorMessage(e.getMessage())
              .eventDetails("Activation rejected: " + e.getMessage())
              .build());
      return ResponseEntity.status(403).body(AdminActivationResponseDto.error(e.getMessage()));
    } catch (IllegalStateException e) {
      rateLimitFilter.recordFailedAttempt(context.clientIp());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_ACTIVATION,
                  AdminAuditConstants.ACTIVATION_CODE_FAILED,
                  null)
              .eventStatus(EventStatus.FAILURE)
              .errorMessage(e.getMessage())
              .eventDetails("Activation blocked: " + e.getMessage())
              .build());
      return ResponseEntity.badRequest().body(AdminActivationResponseDto.error(e.getMessage()));
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.ADMIN_ACTIVATION, AdminAuditConstants.ACTIVATION_ERROR, null)
              .eventStatus(EventStatus.ERROR)
              .errorMessage(e.getMessage())
              .eventDetails("Activation unexpected error: " + e.getClass().getSimpleName())
              .build());
      return ResponseEntity.status(500)
          .body(AdminActivationResponseDto.error("An error occurred during activation"));
    }
  }

  /**
   * Authenticate administrator with passwordless Ezkey MFA.
   *
   * <p>This endpoint implements Ezkey's passwordless authentication for administrators using
   * device-based cryptographic signing. It follows RFC 9457 (Problem Details) for error responses.
   *
   * <p><b>Authentication Flows:</b>
   *
   * <ul>
   *   <li><b>Single-call (blocking):</b> Returns bearer token immediately after device approval
   *       (HTTP 200) - suitable for interactive flows where device is available immediately
   *   <li><b>Two-call (challenge-based):</b> Returns authAttemptId and challengeCode with status
   *       "pending" (HTTP 200), requires separate /passwordless-wait call - suitable for showing
   *       user a display code while waiting for device
   * </ul>
   *
   * <p><b>Security Features:</b>
   *
   * <ul>
   *   <li>Passwordless: No passwords stored, uses device-based cryptography only
   *   <li>Rate Limiting: IP-based rate limiting to prevent brute force (applied at controller)
   *   <li>Challenge Security: Optional display code prevents enumeration attacks on authAttemptId
   *   <li>Audit Logging: All login attempts (success and failure) are logged
   * </ul>
   *
   * <p><b>Error Codes (RFC 9457):</b>
   *
   * <ul>
   *   <li><b>401 Unauthorized:</b>
   *       <ul>
   *         <li>{@code https://ezkey.io/problems/authentication/invalid-credentials} - Username not
   *             found, account not eligible for login, or device signature validation failed
   *             (generic message — SEC-006 anti-enumeration)
   *       </ul>
   *   <li><b>400 Bad Request:</b>
   *       <ul>
   *         <li>{@code https://ezkey.io/problems/authentication/auth-expired} - Authentication
   *             attempt superseded or expired before device response
   *         <li>{@code https://ezkey.io/problems/authentication/auth-rejected} - Device explicitly
   *             rejected the authentication request
   *         <li>{@code https://ezkey.io/problems/authentication/invalid-signature} - Device
   *             signature validation failed
   *       </ul>
   *   <li><b>408 Request Timeout:</b>
   *       <ul>
   *         <li>{@code https://ezkey.io/problems/authentication/auth-timeout} - No device response
   *             within the wait window (aligned with auth attempt TTL, maximum 300 seconds)
   *       </ul>
   * </ul>
   *
   * <p><b>Example Success Response (HTTP 200):</b>
   *
   * <pre>{@code
   * {
   * "status": "approved",
   * "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
   * "expiresAt": "2026-02-12T12:57:19-05:00",
   * "success": true
   * }
   * }</pre>
   *
   * <p><b>Example Pending Response (HTTP 200):</b>
   *
   * <pre>{@code
   * {
   * "status": "pending",
   * "authAttemptId": "uuid-here",
   * "challengeCode": "ABCD-1234",
   * "success": false
   * }
   * }</pre>
   *
   * <p><b>Example Error Response (HTTP 401):</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/invalid-credentials",
   * "title": "Invalid Credentials",
   * "status": 401,
   * "detail": "Username not found or device signature failed",
   * "path": "/api/v1/admin/auth/login",
   * "instance": "uuid-here"
   * }
   * }</pre>
   *
   * @param request the login request containing username and optional flow configuration flags
   * @param httpRequest the HTTP servlet request for client IP extraction and audit logging
   * @return ResponseEntity containing authentication response with bearer token or challenge info
   *     (HTTP 200) on success, or error response (HTTP 400-408) on failure via
   *     GlobalExceptionHandler
   * @throws AdminAuthenticationException (401) if login is not permitted or credentials are invalid
   * @throws AdminAuthenticationExpiredException (400) if auth attempt is superseded or expired
   * @throws AdminAuthenticationRejectedException (400) if device rejects authentication
   * @throws AdminDeviceSignatureInvalidException (400) if device signature validation fails
   * @throws AdminAuthenticationTimeoutException (408) if no device response within timeout
   * @since 2025
   */
  @Operation(
      summary = "Authenticate administrator with passwordless Ezkey MFA",
      description =
          "Passwordless authentication for administrators using device-based cryptography. "
              + "Supports single-call (blocking) or two-call (challenge) flows. "
              + "Returns bearer token on success, or challenge info for two-step authentication. "
              + "Rate limiting applied per IP. Errors follow RFC 9457 Problem Details format.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description =
                "Authentication successful or pending. If status='approved', bearer token is"
                    + " present. If status='pending', authAttemptId and challengeCode present for"
                    + " two-call flow."),
        @ApiResponse(
            responseCode = "400",
            description =
                "Bad Request - Authentication failed with temporal issues (expired, rejected,"
                    + " invalid signature). Returns RFC 9457 ProblemDetail with specific error type"
                    + " URI."),
        @ApiResponse(
            responseCode = "401",
            description =
                "Unauthorized - Invalid credentials or login not permitted (generic message; "
                    + "SEC-006). Returns RFC 9457 ProblemDetail: type='...invalid-credentials'"),
        @ApiResponse(
            responseCode = "408",
            description =
                "Request Timeout - No device response within the wait window (aligned with auth"
                    + " attempt TTL, maximum 300 seconds). "
                    + "Returns RFC 9457 ProblemDetail: type='...auth-timeout'"),
        @ApiResponse(
            responseCode = "429",
            description =
                "Too Many Requests - Rate limit exceeded for this IP address. "
                    + "Applies after 10 failed attempts in 15 minutes."),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
      })
  @PostMapping("/login")
  public ResponseEntity<AdminLoginResponseDto> login(
      @Valid @RequestBody AdminLoginRequestDto request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {

    logger.info("🌐 Login request received for username: {}", request.username());

    // Extract client context for audit logging
    ClientContext context = ClientContext.from(httpRequest);

    // Resolve admin's tenant for audit log association
    Integer adminTenantId = resolveAdminTenantId(request.username());

    // Call service - will throw specific exceptions on error (caught by
    // GlobalExceptionHandler)
    AdminLoginResponseDto response = authService.authenticate(request);

    // Check for successful login outcome
    if (response.success()) {
      logger.info(
          "✅ Login successful for username: {} from IP: {}",
          request.username(),
          context.clientIp());

      // Record successful attempt for rate limiting
      rateLimitFilter.recordSuccessfulAttempt(context.clientIp());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_SUCCESS, adminTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(resolveAdminId(request.username()))
              .eventDetails("Username: " + request.username())
              .build());

      return ResponseEntity.ok(wrapWithSessionCookie(response, httpResponse));
    } else if ("pending".equals(response.status())) {
      logger.info(
          "⏳ Login pending (challenge required) for username: {} from IP: {}",
          request.username(),
          context.clientIp());

      rateLimitFilter.recordSuccessfulAttempt(context.clientIp());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_LOGIN,
                  AdminAuditConstants.LOGIN_MFA_REQUESTED,
                  adminTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(resolveAdminId(request.username()))
              .eventDetails("Username: " + request.username() + ", MFA request created")
              .build());

      return ResponseEntity.ok(response);
    } else {
      logger.error(
          "❌ Unexpected login response status: {} for username: {}",
          response.status(),
          request.username());

      rateLimitFilter.recordFailedAttempt(context.clientIp());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.ADMIN_LOGIN, AdminAuditConstants.LOGIN_FAILURE, adminTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(resolveAdminId(request.username()))
              .errorMessage("Unexpected response status: " + response.status())
              .build());

      throw new IllegalStateException("Unexpected authentication response: " + response.status());
    }
  }

  /**
   * Returns non-secret metadata for the currently authenticated administrator session.
   *
   * <p>This endpoint lets the Admin UI restore its React auth state after a hard refresh when the
   * HttpOnly browser session cookie is still valid.
   *
   * @param httpRequest request containing authentication attributes from the token filter
   * @param httpResponse response used to refresh the readable CSRF cookie in cookie mode
   * @return current session metadata or 401 when authentication is missing
   */
  @Operation(
      summary = "Get current administrator session metadata",
      description =
          "Returns non-secret session metadata for the authenticated administrator. Browser cookie"
              + " sessions also receive a non-secret CSRF token for unsafe requests.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Current session metadata"),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
      })
  @GetMapping("/me")
  public ResponseEntity<AdminSessionResponseDto> me(
      HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(authentication);
    if (principal == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    String username = (String) httpRequest.getAttribute(AdminAuthRequestAttributes.USERNAME);
    OffsetDateTime expiresAt =
        (OffsetDateTime) httpRequest.getAttribute(AdminAuthRequestAttributes.EXPIRES_AT);
    String plainToken = (String) httpRequest.getAttribute(AdminAuthRequestAttributes.PLAIN_TOKEN);
    String csrfToken =
        httpRequest.getAttribute(AdminAuthRequestAttributes.AUTH_SOURCE)
                == AdminAuthRequestAttributes.AuthSource.COOKIE
            ? issueCsrfTokenIfPossible(httpResponse, plainToken, expiresAt)
            : null;

    return ResponseEntity.ok(
        new AdminSessionResponseDto(
            username,
            principal.adminType().name(),
            expiresAt,
            principal.adminId(),
            principal.tenantId(),
            csrfToken));
  }

  /**
   * Logout administrator and invalidate token.
   *
   * <p>This endpoint invalidates the current bearer token, effectively logging out the
   * administrator from the system.
   *
   * @param authorization the authorization header containing the bearer token
   * @param httpRequest the HTTP servlet request for audit logging
   * @return ResponseEntity confirming logout
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    try {
      Optional<String> bearerOpt = resolveBearerForLogout(httpRequest, authorization);
      if (bearerOpt.isEmpty()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
      }
      String bearerToken = bearerOpt.get();
      Integer adminIdForAudit = authService.getAdminIdForToken(bearerToken);
      authService.logout(bearerToken);

      ClientContext context = ClientContext.from(httpRequest);

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.ADMIN_LOGOUT, AdminAuditConstants.LOGOUT_SUCCESS, null)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(adminIdForAudit)
              .build());

      sessionCookieService.clearSessionCookie(httpResponse);
      return ResponseEntity.ok().build();
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Wait for passwordless authentication completion.
   *
   * <p>This endpoint handles the polling/waiting phase of two-call passwordless authentication
   * flows. It is called after the client receives an authAttemptId from /login endpoint.
   *
   * <p><b>Supported Flows:</b>
   *
   * <ul>
   *   <li><b>Challenge Flow:</b> When challengeRequested=true in /login call
   *       <ul>
   *         <li>Client receives authAttemptId and challengeCode from /login
   *         <li>Client displays challengeCode to user (user approves on device)
   *         <li>Client calls /passwordless-wait with both authAttemptId and challengeCode
   *       </ul>
   *   <li><b>Non-Blocking Flow:</b> When nonBlocking=true in /login call
   *       <ul>
   *         <li>Client receives authAttemptId from /login (no challenge code)
   *         <li>Assumes device response already validated during /login
   *         <li>Client calls /passwordless-wait with only authAttemptId (no challengeCode)
   *       </ul>
   * </ul>
   *
   * <p><b>Polling Behavior:</b>
   *
   * <ul>
   *   <li>Blocks for up to the configured wait window (remaining attempt lifetime plus slack,
   *       capped at 300 seconds — same cap as {@link
   *       org.ezkey.authattempt.service.AuthAttemptWaitService})
   *   <li>Polls the database every 2 seconds
   *   <li>Returns immediately when device responds (approved, rejected, expired)
   *   <li>Returns HTTP 408 if no response within that window (if {@code ttl-seconds} exceeds 300,
   *       408 may occur while the attempt row is still valid)
   * </ul>
   *
   * <p><b>Security (Challenge Flow):</b>
   *
   * <ul>
   *   <li>challengeCode is cryptographically verified against stored value
   *   <li>Verification prevents enumeration attacks on authAttemptId values
   *   <li>Only clients that received the original challenge can proceed
   * </ul>
   *
   * <p><b>Error Codes (RFC 9457):</b>
   *
   * <ul>
   *   <li><b>400 Bad Request:</b>
   *       <ul>
   *         <li>{@code https://ezkey.io/problems/authentication/invalid-credentials} - Invalid
   *             challenge code
   *         <li>{@code https://ezkey.io/problems/authentication/auth-expired} - Authentication
   *             attempt superseded or expired
   *         <li>{@code https://ezkey.io/problems/authentication/auth-rejected} - Device rejected
   *             the request
   *         <li>{@code https://ezkey.io/problems/authentication/invalid-signature} - Device
   *             signature validation failed
   *       </ul>
   *   <li><b>408 Request Timeout:</b>
   *       <ul>
   *         <li>{@code https://ezkey.io/problems/authentication/auth-timeout} - No device response
   *             within the wait window (maximum 300 seconds)
   *       </ul>
   * </ul>
   *
   * @param request the wait request containing authAttemptId and optional challengeCode
   * @param httpRequest the HTTP servlet request for audit logging (client IP, user agent)
   * @return ResponseEntity with bearer token on success (HTTP 200)
   * @throws AdminAuthenticationException (401) if challenge code is invalid
   * @throws AdminAuthenticationExpiredException (400) if auth attempt is superseded or expired
   * @throws AdminAuthenticationRejectedException (400) if device rejects authentication
   * @throws AdminDeviceSignatureInvalidException (400) if device signature validation fails
   * @throws AdminAuthenticationTimeoutException (408) if no device response within timeout
   * @since 2025
   */
  @Operation(
      summary = "Wait for passwordless authentication device response",
      description =
          "Polls for passwordless authentication completion in two-call flows. "
              + "Blocks for up to the wait window aligned with auth attempt TTL (capped at 300s). "
              + "Supports both challenge-based (with display code) and non-blocking flows. "
              + "Errors follow RFC 9457 Problem Details format.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description =
                "Authentication successful. Returns bearer token and admin session details."),
        @ApiResponse(
            responseCode = "400",
            description =
                "Bad Request - Device denied, signature failed, or challenge invalid. "
                    + "Returns RFC 9457 ProblemDetail with specific error type URI."),
        @ApiResponse(
            responseCode = "401",
            description =
                "Unauthorized - Invalid challenge code. "
                    + "Returns RFC 9457 ProblemDetail: type='...invalid-credentials'"),
        @ApiResponse(
            responseCode = "408",
            description =
                "Request Timeout - No device response within the wait window (max 300 seconds). "
                    + "Returns RFC 9457 ProblemDetail: type='...auth-timeout'"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
      })
  @PostMapping("/passwordless-wait")
  public ResponseEntity<AdminLoginResponseDto> passwordlessWait(
      @Valid @RequestBody AdminPasswordlessWaitRequestDto request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {

    logger.info("🔐 Passwordless wait request for authAttemptId: {}", request.authAttemptId());

    ClientContext context = ClientContext.from(httpRequest);
    Optional<AdminAuthAuditContext> auditCtx =
        authService.findAuditContextForAuthAttempt(request.authAttemptId());

    try {
      AdminLoginResponseDto response =
          authService.waitForPasswordlessAuth(request.authAttemptId(), request.challengeCode());

      auditCtx.ifPresent(
          ctx ->
              auditLogService.log(
                  AuditHelper.createAdminAudit(
                          context,
                          EventType.ADMIN_LOGIN,
                          AdminAuditConstants.LOGIN_MFA_SESSION_ISSUED,
                          ctx.tenantId())
                      .eventStatus(EventStatus.SUCCESS)
                      .adminId(ctx.adminId())
                      .eventDetails("Username: " + ctx.username())
                      .build()));

      logger.info("✅ Passwordless authentication successful");
      return ResponseEntity.ok(wrapWithSessionCookie(response, httpResponse));
    } catch (AdminAuthenticationExpiredException e) {
      logPasswordlessMfaFailure(
          auditCtx, context, AdminAuditConstants.LOGIN_MFA_EXPIRED, e.getMessage());
      throw e;
    } catch (AdminAuthenticationRejectedException e) {
      logPasswordlessMfaFailure(
          auditCtx, context, AdminAuditConstants.LOGIN_MFA_REJECTED, e.getMessage());
      throw e;
    } catch (AdminAuthenticationTimeoutException e) {
      logPasswordlessMfaFailure(
          auditCtx, context, AdminAuditConstants.LOGIN_MFA_TIMEOUT, e.getMessage());
      throw e;
    } catch (AdminDeviceSignatureInvalidException e) {
      logPasswordlessMfaFailure(
          auditCtx, context, AdminAuditConstants.LOGIN_MFA_INVALID_SIGNATURE, e.getMessage());
      throw e;
    } catch (AdminAuthenticationException e) {
      if (e.getMessage() != null && e.getMessage().contains("Invalid challenge")) {
        logPasswordlessMfaFailure(
            auditCtx, context, AdminAuditConstants.LOGIN_MFA_INVALID_CHALLENGE, e.getMessage());
      } else {
        logPasswordlessMfaUnexpected(
            auditCtx, context, AdminAuditConstants.LOGIN_MFA_ERROR, e.getMessage());
      }
      throw e;
    } catch (IllegalArgumentException e) {
      logPasswordlessMfaUnexpected(
          auditCtx, context, AdminAuditConstants.LOGIN_MFA_ERROR, e.getMessage());
      throw e;
    }
  }

  private void logPasswordlessMfaFailure(
      Optional<AdminAuthAuditContext> auditCtx,
      ClientContext context,
      String action,
      String message) {
    auditCtx.ifPresentOrElse(
        ctx ->
            auditLogService.log(
                AuditHelper.createAdminAudit(context, EventType.ADMIN_LOGIN, action, ctx.tenantId())
                    .eventStatus(EventStatus.FAILURE)
                    .adminId(ctx.adminId())
                    .errorMessage(message)
                    .build()),
        () ->
            auditLogService.log(
                AuditHelper.createAdminAudit(context, EventType.ADMIN_LOGIN, action, null)
                    .eventStatus(EventStatus.FAILURE)
                    .errorMessage(message)
                    .build()));
  }

  private void logPasswordlessMfaUnexpected(
      Optional<AdminAuthAuditContext> auditCtx,
      ClientContext context,
      String action,
      String message) {
    auditCtx.ifPresentOrElse(
        ctx ->
            auditLogService.log(
                AuditHelper.createAdminAudit(context, EventType.ADMIN_LOGIN, action, ctx.tenantId())
                    .eventStatus(EventStatus.ERROR)
                    .adminId(ctx.adminId())
                    .errorMessage(message)
                    .build()),
        () ->
            auditLogService.log(
                AuditHelper.logError(context, EventType.ADMIN_LOGIN, action, message)));
  }

  /**
   * Recover admin access using a recovery code.
   *
   * <p>This endpoint allows administrators who have lost access to their enrolled device to regain
   * access using one of their single-use recovery codes. The recovery code grants a temporary token
   * (30 minutes validity) with limited permissions to re-bind enrollment only.
   *
   * <p><b>Security Features:</b>
   *
   * <ul>
   *   <li>Single-use recovery codes (removed from array after use)
   *   <li>BCrypt hashed storage
   *   <li>Limited token (30 min validity, enrollment binding only)
   *   <li>Rate limited to prevent brute force
   *   <li>Audit logged as critical security event
   * </ul>
   *
   * @param request the recovery request containing username and recovery code
   * @param httpRequest the HTTP servlet request for IP extraction
   * @return ResponseEntity containing recovery token or error
   */
  @Operation(
      summary = "Recover admin access with recovery code",
      description =
          "Emergency access using single-use recovery code. Returns temporary token (30 min) with "
              + "limited permissions (enrollment reset only).")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Recovery successful, temporary token issued"),
        @ApiResponse(
            responseCode = "400",
            description =
                "Validation error (invalid recovery code format) or invalid request data"),
        @ApiResponse(
            responseCode = "403",
            description =
                "Recovery failed — generic message for all pre-authentication failures (unknown"
                    + " user, inactive account, no codes, or wrong code; anti-enumeration)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping("/recover")
  public ResponseEntity<AdminRecoveryResponseDto> recover(
      @Valid @RequestBody AdminRecoveryRequestDto request, HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    try {
      logger.warn(
          "🔑 Recovery attempt for admin: {} from IP: {}", request.username(), context.clientIp());

      String recoveryToken =
          recoveryService.validateRecoveryCode(request.username(), request.recoveryCode());

      // Get admin to determine codes remaining
      // Note: Use validateRecoveryToken (not validateToken) since it's a temp token,
      // not bearer
      EzkeyAdmin admin = recoveryService.validateRecoveryToken(recoveryToken);
      int codesRemaining =
          admin != null && admin.getRecoveryCodes() != null ? admin.getRecoveryCodes().length : 0;

      Integer enrollmentId =
          admin != null && admin.getEnrollment() != null
              ? admin.getEnrollment().getEnrollmentId()
              : null;

      AdminRecoveryResponseDto response =
          new AdminRecoveryResponseDto(
              recoveryToken,
              OffsetDateTime.now().plusMinutes(recoveryProperties.getTempTokenDurationMinutes()),
              codesRemaining,
              enrollmentId);

      logger.warn(
          "✅ Recovery successful for admin: {} ({} codes remaining)",
          request.username(),
          codesRemaining);

      // Record successful attempt for rate limiting
      rateLimitFilter.recordSuccessfulAttempt(context.clientIp());

      Integer recoveryTenantId =
          admin != null && admin.getTenant() != null ? admin.getTenant().getTenantId() : null;

      String recoveryDetailsJson =
          RecoveryAuditDetails.recoveryCodeValidatedSuccess(
              request.username(),
              admin != null ? admin.getAdminId() : null,
              recoveryTenantId,
              codesRemaining,
              enrollmentId,
              RecoveryAuditDetails.recoveryTokenFingerprint(recoveryToken));

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_USE,
                  AdminAuditConstants.RECOVERY_CODE_USED,
                  recoveryTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(admin != null ? admin.getAdminId() : null)
              .eventDetails(recoveryDetailsJson)
              .build());

      return ResponseEntity.ok(response);

    } catch (AuthenticationException e) {
      // SEC-024: client gets a single generic message; distinct reasons stay in logs + audit.
      String internalDetail = e.getInternalDetail();
      logger.warn(
          "❌ Recovery failed for admin: {} from IP: {} - Reason: {}",
          request.username(),
          context.clientIp(),
          internalDetail);

      rateLimitFilter.recordFailedAttempt(context.clientIp());

      Integer failTenantId = resolveAdminTenantId(request.username());
      Integer failAdminId = resolveAdminId(request.username());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_USE,
                  AdminAuditConstants.RECOVERY_CODE_FAILED,
                  failTenantId)
              .eventStatus(EventStatus.FAILURE)
              .errorMessage(internalDetail)
              .adminId(failAdminId)
              .eventDetails(
                  RecoveryAuditDetails.recoveryCodeRejected(
                      request.username(),
                      failTenantId,
                      RecoveryAuditDetails.recoveryRejectionReasonCode(internalDetail),
                      internalDetail))
              .build());

      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(
              new AdminRecoveryResponseDto(
                  org.ezkey.admin.service.AdminRecoveryService.GENERIC_RECOVERY_FAILURE_MESSAGE));

    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("❌ Recovery error for admin: {} - {}", request.username(), e.getMessage(), e);

      Integer errorTenantId = resolveAdminTenantId(request.username());
      Integer errorAdminId = resolveAdminId(request.username());
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_USE,
                  AdminAuditConstants.RECOVERY_ERROR,
                  errorTenantId)
              .eventStatus(EventStatus.ERROR)
              .errorMessage(e.getMessage())
              .adminId(errorAdminId)
              .eventDetails(
                  RecoveryAuditDetails.recoveryUnexpectedError(
                      request.username(), errorTenantId, e.getClass().getSimpleName()))
              .build());

      return ResponseEntity.status(500)
          .body(new AdminRecoveryResponseDto("An error occurred during recovery"));
    }
  }

  private AdminLoginResponseDto wrapWithSessionCookie(
      AdminLoginResponseDto response, HttpServletResponse httpResponse) {
    if (!browserSessionCookieProperties.isBrowserSessionCookieEnabled()
        || !Boolean.TRUE.equals(response.success())
        || !"approved".equals(response.status())
        || response.token() == null
        || response.expiresAt() == null) {
      return response;
    }
    sessionCookieService.addSessionCookie(httpResponse, response.token(), response.expiresAt());
    String csrfToken =
        issueCsrfTokenIfPossible(httpResponse, response.token(), response.expiresAt());
    return response.withCsrfToken(csrfToken).withoutSecretToken();
  }

  private String issueCsrfTokenIfPossible(
      HttpServletResponse httpResponse, String plainToken, OffsetDateTime expiresAt) {
    if (!browserSessionCookieProperties.isBrowserSessionCookieEnabled()
        || plainToken == null
        || plainToken.isBlank()
        || expiresAt == null) {
      return null;
    }
    String csrfToken = csrfTokenService.createToken(plainToken);
    sessionCookieService.addCsrfCookie(httpResponse, csrfToken, expiresAt);
    return csrfToken;
  }

  private Optional<String> resolveBearerForLogout(
      HttpServletRequest httpRequest, String authorization) {
    if (authorization != null && authorization.startsWith(AdminAuditConstants.BEARER_PREFIX)) {
      return Optional.of(authorization.substring(AdminAuditConstants.BEARER_PREFIX.length()));
    }
    if (!browserSessionCookieProperties.isBrowserSessionCookieEnabled()) {
      return Optional.empty();
    }
    Cookie[] cookies = httpRequest.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    String name = browserSessionCookieProperties.getBrowserSessionCookieName();
    for (Cookie c : cookies) {
      if (name.equals(c.getName())) {
        String v = c.getValue();
        if (v != null && !v.isBlank()) {
          return Optional.of(v);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Resolves the tenant ID for an administrator by username.
   *
   * <p>Looks up the admin by username and traverses to their tenant to extract the tenant ID.
   * Returns {@code null} for Global Admins (who have no tenant) or if the admin cannot be found,
   * which is safe for audit logging.
   *
   * @param username the admin username to resolve the tenant from
   * @return the tenant ID, or {@code null} if not resolvable or Global Admin
   */
  private Integer resolveAdminTenantId(String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    return adminRepository
        .findByUsername(username)
        .map(EzkeyAdmin::getTenant)
        .map(tenant -> tenant.getTenantId())
        .orElse(null);
  }

  /**
   * Resolves the admin ID for an administrator by username (for audit logging).
   *
   * @param username the admin username
   * @return the admin ID, or null if not found
   */
  private Integer resolveAdminId(String username) {
    if (username == null || username.isBlank()) {
      return null;
    }
    return adminRepository.findByUsername(username).map(EzkeyAdmin::getAdminId).orElse(null);
  }
}
