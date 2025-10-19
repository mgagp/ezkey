/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AuthAttemptController
 * Description: REST controller for mobile authentication attempt API v1.
 */

package org.ezkey.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.auth.util.AuditHelper;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.dto.AuthAttemptPendingRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptPendingResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptRespondResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for mobile authentication attempt API v1.
 *
 * <p>This controller provides REST endpoints for mobile device authentication operations in the
 * auth-api (external). It handles the mobile authentication flow where devices check for pending
 * authentication requests and submit their responses. Uses cryptographic signatures for secure
 * authentication validation.
 *
 * <p><b>Auth API Endpoints (Mobile):</b>
 *
 * <ul>
 *   <li><b>POST /api/v1/auth-attempts/pending</b> - Check for pending authentication requests
 *   <li><b>POST /api/v1/auth-attempts/respond</b> - Submit authentication response
 * </ul>
 *
 * <p><b>Usage Context:</b> This is part of the auth-api (port 8080) for mobile device consumption.
 * The mobile app uses these endpoints to implement the pull-based authentication model with
 * cryptographic signature validation.
 *
 * <p><b>Security Model:</b> All requests include cryptographic signatures in the body to ensure
 * request authenticity and prevent unauthorized access.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptService
 * @see AuthAttemptPendingRequestDto
 * @see AuthAttemptPendingResponseDto
 * @see AuthAttemptRespondRequestDto
 * @see AuthAttemptRespondResponseDto
 */
@RestController
@RequestMapping("/api/v1/auth-attempts")
@Tag(
    name = "Authentication Attempts",
    description =
        "Mobile authentication attempt operations for checking pending requests and submitting responses")
public class AuthAttemptController {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptController.class);

  // Rate limiting endpoint constants
  public static final String ENDPOINT_PENDING = "/pending";
  public static final String FULL_PATH_PENDING = "/api/v1/auth-attempts" + ENDPOINT_PENDING;

  private final AuthAttemptService authAttemptService;

  private final AuthAttemptMapper authAttemptMapper;

  private final AuditLogService auditLogService;

  /**
   * Constructs the mobile authentication attempt controller with required dependencies.
   *
   * @param authAttemptService the JPA-based authorization attempt service
   * @param authAttemptMapper the MapStruct mapper for entity-DTO conversions
   * @param auditLogService the audit log service for security monitoring
   */
  public AuthAttemptController(
      AuthAttemptService authAttemptService,
      AuthAttemptMapper authAttemptMapper,
      AuditLogService auditLogService) {
    this.authAttemptService = authAttemptService;
    this.authAttemptMapper = authAttemptMapper;
    this.auditLogService = auditLogService;
  }

  /**
   * Retrieve pending authentication attempts for a mobile device.
   *
   * <p>Security Enhancement: Enrollment identification moved from URL path to request body using
   * enrollmentProofToken to prevent enumeration attacks.
   *
   * <p>The mobile device polls this endpoint to check if there are pending authentication requests
   * for its enrollment. The request body contains a cryptographic signature proving the
   * authenticity of the request. Returns 200 with the pending request details, or 204 No Content if
   * no pending requests exist.
   *
   * <p><b>MFA Security Context:</b> This endpoint implements the pull-based authentication model
   * where devices regularly poll for pending authentication requests. The absence of pending
   * requests (204 No Content) is a normal operational state, not an error.
   *
   * <p><b>Security Enhancement:</b> This endpoint now uses enrollmentProofToken in the request body
   * instead of enrollmentId in the URL path to prevent enumeration attacks. The
   * enrollmentProofToken provides cryptographic proof of enrollment ownership.
   *
   * @param request the pending request containing enrollment proof token and device authentication
   * @return ResponseEntity containing pending authentication details with HTTP 200, or 204 No
   *     Content if no pending requests, or 400 for invalid requests
   * @throws IllegalArgumentException if enrollment proof token is invalid or enrollment not found
   * @since 2025
   */
  @PostMapping("/pending")
  @Operation(
      summary = "Get pending authentication attempt",
      description =
          "Retrieve pending authentication attempts using secure enrollment proof token. "
              + "This endpoint prevents enumeration attacks by requiring cryptographic proof of enrollment ownership.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Pending authentication attempt found",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = AuthAttemptPendingResponseDto.class))),
        @ApiResponse(
            responseCode = "204",
            description = "No pending authentication attempts",
            content = @io.swagger.v3.oas.annotations.media.Content()),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request or enrollment proof token",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.ezkey.dto.ErrorResponseDto.class))),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded",
            content = @io.swagger.v3.oas.annotations.media.Content())
      })
  public ResponseEntity<AuthAttemptPendingResponseDto> pending(
      @Valid @RequestBody AuthAttemptPendingRequestDto request, HttpServletRequest httpRequest) {
    logger.info("Processing pending request for enrollment with proof token");

    String clientIp = AuditHelper.extractClientIp(httpRequest);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    try {
      AuthAttemptPendingResponse response =
          authAttemptService.pending(authAttemptMapper.toAuthAttemptPendingRequest(request));

      // Audit pending request found
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.AUTH_ATTEMPT_PENDING)
              .eventAction("auth_attempt_pending_found")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .authAttemptId(response.getAuthAttemptId())
              .build());

      return ResponseEntity.ok(authAttemptMapper.toAuthAttemptPendingResponseDto(response));
    } catch (NoSuchElementException e) {
      logger.debug("No pending authentication attempts found");
      return ResponseEntity.noContent().build();
    }
  }

  /**
   * Submits the mobile device's response to an authentication request.
   *
   * <p>The mobile device uses this endpoint to submit the user's authentication response (approved,
   * denied, or signature) for a specific authentication attempt. The response includes
   * cryptographic signatures for validation. Returns 200 with the response status, or appropriate
   * error codes for invalid requests.
   *
   * @param request the response request DTO containing authentication attempt ID, user's decision
   *     and signatures
   * @return ResponseEntity containing response confirmation with HTTP 200, or 400 for invalid
   *     requests, or 409 for conflicting states
   */
  @PostMapping("/respond")
  @Operation(
      summary = "Submit authentication response",
      description =
          "Submits mobile device's response to an authentication request. "
              + "The authAttemptId is provided in the request body for uniform API design.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Authentication response submitted successfully",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = AuthAttemptRespondResponseDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid response data or validation failed",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.ezkey.dto.ErrorResponseDto.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Authentication attempt state conflict",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.ezkey.dto.ErrorResponseDto.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.ezkey.dto.ErrorResponseDto.class)))
      })
  public ResponseEntity<AuthAttemptRespondResponseDto> respond(
      @Valid @RequestBody AuthAttemptRespondRequestDto request, HttpServletRequest httpRequest) {

    String clientIp = AuditHelper.extractClientIp(httpRequest);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    try {
      AuthAttemptRespondResponse response =
          authAttemptService.respond(authAttemptMapper.toAuthAttemptRespondRequest(request));

      // Determine action based on acceptance status
      String action =
          request.authAttemptAccepted() != null && request.authAttemptAccepted()
              ? "auth_attempt_approved"
              : "auth_attempt_denied";

      // Audit response
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.AUTH_ATTEMPT_RESPOND)
              .eventAction(action)
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .authAttemptId(request.authAttemptId())
              .eventDetails(
                  "User "
                      + (request.authAttemptAccepted() ? "approved" : "denied")
                      + " authentication")
              .build());

      return ResponseEntity.ok(authAttemptMapper.toAuthAttemptRespondResponseDto(response));
    } catch (Exception e) {
      // Audit response failure
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.AUTH_ATTEMPT_RESPOND)
              .eventAction("auth_attempt_respond_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .authAttemptId(request.authAttemptId())
              .errorMessage(e.getMessage())
              .build());

      throw e;
    }
  }
}