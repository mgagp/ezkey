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
import java.util.List;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.dto.AuthAttemptCreateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptWaitResponseDto;
import org.ezkey.authattempt.mapper.AuthAttemptMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  private final AuthAttemptService authAttemptService;

  private final AuthAttemptMapper authAttemptMapper;

  private final AuditLogService auditLogService;

  /**
   * Constructs the authorization attempt controller with required dependencies.
   *
   * @param authAttemptService the JPA-based authorization attempt service
   * @param authAttemptMapper the MapStruct mapper for entity-DTO conversions
   * @param auditLogService the audit log service for security monitoring
   */
  @Autowired
  public AuthAttemptController(
      AuthAttemptService authAttemptService,
      AuthAttemptMapper authAttemptMapper,
      AuditLogService auditLogService) {
    this.authAttemptService = authAttemptService;
    this.authAttemptMapper = authAttemptMapper;
    this.auditLogService = auditLogService;
  }

  /**
   * Retrieves all authorization attempts for administrative purposes.
   *
   * <p>Returns a list of all authorization attempts in the system as DTOs. This endpoint is used by
   * administrators to monitor and manage authentication requests.
   *
   * @return ResponseEntity containing list of authorization attempt DTOs with HTTP 200 status
   */
  @Operation(
      summary = "Retrieve all auth attempts",
      description = "Returns the complete list of authentication attempts in the system")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "List retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @GetMapping
  public ResponseEntity<List<AuthAttemptDto>> getAll() {
    List<AuthAttemptDto> authAttempts = authAttemptMapper.toDtoList(authAttemptService.getAll());
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
  @PostMapping
  public ResponseEntity<AuthAttemptCreateResponseDto> create(
      @Parameter(description = "Auth attempt creation data", required = true) @RequestBody
          AuthAttemptCreateRequestDto request,
      HttpServletRequest httpRequest) {

    String clientIp = AuditHelper.extractClientIp(httpRequest);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    try {
      AuthAttemptCreateResponse response =
          authAttemptService.create(authAttemptMapper.toAuthAttemptCreateRequest(request));

      // Audit successful auth attempt creation
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.AUTH_ATTEMPT_CREATED)
              .eventAction("auth_attempt_created")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .authAttemptId(response.getAuthAttemptId())
              .enrollmentId(request.enrollmentId())
              .eventDetails(
                  "Challenge: "
                      + (response.getAuthAttemptChallenge() != null ? "required" : "not required"))
              .build());

      return ResponseEntity.status(HttpStatus.CREATED)
          .body(authAttemptMapper.toAuthAttemptCreateResponseDto(response));
    } catch (IllegalArgumentException e) {
      // Audit validation failure
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.AUTH_ATTEMPT_CREATED)
              .eventAction("auth_attempt_creation_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(request.enrollmentId())
              .errorMessage(e.getMessage())
              .build());

      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      // Audit error
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.AUTH_ATTEMPT_CREATED)
              .eventAction("auth_attempt_creation_error")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.ADMIN_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(request.enrollmentId())
              .errorMessage(e.getMessage())
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
          Integer pollingSeconds) {

    try {
      // Build request DTO from parameters
      AuthAttemptWaitRequestDto requestDto = new AuthAttemptWaitRequestDto();
      requestDto.setTimeout(timeoutSeconds);
      requestDto.setPolling(pollingSeconds);

      // Convert to domain object
      AuthAttemptWaitRequest request = authAttemptMapper.toAuthAttemptWaitRequest(requestDto);

      // Call service for polling logic
      AuthAttemptWaitResponse response = authAttemptService.waitForResponse(id, request);

      // Convert to response DTO
      AuthAttemptWaitResponseDto responseDto =
          authAttemptMapper.toAuthAttemptWaitResponseDto(response);

      return ResponseEntity.ok(responseDto);

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}