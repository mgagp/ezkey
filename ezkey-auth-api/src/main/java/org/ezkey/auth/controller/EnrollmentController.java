/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.auth.config.TrustedProxyProperties;
import org.ezkey.auth.util.AuditHelper;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentInstanceInfoResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.dto.EnrollmentBindRequestDto;
import org.ezkey.enrollment.dto.EnrollmentBindResponseDto;
import org.ezkey.enrollment.dto.EnrollmentInstanceInfoRequestDto;
import org.ezkey.enrollment.dto.EnrollmentInstanceInfoResponseDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyRequestDto;
import org.ezkey.enrollment.dto.EnrollmentVerifyResponseDto;
import org.ezkey.enrollment.mapper.EnrollmentAuthMapper;
import org.ezkey.enrollment.service.EnrollmentInstanceInfoService;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.auth.EnrollmentAlreadyBoundException;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.ezkey.exception.auth.EnrollmentInstanceInfoFailedException;
import org.ezkey.exception.auth.EnrollmentInvitationExpiredException;
import org.ezkey.exception.auth.EnrollmentNotAvailableAfterLockException;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyStateConflictException;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.springframework.http.ProblemDetail;
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
 *   <li><b>POST /api/v1/enrollments/bind</b> - Initiate device binding using proof token payload
 *   <li><b>POST /api/v1/enrollments/verify</b> - Complete enrollment verification process
 *   <li><b>POST /api/v1/enrollments/instance-info</b> - Integration-signed installation branding
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
        "Mobile device enrollment operations for binding devices to user accounts and completing"
            + " verification")
public class EnrollmentController {

  // Rate limiting endpoint constants
  public static final String ENDPOINT_BIND = "/bind";
  public static final String ENDPOINT_VERIFY = "/verify";
  public static final String ENDPOINT_INSTANCE_INFO = "/instance-info";
  public static final String FULL_PATH_BIND = "/api/v1/enrollments" + ENDPOINT_BIND;
  public static final String FULL_PATH_VERIFY = "/api/v1/enrollments" + ENDPOINT_VERIFY;
  public static final String FULL_PATH_INSTANCE_INFO =
      "/api/v1/enrollments" + ENDPOINT_INSTANCE_INFO;

  private final EnrollmentService enrollmentService;

  private final EnrollmentInstanceInfoService enrollmentInstanceInfoService;

  private final EnrollmentAuthMapper enrollmentMapper;

  private final AuditLogService auditLogService;

  private final EnrollmentRepository enrollmentRepository;

  private final IntegrationRepository integrationRepository;

  private final EzkeyAdminRepository adminRepository;

  private final TrustedProxyProperties trustedProxyProperties;

  /**
   * Constructs the mobile enrollment controller with required dependencies.
   *
   * @param enrollmentService JPA-based enrollment service
   * @param enrollmentInstanceInfoService signed enrolled instance-info service
   * @param enrollmentMapper MapStruct mapper for entity-DTO conversions
   * @param auditLogService audit log service for security monitoring
   * @param enrollmentRepository enrollment repository for audit queries
   * @param integrationRepository integration repository for tenant resolution in audit logs
   * @param adminRepository admin repository for resolving tenant from admin MFA enrollment
   * @param trustedProxyProperties trusted proxy CIDR list for client IP resolution (may be null)
   */
  public EnrollmentController(
      EnrollmentService enrollmentService,
      EnrollmentInstanceInfoService enrollmentInstanceInfoService,
      EnrollmentAuthMapper enrollmentMapper,
      AuditLogService auditLogService,
      EnrollmentRepository enrollmentRepository,
      IntegrationRepository integrationRepository,
      EzkeyAdminRepository adminRepository,
      TrustedProxyProperties trustedProxyProperties) {
    this.enrollmentService = enrollmentService;
    this.enrollmentInstanceInfoService = enrollmentInstanceInfoService;
    this.enrollmentMapper = enrollmentMapper;
    this.auditLogService = auditLogService;
    this.enrollmentRepository = enrollmentRepository;
    this.integrationRepository = integrationRepository;
    this.adminRepository = adminRepository;
    this.trustedProxyProperties = trustedProxyProperties;
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
   * @param request the binding request containing enrollment ID and enrollment proof token
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
                            implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Enrollment already bound or proof token already used",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = ProblemDetail.class)))
      })
  public ResponseEntity<EnrollmentBindResponseDto> bind(
      @RequestBody EnrollmentBindRequestDto request, HttpServletRequest httpRequest) {

    String clientIp =
        AuditHelper.extractClientIp(
            httpRequest, trustedProxyProperties != null ? trustedProxyProperties.getCidrs() : null);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);

    // Validation: enrollmentId + enrollmentProofToken required
    if (request.enrollmentId() == null
        || request.enrollmentProofToken() == null
        || request.enrollmentProofToken().trim().isEmpty()) {

      Integer validationTenantId = resolveTenantIdForAudit(request.enrollmentId());
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_BIND)
              .eventAction("enrollment_bind_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(null)
              .integrationId(resolveIntegrationId(request.enrollmentId()))
              .tenantId(validationTenantId)
              .errorMessage("Enrollment ID and enrollment proof token are required")
              .build());

      throw new EnrollmentBindingFailedException(
          "Enrollment ID and enrollment proof token are required");
    }

    Integer auditTenantId = resolveTenantIdForAudit(request.enrollmentId());

    try {
      EnrollmentBindRequest bindRequest = enrollmentMapper.toEnrollmentBindRequest(request);
      EnrollmentBindResponse response = enrollmentService.bind(bindRequest);

      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_BIND)
              .eventAction("enrollment_bind_success")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(request.enrollmentId())
              .integrationId(resolveIntegrationId(request.enrollmentId()))
              .tenantId(auditTenantId)
              .build());

      return ResponseEntity.ok(enrollmentMapper.toEnrollmentBindResponseDto(response));
    } catch (EnrollmentBindingFailedException
        | EnrollmentAlreadyBoundException
        | EnrollmentInvitationExpiredException
        | EnrollmentNotAvailableAfterLockException e) {
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_BIND)
              .eventAction("enrollment_bind_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(request.enrollmentId())
              .integrationId(resolveIntegrationId(request.enrollmentId()))
              .tenantId(auditTenantId)
              .errorMessage(e.getMessage())
              .build());

      throw e;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_BIND)
              .eventAction("enrollment_bind_error")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(null)
              .integrationId(null)
              .tenantId(auditTenantId)
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
                            implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "409",
            description = "Enrollment state conflict or already verified",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = ProblemDetail.class)))
      })
  public ResponseEntity<EnrollmentVerifyResponseDto> verify(
      @RequestBody EnrollmentVerifyRequestDto req, HttpServletRequest httpRequest) {

    String clientIp =
        AuditHelper.extractClientIp(
            httpRequest, trustedProxyProperties != null ? trustedProxyProperties.getCidrs() : null);
    String userAgent = AuditHelper.extractUserAgent(httpRequest);
    Integer verifyTenantId = resolveTenantIdForAudit(req.enrollmentId());

    try {
      EnrollmentVerifyResponse response =
          enrollmentService.verify(enrollmentMapper.toEnrollmentVerifyRequest(req));

      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_VERIFY)
              .eventAction("enrollment_verify_success")
              .eventStatus(EventStatus.SUCCESS)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(req.enrollmentId())
              .integrationId(resolveIntegrationId(req.enrollmentId()))
              .tenantId(verifyTenantId)
              .eventDetails("Enrollment activated")
              .build());

      return ResponseEntity.ok(enrollmentMapper.toEnrollmentVerifyResponseDto(response));
    } catch (EnrollmentVerifyStateConflictException e) {
      if (e.getMessage() != null
          && e.getMessage().contains("verified enrollment with the same name")) {
        Enrollment attemptedEnrollment =
            enrollmentRepository.findById(req.enrollmentId()).orElse(null);

        Enrollment existing = null;
        if (attemptedEnrollment != null) {
          existing =
              enrollmentRepository
                  .findByIntegrationIdAndEnrollmentNameAndStatusAndEnrollmentIdNot(
                      attemptedEnrollment.getIntegrationId(),
                      attemptedEnrollment.getEnrollmentName(),
                      EnrollmentStatus.VERIFIED,
                      req.enrollmentId())
                  .stream()
                  .findFirst()
                  .orElse(null);
        }

        auditLogService.log(
            AuditLog.builder()
                .eventType(EventType.ENROLLMENT_VERIFY)
                .eventAction("enrollment_verify_failed")
                .eventStatus(EventStatus.FAILURE)
                .apiName(ApiName.AUTH_API)
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .enrollmentId(attemptedEnrollment != null ? req.enrollmentId() : null)
                .integrationId(
                    attemptedEnrollment != null ? attemptedEnrollment.getIntegrationId() : null)
                .tenantId(verifyTenantId)
                .errorMessage(e.getMessage())
                .eventDetails(
                    "Verification rejected: VERIFIED enrollment already exists. "
                        + "Attempted enrollment ID: "
                        + req.enrollmentId()
                        + ", Existing enrollment ID: "
                        + (existing != null ? existing.getEnrollmentId() : "unknown")
                        + ", Enrollment name: "
                        + (attemptedEnrollment != null
                            ? attemptedEnrollment.getEnrollmentName()
                            : "unknown"))
                .build());
      } else {
        auditLogService.log(
            AuditLog.builder()
                .eventType(EventType.ENROLLMENT_VERIFY)
                .eventAction("enrollment_verify_failed")
                .eventStatus(EventStatus.FAILURE)
                .apiName(ApiName.AUTH_API)
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .enrollmentId(null)
                .integrationId(null)
                .tenantId(verifyTenantId)
                .errorMessage(e.getMessage())
                .build());
      }

      throw e;
    } catch (EnrollmentVerifyFailedException e) {
      // Log verification failure (invalid challenge, invalid signature, duplicate device key,
      // etc.).
      // AuditLogService.log() uses REQUIRES_NEW so the audit commits independently; the service
      // already emits ENROLLMENT_EXPIRED for expired enrollments, and having both that and
      // enrollment_verify_failed for the same request is acceptable for traceability.
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_VERIFY)
              .eventAction("enrollment_verify_failed")
              .eventStatus(EventStatus.FAILURE)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(req.enrollmentId())
              .integrationId(resolveIntegrationId(req.enrollmentId()))
              .tenantId(verifyTenantId)
              .errorMessage(e.getMessage())
              .build());

      throw e;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      auditLogService.log(
          AuditLog.builder()
              .eventType(EventType.ENROLLMENT_VERIFY)
              .eventAction("enrollment_verify_error")
              .eventStatus(EventStatus.ERROR)
              .apiName(ApiName.AUTH_API)
              .ipAddress(clientIp)
              .userAgent(userAgent)
              .enrollmentId(null)
              .integrationId(null)
              .tenantId(verifyTenantId)
              .errorMessage(e.getMessage())
              .build());

      throw e;
    }
  }

  /**
   * Resolves the tenant ID for audit log association.
   *
   * <p>For enrollments that belong to an administrator, returns that admin's tenant so that tenant
   * admins see bind/verify events in their tenant's audit view. For other enrollments, falls back
   * to the integration's tenant.
   *
   * @param enrollmentId the enrollment ID to resolve the tenant from
   * @return the tenant ID for audit, or {@code null} if not resolvable
   */
  private Integer resolveTenantIdForAudit(Integer enrollmentId) {
    if (enrollmentId == null) {
      return null;
    }
    return adminRepository
        .findTenantIdByEnrollmentId(enrollmentId)
        .orElseGet(() -> resolveTenantId(enrollmentId));
  }

  /**
   * Returns integration-signed installation branding for an enrolled mobile client.
   *
   * <p>Requires the enrollment proof token only. The response is signed with the enrollment's
   * integration Ed25519 private key over the canonical {@code INSTANCE_INFO} payload. Clients must
   * verify the signature with the stored integration public key before applying branding.
   *
   * @param request request containing the enrollment proof token
   * @return signed branding with HTTP 200, or 400 for invalid proof material
   */
  @PostMapping("/instance-info")
  @Operation(
      summary = "Get integration-signed installation branding",
      description =
          "Returns instance branding signed with the enrollment integration key for enrolled"
              + " clients")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Signed installation branding returned successfully",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = EnrollmentInstanceInfoResponseDto.class))),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid or unknown enrollment proof token",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = ProblemDetail.class))),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content =
                @io.swagger.v3.oas.annotations.media.Content(
                    schema =
                        @io.swagger.v3.oas.annotations.media.Schema(
                            implementation = ProblemDetail.class)))
      })
  public ResponseEntity<EnrollmentInstanceInfoResponseDto> instanceInfo(
      @RequestBody EnrollmentInstanceInfoRequestDto request) {
    if (request == null
        || request.enrollmentProofToken() == null
        || request.enrollmentProofToken().isBlank()) {
      throw new EnrollmentInstanceInfoFailedException(
          "Enrollment proof token is required for instance-info");
    }

    EnrollmentInstanceInfoResponse response =
        enrollmentInstanceInfoService.getSignedInstanceInfo(request.enrollmentProofToken().trim());
    return ResponseEntity.ok(
        new EnrollmentInstanceInfoResponseDto(
            response.enrollmentId(),
            response.authApiPublicBaseUrl(),
            response.instanceName(),
            response.instanceDescription(),
            response.aboutUrl(),
            response.instanceInfoPayloadSignedByIntegration()));
  }

  /**
   * Resolves the tenant ID from an enrollment by traversing enrollment to integration to tenant.
   *
   * <p>Uses a scalar JPQL projection ({@code findTenantIdByIntegrationId}) so that no {@code
   * Integration} entity is loaded into the persistence context. This avoids the OEMIV-era "Found
   * shared references to a collection: Integration.i18n" hazard that arose when the
   * controller-layer entity and the service-layer entity were loaded in the same session.
   *
   * @param enrollmentId the enrollment ID to resolve the tenant from
   * @return the tenant ID, or {@code null} if not resolvable
   */
  private Integer resolveTenantId(Integer enrollmentId) {
    if (enrollmentId == null) {
      return null;
    }
    return enrollmentRepository
        .findById(enrollmentId)
        .map(Enrollment::getIntegrationId)
        .flatMap(integrationRepository::findTenantIdByIntegrationId)
        .orElse(null);
  }

  /**
   * Resolves the integration ID for an enrollment (for audit log enrichment).
   *
   * @param enrollmentId the enrollment ID
   * @return the integration ID, or null if not found
   */
  private Integer resolveIntegrationId(Integer enrollmentId) {
    if (enrollmentId == null) {
      return null;
    }
    return enrollmentRepository
        .findById(enrollmentId)
        .map(Enrollment::getIntegrationId)
        .orElse(null);
  }
}
