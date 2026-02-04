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
   * Authenticate administrator with username and password.
   *
   * <p>This endpoint allows administrators to authenticate using their credentials and receive a
   * bearer token for subsequent API calls. Rate limiting is applied to prevent brute force attacks.
   *
   * <p>Supports two authentication flows:
   *
   * <ul>
   *   <li><b>Single-call mode:</b> Returns bearer token immediately after device approval (HTTP
   *       200)
   *   <li><b>Two-call mode (with challenge):</b> Returns authAttemptId and challengeCode with
   *       status "pending" (HTTP 200), requires separate /passwordless-wait call
   * </ul>
   *
   * @param request the login request containing username and optional challenge flag
   * @param httpRequest the HTTP servlet request for IP extraction
   * @return ResponseEntity containing authentication response with bearer token or challenge info
   */
  @Operation(
      summary = "Authenticate administrator",
      description =
          "Passwordless authentication for administrators. Returns bearer token on success, or "
              + "challenge info for two-step flow. Rate limiting applied.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description =
                "Authentication successful or pending. If status='approved', token is present. "
                    + "If status='pending', authAttemptId and challengeCode are present."),
        @ApiResponse(
            responseCode = "400",
            description =
                "Authentication failed (invalid credentials, no device enrolled) or validation"
                    + " error"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping("/login")
  public ResponseEntity<AdminLoginResponseDto> login(
      @Valid @RequestBody AdminLoginRequestDto request, HttpServletRequest httpRequest) {

    logger.info("🌐 Login request received for username: {}", request.username());

    // Extract client context for audit logging
    ClientContext context = ClientContext.from(httpRequest);

    AdminLoginResponseDto response = authService.authenticate(request);

    // Check for successful login (token issued)
    if (response.success()) {
      logger.info(
          "✅ Login successful for username: {} from IP: {}",
          request.username(),
          context.clientIp());

      // Record successful attempt for rate limiting (clears failure count)
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
      // Check for pending state (challenge required, normal workflow state)

      logger.info(
          "⏳ Login pending (challenge required) for username: {} from IP: {}",
          request.username(),
          context.clientIp());

      // Record as successful attempt for rate limiting (pending is not a failure)
      // This prevents legitimate users from being blocked when using challenge mode
      rateLimitFilter.recordSuccessfulAttempt(context.clientIp());

      // Audit pending login (normal workflow state, not a failure)
      auditLogService.log(
          AuditHelper.logSuccess(
              context,
              EventType.ADMIN_LOGIN,
              AdminAuditConstants.LOGIN_PENDING,
              "Username: " + request.username() + ", Challenge required"));

      // Return HTTP 200 for pending state (correct semantics - request was processed
      // successfully)
      return ResponseEntity.ok(response);
    } else {
      // Actual failure (invalid credentials, no device, etc.)

      logger.warn(
          "❌ Login failed for username: {} from IP: {} - Reason: {}",
          request.username(),
          context.clientIp(),
          response.message());

      // Record failed attempt for rate limiting (may trigger IP blocking)
      rateLimitFilter.recordFailedAttempt(context.clientIp());

      // Audit failed login
      auditLogService.log(
          AuditHelper.logFailure(
              context,
              EventType.ADMIN_LOGIN,
              AdminAuditConstants.LOGIN_FAILURE,
              response.message()));

      return ResponseEntity.badRequest().body(response);
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
   * <p>Supports two authentication flows:
   *
   * <ul>
   *   <li><b>Challenge Flow:</b> Used when challengeRequested=true in login. After receiving
   *       authAttemptId and challengeCode from the /login endpoint, the client displays the
   *       challenge code to the user, then calls this endpoint with both values to wait for device
   *       approval.
   *   <li><b>Non-Blocking Flow:</b> Used when nonBlocking=true in login. After receiving only
   *       authAttemptId from the /login endpoint, the client calls this endpoint with only
   *       authAttemptId (no challengeCode) to poll for device response.
   * </ul>
   *
   * <p>This endpoint blocks for up to 5 minutes waiting for the device response.
   *
   * <p><b>Security (Challenge Flow):</b> The challengeCode is required to prevent enumeration
   * attacks on authAttemptId. Only clients that legitimately initiated the authentication and
   * received the challenge can proceed. When challengeCode is null (non-blocking flow), challenge
   * verification is skipped (already verified in login).
   *
   * @param request the wait request containing auth attempt ID and optional challenge code
   * @return ResponseEntity with bearer token on success, error on failure
   */
  @PostMapping("/passwordless-wait")
  public ResponseEntity<AdminLoginResponseDto> passwordlessWait(
      @Valid @RequestBody AdminPasswordlessWaitRequestDto request) {

    try {
      logger.info("🔐 Passwordless wait request for authAttemptId: {}", request.authAttemptId());

      AdminLoginResponseDto response =
          authService.waitForPasswordlessAuth(request.authAttemptId(), request.challengeCode());

      logger.info("✅ Passwordless authentication successful");
      return ResponseEntity.ok(response);

    } catch (org.ezkey.admin.exception.AuthenticationException e) {
      logger.warn("❌ Passwordless wait failed (authentication): {}", e.getMessage());
      return ResponseEntity.status(403)
          .body(new AdminLoginResponseDto("Authentication failed: " + e.getMessage()));

    } catch (IllegalArgumentException e) {
      logger.warn("❌ Passwordless wait failed (invalid request): {}", e.getMessage());
      return ResponseEntity.badRequest()
          .body(new AdminLoginResponseDto("Invalid request: " + e.getMessage()));

    } catch (Exception e) {
      logger.error("❌ Passwordless wait failed (unexpected): {}", e.getMessage(), e);
      return ResponseEntity.status(500)
          .body(new AdminLoginResponseDto("An unexpected error occurred during authentication"));
    }
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
