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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${ezkey.simulation.mode:false}")
    private boolean simulationMode;

    /**
     * Constructs the enrollment service with required dependencies.
     *
     * @param enrollmentRepository the JPA repository for enrollment operations
     * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
     * @param signatureService the cryptographic signature service
     * @param integrationRepository the JPA repository for integration operations
     */
    @Autowired
    public EnrollmentService(EnrollmentRepository enrollmentRepository,SignatureService signatureService,IntegrationRepository integrationRepository){
        this.enrollmentRepository = enrollmentRepository;
        this.signatureService = signatureService;
        this.integrationRepository = integrationRepository;
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
        enrollment.setEnrollmentRead(false);
        enrollment.setEnrollmentValid(false);
        enrollment.setEnrollmentActive(false);
        enrollment.setAuthAttemptChallengeRequired(request.getAuthAttemptChallengeRequired() != null ? request.getAuthAttemptChallengeRequired() : false);
        enrollment.setCreatedAt(LocalDateTime.now());
        RsaKeyPair integrationKeys = signatureService.generateRsaKeyPair(2048);
        enrollment.setIntegrationPrivateKey(integrationKeys.base64PrivateKey());
        enrollment.setIntegrationPublicKey(integrationKeys.base64PublicKey());
        enrollment.setDevicePublicKey(null);
        java.util.Random random = new java.util.Random();
        enrollment.setEnrollmentChallenge(100000 + random.nextInt(900000)); // 6 digits
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);

        EnrollmentCreateResponse response = new EnrollmentCreateResponse();
        response.setEnrollmentId(savedEnrollment.getEnrollmentId());
        response.setEnrollmentChallenge(savedEnrollment.getEnrollmentChallenge());
        if (simulationMode){
            RsaKeyPair deviceKeys = signatureService.generateRsaKeyPair(2048);
            response.setSimulationDevicePrivateKey(deviceKeys.base64PrivateKey());
            response.setSimulationDevicePublicKey(deviceKeys.base64PublicKey());
            String signature = signatureService.generateSignature(enrollment.getEnrollmentProofToken(),response.getSimulationDevicePrivateKey());
            response.setSimulationEnrollmentProofTokenSigned(signature);

            boolean valid = signatureService.validateSignature(enrollment.getEnrollmentProofToken(),signature,response.getSimulationDevicePublicKey());
            logger.info("Bind code signature valid: {}",valid);
        }
        return response;
    }

    /**
     * Binds an enrollment to a device.
     * <p>
     * This method handles the enrollment binding process, including signature
     * generation and simulation mode support. It uses row-level locking to ensure
     * exclusive access and prevent race conditions during the binding process.
     * </p>
     *
     * @param req the bind request
     * @return the bind response
     * @throws IllegalArgumentException if enrollment not found or already bound
     * @throws IllegalStateException if enrollment is already read by another device
     */
    public EnrollmentBindResponse bind(EnrollmentBindRequest req) {
        // Find and lock the unread enrollment atomically
        Enrollment enrollment = enrollmentRepository.findAndLockUnreadById(req.getEnrollmentId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found or already bound"));
        
        // Double-check if already read (defense in depth)
        if (Boolean.TRUE.equals(enrollment.getEnrollmentRead())) {
            throw new IllegalStateException("Enrollment already bound by a device");
        }
        
        // Mark as read (the lock ensures no race condition)
        enrollment.setEnrollmentRead(true);
        enrollmentRepository.save(enrollment);

        EnrollmentBindResponse response = new EnrollmentBindResponse();
        response.setEnrollmentId(enrollment.getEnrollmentId());
        response.setEnrollmentName(enrollment.getEnrollmentName());
        response.setIntegrationPublicKey(enrollment.getIntegrationPublicKey());
        response.setEnrollmentProofToken(enrollment.getEnrollmentProofToken());

        Optional<Integration> integrationOpt = integrationRepository.findById(enrollment.getIntegrationId());
        Integration integration = integrationOpt.get();

        integration.getI18n().stream().filter(i18n -> i18n.getLanguage().equals(req.getLanguage())).findFirst().ifPresent(i18n -> {
            response.setIntegrationName(i18n.getName());
            response.setIntegrationDescription(i18n.getDescription());
        });
        if (response.getIntegrationName() == null || response.getIntegrationDescription() == null){
            if (!integration.getI18n().isEmpty()){
                response.setIntegrationName(integration.getI18n().get(0).getName());
                response.setIntegrationDescription(integration.getI18n().get(0).getDescription());
            }
        }
        response.setIntegrationLogo(integration.getLogo());

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
        Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(request.getEnrollmentId());
        if (enrollmentOpt.isEmpty()){
            throw new IllegalArgumentException("Enrollment not found");
        }
        Enrollment enrollment = enrollmentOpt.get();
        if (!Boolean.TRUE.equals(enrollment.getEnrollmentRead())){
            enrollment.setEnrollmentVerified(true);
            enrollment.setEnrollmentValid(false);
            enrollment.setEnrollmentChallenge(null);
            enrollmentRepository.save(enrollment);
            throw new IllegalStateException("Pair must be read before verification");
        }
        if (Boolean.TRUE.equals(enrollment.getEnrollmentVerified())){
            throw new IllegalStateException("Pair already verified");
        }
        // Validate signature, this device proof token is the one obtained from the bind request
        boolean valid = signatureService.validateSignature(enrollment.getEnrollmentProofToken(),request.getEnrollmentProofTokenSigned(),request.getDevicePublicKey());
        if (!valid){
            enrollment.setEnrollmentVerified(true);
            enrollment.setEnrollmentValid(false);
            enrollment.setEnrollmentChallenge(null);
            enrollmentRepository.save(enrollment);
            throw new IllegalArgumentException("Invalid bind proof token signature");
        }
        // Validate challenge response
        if (!request.getChallengeResponse().equals(enrollment.getEnrollmentChallenge())){
            enrollment.setEnrollmentVerified(true);
            enrollment.setEnrollmentValid(false);
            enrollment.setEnrollmentChallenge(null);
            enrollmentRepository.save(enrollment);
            throw new IllegalArgumentException("Invalid challenge response");
        }
        // Confirm enrollment
        enrollment.setEnrollmentVerified(true);
        enrollment.setEnrollmentValid(true);
        enrollment.setEnrollmentActive(true);
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