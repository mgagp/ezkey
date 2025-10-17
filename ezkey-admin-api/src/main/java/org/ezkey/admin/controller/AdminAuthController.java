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

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordlessWaitRequestDto;
import org.ezkey.admin.dto.request.AdminRecoveryRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminRecoveryResponseDto;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

  @Autowired(required = false)
  private AdminRateLimitFilter rateLimitFilter;

  public AdminAuthController(
      AdminAuthService authService,
      org.ezkey.admin.service.AdminRecoveryService recoveryService,
      AuditLogService auditLogService) {
    this.authService = authService;
    this.recoveryService = recoveryService;
    this.auditLogService = auditLogService;
  }

  /**
   * Authenticate administrator with username and password.
   *
   * <p>This endpoint allows administrators to authenticate using their credentials and receive a
   * bearer token for subsequent API calls. Rate limiting is applied to prevent brute force attacks.
   *
   * @param request the login request containing username and password
   * @param httpRequest the HTTP servlet request for IP extraction
   * @return ResponseEntity containing authentication response with bearer token
   */
  @PostMapping("/login")
  public ResponseEntity<AdminLoginResponseDto> login(
      @Valid @RequestBody AdminLoginRequestDto request, HttpServletRequest httpRequest) {

    logger.info("🌐 Login request received for username: {}", request.getUsername());

    // Extract client info for audit logging
    String clientIp = AuditHelper.extractClientIp(httpRequest);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    AdminLoginResponseDto response = authService.authenticate(request);

    if (response.getSuccess()) {
      logger.info(
          "✅ Login successful for username: {} from IP: {}", request.getUsername(), clientIp);

      // Record successful attempt for rate limiting (clears failure count)
      if (rateLimitFilter != null) {
        rateLimitFilter.recordSuccessfulAttempt(clientIp);
      }

      // Audit successful login
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ADMIN_LOGIN)
              .eventAction("login_success")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .eventDetails("Username: " + request.getUsername())
              .build());

      return ResponseEntity.ok(response);
    } else {
      logger.warn(
          "❌ Login failed for username: {} from IP: {} - Reason: {}",
          request.getUsername(),
          clientIp,
          response.getMessage());

      // Record failed attempt for rate limiting (may trigger IP blocking)
      if (rateLimitFilter != null) {
        rateLimitFilter.recordFailedAttempt(clientIp);
      }

      // Audit failed login
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ADMIN_LOGIN)
              .eventAction("login_failure")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .eventDetails("Username: " + request.getUsername())
              .errorMessage(response.getMessage())
              .build());

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
      String bearerToken = authorization.replace("Bearer ", "");
      authService.logout(bearerToken);

      // Extract client info for audit logging
      String clientIp = AuditHelper.extractClientIp(httpRequest);
      String userAgent = AuditHelper.extractUserAgent(httpRequest);

      // Audit logout
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ADMIN_LOGOUT)
              .eventAction("logout_success")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .build());

      return ResponseEntity.ok().build();
    } catch (Exception e) {
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * Wait for passwordless authentication completion.
   *
   * <p>This endpoint is used in the two-step passwordless flow when challenge verification is
   * required. After receiving authAttemptId and challengeCode from the /login endpoint, the client
   * displays the challenge to the user then calls this endpoint to wait for device approval.
   *
   * <p>This endpoint blocks for up to 5 minutes waiting for the device response.
   *
   * <p><b>Security:</b> The challengeCode is required to prevent enumeration attacks on
   * authAttemptId. Only clients that legitimately initiated the authentication and received the
   * challenge can proceed.
   *
   * @param request the wait request containing auth attempt ID and challenge code
   * @return ResponseEntity with bearer token on success, error on failure
   */
  @PostMapping("/passwordless-wait")
  public ResponseEntity<AdminLoginResponseDto> passwordlessWait(
      @Valid @RequestBody AdminPasswordlessWaitRequestDto request) {

    try {
      logger.info("🔐 Passwordless wait request for authAttemptId: {}", request.getAuthAttemptId());

      AdminLoginResponseDto response =
          authService.waitForPasswordlessAuth(
              request.getAuthAttemptId(), request.getChallengeCode());

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
      AdminLoginResponseDto errorResponse = new AdminLoginResponseDto();
      errorResponse.setSuccess(false);
      errorResponse.setMessage("An unexpected error occurred during authentication");
      return ResponseEntity.status(500).body(errorResponse);
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
  @PostMapping("/recover")
  public ResponseEntity<AdminRecoveryResponseDto> recover(
      @Valid @RequestBody AdminRecoveryRequestDto request, HttpServletRequest httpRequest) {

    String clientIp = AuditHelper.extractClientIp(httpRequest);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    try {
      logger.warn("🔑 Recovery attempt for admin: {} from IP: {}", request.getUsername(), clientIp);

      String recoveryToken =
          recoveryService.validateRecoveryCode(request.getUsername(), request.getRecoveryCode());

      // Get admin to determine codes remaining
      // Note: Use validateRecoveryToken (not validateToken) since it's a temp token, not bearer
      EzkeyAdmin admin = recoveryService.validateRecoveryToken(recoveryToken);
      int codesRemaining =
          admin != null && admin.getRecoveryCodes() != null ? admin.getRecoveryCodes().length : 0;

      AdminRecoveryResponseDto response =
          new AdminRecoveryResponseDto(
              recoveryToken, OffsetDateTime.now().plusMinutes(30), codesRemaining);

      logger.warn(
          "✅ Recovery successful for admin: {} ({} codes remaining)",
          request.getUsername(),
          codesRemaining);

      // Record successful attempt for rate limiting
      if (rateLimitFilter != null) {
        rateLimitFilter.recordSuccessfulAttempt(clientIp);
      }

      // Audit successful recovery
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ADMIN_RECOVERY_USE)
              .eventAction("recovery_code_used")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .adminId(admin != null ? admin.getAdminId() : null)
              .eventDetails(
                  "Username: " + request.getUsername() + ", Codes remaining: " + codesRemaining)
              .build());

      return ResponseEntity.ok(response);

    } catch (org.ezkey.admin.exception.AuthenticationException e) {
      logger.warn(
          "❌ Recovery failed for admin: {} from IP: {} - Reason: {}",
          request.getUsername(),
          clientIp,
          e.getMessage());

      // Record failed attempt for rate limiting
      if (rateLimitFilter != null) {
        rateLimitFilter.recordFailedAttempt(clientIp);
      }

      // Audit failed recovery
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ADMIN_RECOVERY_USE)
              .eventAction("recovery_code_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .eventDetails("Username: " + request.getUsername())
              .errorMessage(e.getMessage())
              .build());

      return ResponseEntity.status(403)
          .body(new AdminRecoveryResponseDto("Recovery failed: " + e.getMessage()));

    } catch (Exception e) {
      logger.error("❌ Recovery error for admin: {} - {}", request.getUsername(), e.getMessage(), e);

      // Audit error in recovery
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ADMIN_RECOVERY_USE)
              .eventAction("recovery_error")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .eventDetails("Username: " + request.getUsername())
              .errorMessage(e.getMessage())
              .build());

      return ResponseEntity.status(500)
          .body(new AdminRecoveryResponseDto("An error occurred during recovery"));
    }
  }
}
