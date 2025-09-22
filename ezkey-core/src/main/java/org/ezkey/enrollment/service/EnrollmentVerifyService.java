/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentVerifyService
 * Description: Specialized service for handling enrollment verification operations.
 */

package org.ezkey.enrollment.service;


import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.EnrollmentVerifyRequest;
import org.ezkey.enrollment.domain.EnrollmentVerifyResponse;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for handling enrollment verification operations.
 * <p>
 * This service manages the enrollment verification process where a mobile device
 * completes the enrollment by providing cryptographic proof of device ownership.
 * It implements comprehensive security validation to prevent replay attacks and
 * ensure enrollment integrity.
 * </p>
 *
 * <p>
 * <b>Security Features:</b>
 * <ul>
 * <li><b>Signature Validation:</b> Verifies device cryptographic signature</li>
 * <li><b>Device Key Uniqueness:</b> Prevents replay attacks using same public key</li>
 * <li><b>Challenge Verification:</b> Validates enrollment challenge response</li>
 * <li><b>State Consistency:</b> Ensures enrollment state integrity</li>
 * <li><b>Atomic Operations:</b> Uses row-level locking for thread safety</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Transaction Management:</b>
 * This service uses Spring's declarative transaction management to ensure
 * data consistency during the enrollment verification process.
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
 * @see EnrollmentVerifyRequest
 * @see EnrollmentVerifyResponse
 * @see Enrollment
 * @see SignatureService
 */
@Service
@Transactional
public class EnrollmentVerifyService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentVerifyService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final SignatureService signatureService;
    private final EnrollmentTxHelper enrollmentTxHelper;

    /**
     * Constructs the verify service with required dependencies.
     *
     * @param enrollmentRepository the JPA repository for enrollment operations
     * @param signatureService the signature service for cryptographic operations
     * @param enrollmentTxHelper the transactional helper for enrollment operations
     */
    public EnrollmentVerifyService(EnrollmentRepository enrollmentRepository,
                                 SignatureService signatureService,
                                 EnrollmentTxHelper enrollmentTxHelper) {
        this.enrollmentRepository = enrollmentRepository;
        this.signatureService = signatureService;
        this.enrollmentTxHelper = enrollmentTxHelper;
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
     * @param request the verify request containing device keys and signatures
     * @return the verify response confirming successful enrollment
     * @throws IllegalArgumentException if validation fails (signature, uniqueness, or challenge)
     * @throws IllegalStateException if enrollment is in invalid state or already processed
     */
    public EnrollmentVerifyResponse verify(EnrollmentVerifyRequest request) {
        logger.info("Starting enrollment verify process for enrollment ID: {}", request.getEnrollmentId());

        // Step 1: Validate enrollment state
        Enrollment enrollment = validateEnrollmentState(request);
        
        // Step 2: Validate signature
        validateSignature(request, enrollment);
        
        // Step 3: Validate device public key uniqueness
        validateDeviceKeyUniqueness(request);
        
        // Step 4: Validate challenge response
        validateChallengeResponse(request, enrollment);
        
        // Step 5: Acquire lock and final validation
        Enrollment lockedEnrollment = acquireLockAndValidate(request, enrollment);
        
        // Step 6: Mark as verified and activate
        markAsVerified(lockedEnrollment, request);
        
        // Step 7: Build and return response
        return buildVerifyResponse();
    }

    /**
     * Validates the enrollment state and ensures it's ready for verification.
     * <p>
     * This method performs read-only pre-checks to ensure the enrollment exists
     * and is in the correct state for verification.
     * </p>
     *
     * @param request the verify request
     * @return the validated enrollment
     * @throws IllegalStateException if enrollment is not found or in invalid state
     */
    private Enrollment validateEnrollmentState(EnrollmentVerifyRequest request) {
        logger.debug("Step 1: Performing read-only pre-checks for enrollment ID: {}", request.getEnrollmentId());
        
        Enrollment enrollment = enrollmentRepository.findById(request.getEnrollmentId())
            .orElseThrow(() -> {
                logger.warn("Validation failed: Enrollment not found for ID: {}", request.getEnrollmentId());
                return new IllegalStateException("Enrollment already verified");
            });
            
        logger.debug("Enrollment found: ID={}, Status={}", enrollment.getEnrollmentId(), enrollment.getStatus());
        
        if (enrollment.getStatus() == EnrollmentStatus.VERIFIED) {
            logger.warn("Validation failed: Enrollment already verified - ID: {}, Status: {}", 
                       enrollment.getEnrollmentId(), enrollment.getStatus());
            throw new IllegalStateException("Enrollment already verified");
        }
        
        logger.debug("Enrollment status validation passed: Status is not VERIFIED");
        
        if (enrollment.getStatus() != EnrollmentStatus.BOUND) {
            logger.warn("Validation failed: Enrollment must be bound before verification - ID: {}, Status: {}", 
                       enrollment.getEnrollmentId(), enrollment.getStatus());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment must be bound before verification");
        }
        
        logger.debug("Enrollment status validation passed: Status is BOUND");
        return enrollment;
    }

    /**
     * Validates the device signature for the enrollment.
     * <p>
     * This method ensures that the device signature is valid and corresponds to
     * the enrollment proof token.
     * </p>
     *
     * @param request the verify request containing device signature
     * @param enrollment the enrollment containing proof token
     * @throws IllegalArgumentException if signature validation fails
     */
    private void validateSignature(EnrollmentVerifyRequest request, Enrollment enrollment) {
        logger.debug("Step 2: Validating signature for enrollment ID: {}", request.getEnrollmentId());
        
        boolean valid = signatureService.validateSignature(
            enrollment.getEnrollmentProofToken(),
            request.getEnrollmentProofTokenSigned(),
            request.getDevicePublicKey()
        );
        
        if (!valid) {
            logger.warn("Validation failed: Invalid bind proof token signature for enrollment ID: {}", request.getEnrollmentId());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Invalid bind proof token signature");
        }
        
        logger.debug("Signature validation passed for enrollment ID: {}", request.getEnrollmentId());
    }

    /**
     * Validates the device public key uniqueness to prevent replay attacks.
     * <p>
     * This method ensures that the device public key has not been used for
     * any other verified enrollment to prevent enrollment hijacking.
     * </p>
     *
     * @param request the verify request containing device public key
     * @throws IllegalArgumentException if device public key is already used
     */
    private void validateDeviceKeyUniqueness(EnrollmentVerifyRequest request) {
        logger.debug("Step 3: Validating device public key uniqueness for enrollment ID: {}", request.getEnrollmentId());
        
        if (enrollmentRepository.existsByDevicePublicKeyAndVerified(request.getDevicePublicKey())) {
            logger.warn("Validation failed: Device public key already used for verified enrollment - ID: {}, DevicePublicKey: {}", 
                       request.getEnrollmentId(), request.getDevicePublicKey());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Device public key already registered");
        }
        
        logger.debug("Device public key uniqueness validation passed for enrollment ID: {}", request.getEnrollmentId());
    }

    /**
     * Validates the challenge response for the enrollment.
     * <p>
     * This method ensures that the challenge response matches the expected value
     * to complete the enrollment verification process.
     * </p>
     *
     * @param request the verify request containing challenge response
     * @param enrollment the enrollment containing expected challenge
     * @throws IllegalArgumentException if challenge response is invalid
     */
    private void validateChallengeResponse(EnrollmentVerifyRequest request, Enrollment enrollment) {
        logger.debug("Step 4: Validating challenge response for enrollment ID: {}", request.getEnrollmentId());
        
        if (!request.getChallengeResponse().equals(enrollment.getEnrollmentChallenge())) {
            logger.warn("Validation failed: Invalid challenge response for enrollment ID: {} - Expected: {}, Received: {}", 
                       request.getEnrollmentId(), enrollment.getEnrollmentChallenge(), request.getChallengeResponse());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalArgumentException("Invalid challenge response");
        }
        
        logger.debug("Challenge response validation passed for enrollment ID: {}", request.getEnrollmentId());
    }

    /**
     * Acquires a lock on the enrollment and performs final validation.
     * <p>
     * This method implements the read-once guarantee by atomically locking
     * and validating the enrollment state.
     * </p>
     *
     * @param request the verify request
     * @param snapshot the enrollment snapshot from pre-checks
     * @return the locked enrollment
     * @throws IllegalStateException if enrollment is not found or in invalid state
     */
    private Enrollment acquireLockAndValidate(EnrollmentVerifyRequest request, Enrollment snapshot) {
        logger.debug("Step 5: Acquiring lock for enrollment ID: {}", request.getEnrollmentId());
        
        Enrollment enrollment = enrollmentRepository
            .findAndLockBoundById(request.getEnrollmentId())
            .orElse(null);
            
        if (enrollment == null) {
            logger.warn("Validation failed: Enrollment not found or already verified after lock acquisition for ID: {}", request.getEnrollmentId());
            throw new IllegalStateException("Enrollment already verified");
        }
        
        logger.debug("Lock acquired successfully for enrollment ID: {}", enrollment.getEnrollmentId());
        
        if (enrollment.getStatus() != EnrollmentStatus.BOUND) {
            logger.warn("Validation failed: Enrollment must be bound before verification after lock - ID: {}, Status: {}", 
                       enrollment.getEnrollmentId(), enrollment.getStatus());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment must be bound before verification");
        }
        
        logger.debug("Post-lock status validation passed: Status is BOUND");

        // Guard against state changes between snapshot and lock
        logger.debug("Step 6: Validating state consistency between snapshot and locked enrollment for ID: {}", request.getEnrollmentId());
        if (!snapshot.getEnrollmentProofToken().equals(enrollment.getEnrollmentProofToken()) ||
            !snapshot.getEnrollmentChallenge().equals(enrollment.getEnrollmentChallenge())) {
            logger.warn("Validation failed: Enrollment state changed between snapshot and lock - ID: {}", request.getEnrollmentId());
            enrollmentTxHelper.markInvalidAndClear(request.getEnrollmentId());
            throw new IllegalStateException("Enrollment state changed");
        }
        
        logger.debug("State consistency validation passed for enrollment ID: {}", request.getEnrollmentId());
        return enrollment;
    }

    /**
     * Marks the enrollment as verified and activates it.
     * <p>
     * This method updates the enrollment status to VERIFIED and sets it as active,
     * completing the enrollment process.
     * </p>
     *
     * @param enrollment the enrollment to mark as verified
     * @param request the verify request containing device public key
     */
    private void markAsVerified(Enrollment enrollment, EnrollmentVerifyRequest request) {
        logger.info("Step 7: Marking enrollment as VERIFIED - ID: {}", enrollment.getEnrollmentId());
        
        enrollment.setStatus(EnrollmentStatus.VERIFIED);
        enrollment.setActive(true);
        enrollment.setDevicePublicKey(request.getDevicePublicKey());
        enrollmentRepository.save(enrollment);
        
        logger.info("Enrollment successfully verified - ID: {}, Status: VERIFIED, Active: true", enrollment.getEnrollmentId());
    }

    /**
     * Builds the verification response.
     * <p>
     * This method creates a response confirming that the enrollment
     * has been successfully verified and activated.
     * </p>
     *
     * @return the verification response
     */
    private EnrollmentVerifyResponse buildVerifyResponse() {
        logger.debug("Step 8: Building verify response");
        
        EnrollmentVerifyResponse response = new EnrollmentVerifyResponse();
        response.setActive(true);

        logger.info("Enrollment verify process completed successfully");
        return response;
    }
}
