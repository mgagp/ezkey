/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AdminEnrollmentController
 * Description: REST controller for admin enrollment management operations.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.ezkey.admin.audit.RecoveryAuditDetails;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.EnrollmentResetRequestDto;
import org.ezkey.admin.dto.response.EnrollmentResetResponseDto;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.exception.RateLimitExceededException;
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
 * REST controller for admin enrollment management.
 *
 * <p>This controller provides endpoints for administrators to manage their enrollments, including
 * resetting enrollments after device loss.
 *
 * <p><b>Security:</b> Enrollment reset requires a recovery token obtained from /auth/recover
 * endpoint (not a regular bearer token).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/admin/enrollments")
@Tag(
    name = "Admin Enrollment Management",
    description = "Administrator enrollment management and device recovery after loss")
public class AdminEnrollmentController {

  private static final Logger logger = LoggerFactory.getLogger(AdminEnrollmentController.class);

  private final AdminRecoveryService recoveryService;
  private final AdminOperationsRateLimitService adminOpsRateLimitService;
  private final AuditLogService auditLogService;

  public AdminEnrollmentController(
      AdminRecoveryService recoveryService,
      AdminOperationsRateLimitService adminOpsRateLimitService,
      AuditLogService auditLogService) {
    this.recoveryService = recoveryService;
    this.adminOpsRateLimitService = adminOpsRateLimitService;
    this.auditLogService = auditLogService;
  }

  /**
   * Reset enrollment after device loss.
   *
   * <p>This endpoint allows administrators who have lost their device to reset their enrollment,
   * unbinding the old device and generating new credentials for binding a replacement device.
   *
   * <p><b>Authentication:</b> Requires a recovery token (not bearer token) obtained from POST
   * /auth/recover using a recovery code. The recovery token is valid for 30 minutes and grants
   * limited access to this endpoint only.
   *
   * <p><b>Security:</b>
   *
   * <ul>
   *   <li>Validates recovery token (not bearer token)
   *   <li>Verifies admin owns the enrollment being reset
   *   <li>Unbinds old device immediately (device_public_key = null)
   *   <li>Generates new credentials (proof token + challenge)
   *   <li>Old device can no longer authenticate
   * </ul>
   *
   * <p><b>Workflow:</b>
   *
   * <ol>
   *   <li>Admin loses device
   *   <li>Admin uses recovery code → recovery token
   *   <li>Admin calls this endpoint → new enrollment credentials
   *   <li>Admin binds new device with new credentials
   *   <li>Admin can login passwordless with new device
   * </ol>
   *
   * @param authorization the authorization header containing recovery token
   * @param request the reset request containing enrollment ID
   * @param httpRequest the HTTP request (client context for audit)
   * @return ResponseEntity containing new enrollment credentials or error
   */
  @Operation(
      summary = "Reset enrollment after recovery",
      description =
          "Resets an administrator enrollment using a recovery token from /auth/recover."
              + " Missing Authorization header returns 401. Invalid token type or token"
              + " validation failures return 403.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Enrollment reset successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "401", description = "Missing required Authorization header"),
        @ApiResponse(
            responseCode = "403",
            description = "Invalid token type or recovery token validation failed"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping("/reset")
  public ResponseEntity<EnrollmentResetResponseDto> resetEnrollment(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody EnrollmentResetRequestDto request,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);
    String token =
        authorization != null && authorization.startsWith(AdminAuditConstants.BEARER_PREFIX)
            ? authorization.substring(AdminAuditConstants.BEARER_PREFIX.length()).trim()
            : null;
    String fingerprint = RecoveryAuditDetails.recoveryTokenFingerprint(token);
    EzkeyAdmin authenticatedAdmin = null;

    try {
      if (!adminOpsRateLimitService.canResetEnrollment(token)) {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ADMIN_RECOVERY_ENROLLMENT_RESET,
                    AdminAuditConstants.ENROLLMENT_RESET_VIA_RECOVERY_FAILED,
                    null)
                .eventStatus(EventStatus.FAILURE)
                .errorMessage("Rate limit exceeded for enrollment reset")
                .eventDetails(
                    RecoveryAuditDetails.enrollmentResetFailed(
                        null,
                        null,
                        request.enrollmentId(),
                        fingerprint,
                        "rate_limited",
                        "Too many enrollment reset attempts"))
                .build());
        throw new RateLimitExceededException("ENROLLMENT_RESET", 3, 0, 30);
      }

      logger.warn(
          "🔄 Enrollment reset request for enrollmentId: {} with recovery token",
          request.enrollmentId());

      if (token == null || !token.startsWith(AdminAuditConstants.RECOVERY_TOKEN_PREFIX)) {
        logger.warn("❌ Invalid token type - expected recovery token, got bearer token");
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ADMIN_RECOVERY_ENROLLMENT_RESET,
                    AdminAuditConstants.ENROLLMENT_RESET_VIA_RECOVERY_FAILED,
                    null)
                .eventStatus(EventStatus.FAILURE)
                .errorMessage("Invalid token type")
                .eventDetails(
                    RecoveryAuditDetails.enrollmentResetFailed(
                        null,
                        null,
                        request.enrollmentId(),
                        fingerprint,
                        "invalid_token_type",
                        "Authorization must be a recovery token from /auth/recover"))
                .build());
        return ResponseEntity.status(403)
            .body(
                EnrollmentResetResponseDto.error(
                    "Invalid token type. Use recovery token from /auth/recover endpoint."));
      }

      authenticatedAdmin = recoveryService.validateRecoveryToken(token);

      Enrollment resetEnrollment =
          recoveryService.resetEnrollment(request.enrollmentId(), authenticatedAdmin);

      // SEC-021 / contract: recovery token is single-use after successful reset
      recoveryService.deactivateRecoveryToken(token);

      EnrollmentResetResponseDto response =
          EnrollmentResetResponseDto.success(
              resetEnrollment.getEnrollmentId(),
              resetEnrollment.getEnrollmentProofToken(),
              resetEnrollment.getEnrollmentChallenge(),
              resetEnrollment.getIntegrationId());

      logger.warn(
          "✅ Enrollment reset successful for admin: {} (enrollmentId: {})",
          authenticatedAdmin.getUsername(),
          resetEnrollment.getEnrollmentId());

      Integer tenantId =
          authenticatedAdmin.getTenant() != null
              ? authenticatedAdmin.getTenant().getTenantId()
              : null;
      String successDetails =
          RecoveryAuditDetails.enrollmentResetCompleted(
              authenticatedAdmin.getAdminId(),
              tenantId,
              resetEnrollment.getEnrollmentId(),
              resetEnrollment.getIntegrationId(),
              RecoveryAuditDetails.recoveryTokenFingerprint(token),
              resetEnrollment.getStatus().name());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_ENROLLMENT_RESET,
                  AdminAuditConstants.ENROLLMENT_RESET_VIA_RECOVERY,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(authenticatedAdmin.getAdminId())
              .eventDetails(successDetails)
              .build());

      adminOpsRateLimitService.recordResetEnrollment(token);

      return ResponseEntity.ok(response);

    } catch (RateLimitExceededException e) {
      throw e;
    } catch (AuthenticationException e) {
      logger.warn("❌ Enrollment reset failed (authentication): {}", e.getMessage());
      Integer tenantId =
          authenticatedAdmin != null && authenticatedAdmin.getTenant() != null
              ? authenticatedAdmin.getTenant().getTenantId()
              : null;
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_ENROLLMENT_RESET,
                  AdminAuditConstants.ENROLLMENT_RESET_VIA_RECOVERY_FAILED,
                  tenantId)
              .eventStatus(EventStatus.FAILURE)
              .errorMessage(e.getMessage())
              .adminId(authenticatedAdmin != null ? authenticatedAdmin.getAdminId() : null)
              .eventDetails(
                  RecoveryAuditDetails.enrollmentResetFailed(
                      authenticatedAdmin != null ? authenticatedAdmin.getAdminId() : null,
                      tenantId,
                      request.enrollmentId(),
                      fingerprint,
                      "authentication_failed",
                      e.getMessage()))
              .build());
      return ResponseEntity.status(403)
          .body(EnrollmentResetResponseDto.error("Reset failed: " + e.getMessage()));

    } catch (IllegalArgumentException e) {
      logger.warn("❌ Enrollment reset failed (invalid request): {}", e.getMessage());
      Integer tenantId =
          authenticatedAdmin != null && authenticatedAdmin.getTenant() != null
              ? authenticatedAdmin.getTenant().getTenantId()
              : null;
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_ENROLLMENT_RESET,
                  AdminAuditConstants.ENROLLMENT_RESET_VIA_RECOVERY_FAILED,
                  tenantId)
              .eventStatus(EventStatus.FAILURE)
              .errorMessage(e.getMessage())
              .adminId(authenticatedAdmin != null ? authenticatedAdmin.getAdminId() : null)
              .eventDetails(
                  RecoveryAuditDetails.enrollmentResetFailed(
                      authenticatedAdmin != null ? authenticatedAdmin.getAdminId() : null,
                      tenantId,
                      request.enrollmentId(),
                      fingerprint,
                      "invalid_request",
                      e.getMessage()))
              .build());
      return ResponseEntity.badRequest()
          .body(EnrollmentResetResponseDto.error("Invalid request: " + e.getMessage()));

    } catch (Exception e) {
      logger.error("❌ Enrollment reset failed (unexpected): {}", e.getMessage(), e);
      Integer tenantId =
          authenticatedAdmin != null && authenticatedAdmin.getTenant() != null
              ? authenticatedAdmin.getTenant().getTenantId()
              : null;
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ADMIN_RECOVERY_ENROLLMENT_RESET,
                  AdminAuditConstants.ENROLLMENT_RESET_VIA_RECOVERY_FAILED,
                  tenantId)
              .eventStatus(EventStatus.ERROR)
              .errorMessage(e.getMessage())
              .adminId(authenticatedAdmin != null ? authenticatedAdmin.getAdminId() : null)
              .eventDetails(
                  RecoveryAuditDetails.enrollmentResetFailed(
                      authenticatedAdmin != null ? authenticatedAdmin.getAdminId() : null,
                      tenantId,
                      request.enrollmentId(),
                      fingerprint,
                      "unexpected_error",
                      e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()))
              .build());
      return ResponseEntity.status(500)
          .body(
              EnrollmentResetResponseDto.error(
                  "An unexpected error occurred during enrollment reset"));
    }
  }
}
