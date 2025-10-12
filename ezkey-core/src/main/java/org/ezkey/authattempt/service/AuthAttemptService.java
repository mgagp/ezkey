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

import java.time.OffsetDateTime;
import java.util.List;

import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Core service for managing authentication attempts in the Ezkey MFA system.
 * <p>
 * This service acts as the main coordinator for authentication attempt operations,
 * delegating complex business logic to specialized services while maintaining a
 * unified public API. It implements the mobile-first authentication model where
 * devices poll for pending requests and submit cryptographically signed responses.
 * </p>
 *
 * <p>
 * <b>Architecture:</b>
 * This service uses a delegation pattern to specialized services:
 * <ul>
 * <li><b>AuthAttemptPendingService:</b> Handles pending authentication request processing</li>
 * <li><b>AuthAttemptRespondService:</b> Processes authentication responses from mobile devices</li>
 * <li><b>AuthAttemptWaitService:</b> Manages polling and wait operations</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Authentication Flow Support:</b>
 * <ul>
 * <li><b>Admin Flow:</b> Creates authentication attempts via Admin API for web applications</li>
 * <li><b>Mobile Flow:</b> Processes pending requests and responses via Auth API for mobile devices</li>
 * <li><b>Wait Flow:</b> Provides synchronous polling for web applications awaiting authentication</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Features:</b>
 * <ul>
 * <li><b>Cryptographic Validation:</b> Verifies device and integration signatures using RSA-2048</li>
 * <li><b>Enrollment Proof Tokens:</b> Prevents enumeration attacks through secure token-based identification</li>
 * <li><b>Anti-Replay Protection:</b> Ensures device proof tokens are used only once</li>
 * <li><b>Challenge-Based Security:</b> Implements configurable numeric challenges for additional verification</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Transaction Management:</b>
 * This service uses Spring's declarative transaction management with appropriate propagation
 * settings. Specialized services handle their own transaction boundaries as needed.
 * </p>
 *
 * <p>
 * <b>Integration Points:</b>
 * <ul>
 * <li><b>AuthAttemptRepository:</b> Data persistence layer for authentication attempts</li>
 * <li><b>EnrollmentRepository:</b> Enrollment data access for validation and device management</li>
 * <li><b>SignatureService:</b> Cryptographic operations for signature validation</li>
 * <li><b>Specialized Services:</b> Delegated business logic for specific operations</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Error Handling:</b>
 * Implements comprehensive error handling with secure error messages to prevent information
 * leakage while providing detailed logging for debugging and monitoring purposes.
 * </p>
 *
 * <p>
 * <b>Performance Considerations:</b>
 * <ul>
 * <li><b>Read-Once Guarantee:</b> Authentication attempts can only be read once to prevent replay attacks</li>
 * <li><b>Efficient Polling:</b> Optimized database queries for pending request checking</li>
 * <li><b>Timeout Management:</b> Configurable timeouts prevent resource exhaustion</li>
 * <li><b>Service Separation:</b> Specialized services allow for targeted optimization</li>
 * </ul>
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
 * @see AuthAttempt
 * @see AuthAttemptCreateRequest
 * @see AuthAttemptPendingRequest
 * @see AuthAttemptRespondRequest
 * @see AuthAttemptWaitRequest
 * @see Enrollment
 * @see SignatureService
 * @see AuthAttemptPendingService
 * @see AuthAttemptRespondService
 * @see AuthAttemptWaitService
 */
@Service
@Transactional
public class AuthAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(AuthAttemptService.class);

    private final AuthAttemptRepository authAttemptRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SignatureService signatureService;

    // Specialized services for specific operations
    private final AuthAttemptPendingService pendingService;
    private final AuthAttemptRespondService respondService;
    private final AuthAttemptWaitService waitService;

    @Value("${ezkey.auth-attempt.challenge-digits:2}")
    private int challengeDigits;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Constructs the authentication attempt service with required dependencies.
     *
     * @param authAttemptRepository the JPA repository for authentication attempts
     * @param enrollmentRepository the JPA repository for enrollments
     * @param signatureService the signature service for cryptographic operations
     * @param pendingService the specialized service for pending operations
     * @param respondService the specialized service for respond operations
     * @param waitService the specialized service for wait operations
     */
    public AuthAttemptService(AuthAttemptRepository authAttemptRepository,
                            EnrollmentRepository enrollmentRepository,
                            SignatureService signatureService,
                            AuthAttemptPendingService pendingService,
                            AuthAttemptRespondService respondService,
                            AuthAttemptWaitService waitService) {
        this.authAttemptRepository = authAttemptRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.signatureService = signatureService;
        this.pendingService = pendingService;
        this.respondService = respondService;
        this.waitService = waitService;
    }

    /**
     * Retrieves an authentication attempt by its ID.
     *
     * @param id the authentication attempt ID
     * @return the authentication attempt entity
     * @throws ResourceNotFoundException if the authentication attempt is not found
     */
    public AuthAttempt getById(Integer id) {
        return authAttemptRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Authentication attempt",id));
    }

    /**
     * Retrieves all authentication attempts.
     *
     * @return list of all authentication attempts
     */
    public List<AuthAttempt> getAll() {
        return authAttemptRepository.findAll();
    }

    /**
     * Creates a new authentication attempt.
     * <p>
     * This method creates a new authentication attempt with the provided data,
     * validates the associated enrollment, and generates necessary codes and challenges.
     * </p>
     *
     * @param authRequest the authentication attempt creation request
     * @return the created authentication attempt response
     * @throws IllegalArgumentException if the enrollment is not found or validation fails
     * @throws RuntimeException if the creation fails
     */
    public AuthAttemptCreateResponse create(AuthAttemptCreateRequest authRequest) {
        // Find the enrollment
        Enrollment enrollment = enrollmentRepository.findById(authRequest.getEnrollmentId())
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found for ID: " + authRequest.getEnrollmentId()));

        // Supersession: Mark existing non-final attempts as EXPIRED
        List<AuthAttempt> existingAttempts = authAttemptRepository.findByEnrollmentIdAndStatusIn(authRequest.getEnrollmentId(),
                List.of(AuthAttemptStatus.PENDING,AuthAttemptStatus.READ));
        if (!existingAttempts.isEmpty()){
            List<Integer> attemptIds = existingAttempts.stream().map(AuthAttempt::getAuthAttemptId).toList();

            int updatedCount = authAttemptRepository.updateStatusForMultipleAttempts(attemptIds,AuthAttemptStatus.EXPIRED);
            logger.info("Supersession: Marked {} existing auth attempts as EXPIRED for enrollment {}",updatedCount,authRequest.getEnrollmentId());
        }
        // Create the authorization attempt
        AuthAttempt authAttempt = new AuthAttempt();

        authAttempt.setEnrollmentId(enrollment.getEnrollmentId());
        authAttempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);

        // Generate challenge if required
        boolean shouldGenerateChallenge = Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired()) || Boolean.TRUE.equals(authRequest.getChallengeRequested());
        if (shouldGenerateChallenge){
            authAttempt.setAuthAttemptChallenge(generateChallenge());
            logger.debug("Generated challenge code for auth attempt: {}",authAttempt.getAuthAttemptChallenge());
        } else{
            authAttempt.setAuthAttemptChallenge(null);
            logger.debug("No challenge code generated for auth attempt");
        }
        authAttempt.setAuthAttemptProofToken(signatureService.generateProofToken());
        authAttempt.setCreatedAt(OffsetDateTime.now());
        authAttempt.setExpiresAt(OffsetDateTime.now().plusSeconds(120)); // TTL: 120 seconds

        // Save the authorization attempt
        AuthAttempt savedAuthAttempt = authAttemptRepository.save(authAttempt);

        // Create response
        AuthAttemptCreateResponse response = new AuthAttemptCreateResponse();
        response.setAuthAttemptId(savedAuthAttempt.getAuthAttemptId());

        return response;
    }

    /**
     * Updates an authentication attempt.
     *
     * @param authAttempt the authentication attempt to update
     * @return the updated authentication attempt
     */
    public AuthAttempt update(AuthAttempt authAttempt) {
        return authAttemptRepository.save(authAttempt);
    }

    /**
     * Deletes an authentication attempt by its ID.
     *
     * @param id the authentication attempt ID to delete
     * @throws ResourceNotFoundException if the authentication attempt is not found
     */
    public void delete(Integer id) {
        if (!authAttemptRepository.existsById(id)){
            throw new ResourceNotFoundException("Authentication attempt",id);
        }
        authAttemptRepository.deleteById(id);
    }

    /**
     * Process pending authentication attempt request using secure enrollment proof token.
     *
     * Security Enhancement: Validates enrollment ownership through cryptographic proof token
     * instead of relying on URL-based enrollment ID, preventing enumeration attacks.
     * <p>
     * This method implements the fundamental Ezkey security principle of read-once guarantee.
     * It validates the request completely before locking the authentication attempt to ensure
     * that each authentication attempt can only be read once by a legitimate device.
     * </p>
     *
     * <p>
     * <b>Security Principle - Read-Once Guarantee:</b>
     * <ul>
     * <li>Each authentication attempt can only be read once by a legitimate device</li>
     * <li>Once read, the attempt is marked as processed and cannot be read again</li>
     * <li>This prevents replay attacks and ensures proof-of-possession</li>
     * <li>The proof token returned can only be obtained by the legitimate reader</li>
     * </ul>
     * </p>
     *
     * <p>
     * <b>Security Enhancement:</b> This method now uses enrollmentProofToken to find
     * the enrollment instead of relying on enrollmentId from the URL path. This prevents
     * enumeration attacks while maintaining the security of the authentication flow.
     * </p>
     *
     * <p>
     * <b>Implementation Strategy:</b>
     * <ul>
     * <li><b>Step 1:</b> Find enrollment by proof token and validate it's active</li>
     * <li><b>Step 2:</b> Validate enrollment ID matches the proof token</li>
     * <li><b>Step 3:</b> Complete validation before any database modification</li>
     * <li><b>Step 4:</b> Atomic lock and marking only if validation succeeds</li>
     * <li><b>Step 5:</b> Immediate marking to preserve read-once guarantee</li>
     * <li><b>Step 6:</b> Response generation with signed proof token</li>
     * </ul>
     * </p>
     *
     * <p>
     * <b>Error Handling:</b>
     * <ul>
     * <li>Uses secure error messages to prevent information leakage</li>
     * <li>Logs detailed information for debugging purposes</li>
     * <li>Maintains read-once guarantee even in error scenarios</li>
     * </ul>
     * </p>
     *
     * @param request the pending request with enrollment proof token
     * @return the pending authentication response with proof token
     * @throws IllegalArgumentException if enrollment proof token is invalid
     * @throws IllegalStateException if the authentication attempt is already processed
     * @throws NoPendingAuthAttemptException if no pending authentication attempt is found
     * @since 2025
     */
    /**
     * Process pending authentication attempt request using secure enrollment proof token.
     * <p>
     * This method delegates to the specialized AuthAttemptPendingService to handle
     * the complex logic of processing pending authentication requests while maintaining
     * the same public API for backward compatibility.
     * </p>
     *
     * @param request the pending request with enrollment proof token
     * @return the pending authentication response with proof token
     * @throws IllegalArgumentException if enrollment proof token is invalid
     * @throws IllegalStateException if the authentication attempt is already processed
     * @throws NoPendingAuthAttemptException if no pending authentication attempt is found
     */
    public AuthAttemptPendingResponse pending(AuthAttemptPendingRequest request) {
        return pendingService.pending(request);
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
     * <p>
     * <b>Security Validations:</b>
     * <ul>
     * <li><b>Signature Verification:</b> Validates device signature using enrollment public key</li>
     * <li><b>Challenge Validation:</b> Verifies challenge response if required by enrollment</li>
     * <li><b>Status Validation:</b> Ensures authentication attempt is in PENDING status</li>
     * <li><b>Proof Token Validation:</b> Verifies authentication attempt proof token signature</li>
     * </ul>
     * </p>
     *
     * <p>
     * <b>Response Processing:</b>
     * Based on the user's decision (accept/reject) and validation results, the method
     * updates the authentication attempt status to ACCEPTED, REJECTED, or INVALID.
     * </p>
     *
     * @param request the authentication response request from mobile device
     * @return the authentication response result with status and message
     * @see AuthAttemptRespondRequest
     * @see AuthAttemptRespondResponse
     * @see AuthenticationResult
     */
    /**
     * Processes an authentication response from a mobile device.
     * <p>
     * This method delegates to the specialized AuthAttemptRespondService to handle
     * the complex logic of processing authentication responses while maintaining
     * the same public API for backward compatibility.
     * </p>
     *
     * @param request the authentication response request from mobile device
     * @return the authentication response result with status and message
     */
    public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
        return respondService.respond(request);
    }

    /**
     * Waits for authentication response completion with configurable timeout and polling.
     * <p>
     * This method delegates to the specialized AuthAttemptWaitService to handle
     * the complex logic of polling and waiting while maintaining the same public
     * API for backward compatibility.
     * </p>
     *
     * @param authAttemptId the ID of the authentication attempt to wait for
     * @param request the wait request containing timeout and polling configuration
     * @return the wait response with final status and metadata
     */
    public AuthAttemptWaitResponse waitForResponse(Integer authAttemptId, AuthAttemptWaitRequest request) {
        return waitService.waitForResponse(authAttemptId, request);
    }

    /**
     * Generates a challenge code with configurable number of digits using secure random generation.
     * <p>
     * This method generates a cryptographically secure challenge code based on the configured
     * number of digits. The default is 2 digits, with a maximum of 6 digits.
     * If a value greater than 6 is configured, it will be truncated to 6 with
     * a trace log message. Challenge generation is delegated to {@link SignatureService}
     * to ensure cryptographic security using SecureRandom.
     * </p>
     *
     * @return the generated challenge code as an integer
     * @since 2025
     */
    private Integer generateChallenge() {
        int effectiveDigits = challengeDigits;

        // Validate and truncate if necessary
        if (effectiveDigits > 6){
            logger.trace("Challenge digits configured as {} exceeds maximum of 6, truncating to 6",challengeDigits);
            effectiveDigits = 6;
        }
        // Ensure minimum of 1 digit
        if (effectiveDigits < 1){
            effectiveDigits = 1;
        }
        // Use SignatureService for cryptographically secure challenge generation
        return signatureService.generateSecureChallenge(effectiveDigits);
    }
}