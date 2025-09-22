/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptRespondService
 * Description: Specialized service for processing authentication responses.
 */

package org.ezkey.authattempt.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for processing authentication responses from mobile devices.
 * <p>
 * This service handles the completion of authentication attempts when mobile devices
 * submit their responses (approve/deny) with cryptographic proof. It validates device
 * signatures, checks challenge responses if required, and updates authentication
 * attempt status accordingly.
 * </p>
 *
 * <p>
 * <b>Security Validations:</b>
 * <ul>
 * <li><b>Signature Verification:</b> Validates device signature using enrollment public key</li>
 * <li><b>Challenge Validation:</b> Verifies challenge response if required by enrollment</li>
 * <li><b>Status Validation:</b> Ensures authentication attempt is in READ status</li>
 * <li><b>Proof Token Validation:</b> Verifies authentication attempt proof token signature</li>
 * <li><b>Supersession Check:</b> Ensures no newer authentication attempt exists</li>
 * <li><b>Expiration Check:</b> Validates authentication attempt has not expired</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Transaction Management:</b>
 * This service uses Spring's declarative transaction management to ensure
 * data consistency during the authentication response processing.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthAttemptRespondRequest
 * @see AuthAttemptRespondResponse
 * @see AuthAttempt
 * @see Enrollment
 * @see SignatureService
 * @see AuthenticationResult
 */
@Service
@Transactional
public class AuthAttemptRespondService {

    private static final Logger logger = LoggerFactory.getLogger(AuthAttemptRespondService.class);

    private final AuthAttemptRepository authAttemptRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SignatureService signatureService;

    /**
     * Constructs the respond service with required dependencies.
     *
     * @param authAttemptRepository the JPA repository for authentication attempts
     * @param enrollmentRepository the JPA repository for enrollments
     * @param signatureService the signature service for cryptographic operations
     */
    public AuthAttemptRespondService(AuthAttemptRepository authAttemptRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   SignatureService signatureService) {
        this.authAttemptRepository = authAttemptRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.signatureService = signatureService;
    }

    /**
     * Processes an authentication response from a mobile device.
     * <p>
     * This method handles the completion of an authentication attempt when a mobile device
     * submits its response (approve/deny) with cryptographic proof. It validates the device
     * signature, checks challenge responses if required, and updates the authentication
     * attempt status accordingly.
     * </p>
     *
     * @param request the authentication response request from mobile device
     * @return the authentication response result with status and message
     */
    public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
        // Step 1: Validate and get the authentication attempt
        AuthAttempt authAttempt = validateAndGetAttempt(request);
        
        // Step 2: Validate enrollment
        Enrollment enrollment = validateEnrollment(authAttempt);
        
        // Step 3: Validate device signature
        validateDeviceSignature(request, authAttempt, enrollment);
        
        // Step 4: Validate challenge if required
        validateChallenge(request, authAttempt, enrollment);
        
        // Step 5: Update attempt status
        updateAttemptStatus(authAttempt, request);
        
        // Step 6: Build and return response
        return buildResponse(request);
    }

    /**
     * Validates and retrieves the authentication attempt.
     * <p>
     * This method ensures the authentication attempt exists and is in the correct
     * state for processing a response.
     * </p>
     *
     * @param request the authentication response request
     * @return the validated authentication attempt
     */
    private AuthAttempt validateAndGetAttempt(AuthAttemptRespondRequest request) {
        Optional<AuthAttempt> authAttemptOpt = authAttemptRepository.findById(request.getAuthAttemptId());
        if (authAttemptOpt.isEmpty()) {
            throw new IllegalArgumentException("Auth attempt record not found");
        }
        
        AuthAttempt authAttempt = authAttemptOpt.get();

        // Check if in READ status (device has claimed the attempt)
        if (authAttempt.getAuthAttemptStatus() != AuthAttemptStatus.READ) {
            throw new IllegalArgumentException("Auth attempt not read by device");
        }

        // Check if superseded by a newer authentication attempt for the same enrollment
        Optional<AuthAttempt> newerAttempt = authAttemptRepository
            .findNewerAttemptByEnrollmentId(authAttempt.getEnrollmentId(), authAttempt.getCreatedAt());
        if (newerAttempt.isPresent()) {
            logger.info("Auth attempt {} superseded by newer attempt {} for enrollment {}", 
                       authAttempt.getAuthAttemptId(), newerAttempt.get().getAuthAttemptId(),
                       authAttempt.getEnrollmentId());
            throw new IllegalStateException("Authentication attempt superseded by newer request");
        }

        // Check if expired
        LocalDateTime now = LocalDateTime.now();
        if (authAttempt.getExpiresAt() != null && now.isAfter(authAttempt.getExpiresAt())) {
            throw new IllegalStateException("Authentication attempt expired");
        }

        return authAttempt;
    }

    /**
     * Validates the enrollment for the authentication attempt.
     * <p>
     * This method ensures the enrollment exists and contains the necessary
     * cryptographic keys for validation.
     * </p>
     *
     * @param authAttempt the authentication attempt
     * @return the validated enrollment
     */
    private Enrollment validateEnrollment(AuthAttempt authAttempt) {
        Enrollment enrollment = enrollmentRepository.findById(authAttempt.getEnrollmentId())
            .orElseThrow(() -> new IllegalArgumentException("Enrollment record not found"));

        // Validate device public key
        String devicePublicKey = enrollment.getDevicePublicKey();
        if (devicePublicKey == null) {
            throw new IllegalArgumentException("Device public key not found");
        }

        return enrollment;
    }

    /**
     * Validates the device signature for the authentication attempt.
     * <p>
     * This method ensures the device signature is valid and corresponds to
     * the authentication attempt proof token.
     * </p>
     *
     * @param request the authentication response request
     * @param authAttempt the authentication attempt
     * @param enrollment the enrollment containing device public key
     */
    private void validateDeviceSignature(AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
        // Validate device signature
        boolean isDeviceProofTokenValid = signatureService.validateSignature(
            authAttempt.getAuthAttemptProofToken(),
            request.getAuthAttemptProofTokenSignedByDevice(),
            enrollment.getDevicePublicKey()
        );
        
        if (!isDeviceProofTokenValid) {
            authAttempt.setAuthAttemptStatus(AuthAttemptStatus.INVALID);
            authAttemptRepository.save(authAttempt);
            throw new IllegalArgumentException("Invalid signature for auth attempt code");
        }
    }

    /**
     * Validates the challenge response if required by the enrollment.
     * <p>
     * This method ensures the challenge response matches the expected value
     * if challenge validation is required.
     * </p>
     *
     * @param request the authentication response request
     * @param authAttempt the authentication attempt
     * @param enrollment the enrollment containing challenge requirements
     */
    private void validateChallenge(AuthAttemptRespondRequest request, AuthAttempt authAttempt, Enrollment enrollment) {
        // Validate challenge if required
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())) {
            if (request.getAuthAttemptChallengeResponse() == null || 
                !request.getAuthAttemptChallengeResponse().equals(authAttempt.getAuthAttemptChallenge())) {
                authAttempt.setAuthAttemptStatus(AuthAttemptStatus.INVALID);
                authAttemptRepository.save(authAttempt);
                throw new IllegalArgumentException("Challenge value mismatch");
            }
        }
    }

    /**
     * Updates the authentication attempt status based on the user's decision.
     * <p>
     * This method sets the final status of the authentication attempt based on
     * whether the user approved or denied the authentication request.
     * </p>
     *
     * @param authAttempt the authentication attempt to update
     * @param request the authentication response request
     */
    private void updateAttemptStatus(AuthAttempt authAttempt, AuthAttemptRespondRequest request) {
        // Update authorization attempt based on user decision
        if (Boolean.TRUE.equals(request.getAuthAttemptAccepted())) {
            authAttempt.setAuthAttemptStatus(AuthAttemptStatus.ACCEPTED);
        } else {
            authAttempt.setAuthAttemptStatus(AuthAttemptStatus.REJECTED);
        }
        authAttemptRepository.save(authAttempt);
    }

    /**
     * Builds the authentication response with the appropriate result.
     * <p>
     * This method creates a response indicating whether the authentication
     * was approved or denied based on the user's decision.
     * </p>
     *
     * @param request the authentication response request
     * @return the authentication response with result and message
     */
    private AuthAttemptRespondResponse buildResponse(AuthAttemptRespondRequest request) {
        AuthAttemptRespondResponse response = new AuthAttemptRespondResponse();

        // Set result based on user's choice
        if (Boolean.TRUE.equals(request.getAuthAttemptAccepted())) {
            response.setResult(AuthenticationResult.APPROVED);
        } else {
            response.setResult(AuthenticationResult.DENIED);
        }
        
        response.setMessage("Auth attempt completed");
        return response;
    }
}

