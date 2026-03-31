/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.request.EnrollmentUpdateRequestDto;
import org.ezkey.admin.exception.EnrollmentCannotBeDeletedException;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.admin.service.EnrollmentUpdateOutcome;
import org.ezkey.admin.service.EnrollmentUpdateService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.admin.util.AuditHelper;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.ClientContext;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.dto.EnrollmentCreateRequestDto;
import org.ezkey.enrollment.dto.EnrollmentCreateResponseDto;
import org.ezkey.enrollment.dto.EnrollmentResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
@Validated
@RestController
@RequestMapping("/api/v1/enrollments")
@Tag(name = "Enrollments", description = "Enrollment management API")
public class EnrollmentController {

  private final EnrollmentService enrollmentService;
  private final EnrollmentAdminMapper enrollmentMapper;
  private final AuditLogService auditLogService;
  private final QrCodeGeneratorService qrCodeGeneratorService;
  private final QrCodePayloadService qrCodePayloadService;
  private final AccessControlService accessControlService;
  private final EnrollmentRepository enrollmentRepository;
  private final IntegrationRepository integrationRepository;
  private final EnrollmentRevocationService enrollmentRevocationService;
  private final EnrollmentUpdateService enrollmentUpdateService;
  private final AuthAttemptRepository authAttemptRepository;

  /**
   * Constructs the enrollment controller with required dependencies.
   *
   * @param enrollmentService the JPA-based enrollment service
   * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
   * @param auditLogService the audit log service for security monitoring
   * @param qrCodeGeneratorService the QR code generator service
   * @param qrCodePayloadService the QR code payload composition service
   * @param accessControlService the access control service for tenant scoping validation
   * @param enrollmentRepository the enrollment repository for audit queries
   * @param integrationRepository the integration repository for tenant resolution in audit logs
   * @param enrollmentRevocationService the service for enrollment revocation lifecycle
   * @param enrollmentUpdateService the service for enrollment metadata partial updates
   * @param authAttemptRepository the repository to check for auth attempts before enrollment delete
   */
  public EnrollmentController(
      EnrollmentService enrollmentService,
      EnrollmentAdminMapper enrollmentMapper,
      AuditLogService auditLogService,
      QrCodeGeneratorService qrCodeGeneratorService,
      QrCodePayloadService qrCodePayloadService,
      AccessControlService accessControlService,
      EnrollmentRepository enrollmentRepository,
      IntegrationRepository integrationRepository,
      EnrollmentRevocationService enrollmentRevocationService,
      EnrollmentUpdateService enrollmentUpdateService,
      AuthAttemptRepository authAttemptRepository) {
    this.enrollmentService = enrollmentService;
    this.enrollmentMapper = enrollmentMapper;
    this.auditLogService = auditLogService;
    this.qrCodeGeneratorService = qrCodeGeneratorService;
    this.qrCodePayloadService = qrCodePayloadService;
    this.accessControlService = accessControlService;
    this.enrollmentRepository = enrollmentRepository;
    this.integrationRepository = integrationRepository;
    this.enrollmentRevocationService = enrollmentRevocationService;
    this.enrollmentUpdateService = enrollmentUpdateService;
    this.authAttemptRepository = authAttemptRepository;
  }

  /**
   * Searches enrollments with optional filters and pagination.
   *
   * <p>Retrieves enrollments matching the specified criteria with pagination support. All filter
   * parameters are optional - if none are provided, returns all enrollments (paginated). Results
   * are ordered by creation date descending (newest first) by default.
   *
   * <p><b>Use Case:</b> Security operators monitoring enrollments, forensic analysis, incident
   * investigation, and compliance reporting.
   *
   * <p><b>Pagination and Sorting:</b>
   *
   * <ul>
   *   <li>Use <code>?page=0&size=20</code> for pagination (zero-based page numbers)
   *   <li>Use <code>?sort=field,direction</code> for sorting (e.g., <code>?sort=enrollmentId,asc
   *       </code> or <code>?sort=createdAt,desc</code>)
   *   <li>Default: page=0, size=20, sort=createdAt,DESC
   *   <li>Sortable fields: enrollmentId, enrollmentName, userIdentifier, createdAt, integrationId,
   *       status, active, verifiedAt, lastUsedAt, authAttemptChallengeRequired
   * </ul>
   *
   * @param status optional filter by enrollment status (CREATED, BOUND, VERIFIED, INVALID, REVOKED,
   *     EXPIRED)
   * @param integrationId optional filter by integration ID
   * @param enrollmentName optional filter by enrollment name (partial match, case-insensitive)
   * @param active optional filter by active flag
   * @param createdAfter optional filter for enrollments created after this timestamp
   * @param createdBefore optional filter for enrollments created before this timestamp
   * @param pageable pagination and sorting parameters (default: page=0, size=20,
   *     sort=createdAt,DESC)
   * @return ResponseEntity containing page of enrollment response DTOs with HTTP 200 status
   */
  @Operation(
      summary = "Search enrollments",
      description =
          "Retrieves enrollments with optional filters and pagination for security monitoring, "
              + "forensic analysis, and compliance reporting. Supports dynamic sorting via "
              + "?sort=field,direction (e.g., ?sort=enrollmentId,asc). Default sort is by "
              + "creation date descending (newest first).")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @GetMapping
  public ResponseEntity<Page<EnrollmentResponseDto>> search(
      @Parameter(
              description =
                  "Filter by enrollment status (CREATED, BOUND, VERIFIED, INVALID, REVOKED,"
                      + " EXPIRED)")
          @RequestParam(required = false)
          EnrollmentStatus status,
      @Parameter(description = "Filter by integration ID") @RequestParam(required = false)
          Integer integrationId,
      @Parameter(description = "Filter by enrollment name (partial match, case-insensitive)")
          @RequestParam(required = false)
          String enrollmentName,
      @Parameter(description = "Filter by active flag") @RequestParam(required = false)
          Boolean active,
      @Parameter(description = "Filter enrollments created after this timestamp (ISO-8601)")
          @RequestParam(required = false)
          OffsetDateTime createdAfter,
      @Parameter(description = "Filter enrollments created before this timestamp (ISO-8601)")
          @RequestParam(required = false)
          OffsetDateTime createdBefore,
      @ParameterObject
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {

    // Extract tenant ID from authentication for tenant scoping
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Integer tenantId = extractTenantId(auth);

    Page<EnrollmentResponseDto> enrollments =
        enrollmentService
            .findByFilters(
                status,
                integrationId,
                enrollmentName,
                active,
                createdAfter,
                createdBefore,
                tenantId,
                pageable)
            .map(enrollmentMapper::toResponse);

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
      // Validate tenant scoping: admin must have access to this enrollment
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (!accessControlService.canAccessEnrollment(auth, id)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
      }

      var enrollment = enrollmentService.getById(id);
      var integration =
          enrollment.getIntegrationId() != null
              ? integrationRepository.findById(enrollment.getIntegrationId())
              : java.util.Optional.<Integration>empty();
      EnrollmentResponseDto response =
          enrollmentMapper.toResponseWithIntegration(enrollment, integration.orElse(null));
      return ResponseEntity.ok(response);
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Partially updates an enrollment's metadata.
   *
   * <p>Only non-null fields in the request body are applied. Supports enrollmentName, contactEmail,
   * expiresAt, authAttemptChallengeRequired, userIdentifier. Only active, non-revoked VERIFIED
   * enrollments can be updated. Include {@code version} from the GET response for optimistic
   * locking.
   *
   * @param id the enrollment ID to update
   * @param request the partial update request
   * @param httpRequest the HTTP request for audit context
   * @return ResponseEntity containing updated enrollment with HTTP 200, or error status
   */
  @Operation(
      summary = "Partially update enrollment metadata",
      description =
          "Updates enrollment metadata (name, contactEmail, expiresAt,"
              + " authAttemptChallengeRequired, userIdentifier). Only active VERIFIED enrollments."
              + " Include version from GET for optimistic locking.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Enrollment updated successfully"),
        @ApiResponse(
            responseCode = "400",
            description =
                "Invalid data, enrollment not updatable (revoked/inactive), or validation failed"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(
            responseCode = "409",
            description = "Optimistic lock conflict - resource was modified, re-fetch and retry")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PatchMapping("/{id}")
  public ResponseEntity<EnrollmentResponseDto> update(
      @Parameter(description = "Enrollment ID to update", example = "1") @PathVariable("id")
          Integer id,
      @Valid @RequestBody EnrollmentUpdateRequestDto request,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);

    if (!accessControlService.canAccessEnrollment(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    try {
      EnrollmentUpdateOutcome outcome = enrollmentUpdateService.updateEnrollment(id, request);
      Enrollment updated = outcome.enrollment();
      Integer integrationIdForResponse = updated.getIntegrationId();
      var integrationForResponse =
          integrationIdForResponse != null
              ? integrationRepository.findById(integrationIdForResponse)
              : java.util.Optional.<Integration>empty();
      EnrollmentResponseDto response =
          enrollmentMapper.toResponseWithIntegration(updated, integrationForResponse.orElse(null));
      Integer tenantId = resolveTenantId(updated.getIntegrationId());

      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_UPDATED,
                  AdminAuditConstants.ENROLLMENT_UPDATED,
                  tenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(principal != null ? principal.adminId() : null)
              .enrollmentId(id)
              .integrationId(updated.getIntegrationId())
              .eventDetails(outcome.auditEventDetailsJson())
              .build());

      return ResponseEntity.ok(response);
    } catch (ResourceNotFoundException e) {
      if (principal != null) {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_UPDATED,
                    AdminAuditConstants.ENROLLMENT_UPDATE_FAILED)
                .eventStatus(EventStatus.FAILURE)
                .adminId(principal.adminId())
                .enrollmentId(id)
                .errorMessage("Enrollment not found: " + id)
                .build());
      }
      throw e;
    } catch (IllegalArgumentException e) {
      if (principal != null) {
        Integer tenantId = resolveTenantIdFromEnrollment(id);
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_UPDATED,
                    AdminAuditConstants.ENROLLMENT_UPDATE_FAILED,
                    tenantId)
                .eventStatus(EventStatus.FAILURE)
                .adminId(principal.adminId())
                .enrollmentId(id)
                .errorMessage(e.getMessage())
                .build());
      }
      throw e;
    } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
      if (principal != null) {
        Integer tenantId = resolveTenantIdFromEnrollment(id);
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_UPDATED,
                    AdminAuditConstants.ENROLLMENT_UPDATE_FAILED,
                    tenantId)
                .eventStatus(EventStatus.FAILURE)
                .adminId(principal.adminId())
                .enrollmentId(id)
                .errorMessage("Optimistic lock conflict")
                .build());
      }
      throw e;
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
      @Parameter(description = "Enrollment creation data", required = true)
          @RequestBody
          @jakarta.validation.Valid
          EnrollmentCreateRequestDto request,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    // Validate tenant scoping: admin must have access to the integration
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    if (!accessControlService.canAccessIntegration(auth, request.integrationId())) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_CREATED,
                  AdminAuditConstants.ENROLLMENT_CREATION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal != null ? principal.adminId() : null)
              .errorMessage(
                  "Access denied: admin does not have access to integration "
                      + request.integrationId())
              .build());
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Resolve tenant from integration for audit log association
    Integer auditTenantId = resolveTenantId(request.integrationId());

    try {
      EnrollmentCreateRequest createRequest = enrollmentMapper.toCreateRequest(request);
      if (principal != null) {
        createRequest.setCreatedByAdminId(principal.adminId());
      }
      EnrollmentCreateResponse response = enrollmentService.create(createRequest);

      // Check if inactive VERIFIED enrollment exists for audit context
      List<Enrollment> inactiveVerified =
          enrollmentRepository
              .findByIntegrationIdAndEnrollmentNameAndStatus(
                  request.integrationId(), request.name(), EnrollmentStatus.VERIFIED)
              .stream()
              .filter(e -> Boolean.FALSE.equals(e.getActive()))
              .toList();

      // Audit successful enrollment creation
      if (!inactiveVerified.isEmpty()) {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_CREATED,
                    AdminAuditConstants.ENROLLMENT_CREATED,
                    auditTenantId)
                .eventStatus(EventStatus.SUCCESS)
                .adminId(principal != null ? principal.adminId() : null)
                .enrollmentId(response.getEnrollmentId())
                .integrationId(request.integrationId())
                .eventDetails(
                    "Enrollment name: "
                        + request.name()
                        + ". Replacing inactive VERIFIED enrollment (ID: "
                        + inactiveVerified.get(0).getEnrollmentId()
                        + ")")
                .build());
      } else {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_CREATED,
                    AdminAuditConstants.ENROLLMENT_CREATED,
                    auditTenantId)
                .eventStatus(EventStatus.SUCCESS)
                .adminId(principal != null ? principal.adminId() : null)
                .enrollmentId(response.getEnrollmentId())
                .integrationId(request.integrationId())
                .eventDetails("Enrollment name: " + request.name())
                .build());
      }

      return ResponseEntity.status(HttpStatus.CREATED)
          .body(enrollmentMapper.toCreateResponseDto(response));
    } catch (IllegalArgumentException e) {
      // Check if error is about existing VERIFIED enrollment
      if (e.getMessage() != null && e.getMessage().contains("active verified enrollment")) {
        // Query to find existing enrollment for audit purposes
        Enrollment existing =
            enrollmentRepository
                .findByIntegrationIdAndEnrollmentNameAndStatus(
                    request.integrationId(), request.name(), EnrollmentStatus.VERIFIED)
                .stream()
                .filter(enrollment -> Boolean.TRUE.equals(enrollment.getActive()))
                .findFirst()
                .orElse(null);

        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_CREATED,
                    AdminAuditConstants.ENROLLMENT_CREATION_FAILED,
                    auditTenantId)
                .eventStatus(EventStatus.FAILURE)
                .adminId(principal != null ? principal.adminId() : null)
                .integrationId(request.integrationId())
                .enrollmentId(existing != null ? existing.getEnrollmentId() : null)
                .errorMessage(e.getMessage())
                .eventDetails(
                    "Enrollment creation rejected: Active VERIFIED enrollment exists. "
                        + "Existing enrollment ID: "
                        + (existing != null ? existing.getEnrollmentId() : "unknown")
                        + ", Requested name: "
                        + request.name())
                .build());
      } else {
        auditLogService.log(
            AuditHelper.createAdminAudit(
                    context,
                    EventType.ENROLLMENT_CREATED,
                    AdminAuditConstants.ENROLLMENT_CREATION_FAILED,
                    auditTenantId)
                .eventStatus(EventStatus.FAILURE)
                .adminId(principal != null ? principal.adminId() : null)
                .errorMessage(e.getMessage() + " (integrationId: " + request.integrationId() + ")")
                .build());
      }

      // Let GlobalExceptionHandler handle the exception to return proper error
      // response with
      // message
      // This ensures consistent error response format across all endpoints
      throw e;
    } catch (DataIntegrityViolationException e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_CREATED,
                  AdminAuditConstants.ENROLLMENT_CREATION_FAILED,
                  auditTenantId)
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal != null ? principal.adminId() : null)
              .errorMessage(
                  "Invalid integration ID or constraint violation: "
                      + e.getMostSpecificCause().getMessage())
              .build());

      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_CREATED,
                  AdminAuditConstants.ENROLLMENT_CREATION_ERROR,
                  auditTenantId)
              .eventStatus(EventStatus.ERROR)
              .adminId(principal != null ? principal.adminId() : null)
              .errorMessage(e.getMessage() + " (integrationId: " + request.integrationId() + ")")
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
        @ApiResponse(
            responseCode = "403",
            description = "Cannot delete your own MFA enrollment (RFC 9457)"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(
            responseCode = "409",
            description =
                "Enrollment has authentication history (revoke instead) or is linked as an"
                    + " administrator's MFA (use recovery flow to reset that admin's MFA) (RFC"
                    + " 9457)"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "Enrollment ID to delete", example = "1") @PathVariable("id")
          Integer id,
      @Parameter(description = "Audit justification for the deletion (min 10 characters)")
          @RequestParam(required = false)
          @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    ClientContext context = ClientContext.from(httpRequest);

    try {
      // Validate tenant access for enrollment
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(authentication);
      if (!accessControlService.canAccessEnrollment(authentication, id)) {
        // Return 404 to hide existence of cross-tenant resource
        throw new ResourceNotFoundException("Enrollment", id);
      }

      // Get enrollment details after access validation
      var enrollment = enrollmentService.getById(id);
      Integer deleteTenantId = resolveTenantId(enrollment.getIntegrationId());

      // Business guards: return RFC 9457 instead of DB constraint violation
      enrollmentRevocationService.assertNotSelfDeletion(principal, id);
      enrollmentRevocationService.assertNotLinkedAsAdminMfa(id);
      if (authAttemptRepository.existsByEnrollmentId(id)) {
        throw new EnrollmentCannotBeDeletedException(
            "Enrollment cannot be deleted because it has authentication history. Revoke the"
                + " enrollment instead.");
      }

      // Create audit log BEFORE deletion to avoid foreign key constraint violation
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_DELETED,
                  AdminAuditConstants.ENROLLMENT_DELETED,
                  deleteTenantId)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(principal != null ? principal.adminId() : null)
              .enrollmentId(id)
              .integrationId(enrollment.getIntegrationId())
              .eventDetails("Enrollment name: " + enrollment.getEnrollmentName())
              .reason(reason)
              .build());

      // Delete enrollment after audit log is created
      enrollmentService.delete(id);

      return ResponseEntity.noContent().build();
    } catch (ResourceNotFoundException e) {
      // Audit not found - do not include enrollmentId as it doesn't exist (would
      // violate FK
      // constraint)
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(authentication);
      auditLogService.log(
          AuditHelper.createAdminAudit(
                  context,
                  EventType.ENROLLMENT_DELETED,
                  AdminAuditConstants.ENROLLMENT_DELETION_FAILED)
              .eventStatus(EventStatus.FAILURE)
              .adminId(principal != null ? principal.adminId() : null)
              // enrollmentId omitted - enrollment doesn't exist, would violate FK constraint
              .errorMessage("Enrollment not found: " + id)
              .build());

      return ResponseEntity.notFound().build();
    }
  }

  /**
   * Generates a QR code for enrollment binding.
   *
   * <p>Returns a PNG image containing a QR code with a JSON payload:
   *
   * <pre>
   * {"enrollmentId":"4","enrollmentProofToken":"abc...","authUrl":"https://..."}
   * </pre>
   *
   * <p>The {@code authUrl} field is included only when {@code ezkey.qr.auth-base-url} is
   * configured. This QR code can be scanned by the Ezkey mobile application to automatically
   * populate enrollment credentials and connect to the correct auth-api instance.
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
          "Returns a PNG QR code image containing enrollment credentials as JSON"
              + " ({enrollmentId, enrollmentProofToken, authUrl})")
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

      // Compose JSON payload with optional authUrl
      String qrContent =
          qrCodePayloadService.composePayload(
              enrollment.getEnrollmentId(), enrollment.getEnrollmentProofToken());

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

  /**
   * Permanently revokes an enrollment.
   *
   * <p>Revocation is <b>irreversible</b>: the enrollment transitions to status {@code REVOKED} and
   * {@code active=false}. If the enrollment belongs to an administrator, all their active bearer
   * tokens are immediately invalidated.
   *
   * <p><b>Self-revocation guard:</b> Administrators cannot revoke their own MFA enrollment.
   *
   * <p><b>Peer revocation:</b> Tenant Admins may revoke other Tenant Admins' enrollments within
   * their tenant. Global Admins may revoke any enrollment.
   *
   * @param id the enrollment ID to revoke
   * @param reason mandatory justification (min 10, max 500 characters)
   * @param httpRequest the HTTP request
   * @return 204 No Content on success
   */
  @Operation(
      summary = "Permanently revoke an enrollment",
      description =
          "Irrevocably revokes an enrollment. Sets status to REVOKED and active=false."
              + " If the enrollment belongs to an admin, their active bearer tokens are"
              + " immediately invalidated. Self-revocation is not allowed.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Enrollment revoked successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters"),
        @ApiResponse(
            responseCode = "403",
            description = "Access denied, self-revocation attempted, or system integration guard"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/revoke")
  public ResponseEntity<Void> revoke(
      @Parameter(description = "Enrollment ID to revoke", example = "1") @PathVariable("id")
          Integer id,
      @Parameter(description = "Mandatory justification for revocation (min 10 characters)")
          @RequestParam
          @jakarta.validation.constraints.Size(
              min = 10,
              max = 500,
              message = "Reason must be between 10 and 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canRevokeEnrollment(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    Integer tenantId = resolveTenantIdFromEnrollment(id);

    enrollmentRevocationService.revoke(id, principal, reason, context, tenantId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Deactivates an enrollment (reversible soft-disable).
   *
   * <p>Sets {@code active=false} while preserving status as {@code VERIFIED}. The enrollment can
   * later be reactivated via {@code POST /api/v1/enrollments/{id}/reactivate}. If the enrollment
   * belongs to an administrator, all their active bearer tokens are immediately invalidated.
   *
   * <p><b>Self-revocation guard:</b> Administrators cannot deactivate their own MFA enrollment.
   *
   * @param id the enrollment ID to deactivate
   * @param reason optional justification
   * @param httpRequest the HTTP request
   * @return 204 No Content on success
   */
  @Operation(
      summary = "Deactivate an enrollment",
      description =
          "Reversibly deactivates an enrollment (active=false). Status remains VERIFIED."
              + " Can be undone with /reactivate. If the enrollment belongs to an admin,"
              + " their active bearer tokens are immediately invalidated."
              + " Self-deactivation is not allowed.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Enrollment deactivated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters or state conflict"),
        @ApiResponse(responseCode = "403", description = "Access denied or self-deactivation"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/deactivate")
  public ResponseEntity<Void> deactivate(
      @Parameter(description = "Enrollment ID to deactivate", example = "1") @PathVariable("id")
          Integer id,
      @Parameter(description = "Optional justification for deactivation")
          @RequestParam(required = false)
          @jakarta.validation.constraints.Size(
              max = 500,
              message = "Reason must be at most 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canRevokeEnrollment(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    Integer tenantId = resolveTenantIdFromEnrollment(id);

    enrollmentRevocationService.deactivate(id, principal, reason, context, tenantId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Reactivates a previously deactivated enrollment.
   *
   * <p>Only applicable to enrollments with status {@code VERIFIED} and {@code active=false}.
   * Permanently revoked enrollments ({@code status=REVOKED}) cannot be reactivated.
   *
   * @param id the enrollment ID to reactivate
   * @param reason optional justification
   * @param httpRequest the HTTP request
   * @return 204 No Content on success
   */
  @Operation(
      summary = "Reactivate a deactivated enrollment",
      description =
          "Reactivates an enrollment that was previously deactivated via /deactivate."
              + " Only applicable to VERIFIED enrollments. Cannot reactivate REVOKED enrollments.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Enrollment reactivated successfully"),
        @ApiResponse(
            responseCode = "400",
            description = "Enrollment is already active, revoked, or not in VERIFIED status"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Enrollment not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/{id}/reactivate")
  public ResponseEntity<Void> reactivate(
      @Parameter(description = "Enrollment ID to reactivate", example = "1") @PathVariable("id")
          Integer id,
      @Parameter(description = "Optional justification for reactivation")
          @RequestParam(required = false)
          @jakarta.validation.constraints.Size(
              max = 500,
              message = "Reason must be at most 500 characters")
          String reason,
      HttpServletRequest httpRequest) {

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!accessControlService.canRevokeEnrollment(auth, id)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    ClientContext context = ClientContext.from(httpRequest);
    AdminPrincipal principal = AdminProvisioningService.extractAdminPrincipal(auth);
    Integer tenantId = resolveTenantIdFromEnrollment(id);

    enrollmentRevocationService.reactivate(id, principal, reason, context, tenantId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Extracts tenant ID from authentication context for tenant scoping.
   *
   * <p>Returns the tenant ID from AdminPrincipal if present (for TenantAdmin), or null for
   * GlobalAdmin (who can access all tenants).
   *
   * @param auth the authentication context
   * @return tenant ID if TenantAdmin, null if GlobalAdmin
   */
  private Integer extractTenantId(Authentication auth) {
    if (auth == null || auth.getPrincipal() == null) {
      return null;
    }

    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      return adminPrincipal.tenantId(); // null for GlobalAdmin, tenantId for TenantAdmin
    }

    return null;
  }

  /**
   * Resolves the tenant ID from an integration for audit log tenant association.
   *
   * <p>Loads the integration by ID and traverses to its tenant to extract the tenant ID. Returns
   * {@code null} if the integration or tenant cannot be resolved, which is safe for audit logging
   * (system-level events have {@code tenant_id = NULL}).
   *
   * @param integrationId the integration ID to resolve the tenant from
   * @return the tenant ID, or {@code null} if not resolvable
   */
  private Integer resolveTenantId(Integer integrationId) {
    if (integrationId == null) {
      return null;
    }
    return integrationRepository
        .findById(integrationId)
        .map(Integration::getTenant)
        .map(tenant -> tenant.getTenantId())
        .orElse(null);
  }

  /**
   * Resolves the tenant ID from an enrollment's integration for audit log association.
   *
   * <p>Used by the revoke/deactivate/reactivate endpoints which operate on enrollments directly.
   *
   * @param enrollmentId the enrollment ID to resolve the tenant from
   * @return the tenant ID, or {@code null} if not resolvable
   */
  private Integer resolveTenantIdFromEnrollment(Integer enrollmentId) {
    if (enrollmentId == null) {
      return null;
    }
    return enrollmentRepository
        .findById(enrollmentId)
        .map(enrollment -> resolveTenantId(enrollment.getIntegrationId()))
        .orElse(null);
  }
}
