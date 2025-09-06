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
        // 1) Fast, read‑only pre‑checks (no lock yet)
        Enrollment snapshot = enrollmentRepository.findById(req.getEnrollmentId()).orElse(null);
        if (snapshot == null){
            logger.warn("Enrollment not found for ID: {}",req.getEnrollmentId());
            throw new IllegalArgumentException("Enrollment binding failed");
        }
        // Short‑circuit: if already processed, avoid acquiring a lock
        if (snapshot.getStatus() != EnrollmentStatus.CREATED){
            logger.warn("Enrollment already processed: {}",snapshot.getEnrollmentId());
            throw new IllegalStateException("Enrollment already bound by a device");
        }
        // Load integration and resolve i18n (do it before lock to keep lock window minimal)
        Optional<Integration> integrationOpt = integrationRepository.findById(snapshot.getIntegrationId());
        if (integrationOpt.isEmpty()){
            logger.warn("Integration not found for enrollment: {}",req.getEnrollmentId());
            throw new IllegalStateException("Enrollment binding failed");
        }
        Integration integration = integrationOpt.get();

        String integrationName = null;
        String integrationDescription = null;
        var i18nList = integration.getI18n();
        if (i18nList != null){
            integration.getI18n().stream().filter(i18n -> i18n.getLanguage().equals(req.getLanguage())).findFirst().ifPresent(i18n -> {
                // capture into effectively final holders
            });
            if (integrationName == null || integrationDescription == null){
                if (!i18nList.isEmpty()){
                    integrationName = i18nList.get(0).getName();
                    integrationDescription = i18nList.get(0).getDescription();
                }
            }
        }
        // 2) Critical section: lock and re‑check just what can change
        Enrollment enrollment = enrollmentRepository.findAndLockUnreadById(req.getEnrollmentId()).orElse(null);
        if (enrollment == null){
            // Either not found anymore or no longer in CREATED state (depending on the query)
            throw new IllegalArgumentException("Enrollment not found or already bound");
        }
        if (enrollment.getStatus() != EnrollmentStatus.CREATED){
            logger.warn("Enrollment already processed after lock: {}",enrollment.getEnrollmentId());
            throw new IllegalStateException("Enrollment already bound by a device");
        }
        // 3) Mark as BOUND (read‑once guarantee)
        enrollment.setStatus(EnrollmentStatus.BOUND);
        enrollmentRepository.save(enrollment);

        // 4) Build response (use data resolved pre‑lock to minimize time under lock)
        EnrollmentBindResponse response = new EnrollmentBindResponse();
        response.setEnrollmentId(enrollment.getEnrollmentId());
        response.setEnrollmentName(snapshot.getEnrollmentName());
        response.setIntegrationPublicKey(snapshot.getIntegrationPublicKey());
        response.setEnrollmentProofToken(snapshot.getEnrollmentProofToken());
        response.setIntegrationLogo(integration.getLogo());
        response.setIntegrationName(integrationName);
        response.setIntegrationDescription(integrationDescription);

        return response;
    }

    /**
     * Verifies an enrollment.
     * <p>
     * This method handles the enrollment confirmation process, including
     * signature validation and challenge verification.
     * </p>
     *
     * @param request the verify request
     * @return the verify response
     * @throws IllegalArgumentException if enrollment not found or validation fails
     * @throws IllegalStateException if enrollment is in invalid state
     */
    public EnrollmentVerifyResponse verify(EnrollmentVerifyRequest request) {
        // Read-only pre-checks (no lock)
        Enrollment snapshot = enrollmentRepository.findById(request.getEnrollmentId()).orElseThrow(() -> new IllegalStateException("Enrollment already verified"));
        if (snapshot.getStatus() == EnrollmentStatus.VERIFIED){
            throw new IllegalStateException("Enrollment already verified");
        }
        if (snapshot.getStatus() != EnrollmentStatus.BOUND){
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment must be bound before verification");
        }
        boolean valid = signatureService.validateSignature(snapshot.getEnrollmentProofToken(),request.getEnrollmentProofTokenSigned(),request.getDevicePublicKey());
        if (!valid){
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Invalid bind proof token signature");
        }
        if (!request.getChallengeResponse().equals(snapshot.getEnrollmentChallenge())){
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Invalid challenge response");
        }
        // Critical section: acquire lock and re-check before writing
        Enrollment enrollment = enrollmentRepository.findAndLockBoundById(request.getEnrollmentId()).orElse(null);
        if (enrollment == null){
            throw new IllegalStateException("Enrollment already verified");
        }
        if (enrollment.getStatus() != EnrollmentStatus.BOUND){
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment must be bound before verification");
        }
        // Guard against state changes between snapshot and lock
        if (!snapshot.getEnrollmentProofToken().equals(enrollment.getEnrollmentProofToken())
                || !snapshot.getEnrollmentChallenge().equals(enrollment.getEnrollmentChallenge())){
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment state changed");
        }
        // Commit verified
        enrollment.setStatus(EnrollmentStatus.VERIFIED);
        enrollment.setActive(true);
        enrollment.setDevicePublicKey(request.getDevicePublicKey());
        enrollmentRepository.save(enrollment);

        EnrollmentVerifyResponse response = new EnrollmentVerifyResponse();
        response.setActive(true);
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