/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordlessWaitRequestDto;
import org.ezkey.admin.dto.request.AdminRecoveryRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminRecoveryResponseDto;
import org.ezkey.admin.exception.AdminAuthenticationException;
import org.ezkey.admin.exception.AdminAuthenticationExpiredException;
import org.ezkey.admin.exception.AdminAuthenticationRejectedException;
import org.ezkey.admin.exception.AdminAuthenticationTimeoutException;
import org.ezkey.admin.exception.AdminDeviceSignatureInvalidException;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.admin.util.ClientContext;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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

  private final org.ezkey.admin.service.AdminRecoveryService recoveryService;

  private final AuditLogService auditLogService;

  private final AdminRateLimitFilter rateLimitFilter;

  private final AdminRecoveryProperties recoveryProperties;

  public AdminAuthController(
      AdminAuthService authService,
      org.ezkey.admin.service.AdminRecoveryService recoveryService,
      AuditLogService auditLogService,
      AdminRateLimitFilter rateLimitFilter,
      AdminRecoveryProperties recoveryProperties) {
    this.authService = authService;
    this.recoveryService = recoveryService;
    this.auditLogService = auditLogService;
    this.rateLimitFilter = rateLimitFilter;
    this.recoveryProperties = recoveryProperties;
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
   *             found or device signature validation failed
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
   *   <li><b>403 Forbidden:</b>
   *       <ul>
   *         <li>{@code https://ezkey.io/problems/authentication/account-inactive} - Administrator
   *             account or tenant is deactivated
   *         <li>{@code https://ezkey.io/problems/authentication/no-enrollment} - No device enrolled
   *             for this administrator account
   *       </ul>
   *   <li><b>408 Request Timeout:</b>
   *       <ul>
   *         <li>{@code https://ezkey.io/problems/authentication/auth-timeout} - No device response
   *             within 5-minute timeout window
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
   * @throws AdminAuthenticationException (401) if username not found or device signature failed
   * @throws AdminAccountInactiveException (403) if admin or tenant is deactivated
   * @throws AdminNoEnrollmentException (403) if no device is enrolled
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
                "Unauthorized - Invalid credentials (username not found). "
                    + "Returns RFC 9457 ProblemDetail: type='...invalid-credentials'"),
        @ApiResponse(
            responseCode = "403",
            description =
                "Forbidden - Account inactive or no device enrolled. Returns RFC 9457"
                    + " ProblemDetail: type='...account-inactive' or '...no-enrollment'"),
        @ApiResponse(
            responseCode = "408",
            description =
                "Request Timeout - No device response within 5 minutes. "
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
      @Valid @RequestBody AdminLoginRequestDto request, HttpServletRequest httpRequest) {

    logger.info("🌐 Login request received for username: {}", request.username());

    // Extract client context for audit logging
    ClientContext context = ClientContext.from(httpRequest);

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

      // Audit successful login
      auditLogService.log(
          AuditHelper.logSuccess(
              context,
              EventType.ADMIN_LOGIN,
              AdminAuditConstants.LOGIN_SUCCESS,
              "Username: " + request.username()));

      return ResponseEntity.ok(response);
    } else if ("pending".equals(response.status())) {
      // Pending state is normal workflow (challenge required)
      logger.info(
          "⏳ Login pending (challenge required) for username: {} from IP: {}",
          request.username(),
          context.clientIp());

      // Record as successful attempt for rate limiting
      // Pending is not a failure; it's normal flow progression
      rateLimitFilter.recordSuccessfulAttempt(context.clientIp());

      // Audit pending login (normal workflow state)
      auditLogService.log(
          AuditHelper.logSuccess(
              context,
              EventType.ADMIN_LOGIN,
              AdminAuditConstants.LOGIN_PENDING,
              "Username: " + request.username() + ", Challenge required"));

      return ResponseEntity.ok(response);
    } else {
      // Should not reach here - service throws exceptions for failures
      // This is a fallback for unexpected states
      logger.error(
          "❌ Unexpected login response status: {} for username: {}",
          response.status(),
          request.username());

      // Record failed attempt for rate limiting
      rateLimitFilter.recordFailedAttempt(context.clientIp());

      // Audit the unexpected error
      auditLogService.log(
          AuditHelper.logFailure(
              context,
              EventType.ADMIN_LOGIN,
              AdminAuditConstants.LOGIN_FAILURE,
              "Unexpected response status: " + response.status()));

      throw new IllegalStateException("Unexpected authentication response: " + response.status());
    }
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
      @RequestHeader("Authorization") String authorization, HttpServletRequest httpRequest) {
    try {
      // Extract bearer token from authorization header
      String bearerToken = authorization.replace(AdminAuditConstants.BEARER_PREFIX, "");
      authService.logout(bearerToken);

      // Extract client context for audit logging
      ClientContext context = ClientContext.from(httpRequest);

      // Audit logout
      auditLogService.log(
          AuditHelper.logSuccess(
              context, EventType.ADMIN_LOGOUT, AdminAuditConstants.LOGOUT_SUCCESS, null));

      return ResponseEntity.ok().build();
    } catch (Exception e) {
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
   *   <li>Blocks for up to 5 minutes waiting for device response
   *   <li>Polls device every 2 seconds
   *   <li>Returns immediately when device responds (approved, rejected, expired)
   *   <li>Returns timeout if no response within 5 minutes
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
   *             within 5-minute timeout
   *       </ul>
   * </ul>
   *
   * @param request the wait request containing authAttemptId and optional challengeCode
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
          "Polls device for passwordless authentication response in two-call flows. "
              + "Blocks for up to 5 minutes waiting for device approval. "
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
                "Request Timeout - No device response within 5-minute window. "
                    + "Returns RFC 9457 ProblemDetail: type='...auth-timeout'"),
        @ApiResponse(responseCode = "500", description = "Internal Server Error")
      })
  @PostMapping("/passwordless-wait")
  public ResponseEntity<AdminLoginResponseDto> passwordlessWait(
      @Valid @RequestBody AdminPasswordlessWaitRequestDto request) {

    logger.info("🔐 Passwordless wait request for authAttemptId: {}", request.authAttemptId());

    // Call service - will throw specific exceptions on error (caught by
    // GlobalExceptionHandler)
    AdminLoginResponseDto response =
        authService.waitForPasswordlessAuth(request.authAttemptId(), request.challengeCode());

    logger.info("✅ Passwordless authentication successful");
    return ResponseEntity.ok(response);
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
            description = "Recovery failed (invalid or expired recovery code)"),
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

      AdminRecoveryResponseDto response =
          new AdminRecoveryResponseDto(
              recoveryToken,
              OffsetDateTime.now().plusMinutes(recoveryProperties.getTempTokenDurationMinutes()),
              codesRemaining);

      logger.warn(
          "✅ Recovery successful for admin: {} ({} codes remaining)",
          request.username(),
          codesRemaining);

      // Record successful attempt for rate limiting
      rateLimitFilter.recordSuccessfulAttempt(context.clientIp());

      // Audit successful recovery
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.ADMIN_RECOVERY_USE, AdminAuditConstants.RECOVERY_CODE_USED)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(admin != null ? admin.getAdminId() : null)
              .eventDetails(
                  "Username: " + request.username() + ", Codes remaining: " + codesRemaining)
              .build());

      return ResponseEntity.ok(response);

    } catch (org.ezkey.admin.exception.AuthenticationException e) {
      logger.warn(
          "❌ Recovery failed for admin: {} from IP: {} - Reason: {}",
          request.username(),
          context.clientIp(),
          e.getMessage());

      // Record failed attempt for rate limiting
      rateLimitFilter.recordFailedAttempt(context.clientIp());

      // Audit failed recovery
      auditLogService.log(
          AuditHelper.logFailure(
              context,
              EventType.ADMIN_RECOVERY_USE,
              AdminAuditConstants.RECOVERY_CODE_FAILED,
              e.getMessage()));

      return ResponseEntity.status(403)
          .body(new AdminRecoveryResponseDto("Recovery failed: " + e.getMessage()));

    } catch (Exception e) {
      logger.error("❌ Recovery error for admin: {} - {}", request.username(), e.getMessage(), e);

      // Audit error in recovery
      auditLogService.log(
          AuditHelper.logError(
              context,
              EventType.ADMIN_RECOVERY_USE,
              AdminAuditConstants.RECOVERY_ERROR,
              e.getMessage()));

      return ResponseEntity.status(500)
          .body(new AdminRecoveryResponseDto("An error occurred during recovery"));
    }
  }
}
