/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EnrollmentController
 * Description: REST controller for mobile enrollment API v1.
 */

package org.ezkey.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.auth.util.AuditHelper;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.dto.EnrollmentBindRequestDto;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAuthMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for mobile enrollment API v1.
 *
 * <p>This controller provides REST endpoints for mobile device enrollment operations in the
 * auth-api (external). It handles the enrollment binding and verification process where mobile
 * devices link themselves to user accounts and complete the cryptographic enrollment setup.
 *
 * <p><b>Auth API Endpoints (Mobile):</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/enrollments/bind/{enrollmentId}</b> - Initiate device binding to enrollment
 *   <li><b>POST /api/v1/enrollments/verify</b> - Complete enrollment verification process
 * </ul>
 *
 * <p><b>Usage Context:</b> This is part of the auth-api (port 8080) for mobile device consumption.
 * Mobile apps use these endpoints to complete the enrollment process by linking devices to user
 * accounts through cryptographic key exchange.
 *
 * <p><b>Enrollment Flow:</b>
 *
 * <ol>
 *   <li>Mobile device scans QR code or deep link containing enrollmentId
 *   <li>Device calls bind endpoint to retrieve enrollment details and challenges
 *   <li>Device generates cryptographic keys and signs enrollment code
 *   <li>Device calls verify endpoint to complete enrollment with signatures
 * </ol>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentService
 * @see EnrollmentBindResponseDto
 * @see EnrollmentVerifyRequestDto
 * @see EnrollmentVerifyResponseDto
 */
@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(
    name = "Enrollments",
    description =
        "Mobile device enrollment operations for binding devices to user accounts and completing verification")
public class EnrollmentController {

  // Rate limiting endpoint constants
  public static final String ENDPOINT_BIND = "/bind";
  public static final String ENDPOINT_VERIFY = "/verify";
  public static final String FULL_PATH_BIND = "/api/v1/enrollments" + ENDPOINT_BIND;
  public static final String FULL_PATH_VERIFY = "/api/v1/enrollments" + ENDPOINT_VERIFY;

  private final EnrollmentService enrollmentService;

  private final EnrollmentAuthMapper enrollmentMapper;

  private final AuditLogService auditLogService;

  /**
   * Constructs the mobile enrollment controller with required dependencies.
   *
   * @param enrollmentService JPA-based enrollment service
   * @param enrollmentMapper MapStruct mapper for entity-DTO conversions
   * @param auditLogService audit log service for security monitoring
   */
  public EnrollmentController(
      EnrollmentService enrollmentService,
      EnrollmentAuthMapper enrollmentMapper,
      AuditLogService auditLogService) {
    this.enrollmentService = enrollmentService;
    this.enrollmentMapper = enrollmentMapper;
    this.auditLogService = auditLogService;
  }

  /**
   * Initiates the device binding process for mobile enrollment with proof token authentication.
   *
   * <p>The mobile device calls this endpoint to start the enrollment binding process. It requires
   * both the enrollment ID and enrollment proof token to prevent enumeration attacks and ensure
   * secure access to enrollment data.
   *
   * <p><b>Security Enhancement:</b> This endpoint now requires an enrollment proof token in
   * addition to the enrollment ID, preventing attackers from systematically testing enrollment IDs
   * to discover valid enrollments.
   *
   * @param request the binding request containing enrollment ID, proof token, and language
   *     preference
   * @return ResponseEntity containing enrollment binding information with HTTP 200, or 400 for
   *     invalid enrollment ID/proof token, or 409 if enrollment is already bound
   */
  @PostMapping("/bind")
  @Operation(
      summary = "Initiate device binding with proof token",
      description = "Retrieves enrollment binding information using secure enrollment proof token")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Enrollment binding information retrieved successfully",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = EnrollmentBindResponseDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid enrollment ID, proof token, or enrollment expired",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.ezkey.dto.ErrorResponseDto.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Enrollment already bound or proof token already used",
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
  public ResponseEntity<EnrollmentBindResponseDto> bind(
      @RequestBody EnrollmentBindRequestDto request, HttpServletRequest httpRequest) {

    String clientIp = AuditHelper.extractClientIp(httpRequest);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    // Validation: enrollmentId + enrollmentProofToken required
    if (request.enrollmentId() == null
        || request.enrollmentProofToken() == null
        || request.enrollmentProofToken().trim().isEmpty()) {

      // Audit validation failure
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_BIND)
              .eventAction("enrollment_bind_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(request.enrollmentId())
              .errorMessage("Enrollment ID and enrollment proof token are required")
              .build());

      throw new IllegalArgumentException("Enrollment ID and enrollment proof token are required");
    }

    try {
      EnrollmentBindRequest bindRequest = enrollmentMapper.toEnrollmentBindRequest(request);
      EnrollmentBindResponse response = enrollmentService.bind(bindRequest);

      // Audit successful binding
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_BIND)
              .eventAction("enrollment_bind_success")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(request.enrollmentId())
              .build());

      return ResponseEntity.ok(enrollmentMapper.toEnrollmentBindResponseDto(response));
    } catch (Exception e) {
      // Audit bind error
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_BIND)
              .eventAction("enrollment_bind_error")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(request.enrollmentId())
              .errorMessage(e.getMessage())
              .build());

      throw e;
    }
  }

  /**
   * Completes the enrollment verification process for mobile devices.
   *
   * <p>The mobile device submits its cryptographic keys and signed proof token to finalize the
   * enrollment process. The device generates a public/private key pair, signs the proof token with
   * its private key, and submits the public key and signature for verification. Once verified, the
   * enrollment becomes active and the device can authenticate users. The proof token must be the
   * one obtained from the bind endpoint.
   *
   * @param req the verification request DTO containing device keys and signatures
   * @return ResponseEntity containing verification confirmation with HTTP 200, or 400 for invalid
   *     verification data, or 409 if enrollment state conflicts
   */
  @PostMapping("/verify")
  @Operation(
      summary = "Complete enrollment verification",
      description = "Submits device cryptographic keys and signatures to finalize enrollment")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Enrollment verification completed successfully",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = EnrollmentVerifyResponseDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid verification data or cryptographic validation failed",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = org.ezkey.dto.ErrorResponseDto.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Enrollment state conflict or already verified",
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
  public ResponseEntity<EnrollmentVerifyResponseDto> verify(
      @RequestBody EnrollmentVerifyRequestDto req, HttpServletRequest httpRequest) {

    String clientIp = AuditHelper.extractClientIp(httpRequest);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    try {
      EnrollmentVerifyResponse response =
          enrollmentService.verify(enrollmentMapper.toEnrollmentVerifyRequest(req));

      // Audit successful verification
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_VERIFY)
              .eventAction("enrollment_verify_success")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(req.enrollmentId())
              .eventDetails("Enrollment activated")
              .build());

      return ResponseEntity.ok(enrollmentMapper.toEnrollmentVerifyResponseDto(response));
    } catch (Exception e) {
      // Audit verification failure
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_VERIFY)
              .eventAction("enrollment_verify_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(req.enrollmentId())
              .errorMessage(e.getMessage())
              .build());

      throw e;
    }
  }
}