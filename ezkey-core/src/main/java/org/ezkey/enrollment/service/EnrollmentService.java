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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    private final EnrollmentCoreMapper enrollmentMapper;

    private final SignatureService signatureService;

    @Value("${ezkey.simulation.mode:false}")
    private boolean simulationMode;

    /**
     * Constructs the enrollment service with required dependencies.
     *
     * @param enrollmentRepository the JPA repository for enrollment operations
     * @param enrollmentMapper the MapStruct mapper for entity-DTO conversions
     * @param signatureService the cryptographic signature service
     */
    @Autowired
    public EnrollmentService(EnrollmentRepository enrollmentRepository,EnrollmentCoreMapper enrollmentMapper,SignatureService signatureService){
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentMapper = enrollmentMapper;
        this.signatureService = signatureService;
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
     * This method creates a new enrollment with the provided data, generates
     * cryptographic keys, and returns the new response format.
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
        enrollment.setDeviceProofToken(UUID.randomUUID().toString());
        enrollment.setEnrollmentRead(false);
        enrollment.setEnrollmentVerified(false);
        enrollment.setEnrollmentActive(false);
        enrollment.setAuthAttemptChallengeRequired(false);
        enrollment.setCreatedAt(LocalDateTime.now());
        try{
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair kp = keyGen.generateKeyPair();
            enrollment.setIntegrationPrivateKey(java.util.Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
            enrollment.setIntegrationPublicKey(java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
        } catch (NoSuchAlgorithmException e){
            throw new RuntimeException("Key generation failed",e);
        }
        enrollment.setDevicePublicKey(null);
        java.util.Random random = new java.util.Random();
        enrollment.setEnrollmentChallenge(100000 + random.nextInt(900000)); // 6 digits
        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);
        return enrollmentMapper.toCreateResponse(savedEnrollment);
    }

    /**
     * Binds an enrollment to a device.
     * <p>
     * This method handles the enrollment binding process, including signature
     * generation and simulation mode support.
     * </p>
     *
     * @param req the bind request
     * @return the bind response
     * @throws IllegalArgumentException if enrollment not found or already bound
     */
    public EnrollmentBindResponse bind(EnrollmentBindRequest req) {
        Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(req.getEnrollmentId());
        if (enrollmentOpt.isEmpty()){
            throw new IllegalArgumentException("Pair not found");
        }
        Enrollment enrollment = enrollmentOpt.get();
        if (Boolean.TRUE.equals(enrollment.getEnrollmentRead())){
            throw new IllegalStateException("Pair already bound by a device");
        }
        enrollmentRepository.setDeviceReadTrue(req.getEnrollmentId());

        EnrollmentBindResponse response = new EnrollmentBindResponse();
        response.setEnrollmentId(enrollment.getEnrollmentId());
        response.setIntegrationPublicKey(enrollment.getIntegrationPublicKey());
        response.setDeviceProofToken(enrollment.getDeviceProofToken());
        response.setDeviceProofTokenSignedByIntegration(signatureService.generateSignature(enrollment.getDeviceProofToken(),enrollment.getIntegrationPrivateKey()));
        if (simulationMode){
            try{
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                KeyPair kp = keyGen.generateKeyPair();
                response.setSimulationDevicePrivateKey(java.util.Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()));
                response.setSimulationDevicePublicKey(java.util.Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
            } catch (NoSuchAlgorithmException e){
                throw new RuntimeException("Key generation failed",e);
            }
            String signature = signatureService.generateSignature(enrollment.getDeviceProofToken(),response.getSimulationDevicePrivateKey());
            response.setSimulationDeviceProofTokenSigned(signature);

            boolean valid = signatureService.validateSignature(enrollment.getDeviceProofToken(),signature,response.getSimulationDevicePublicKey());
            logger.info("Bind code signature valid: {}",valid);
        }
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
            throw new IllegalArgumentException("Pair not found");
        }
        Enrollment enrollment = enrollmentOpt.get();
        if (!Boolean.TRUE.equals(enrollment.getEnrollmentRead())){
            throw new IllegalStateException("Pair must be read before confirmation");
        }
        if (Boolean.TRUE.equals(enrollment.getEnrollmentVerified())){
            throw new IllegalStateException("Pair already verified");
        }
        if (Boolean.TRUE.equals(enrollment.getEnrollmentActive())){
            throw new IllegalStateException("Pair already active");
        }
        // Validate signature, this device proof token is the one obtained from the bind request
        boolean valid = request.getDeviceProofTokenSigned() != null
                && signatureService.validateSignature(enrollment.getDeviceProofToken(),request.getDeviceProofTokenSigned(),request.getDevicePublicKey());
        if (!valid){
            enrollment.setEnrollmentVerified(false);
            enrollment.setEnrollmentChallenge(null);
            enrollmentRepository.save(enrollment);
            throw new IllegalArgumentException("Invalid bind code signature");
        }
        // Validate challenge response
        if (!request.getChallengeResponse().equals(enrollment.getEnrollmentChallenge())){
            enrollment.setEnrollmentVerified(false);
            enrollment.setEnrollmentChallenge(null);
            enrollmentRepository.save(enrollment);
            throw new IllegalArgumentException("Invalid challenge response");
        }
        // Confirm enrollment
        enrollment.setEnrollmentVerified(true);
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
    public int delete(Integer id) {
        if (!enrollmentRepository.existsById(id)){
            return 0;
        }
        enrollmentRepository.deleteById(id);
        return 1;
    }

    /**
     * Generates a new UUID v4 for enrollment codes.
     * <p>
     * This method provides a utility for generating unique enrollment identifiers.
     * </p>
     *
     * @return a new UUID v4 string
     */
    public String generateUuidV4() {
        return UUID.randomUUID().toString();
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