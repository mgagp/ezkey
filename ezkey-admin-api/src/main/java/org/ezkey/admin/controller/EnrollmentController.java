/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: EnrollmentController
 * Description: REST controller for enrollment API v1 using JPA service.
 */

package org.ezkey.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.admin.util.ClientContext;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.dto.EnrollmentCreateResponseDto;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for enrollment administration API v1.
 *
 * <p>This controller provides REST endpoints for enrollment management operations in the admin API
 * (internal). It handles CRUD operations for device enrollments, allowing administrators to create,
 * view, and delete enrollments. Uses JPA-based service and DTOs for clean API responses with proper
 * HTTP status codes.
 *
 * <p><b>Admin API Endpoints (Internal):</b>
 *
 * <ul>
 *   <li><b>GET /api/v1/enrollments</b> - List all enrollments
 *   <li><b>GET /api/v1/enrollments/{id}</b> - Get enrollment by ID
 *   <li><b>POST /api/v1/enrollments</b> - Create new enrollment
 *   <li><b>DELETE /api/v1/enrollments/{id}</b> - Delete enrollment
 * </ul>
 *
 * <p><b>Usage Context:</b> This is part of the admin-api (port 9080) for internal administration
 * purposes. For mobile enrollment binding and verification, see auth-api endpoints.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentService
 * @see EnrollmentResponseDto
 * @see EnrollmentCreateRequestDto
 * @see EnrollmentCreateResponseDto
 */
@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "Enrollment management API")
public class EnrollmentController {

  private final EnrollmentService enrollmentService;

  private final EnrollmentAdminMapper enrollmentMapper;

  private final AuditLogService auditLogService;

  private final QrCodeGeneratorService qrCodeGeneratorService;

  /**
   * Constructs the enrollment controller with required dependencies.
   *
   * @param enrollmentService the JPA-based enrollment service
   * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
   * @param auditLogService the audit log service for security monitoring
   * @param qrCodeGeneratorService the QR code generator service
   */
  public EnrollmentController(
      EnrollmentService enrollmentService,
      EnrollmentAdminMapper enrollmentMapper,
      AuditLogService auditLogService,
      QrCodeGeneratorService qrCodeGeneratorService) {
    this.enrollmentService = enrollmentService;
    this.enrollmentMapper = enrollmentMapper;
    this.auditLogService = auditLogService;
    this.qrCodeGeneratorService = qrCodeGeneratorService;
  }

  /**
   * Retrieves all enrollments for administrative purposes.
   *
   * <p>Returns a list of all enrollments in the system as response DTOs. This endpoint is used by
   * administrators to monitor and manage device enrollments.
   *
   * @return ResponseEntity containing list of enrollment responses with HTTP 200 status
   */
  @Operation(
      summary = "Retrieve all enrollments",
      description = "Returns the complete list of enrollments in the system")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "List retrieved successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<List<EnrollmentResponseDto>> getAll() {
    List<EnrollmentResponseDto> enrollments =
        enrollmentMapper.toResponseList(enrollmentService.getAll());
    return ResponseEntity.ok(enrollments);
  }

  /**
   * Retrieves an enrollment by its ID for administrative purposes.
   *
   * <p>Returns the enrollment data as a response DTO for administrative review. Returns 404 if the
   * enrollment is not found.
   *
   * @param id the enrollment ID
   * @return ResponseEntity containing enrollment response with HTTP 200 status, or 404 if not found
   */
  @Operation(
      summary = "Retrieve enrollment by ID",
      description = "Returns details of a specific enrollment")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Enrollment found"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{id}")
  public ResponseEntity<EnrollmentResponseDto> getById(
      @Parameter(description = "Unique enrollment ID", example = "1") @PathVariable("id")
          Integer id) {
    try {
      var enrollment = enrollmentService.getById(id);
      EnrollmentResponseDto response = enrollmentMapper.toResponse(enrollment);
      return ResponseEntity.ok(response);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Creates a new enrollment for administrative purposes.
   *
   * <p>Creates a new enrollment with the provided data and returns the created enrollment. This
   * generates an enrollment that can later be bound to a mobile device. Returns 201 Created with
   * the created enrollment data including enrollment code and challenge.
   *
   * @param request the enrollment creation request DTO
   * @return ResponseEntity containing created enrollment response with HTTP 201 status
   */
  @Operation(
      summary = "Create new enrollment",
      description = "Creates a new enrollment that can later be bound to a mobile device")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Enrollment created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping
  public ResponseEntity<EnrollmentCreateResponseDto> create(
      @Parameter(description = "Enrollment creation data", required = true) @RequestBody
          EnrollmentCreateRequestDto request,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    try {
      EnrollmentCreateResponse response =
          enrollmentService.create(enrollmentMapper.toCreateRequest(request));

      // Audit successful enrollment creation
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.ENROLLMENT_CREATED, AdminAuditConstants.ENROLLMENT_CREATED)
              .eventStatus(EventStatus.SUCCESS)
              .enrollmentId(response.getEnrollmentId())
              .integrationId(request.integrationId())
              .eventDetails("Enrollment name: " + request.name())
              .build());

      return ResponseEntity.status(HttpStatus.CREATED)
          .body(enrollmentMapper.toCreateResponseDto(response));
    } catch (IllegalArgumentException e) {
      // Audit validation failure
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_CREATED,
                  AdminAuditConstants.ENROLLMENT_CREATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .integrationId(request.integrationId())
              .errorMessage(e.getMessage())
              .build());

      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      // Audit error
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_CREATED,
                  AdminAuditConstants.ENROLLMENT_CREATION_ERROR)
              .eventStatus(EventStatus.ERROR)
              .integrationId(request.integrationId())
              .errorMessage(e.getMessage())
              .build());

      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  /**
   * Deletes an enrollment by its ID for administrative purposes.
   *
   * <p>Removes an enrollment from the system, effectively unlinking the device from the
   * integration. Returns 204 No Content on successful deletion, or 404 if not found.
   *
   * @param id the enrollment ID to delete
   * @return ResponseEntity with HTTP 204 No Content on success, or 404 if not found
   */
  @Operation(summary = "Delete enrollment", description = "Removes an enrollment from the system")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Enrollment deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Enrollment ID to delete", example = "1") @PathVariable("id")
          Integer id,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    try {
      // Get enrollment details before deletion for audit
      var enrollment = enrollmentService.getById(id);

      enrollmentService.delete(id);

      // Audit successful deletion
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context, EventType.ENROLLMENT_DELETED, AdminAuditConstants.ENROLLMENT_DELETED)
              .eventStatus(EventStatus.SUCCESS)
              .enrollmentId(id)
              .integrationId(enrollment.getIntegrationId())
              .eventDetails("Enrollment name: " + enrollment.getEnrollmentName())
              .build());

      return ResponseEntity.noContent().build();
    } catch (ResourceNotFoundException e) {
      // Audit not found
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_DELETED,
                  AdminAuditConstants.ENROLLMENT_DELETION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .enrollmentId(id)
              .errorMessage("Enrollment not found")
              .build());

      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Generates a QR code for enrollment binding.
   *
   * <p>Returns a PNG image containing a QR code with the format: {@code
   * enrollmentId|enrollmentProofToken}
   *
   * <p>This QR code can be scanned by the Ezkey mobile application to automatically populate
   * enrollment credentials, eliminating manual entry and reducing errors.
   *
   * <p><b>Example QR Content:</b> {@code 4|abc123def456...}
   *
   * <p><b>Usage in Postman:</b>
   *
   * <ol>
   *   <li>Send GET request to {@code /api/v1/enrollments/{id}/qrcode}
   *   <li>Response will be PNG image that can be viewed directly in Postman
   *   <li>QR code can be scanned by mobile app or tested with online QR readers
   * </ol>
   *
   * @param id the enrollment ID
   * @return ResponseEntity containing PNG image bytes with HTTP 200 status, or 404 if not found
   */
  @Operation(
      summary = "Generate QR code for enrollment",
      description =
          "Returns a PNG QR code image containing enrollment credentials (enrollmentId|enrollmentProofToken)")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "QR code generated successfully"),
        @ApiResponse(responseCode = "400", description = "Enrollment missing proof token"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/{id}/qrcode")
  public ResponseEntity<byte[]> getQrCode(
      @Parameter(description = "Enrollment ID", example = "4") @PathVariable("id") Integer id) {

    try {
      // Get enrollment details
      var enrollment = enrollmentService.getById(id);

      // Validate enrollment has proof token
      if (enrollment.getEnrollmentProofToken() == null
          || enrollment.getEnrollmentProofToken().isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      // Format: enrollmentId|enrollmentProofToken
      String qrContent = enrollment.getEnrollmentId() + "|" + enrollment.getEnrollmentProofToken();

      // Generate QR code (300x300 pixels)
      byte[] qrCodeImage = qrCodeGeneratorService.generateQrCodeImage(qrContent, 300, 300);

      // Return as PNG image
      return ResponseEntity.ok()
          .header("Content-Type", "image/png")
          .header("Content-Disposition", "inline; filename=enrollment-" + id + "-qrcode.png")
          .body(qrCodeImage);

    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}
