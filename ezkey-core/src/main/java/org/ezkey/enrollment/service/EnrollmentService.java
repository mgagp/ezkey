/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EzkeyEnrollmentService
 * Description: Service for enrollment operations using JPA with MyBatis fallback.
 */

package org.ezkey.enrollment.service;

import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.config.EnrollmentProperties;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.ECP256KeyPair;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for managing enrollment operations in the Ezkey MFA system.
 *
 * <p>This service acts as the main coordinator for enrollment operations, delegating complex
 * business logic to specialized services while maintaining a unified public API. It implements the
 * enrollment lifecycle where devices bind to integrations and complete enrollment verification.
 *
 * <p><b>Architecture:</b> This service uses a delegation pattern to specialized services:
 *
 * <ul>
 *   <li><b>EnrollmentBindService:</b> Handles enrollment binding operations
 *   <li><b>EnrollmentVerifyService:</b> Processes enrollment verification
 *   <li><b>EnrollmentTxHelper:</b> Manages transactional operations and cleanup
 * </ul>
 *
 * <p><b>Enrollment Flow Support:</b>
 *
 * <ul>
 *   <li><b>Creation Flow:</b> Creates enrollments via Admin API for web applications
 *   <li><b>Binding Flow:</b> Processes device binding via Auth API for mobile devices
 *   <li><b>Verification Flow:</b> Completes enrollment verification with cryptographic proof
 * </ul>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li><b>Cryptographic Validation:</b> Verifies device and integration signatures using Ed25519
 *   <li><b>Enrollment Proof Tokens:</b> Prevents enumeration attacks through secure token-based
 *       identification
 *   <li><b>Device Key Uniqueness:</b> Ensures device public keys are used only once
 *   <li><b>Challenge-Based Security:</b> Implements numeric challenges for additional verification
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * with appropriate propagation settings. Specialized services handle their own transaction
 * boundaries as needed.
 *
 * <p><b>Integration Points:</b>
 *
 * <ul>
 *   <li><b>EnrollmentRepository:</b> Data persistence layer for enrollments
 *   <li><b>SignatureService:</b> Cryptographic operations for signature validation
 *   <li><b>EnrollmentBindService:</b> Specialized service for enrollment binding operations
 *   <li><b>EnrollmentVerifyService:</b> Specialized service for enrollment verification operations
 * </ul>
 *
 * <p><b>Error Handling:</b> Implements comprehensive error handling with secure error messages to
 * prevent information leakage while providing detailed logging for debugging and monitoring
 * purposes.
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li><b>Read-Once Guarantee:</b> Enrollments can only be bound once to prevent replay attacks
 *   <li><b>Efficient Locking:</b> Optimized database queries for enrollment state management
 *   <li><b>Timeout Management:</b> Configurable timeouts prevent resource exhaustion
 *   <li><b>Service Separation:</b> Specialized services allow for targeted optimization
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Enrollment
 * @see EnrollmentCreateRequest
 * @see EnrollmentBindRequest
 * @see EnrollmentVerifyRequest
 * @see SignatureService
 * @see EnrollmentBindService
 * @see EnrollmentVerifyService
 * @see EnrollmentTxHelper
 */
@Service
public class EnrollmentService {

  private static final Logger logger = LoggerFactory.getLogger(EnrollmentService.class);

  private final EnrollmentRepository enrollmentRepository;
  private final SignatureService signatureService;
  private final EzkeyCoreProperties ezkeyCoreProperties;
  private final EnrollmentProperties enrollmentProperties;
  private final IntegrationRepository integrationRepository;

  // Specialized services for specific operations
  private final EnrollmentBindService bindService;
  private final EnrollmentVerifyService verifyService;

  /**
   * Constructs the enrollment service with required dependencies.
   *
   * @param enrollmentRepository the JPA repository for enrollment operations
   * @param signatureService the cryptographic signature service
   * @param ezkeyCoreProperties the ezkey core configuration properties
   * @param enrollmentProperties the enrollment expiration and cleanup configuration
   * @param integrationRepository the JPA repository for integration operations
   * @param bindService the specialized service for binding operations
   * @param verifyService the specialized service for verification operations
   */
  public EnrollmentService(
      EnrollmentRepository enrollmentRepository,
      SignatureService signatureService,
      EzkeyCoreProperties ezkeyCoreProperties,
      EnrollmentProperties enrollmentProperties,
      IntegrationRepository integrationRepository,
      EnrollmentBindService bindService,
      EnrollmentVerifyService verifyService) {
    this.enrollmentRepository = enrollmentRepository;
    this.signatureService = signatureService;
    this.ezkeyCoreProperties = ezkeyCoreProperties;
    this.enrollmentProperties = enrollmentProperties;
    this.integrationRepository = integrationRepository;
    this.bindService = bindService;
    this.verifyService = verifyService;
  }

  /**
   * Retrieves an enrollment by its ID.
   *
   * <p>This method searches for an enrollment using its primary key and returns the enrollment
   * entity for internal use.
   *
   * @param id the enrollment ID to search for
   * @return the enrollment entity
   * @throws ResourceNotFoundException if the enrollment is not found
   */
  @Transactional(readOnly = true)
  public Enrollment getById(Integer id) {
    Optional<Enrollment> enrollment = enrollmentRepository.findById(id);
    if (enrollment.isEmpty()) {
      throw new ResourceNotFoundException("Enrollment", id);
    }
    return enrollment.get();
  }

  /**
   * Retrieves all enrollments.
   *
   * <p>This method returns all enrollments in the system as a list of entities.
   *
   * @return list of all enrollment entities
   */
  public List<Enrollment> getAll() {
    return enrollmentRepository.findAll();
  }

  /**
   * Searches enrollments with optional filters and pagination.
   *
   * <p>This method supports multi-criteria search for administrative and operational purposes. All
   * filter parameters are optional - if null, they are ignored in the query. Results are ordered by
   * creation date descending (newest first) by default.
   *
   * <p><b>Tenant Scoping:</b> If tenantId is provided (non-null), results are filtered to only
   * include enrollments whose integration belongs to that tenant. This enables tenant isolation for
   * TenantAdmins while allowing GlobalAdmins to see all enrollments (by passing null).
   *
   * <p><b>Use Case:</b> Security operators monitoring enrollments, forensic analysis, and
   * compliance reporting.
   *
   * @param status optional enrollment status filter (CREATED, BOUND, VERIFIED, INVALID)
   * @param integrationId optional integration ID filter
   * @param enrollmentName optional enrollment name filter (partial match, case-insensitive)
   * @param active optional active flag filter
   * @param createdAfter optional start of date range filter
   * @param createdBefore optional end of date range filter
   * @param tenantId optional tenant ID filter for tenant scoping (null = all tenants, for
   *     GlobalAdmin)
   * @param pageable pagination and sorting parameters
   * @return page of enrollments matching criteria
   */
  @Transactional(readOnly = true)
  public Page<Enrollment> findByFilters(
      EnrollmentStatus status,
      Integer integrationId,
      String enrollmentName,
      Boolean active,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      Integer tenantId,
      Pageable pageable) {

    Specification<Enrollment> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (status != null) {
            predicates.add(cb.equal(root.get("status"), status));
          }

          if (integrationId != null) {
            predicates.add(cb.equal(root.get("integrationId"), integrationId));
          }

          if (enrollmentName != null && !enrollmentName.isBlank()) {
            predicates.add(
                cb.like(
                    cb.lower(root.get("enrollmentName")),
                    "%" + enrollmentName.toLowerCase() + "%"));
          }

          if (active != null) {
            predicates.add(cb.equal(root.get("active"), active));
          }

          if (createdAfter != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
          }

          if (createdBefore != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
          }

          // Tenant scoping: filter by integration's tenant if tenantId is provided
          // Since Enrollment doesn't have a direct JPA relation to Integration, we use a
          // subquery
          // to check if the enrollment's integrationId belongs to an integration with the
          // specified
          // tenantId
          if (tenantId != null) {
            var subquery = query.subquery(Integer.class);
            var integrationRoot = subquery.from(Integration.class);
            subquery.select(integrationRoot.get("id"));
            subquery.where(
                cb.equal(integrationRoot.get("tenant").get("tenantId"), tenantId),
                cb.equal(integrationRoot.get("id"), root.get("integrationId")));
            predicates.add(cb.exists(subquery));
          }

          // Apply default sort only if pageable is unsorted
          if (pageable.getSort().isUnsorted()) {
            query.orderBy(cb.desc(root.get("createdAt")));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return enrollmentRepository.findAll(spec, pageable);
  }

  /**
   * Creates a new enrollment using the new DTO format.
   *
   * <p>This method creates a new enrollment with the provided data, requests an Ed25519 key pair
   * from the cryptographic service, and returns the new response format. Ed25519 key generation
   * responsibility is delegated to {@link SignatureService} to centralize cryptographic operations.
   *
   * <p><b>Security:</b> This method blocks enrollment creation for system integrations. System
   * integrations are reserved for global admin authentication and enrollments can only be created
   * through the admin provisioning API endpoints.
   *
   * @param request the enrollment creation request
   * @return the created enrollment response
   * @throws IllegalArgumentException if required fields are missing or invalid, or if attempting to
   *     create enrollment for a system integration
   */
  public EnrollmentCreateResponse create(EnrollmentCreateRequest request) {
    if (request.getIntegrationId() == null) {
      throw new IllegalArgumentException("Integration ID is required");
    }
    if (request.getName() == null || request.getName().trim().isEmpty()) {
      throw new IllegalArgumentException("Enrollment name is required");
    }

    // Security: Block enrollment creation for system integrations
    // System integrations are reserved for global admin authentication and
    // enrollments
    // can only be created through the admin provisioning API endpoints
    Integration integration =
        integrationRepository
            .findById(request.getIntegrationId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Integration not found: " + request.getIntegrationId()));

    if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
      logger.warn(
          "Attempt to create enrollment for system integration (ID: {}) blocked. "
              + "System integrations are reserved for global admin authentication.",
          request.getIntegrationId());
      throw new IllegalArgumentException(
          "Cannot create enrollment for system integration. System integrations are reserved for"
              + " global admin authentication and enrollments can only be created through the admin"
              + " provisioning API endpoints.");
    }

    // Security: Block enrollment creation for inactive tenants
    if (integration.getTenant() != null && !integration.getTenant().getActive()) {
      logger.warn(
          "Enrollment creation blocked: tenant (ID: {}) is inactive for integration {}",
          integration.getTenant().getTenantId(),
          request.getIntegrationId());
      throw new IllegalStateException(
          "Cannot create enrollment for inactive tenant. Contact your Ezkey administrator.");
    }

    // Security validation: Check for existing VERIFIED enrollment
    List<Enrollment> existingVerifiedEnrollments;
    existingVerifiedEnrollments =
        enrollmentRepository.findByIntegrationIdAndEnrollmentNameAndStatus(
            request.getIntegrationId(), request.getName().trim(), EnrollmentStatus.VERIFIED);

    if (!existingVerifiedEnrollments.isEmpty()) {
      Enrollment existing = existingVerifiedEnrollments.get(0);

      // If VERIFIED enrollment is active, reject creation
      if (Boolean.TRUE.equals(existing.getActive())) {
        logger.warn(
            "Enrollment creation rejected: Active VERIFIED enrollment {} (ID: {}) already exists"
                + " for integration {} and name '{}'. Use recovery process"
                + " (/api/v1/admin/auth/recover + /api/v1/admin/enrollments/reset) to replace"
                + " enrollment.",
            existing.getEnrollmentName(),
            existing.getEnrollmentId(),
            request.getIntegrationId(),
            request.getName());
        throw new IllegalArgumentException(
            "An active verified enrollment with the same name already exists for this integration."
                + " To replace an enrollment, use the recovery process: POST"
                + " /api/v1/admin/auth/recover with a recovery code, then POST"
                + " /api/v1/admin/enrollments/reset to reset the existing enrollment.");
      }

      // If VERIFIED enrollment is inactive, allow creation (admin has deactivated it)
      logger.info(
          "Enrollment creation allowed: Inactive VERIFIED enrollment {} (ID: {}) exists. "
              + "Creating new enrollment for replacement.",
          existing.getEnrollmentName(),
          existing.getEnrollmentId());
    }

    var enrollment = new Enrollment();
    enrollment.setIntegrationId(request.getIntegrationId());
    enrollment.setEnrollmentName(request.getName().trim());
    enrollment.setEnrollmentProofToken(signatureService.generateProofToken());
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(false);
    enrollment.setAuthAttemptChallengeRequired(
        request.getAuthAttemptChallengeRequired() != null
            ? request.getAuthAttemptChallengeRequired()
            : false);
    OffsetDateTime createdAt = OffsetDateTime.now();
    enrollment.setCreatedAt(createdAt);
    if (enrollmentProperties.getPendingExpirationDays() != null
        && enrollmentProperties.getPendingExpirationDays() > 0) {
      enrollment.setExpiresAt(createdAt.plusDays(enrollmentProperties.getPendingExpirationDays()));
    }
    enrollment.setContactEmail(
        request.getContactEmail() != null && !request.getContactEmail().isBlank()
            ? request.getContactEmail().trim()
            : null);
    enrollment.setUserIdentifier(
        request.getUserIdentifier() != null && !request.getUserIdentifier().isBlank()
            ? request.getUserIdentifier().trim()
            : null);
    enrollment.setCreatedByAdminId(request.getCreatedByAdminId());
    ECP256KeyPair integrationKeys = signatureService.generateECP256KeyPair();
    enrollment.setIntegrationPrivateKey(integrationKeys.base64PrivateKey());
    enrollment.setIntegrationPublicKey(integrationKeys.base64PublicKey());
    enrollment.setDevicePublicKey(null);
    enrollment.setEnrollmentChallenge(
        signatureService.generateSecureChallenge(6)); // 6 digits for enrollment
    Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

    EnrollmentCreateResponse response = new EnrollmentCreateResponse();
    response.setEnrollmentId(savedEnrollment.getEnrollmentId());
    response.setEnrollmentChallenge(savedEnrollment.getEnrollmentChallenge());
    return response;
  }

  /**
   * Binds an enrollment to a device.
   *
   * <p>This method delegates to the specialized EnrollmentBindService to handle the complex logic
   * of enrollment binding while maintaining the same public API for backward compatibility.
   *
   * @param request the bind request
   * @return the bind response
   * @throws IllegalArgumentException if validation fails (with secure error messages)
   * @throws IllegalStateException if the enrollment is already processed
   */
  public EnrollmentBindResponse bind(EnrollmentBindRequest request) {
    return bindService.bind(request);
  }

  /**
   * Verifies an enrollment with comprehensive security validation.
   *
   * <p>This method delegates to the specialized EnrollmentVerifyService to handle the complex logic
   * of enrollment verification while maintaining the same public API for backward compatibility.
   *
   * @param request the verify request containing device keys and signatures
   * @return the verify response confirming successful enrollment
   * @throws IllegalArgumentException if validation fails (signature, uniqueness, or challenge)
   * @throws IllegalStateException if enrollment is in invalid state or already processed
   */
  public EnrollmentVerifyResponse verify(EnrollmentVerifyRequest request) {
    return verifyService.verify(request);
  }

  /**
   * Deletes an enrollment by its ID.
   *
   * <p>This method removes an enrollment from the system.
   *
   * @param id the enrollment ID to delete
   * @return number of rows affected
   */
  public void delete(Integer id) {
    enrollmentRepository.deleteById(id);
  }

  /**
   * Finds all enrollments for a specific integration.
   *
   * <p>This method retrieves all enrollments associated with a particular integration.
   *
   * @param integrationId the integration ID
   * @return list of enrollment entities
   */
  public List<Enrollment> findByIntegrationId(Integer integrationId) {
    return enrollmentRepository.findByIntegrationId(integrationId);
  }
}
