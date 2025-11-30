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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
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
import org.ezkey.signature.Ed25519KeyPair;
import org.ezkey.signature.SignatureService;
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

  private final EnrollmentRepository enrollmentRepository;
  private final SignatureService signatureService;
  private final EzkeyCoreProperties ezkeyCoreProperties;

  // Specialized services for specific operations
  private final EnrollmentBindService bindService;
  private final EnrollmentVerifyService verifyService;

  /**
   * Constructs the enrollment service with required dependencies.
   *
   * @param enrollmentRepository the JPA repository for enrollment operations
   * @param signatureService the cryptographic signature service
   * @param ezkeyCoreProperties the ezkey core configuration properties
   * @param bindService the specialized service for binding operations
   * @param verifyService the specialized service for verification operations
   */
  public EnrollmentService(
      EnrollmentRepository enrollmentRepository,
      SignatureService signatureService,
      EzkeyCoreProperties ezkeyCoreProperties,
      EnrollmentBindService bindService,
      EnrollmentVerifyService verifyService) {
    this.enrollmentRepository = enrollmentRepository;
    this.signatureService = signatureService;
    this.ezkeyCoreProperties = ezkeyCoreProperties;
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
   * Creates a new enrollment using the new DTO format.
   *
   * <p>This method creates a new enrollment with the provided data, requests an Ed25519 key pair
   * from the cryptographic service, and returns the new response format. Ed25519 key generation
   * responsibility is delegated to {@link SignatureService} to centralize cryptographic
   * operations.
   *
   * @param request the enrollment creation request
   * @return the created enrollment response
   * @throws IllegalArgumentException if required fields are missing or invalid
   */
  public EnrollmentCreateResponse create(EnrollmentCreateRequest request) {
    if (request.getIntegrationId() == null) {
      throw new IllegalArgumentException("Integration ID is required");
    }
    if (request.getName() == null || request.getName().trim().isEmpty()) {
      throw new IllegalArgumentException("Enrollment name is required");
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
    enrollment.setCreatedAt(OffsetDateTime.now());
    Ed25519KeyPair integrationKeys = signatureService.generateEd25519KeyPair();
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
