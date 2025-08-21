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

import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.NoPendingAuthAttemptException;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.signature.SignatureService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthAttemptService {

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
    public AuthAttemptService(AuthAttemptRepository authAttemptRepository,EnrollmentRepository enrollmentRepository,SignatureService signatureService){
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
    public AuthAttempt getById(Integer id) {
        return authAttemptRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Authorization attempt",id));
    }

    /**
     * Retrieves all authorization attempts.
     *
     * @return list of all authorization attempts
     */
    public List<AuthAttempt> getAll() {
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
    public AuthAttemptCreateResponse create(AuthAttemptCreateRequest authRequest) {
        // Find the enrollment
        Enrollment enrollment = enrollmentRepository.findById(authRequest.getEnrollmentId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found for ID: " + authRequest.getEnrollmentId()));

        // Validate challenge requirement
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired()) && Boolean.FALSE.equals(authRequest.getChallengeRequested())){
            throw new IllegalArgumentException("Challenge for device is required for this enrollment");
        }
        // Create the authorization attempt
        AuthAttempt authAttempt = new AuthAttempt();
        authAttempt.setEnrollmentId(enrollment.getEnrollmentId());
        authAttempt.setAuthAttemptRead(false);
        authAttempt.setAuthAttemptResponded(false);
        authAttempt.setAuthAttemptAccepted(false);

        // Generate challenge if required
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())){
            Random random = new Random();
            authAttempt.setAuthAttemptChallenge(100000 + random.nextInt(900000));
        } else{
            authAttempt.setAuthAttemptChallenge(null);
        }
        authAttempt.setAuthAttemptProofToken(signatureService.generateProofToken());
        authAttempt.setCreatedAt(LocalDateTime.now());

        // Save the authorization attempt
        AuthAttempt savedAuthAttempt = authAttemptRepository.save(authAttempt);

        // Create response
        AuthAttemptCreateResponse response = new AuthAttemptCreateResponse();
        response.setAuthAttemptId(savedAuthAttempt.getAuthAttemptId());

        // Add simulation data if in simulation mode
        if (simulationMode){
            response.setSimulationAuthAttemptProofTokenSignedByDevice(
                    signatureService.generateSignature(authAttempt.getAuthAttemptProofToken(),authRequest.getSimulationDevicePrivateKey()));
            response.setSimulationAuthAttemptChallengeResponse(authAttempt.getAuthAttemptChallenge());
            response.setSimulationPendingDeviceProofToken(signatureService.generateProofToken());
            response.setSimulationPendingDeviceProofTokenSigned(
                    signatureService.generateSignature(response.getSimulationPendingDeviceProofToken(),authRequest.getSimulationDevicePrivateKey()));
        }
        return response;
    }

    /**
     * Updates an authorization attempt.
     *
     * @param authAttempt the authorization attempt to update
     * @return the updated authorization attempt
     */
    public AuthAttempt update(AuthAttempt authAttempt) {
        return authAttemptRepository.save(authAttempt);
    }

    /**
     * Deletes an authorization attempt by its ID.
     *
     * @param id the authorization attempt ID to delete
     * @throws ResourceNotFoundException if the authorization attempt is not found
     */
    public void delete(Integer id) {
        if (!authAttemptRepository.existsById(id)){
            throw new ResourceNotFoundException("Authorization attempt",id);
        }
        authAttemptRepository.deleteById(id);
    }

    public AuthAttemptPendingResponse pending(AuthAttemptPendingRequest request) {
        // Find the most recent unread authorization attempt
        AuthAttempt authAttempt = authAttemptRepository.findMostRecentUnreadByEnrollmentId(request.getEnrollmentId())
                .orElseThrow(() -> new NoPendingAuthAttemptException("No pending authentication attempt found for enrollment: " + request.getEnrollmentId()));
        // Mark as read
        int updated = authAttemptRepository.setDeviceReadTrueIfNotRead(authAttempt.getAuthAttemptId());
        if (updated == 0){
            throw new IllegalStateException("Failed to mark auth attempt as read");
        }
        // Find the enrollment
        Enrollment enrollment = enrollmentRepository.findById(request.getEnrollmentId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found for ID: " + request.getEnrollmentId()));

        // Validate device public key
        String devicePublicKey = enrollment.getDevicePublicKey();
        if (devicePublicKey == null){
            throw new IllegalStateException("Device public key not found for this enrollment");
        }
        // Validate signature
        boolean isValid = signatureService.validateSignature(request.getDeviceProofToken(),request.getDeviceProofTokenSigned(),devicePublicKey);
        if (!isValid){
            throw new IllegalArgumentException("Invalid device signature for authentication request");
        }
        // Update the authorization attempt
        authAttempt.setAuthAttemptRead(true);
        authAttemptRepository.save(authAttempt);

        // Create response
        AuthAttemptPendingResponse response = new AuthAttemptPendingResponse();
        response.setAuthAttemptId(authAttempt.getAuthAttemptId());
        response.setAuthAttemptProofToken(authAttempt.getAuthAttemptProofToken());
        response.setAuthAttemptProofTokenSignedByIntegration(signatureService.generateSignature(authAttempt.getAuthAttemptProofToken(),enrollment.getIntegrationPrivateKey()));
        response.setAuthAttemptChallengeRequired(enrollment.getAuthAttemptChallengeRequired());
        return response;
    }

    public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
        AuthAttemptRespondResponse response = new AuthAttemptRespondResponse();

        Optional<AuthAttempt> authAttemptOpt = authAttemptRepository.findById(request.getAuthAttemptId());
        if (authAttemptOpt.isEmpty()){
            response.setSuccess(false);
            response.setMessage("Auth attempt record not found");
            return response;
        }
        AuthAttempt authAttempt = authAttemptOpt.get();

        // Check if read
        if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())){
            response.setSuccess(false);
            response.setMessage("Auth attempt not read by device");
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
        String devicePublicKey = enrollment.getDevicePublicKey();
        if (devicePublicKey == null){
            response.setSuccess(false);
            response.setMessage("Device public key not found");
            return response;
        }
        // Validate device signature
        boolean isDeviceProofTokenValid = signatureService.validateSignature(authAttempt.getAuthAttemptProofToken(),request.getAuthAttemptProofTokenSignedByDevice(),
                devicePublicKey);
        if (!isDeviceProofTokenValid){
            authAttempt.setAuthAttemptAccepted(false);
            authAttempt.setAuthAttemptValid(false);
            authAttempt.setAuthAttemptResponded(true);
            authAttempt.setDeviceProofTokenValid(false);
            authAttemptRepository.save(authAttempt);
            response.setSuccess(false);
            response.setMessage("Invalid signature for auth attempt code");
            return response;
        }
        // Validate challenge if required
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())){
            if (request.getAuthAttemptChallengeResponse() == null || !request.getAuthAttemptChallengeResponse().equals(authAttempt.getAuthAttemptChallenge())){
                authAttempt.setAuthAttemptAccepted(false);
                authAttempt.setAuthAttemptValid(false);
                authAttempt.setAuthAttemptResponded(true);
                authAttempt.setDeviceProofTokenValid(true);
                authAttemptRepository.save(authAttempt);
                response.setSuccess(false);
                response.setMessage("Challenge value mismatch");
                return response;
            }
        }
        // Update authorization attempt
        authAttempt.setAuthAttemptAccepted(request.getAuthAttemptAccepted());
        authAttempt.setAuthAttemptValid(true);
        authAttempt.setAuthAttemptResponded(true);
        authAttempt.setDeviceProofTokenValid(true);
        authAttemptRepository.save(authAttempt);

        response.setSuccess(true);
        response.setMessage("Auth attempt completed");
        return response;
    }
}