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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentCreateRequest;
import org.ezkey.enrollment.domain.EnrollmentCreateResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.mapper.EnrollmentCoreMapper;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.RsaKeyPair;
import org.ezkey.signature.SignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for enrollment operations using JPA with MyBatis fallback.
 * <p>
 * This service provides enrollment management functionality using Spring Data JPA
 * as the primary data access method, with fallback to MyBatis for backward compatibility
 * during the migration period. It handles enrollment creation, binding, confirmation,
 * and various enrollment lifecycle operations.
 * </p>
 *
 * <p>
 * <b>Migration Strategy:</b>
 * <ul>
 * <li><b>Primary:</b> JPA for all new operations</li>
 * <li><b>Fallback:</b> MyBatis for AuthAttempt compatibility</li>
 * <li><b>Future:</b> Complete migration to JPA only</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Enrollment business logic and operations
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Enrollment
 * @see EnrollmentRepository
 * @see EnrollmentCoreMapper
 */
@Service
@Transactional
public class EnrollmentService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository enrollmentRepository;

    private final SignatureService signatureService;

    private final IntegrationRepository integrationRepository;

    private final EnrollmentTxHelper enrollmentTxHelper;

    /**
     * Constructs the enrollment service with required dependencies.
     *
     * @param enrollmentRepository the JPA repository for enrollment operations
     * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
     * @param signatureService the cryptographic signature service
     * @param integrationRepository the JPA repository for integration operations
     */
    @Autowired
    public EnrollmentService(EnrollmentRepository enrollmentRepository,SignatureService signatureService,IntegrationRepository integrationRepository,
            EnrollmentTxHelper enrollmentTxHelper){
        this.enrollmentRepository = enrollmentRepository;
        this.signatureService = signatureService;
        this.integrationRepository = integrationRepository;
        this.enrollmentTxHelper = enrollmentTxHelper;
    }

    /**
     * Retrieves an enrollment by its ID.
     * <p>
     * This method searches for an enrollment using its primary key and returns
     * the enrollment entity for internal use.
     * </p>
     *
     * @param id the enrollment ID to search for
     * @return the enrollment entity
     * @throws ResourceNotFoundException if the enrollment is not found
     */
    public Enrollment getById(Integer id) {
        Optional<Enrollment> enrollment = enrollmentRepository.findById(id);
        if (enrollment.isEmpty()){
            throw new ResourceNotFoundException("Enrollment",id);
        }
        return enrollment.get();
    }

    /**
     * Retrieves all enrollments.
     * <p>
     * This method returns all enrollments in the system as a list of entities.
     * </p>
     *
     * @return list of all enrollment entities
     */
    public List<Enrollment> getAll() {
        return enrollmentRepository.findAll();
    }

    /**
     * Creates a new enrollment using the new DTO format.
     * <p>
     * This method creates a new enrollment with the provided data, requests an RSA
     * key pair from the cryptographic service, and returns the new response format.
     * RSA key generation responsibility is delegated to {@link SignatureService}
     * to centralize cryptographic operations.
     * </p>
     *
     * @param request the enrollment creation request
     * @return the created enrollment response
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public EnrollmentCreateResponse create(EnrollmentCreateRequest request) {
        if (request.getIntegrationId() == null){
            throw new IllegalArgumentException("Integration ID is required");
        }
        var enrollment = new Enrollment();
        enrollment.setIntegrationId(request.getIntegrationId());
        enrollment.setEnrollmentName(request.getName().trim());
        enrollment.setEnrollmentProofToken(signatureService.generateProofToken());
        enrollment.setStatus(EnrollmentStatus.CREATED);
        enrollment.setActive(false);
        enrollment.setAuthAttemptChallengeRequired(request.getAuthAttemptChallengeRequired() != null ? request.getAuthAttemptChallengeRequired() : false);
        enrollment.setCreatedAt(LocalDateTime.now());
        RsaKeyPair integrationKeys = signatureService.generateRsaKeyPair(2048);
        enrollment.setIntegrationPrivateKey(integrationKeys.base64PrivateKey());
        enrollment.setIntegrationPublicKey(integrationKeys.base64PublicKey());
        enrollment.setDevicePublicKey(null);
        enrollment.setEnrollmentChallenge(signatureService.generateSecureChallenge(6)); // 6 digits for enrollment
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        EnrollmentCreateResponse response = new EnrollmentCreateResponse();
        response.setEnrollmentId(savedEnrollment.getEnrollmentId());
        response.setEnrollmentChallenge(savedEnrollment.getEnrollmentChallenge());
        return response;
    }

    /**
     * Binds an enrollment to a device.
     * <p>
     * This method validates the request completely before locking the enrollment
     * to ensure the read-once guarantee is maintained. The method follows the security principle
     * of validation before modification to prevent transaction rollbacks that could compromise
     * the read-once guarantee.
     * </p>
     *
     * @param req the bind request
     * @return the bind response
     * @throws IllegalArgumentException if validation fails (with secure error messages)
     * @throws IllegalStateException if the enrollment is already processed
     * @since 2025
     */
    public EnrollmentBindResponse bind(EnrollmentBindRequest req) {
        logger.info("Starting enrollment bind process for enrollment ID: {}",req.getEnrollmentId());

        // 1) Fast, read-only pre-checks (no lock yet)
        logger.debug("Step 1: Performing read-only pre-checks for enrollment ID: {}",req.getEnrollmentId());
        Enrollment snapshot = enrollmentRepository.findById(req.getEnrollmentId()).orElse(null);
        if (snapshot == null){
            logger.warn("Validation failed: Enrollment not found for ID: {}",req.getEnrollmentId());
            throw new IllegalArgumentException("Enrollment binding failed");
        }
        logger.debug("Enrollment found: ID={}, Status={}, IntegrationId={}",snapshot.getEnrollmentId(),snapshot.getStatus(),snapshot.getIntegrationId());

        // Short-circuit: if already processed, avoid acquiring a lock
        if (snapshot.getStatus() != EnrollmentStatus.CREATED){
            logger.warn("Validation failed: Enrollment already processed - ID: {}, Status: {}",snapshot.getEnrollmentId(),snapshot.getStatus());
            throw new IllegalStateException("Enrollment already bound by a device");
        }
        logger.debug("Enrollment status validation passed: Status is CREATED");

        // Load integration and resolve i18n (do it before lock to keep lock window minimal)
        logger.debug("Step 2: Loading integration for ID: {}",snapshot.getIntegrationId());
        Optional<Integration> integrationOpt = integrationRepository.findById(snapshot.getIntegrationId());
        if (integrationOpt.isEmpty()){
            logger.warn("Validation failed: Integration not found for enrollment ID: {}, Integration ID: {}",req.getEnrollmentId(),snapshot.getIntegrationId());
            throw new IllegalStateException("Enrollment binding failed");
        }
        Integration integration = integrationOpt.get();
        logger.debug("Integration loaded successfully: ID={}",integration.getId());

        String integrationName = null;
        String integrationDescription = null;
        var i18nList = integration.getI18n();
        logger.debug("Step 3: Resolving i18n for language: {}",req.getLanguage());
        if (i18nList != null){
            logger.debug("Found {} i18n entries for integration",i18nList.size());
            integration.getI18n().stream().filter(i18n -> i18n.getLanguage().equals(req.getLanguage())).findFirst().ifPresent(i18n -> {
                // capture into effectively final holders
            });
            if (integrationName == null || integrationDescription == null){
                if (!i18nList.isEmpty()){
                    integrationName = i18nList.get(0).getName();
                    integrationDescription = i18nList.get(0).getDescription();
                    logger.debug("Using fallback i18n: Name={}, Description={}",integrationName,integrationDescription);
                }
            }
        } else{
            logger.debug("No i18n entries found for integration");
        }
        // 2) Critical section: lock and re-check just what can change
        logger.debug("Step 4: Acquiring lock for enrollment ID: {}",req.getEnrollmentId());
        Enrollment enrollment = enrollmentRepository.findAndLockUnreadById(req.getEnrollmentId()).orElse(null);
        if (enrollment == null){
            logger.warn("Validation failed: Enrollment not found or already bound after lock acquisition for ID: {}",req.getEnrollmentId());
            // Either not found anymore or no longer in CREATED state (depending on the query)
            throw new IllegalArgumentException("Enrollment not found or already bound");
        }
        logger.debug("Lock acquired successfully for enrollment ID: {}",enrollment.getEnrollmentId());
        if (enrollment.getStatus() != EnrollmentStatus.CREATED){
            logger.warn("Validation failed: Enrollment already processed after lock - ID: {}, Status: {}",enrollment.getEnrollmentId(),enrollment.getStatus());
            throw new IllegalStateException("Enrollment already bound by a device");
        }
        logger.debug("Post-lock status validation passed: Status is CREATED");

        // 3) Mark as BOUND (read-once guarantee)
        logger.info("Step 5: Marking enrollment as BOUND - ID: {}",enrollment.getEnrollmentId());
        enrollment.setStatus(EnrollmentStatus.BOUND);
        enrollmentRepository.save(enrollment);
        logger.info("Enrollment successfully bound - ID: {}, Status: BOUND",enrollment.getEnrollmentId());

        // 4) Build response (use data resolved pre-lock to minimize time under lock)
        logger.debug("Step 6: Building bind response for enrollment ID: {}",enrollment.getEnrollmentId());
        EnrollmentBindResponse response = new EnrollmentBindResponse();
        response.setEnrollmentId(enrollment.getEnrollmentId());
        response.setEnrollmentName(snapshot.getEnrollmentName());
        response.setIntegrationPublicKey(snapshot.getIntegrationPublicKey());
        response.setEnrollmentProofToken(snapshot.getEnrollmentProofToken());
        response.setIntegrationLogo(integration.getLogo());
        response.setIntegrationName(integrationName);
        response.setIntegrationDescription(integrationDescription);

        logger.info("Enrollment bind process completed successfully for ID: {}",enrollment.getEnrollmentId());
        return response;
    }

    /**
     * Verifies an enrollment with comprehensive security validation.
     * <p>
     * This method handles the enrollment confirmation process with multiple layers
     * of security validation including signature verification, device public key
     * uniqueness validation, and challenge verification. It implements the same
     * security principles as AuthAttemptService to prevent replay attacks and
     * ensure enrollment integrity.
     * </p>
     *
     * <p>
     * <b>Security Validations:</b>
     * <ol>
     * <li><b>Signature Validation:</b> Verifies device cryptographic signature</li>
     * <li><b>Device Key Uniqueness:</b> Prevents replay attacks using same public key</li>
     * <li><b>Challenge Verification:</b> Validates enrollment challenge response</li>
     * <li><b>State Consistency:</b> Ensures enrollment state integrity</li>
     * <li><b>Atomic Operations:</b> Uses row-level locking for thread safety</li>
     * </ol>
     * </p>
     *
     * <p>
     * <b>Replay Attack Prevention:</b>
     * Implements device public key uniqueness validation to ensure that each
     * device public key can only be associated with one verified enrollment.
     * This prevents enrollment hijacking and maintains cryptographic identity
     * integrity across the system.
     * </p>
     *
     * @param request the verify request containing device keys and signatures
     * @return the verify response confirming successful enrollment
     * @throws IllegalArgumentException if validation fails (signature, uniqueness, or challenge)
     * @throws IllegalStateException if enrollment is in invalid state or already processed
     * @see org.ezkey.authattempt.service.AuthAttemptService#pending(org.ezkey.authattempt.domain.AuthAttemptPendingRequest)
     * @since 2025
     */
    public EnrollmentVerifyResponse verify(EnrollmentVerifyRequest request) {
        logger.info("Starting enrollment verify process for enrollment ID: {}",request.getEnrollmentId());

        // Read-only pre-checks (no lock)
        logger.debug("Step 1: Performing read-only pre-checks for enrollment ID: {}",request.getEnrollmentId());
        Enrollment snapshot = enrollmentRepository.findById(request.getEnrollmentId()).orElseThrow(() -> {
            logger.warn("Validation failed: Enrollment not found for ID: {}",request.getEnrollmentId());
            return new IllegalStateException("Enrollment already verified");
        });
        logger.debug("Enrollment found: ID={}, Status={}",snapshot.getEnrollmentId(),snapshot.getStatus());
        if (snapshot.getStatus() == EnrollmentStatus.VERIFIED){
            logger.warn("Validation failed: Enrollment already verified - ID: {}, Status: {}",snapshot.getEnrollmentId(),snapshot.getStatus());
            throw new IllegalStateException("Enrollment already verified");
        }
        logger.debug("Enrollment status validation passed: Status is not VERIFIED");
        if (snapshot.getStatus() != EnrollmentStatus.BOUND){
            logger.warn("Validation failed: Enrollment must be bound before verification - ID: {}, Status: {}",snapshot.getEnrollmentId(),snapshot.getStatus());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment must be bound before verification");
        }
        logger.debug("Enrollment status validation passed: Status is BOUND");

        logger.debug("Step 2: Validating signature for enrollment ID: {}",request.getEnrollmentId());
        boolean valid = signatureService.validateSignature(snapshot.getEnrollmentProofToken(),request.getEnrollmentProofTokenSigned(),request.getDevicePublicKey());
        if (!valid){
            logger.warn("Validation failed: Invalid bind proof token signature for enrollment ID: {}",request.getEnrollmentId());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Invalid bind proof token signature");
        }
        logger.debug("Signature validation passed for enrollment ID: {}",request.getEnrollmentId());

        logger.debug("Step 3: Validating device public key uniqueness for enrollment ID: {}",request.getEnrollmentId());
        if (enrollmentRepository.existsByDevicePublicKeyAndVerified(request.getDevicePublicKey())) {
            logger.warn("Validation failed: Device public key already used for verified enrollment - ID: {}, DevicePublicKey: {}",request.getEnrollmentId(),request.getDevicePublicKey());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Device public key already registered");
        }
        logger.debug("Device public key uniqueness validation passed for enrollment ID: {}",request.getEnrollmentId());

        logger.debug("Step 4: Validating challenge response for enrollment ID: {}",request.getEnrollmentId());
        if (!request.getChallengeResponse().equals(snapshot.getEnrollmentChallenge())){
            logger.warn("Validation failed: Invalid challenge response for enrollment ID: {} - Expected: {}, Received: {}",request.getEnrollmentId(),
                    snapshot.getEnrollmentChallenge(),request.getChallengeResponse());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Invalid challenge response");
        }
        logger.debug("Challenge response validation passed for enrollment ID: {}",request.getEnrollmentId());
        // Critical section: acquire lock and re-check before writing
        logger.debug("Step 5: Acquiring lock for enrollment ID: {}",request.getEnrollmentId());
        Enrollment enrollment = enrollmentRepository.findAndLockBoundById(request.getEnrollmentId()).orElse(null);
        if (enrollment == null){
            logger.warn("Validation failed: Enrollment not found or already verified after lock acquisition for ID: {}",request.getEnrollmentId());
            throw new IllegalStateException("Enrollment already verified");
        }
        logger.debug("Lock acquired successfully for enrollment ID: {}",enrollment.getEnrollmentId());
        if (enrollment.getStatus() != EnrollmentStatus.BOUND){
            logger.warn("Validation failed: Enrollment must be bound before verification after lock - ID: {}, Status: {}",enrollment.getEnrollmentId(),enrollment.getStatus());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment must be bound before verification");
        }
        logger.debug("Post-lock status validation passed: Status is BOUND");

        // Guard against state changes between snapshot and lock
        logger.debug("Step 6: Validating state consistency between snapshot and locked enrollment for ID: {}",request.getEnrollmentId());
        if (!snapshot.getEnrollmentProofToken().equals(enrollment.getEnrollmentProofToken())
                || !snapshot.getEnrollmentChallenge().equals(enrollment.getEnrollmentChallenge())){
            logger.warn("Validation failed: Enrollment state changed between snapshot and lock - ID: {}",request.getEnrollmentId());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment state changed");
        }
        logger.debug("State consistency validation passed for enrollment ID: {}",request.getEnrollmentId());

        // Commit verified
        logger.info("Step 7: Marking enrollment as VERIFIED - ID: {}",enrollment.getEnrollmentId());
        enrollment.setStatus(EnrollmentStatus.VERIFIED);
        enrollment.setActive(true);
        enrollment.setDevicePublicKey(request.getDevicePublicKey());
        enrollmentRepository.save(enrollment);
        logger.info("Enrollment successfully verified - ID: {}, Status: VERIFIED, Active: true",enrollment.getEnrollmentId());

        logger.debug("Step 8: Building verify response for enrollment ID: {}",enrollment.getEnrollmentId());
        EnrollmentVerifyResponse response = new EnrollmentVerifyResponse();
        response.setActive(true);

        logger.info("Enrollment verify process completed successfully for ID: {}",enrollment.getEnrollmentId());
        return response;
    }

    /**
     * Deletes an enrollment by its ID.
     * <p>
     * This method removes an enrollment from the system.
     * </p>
     *
     * @param id the enrollment ID to delete
     * @return number of rows affected
     */
    public void delete(Integer id) {
        enrollmentRepository.deleteById(id);
    }

    /**
     * Finds all enrollments for a specific integration.
     * <p>
     * This method retrieves all enrollments associated with a particular integration.
     * </p>
     *
     * @param integrationId the integration ID
     * @return list of enrollment entities
     */
    public List<Enrollment> findByIntegrationId(Integer integrationId) {
        return enrollmentRepository.findByIntegrationId(integrationId);
    }
}