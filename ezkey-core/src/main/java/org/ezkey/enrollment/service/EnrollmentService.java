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

import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.dto.EnrollmentBindRequest;
import org.ezkey.enrollment.dto.EnrollmentBindResponse;
import org.ezkey.enrollment.dto.EnrollmentConfirmRequest;
import org.ezkey.enrollment.dto.EnrollmentConfirmResponse;
import org.ezkey.enrollment.dto.request.EnrollmentCreateRequest;
import org.ezkey.enrollment.dto.response.EnrollmentCreateResponse;
import org.ezkey.enrollment.mapper.EnrollmentMapper;
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
 * @see EnrollmentMapper
 */
@Service
@Transactional
public class EnrollmentService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(EnrollmentService.class);

    private final EnrollmentRepository enrollmentRepository;

    private final EnrollmentMapper enrollmentMapper;

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
    public EnrollmentService(EnrollmentRepository enrollmentRepository,EnrollmentMapper enrollmentMapper,SignatureService signatureService){
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
    public Enrollment getById(Integer id){
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
    public List<Enrollment> getAll(){
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
    public EnrollmentCreateResponse create(EnrollmentCreateRequest request){
        // Validate required fields
        if (request.getIntegrationId() == null){
            throw new IllegalArgumentException("Integration ID is required");
        }

        var enrollment = new Enrollment();
        enrollment.setIntegrationId(request.getIntegrationId());
        enrollment.setEnrollmentName(request.getName().trim());
        enrollment.setEnrollmentCode(UUID.randomUUID().toString());
        enrollment.setEnrollmentRead(false);
        enrollment.setEnrollmentConfirmed(false);
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

        enrollment.setAuthAttemptPublicKey(null);
        java.util.Random random = new java.util.Random();
        enrollment.setEnrollmentChallenge(100000 + random.nextInt(900000)); // 6 digits

        Enrollment savedEnrollment = enrollmentRepository.save(enrollment);
        return enrollmentMapper.toCreateResponse(savedEnrollment);
    }

    /**
     * Updates an existing enrollment.
     * <p>
     * This method updates an enrollment with new data and persists the changes.
     * </p>
     *
     * @param enrollment the enrollment entity to update
     * @return number of rows affected
     */
    public int update(Enrollment enrollment){
        enrollmentRepository.save(enrollment);
        return 1; // JPA save returns the entity, so we return 1 for compatibility
    }

    /**
     * Marks an enrollment as read by the device.
     * <p>
     * This method updates the enrollment_read flag to true, indicating
     * that the enrollment has been read by a device.
     * </p>
     *
     * @param id the enrollment ID to mark as read
     * @return number of rows affected
     */
    public int setDeviceReadTrue(Integer id){
        return enrollmentRepository.setDeviceReadTrue(id);
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
    public EnrollmentBindResponse bind(EnrollmentBindRequest req){
        Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(req.getId());
        if (enrollmentOpt.isEmpty()){
            throw new IllegalArgumentException("Pair not found");
        }

        Enrollment enrollment = enrollmentOpt.get();
        if (Boolean.TRUE.equals(enrollment.getEnrollmentRead())){
            throw new IllegalStateException("Pair already bound by a device");
        }

        enrollmentRepository.setDeviceReadTrue(req.getId());

        EnrollmentBindResponse response = new EnrollmentBindResponse();
        response.setEnrollmentId(enrollment.getEnrollmentId());
        response.setIntegrationPublicKey(enrollment.getIntegrationPublicKey());
        response.setEnrollmentCode(enrollment.getEnrollmentCode());
        response.setEnrollmentCodeSigned(signatureService.generateSignature(enrollment.getEnrollmentCode(),enrollment.getIntegrationPrivateKey()));

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
            String signature = signatureService.generateSignature(enrollment.getEnrollmentCode(),response.getSimulationDevicePrivateKey());
            response.setSimulationEnrollmentCodeSigned(signature);

            boolean valid = signatureService.validateSignature(enrollment.getEnrollmentCode(),signature,response.getSimulationDevicePublicKey());
            logger.info("Bind code signature valid: {}",valid);
        }

        return response;
    }

    /**
     * Confirms an enrollment.
     * <p>
     * This method handles the enrollment confirmation process, including
     * signature validation and challenge verification.
     * </p>
     *
     * @param req the confirm request
     * @return the confirm response
     * @throws IllegalArgumentException if enrollment not found or validation fails
     * @throws IllegalStateException if enrollment is in invalid state
     */
    public EnrollmentConfirmResponse confirm(EnrollmentConfirmRequest req){
        Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(req.getEnrollmentId());
        if (enrollmentOpt.isEmpty()){
            throw new IllegalArgumentException("Pair not found");
        }

        Enrollment enrollment = enrollmentOpt.get();
        if (!Boolean.TRUE.equals(enrollment.getEnrollmentRead())){
            throw new IllegalStateException("Pair must be read before confirmation");
        }
        if (Boolean.TRUE.equals(enrollment.getEnrollmentConfirmed())){
            throw new IllegalStateException("Pair already confirmed");
        }
        if (Boolean.TRUE.equals(enrollment.getEnrollmentActive())){
            throw new IllegalStateException("Pair already active");
        }

        // Validate signature
        boolean valid = req.getEnrollmentCodeSigned() != null
                && signatureService.validateSignature(enrollment.getEnrollmentCode(),req.getEnrollmentCodeSigned(),req.getDevicePublicKey());
        if (!valid){
            enrollment.setEnrollmentConfirmed(false);
            enrollment.setEnrollmentChallenge(null);
            enrollmentRepository.save(enrollment);
            throw new IllegalArgumentException("Invalid bind code signature");
        }

        // Validate challenge response
        if (!req.getChallengeResponse().equals(enrollment.getEnrollmentChallenge())){
            enrollment.setEnrollmentConfirmed(false);
            enrollment.setEnrollmentChallenge(null);
            enrollmentRepository.save(enrollment);
            throw new IllegalArgumentException("Invalid challenge response");
        }

        // Confirm enrollment
        enrollment.setEnrollmentConfirmed(true);
        enrollment.setEnrollmentActive(true);
        enrollment.setAuthAttemptPublicKey(req.getDevicePublicKey());
        enrollmentRepository.save(enrollment);

        EnrollmentConfirmResponse response = new EnrollmentConfirmResponse();
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
    public int delete(Integer id){
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
    public String generateUuidV4(){
        return UUID.randomUUID().toString();
    }

    /**
     * Finds an enrollment by its unique enrollment code.
     * <p>
     * This method is used for enrollment verification and lookup operations.
     * </p>
     *
     * @param enrollmentCode the unique enrollment code
     * @return the enrollment entity
     * @throws ResourceNotFoundException if the enrollment is not found
     */
    public Enrollment findByEnrollmentCode(String enrollmentCode){
        Optional<Enrollment> enrollment = enrollmentRepository.findByEnrollmentCode(enrollmentCode);
        if (enrollment.isEmpty()){
            throw new ResourceNotFoundException("Enrollment",enrollmentCode);
        }
        return enrollment.get();
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
    public List<Enrollment> findByIntegrationId(Integer integrationId){
        return enrollmentRepository.findByIntegrationId(integrationId);
    }
}