/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EzkeyAuthAttemptService
 * Description: JPA-based service for authorization attempt business logic.
 */

package org.ezkey.authattempt.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.dto.AuthAttemptCompleteRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptCompleteResponseDto;
import org.ezkey.authattempt.dto.AuthAttemptCreateDtoRequest;
import org.ezkey.authattempt.dto.AuthAttemptCreateDtoResponse;
import org.ezkey.authattempt.dto.AuthAttemptInitiateRequestDto;
import org.ezkey.authattempt.dto.AuthAttemptInitiateResponseDto;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.signature.SignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-based service for authorization attempt business logic.
 * <p>
 * This service provides business operations for authorization attempts,
 * including creation, initiation, completion, and status management.
 * It uses JPA repositories for data persistence and maintains all
 * the functional requirements from the original MyBatis implementation.
 * </p>
 *
 * <p>
 * <b>Supported Operations:</b>
 * <ul>
 * <li><b>CRUD Operations:</b> Create, read, update, delete authorization attempts</li>
 * <li><b>Business Logic:</b> Initiate, complete, and manage authorization flow</li>
 * <li><b>Status Management:</b> Read, reply, and acceptance status updates</li>
 * <li><b>Challenge Handling:</b> Challenge generation and validation</li>
 * <li><b>Signature Validation:</b> Cryptographic signature verification</li>
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
 * <b>Usage:</b> Business logic layer for authorization attempts
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttempt
 * @see AuthAttemptRepository
 * @see Enrollment
 */
@Service
@Transactional
public class AuthAttemptService{

    private final AuthAttemptRepository authAttemptRepository;

    private final EnrollmentRepository enrollmentRepository;

    private final SignatureService signatureService;

    @Value("${ezkey.simulation.mode:false}")
    private boolean simulationMode;

    /**
     * Constructs the authorization attempt service with required dependencies.
     *
     * @param authAttemptRepository the JPA repository for authorization attempts
     * @param enrollmentRepository the JPA repository for enrollments
     * @param signatureService the signature service for cryptographic operations
     */
    @Autowired
    public AuthAttemptService(AuthAttemptRepository authAttemptRepository,
            EnrollmentRepository enrollmentRepository,SignatureService signatureService){
        this.authAttemptRepository = authAttemptRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.signatureService = signatureService;
    }

    /**
     * Retrieves an authorization attempt by its ID.
     *
     * @param id the authorization attempt ID
     * @return the authorization attempt entity
     * @throws ResourceNotFoundException if the authorization attempt is not found
     */
    public AuthAttempt getById(Integer id){
        return authAttemptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Authorization attempt",id));
    }

    /**
     * Retrieves all authorization attempts.
     *
     * @return list of all authorization attempts
     */
    public List<AuthAttempt> getAll(){
        return authAttemptRepository.findAll();
    }

    /**
     * Creates a new authorization attempt.
     * <p>
     * This method creates a new authorization attempt with the provided data,
     * validates the associated enrollment, and generates necessary codes and challenges.
     * </p>
     *
     * @param authRequest the authorization attempt creation request
     * @return the created authorization attempt response
     * @throws IllegalArgumentException if the enrollment is not found or validation fails
     * @throws RuntimeException if the creation fails
     */
    public AuthAttemptCreateDtoResponse create(AuthAttemptCreateDtoRequest authRequest){
        // Find the enrollment
        Enrollment enrollment = enrollmentRepository.findById(authRequest.getEnrollmentId()).orElseThrow(
                () -> new IllegalArgumentException("Enrollment not found for ID: " + authRequest.getEnrollmentId()));

        // Validate challenge requirement
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())
                && Boolean.FALSE.equals(authRequest.getChallengeRequested())){
            throw new IllegalArgumentException("Challenge for device is required for this enrollment");
        }

        // Create the authorization attempt
        AuthAttempt authAttempt = new AuthAttempt();
        authAttempt.setEnrollmentId(enrollment.getEnrollmentId());
        authAttempt.setAuthAttemptRead(false);
        authAttempt.setAuthAttemptReplied(false);
        authAttempt.setAuthAttemptAccepted(false);

        // Generate challenge if required
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())){
            Random random = new Random();
            authAttempt.setAuthAttemptChallenge(100000 + random.nextInt(900000));
        } else{
            authAttempt.setAuthAttemptChallenge(null);
        }

        authAttempt.setCreatedAt(LocalDateTime.now());
        authAttempt.setAuthAttemptCode(UUID.randomUUID().toString());

        // Save the authorization attempt
        AuthAttempt savedAuthAttempt = authAttemptRepository.save(authAttempt);

        // Create response
        AuthAttemptCreateDtoResponse response = new AuthAttemptCreateDtoResponse();
        response.setAuthAttemptId(savedAuthAttempt.getAuthAttemptId());

        // Add simulation data if in simulation mode
        if (simulationMode){
            response.setSimulationAuthAttemptEnrolleeCode(UUID.randomUUID().toString());
            response.setSimulationAuthAttemptEnrolleeCodeSigned(signatureService.generateSignature(
                    response.getSimulationAuthAttemptEnrolleeCode(),authRequest.getSimulationDevicePrivateKey()));
            response.setSimulationAuthAttemptChallengeResponse(authAttempt.getAuthAttemptChallenge());
        }

        return response;
    }

    /**
     * Updates an authorization attempt.
     *
     * @param authAttempt the authorization attempt to update
     * @return the updated authorization attempt
     */
    public AuthAttempt update(AuthAttempt authAttempt){
        return authAttemptRepository.save(authAttempt);
    }

    /**
     * Deletes an authorization attempt by its ID.
     *
     * @param id the authorization attempt ID to delete
     * @throws ResourceNotFoundException if the authorization attempt is not found
     */
    public void delete(Integer id){
        if (!authAttemptRepository.existsById(id)){
            throw new ResourceNotFoundException("Authorization attempt",id);
        }
        authAttemptRepository.deleteById(id);
    }

    /**
     * Initiates an authorization attempt.
     * <p>
     * This method handles the initiation of an authorization attempt, including
     * marking it as read, validating signatures, and preparing the response.
     * </p>
     *
     * @param request the initiation request
     * @return the initiation response
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if the attempt is already read or update fails
     */
    public AuthAttemptInitiateResponseDto initiate(AuthAttemptInitiateRequestDto request){
        // Find the most recent authorization attempt
        AuthAttempt authAttempt = authAttemptRepository.findMostRecentByEnrollmentId(request.getEnrollmentId())
                .orElseThrow(() -> new IllegalArgumentException("Auth attempt record not found for this enrollment"));

        // Check if already read
        if (Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())){
            throw new IllegalStateException("Auth attempt already read by device");
        }

        // Mark as read
        int updated = authAttemptRepository.setDeviceReadTrueIfNotRead(authAttempt.getAuthAttemptId());
        if (updated == 0){
            throw new IllegalStateException("Failed to mark auth attempt as read");
        }

        // Find the enrollment
        Enrollment enrollment = enrollmentRepository.findById(request.getEnrollmentId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));

        // Validate device public key
        String devicePublicKey = enrollment.getAuthAttemptPublicKey();
        if (devicePublicKey == null){
            throw new IllegalStateException("Device public key not found for this enrollment");
        }

        // Validate signature
        boolean isValid = signatureService.validateSignature(request.getAuthAttemptEnrolleeCode(),
                request.getAuthAttemptEnrolleeCodeSigned(),devicePublicKey);
        if (!isValid){
            throw new IllegalArgumentException("Invalid signature for auth attempt code");
        }

        // Update the authorization attempt
        authAttempt.setAuthAttemptRead(true);
        authAttemptRepository.save(authAttempt);

        // Create response
        AuthAttemptInitiateResponseDto response = new AuthAttemptInitiateResponseDto();
        response.setAuthAttemptId(authAttempt.getAuthAttemptId());
        response.setAuthAttemptCode(authAttempt.getAuthAttemptCode());
        response.setAuthAttemptCodeSigned(signatureService.generateSignature(authAttempt.getAuthAttemptCode(),
                enrollment.getIntegrationPrivateKey()));
        response.setAuthAttemptChallengeRequired(enrollment.getAuthAttemptChallengeRequired());
        return response;
    }

    /**
     * Completes an authorization attempt.
     * <p>
     * This method handles the completion of an authorization attempt, including
     * validation of all required fields, signatures, and challenge responses.
     * </p>
     *
     * @param request the completion request
     * @return the completion response
     */
    public AuthAttemptCompleteResponseDto complete(AuthAttemptCompleteRequestDto request){
        AuthAttemptCompleteResponseDto response = new AuthAttemptCompleteResponseDto();

        // Find the authorization attempt
        Optional<AuthAttempt> authAttemptOpt = authAttemptRepository.findById(request.getAuthAttemptId());
        if (authAttemptOpt.isEmpty()){
            response.setSuccess(false);
            response.setMessage("Auth attempt record not found");
            return response;
        }

        AuthAttempt authAttempt = authAttemptOpt.get();

        // Mark as replied
        int updated = authAttemptRepository.setDeviceRepliedTrueIfNotReplied(authAttempt.getAuthAttemptId());
        if (updated == 0){
            throw new IllegalStateException("Failed to mark auth attempt as replied");
        }

        // Check if read
        if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())){
            response.setSuccess(false);
            response.setMessage("Auth attempt not read by device");
            return response;
        }

        // Validate auth attempt code
        if (!authAttempt.getAuthAttemptCode().equals(request.getAuthAttemptCode())){
            response.setSuccess(false);
            response.setMessage("Integration code mismatch");
            return response;
        }

        // Validate enrollment ID
        if (!authAttempt.getEnrollmentId().equals(request.getEnrollmentId())){
            response.setSuccess(false);
            response.setMessage("Enrollment id mismatch");
            return response;
        }

        // Find the enrollment
        Enrollment enrollment = enrollmentRepository.findById(authAttempt.getEnrollmentId()).orElse(null);
        if (enrollment == null){
            response.setSuccess(false);
            response.setMessage("Enrollment record not found");
            return response;
        }

        // Validate device public key
        String devicePublicKey = enrollment.getAuthAttemptPublicKey();
        if (devicePublicKey == null){
            response.setSuccess(false);
            response.setMessage("Device public key not found");
            return response;
        }

        // Validate device signature
        boolean isValid = signatureService.validateSignature(request.getAuthAttemptEnrolleeCode(),
                request.getAuthAttemptEnrolleeCodeSigned(),devicePublicKey);
        if (!isValid){
            response.setSuccess(false);
            response.setMessage("Invalid signature for auth attempt code");
            return response;
        }

        // Validate integration public key
        String integrationPublicKey = enrollment.getIntegrationPublicKey();
        if (integrationPublicKey == null){
            response.setSuccess(false);
            response.setMessage("Integration public key not found");
            return response;
        }

        // Validate integration signature
        boolean isAppCodeValid = signatureService.validateSignature(request.getAuthAttemptCode(),
                request.getAuthAttemptCodeSigned(),integrationPublicKey);
        if (!isAppCodeValid){
            response.setSuccess(false);
            response.setMessage("Invalid signature for integration code");
            return response;
        }

        // Validate challenge if required
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())){
            if (request.getAuthAttemptChallengeResponse() == null
                    || !request.getAuthAttemptChallengeResponse().equals(authAttempt.getAuthAttemptChallenge())){
                authAttempt.setAuthAttemptAccepted(false);
                authAttemptRepository.save(authAttempt);
                response.setSuccess(false);
                response.setMessage("Challenge value mismatch");
                return response;
            }
        }

        // Update authorization attempt
        authAttempt.setAuthAttemptReplied(true);
        authAttempt.setAuthAttemptAccepted(request.getAuthAttemptAccepted());
        authAttemptRepository.save(authAttempt);

        response.setSuccess(true);
        response.setMessage("Auth attempt completed");
        return response;
    }
}