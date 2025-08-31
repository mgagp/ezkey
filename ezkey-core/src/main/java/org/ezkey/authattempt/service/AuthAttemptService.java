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

import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptPendingRequest;
import org.ezkey.authattempt.domain.AuthAttemptPendingResponse;
import org.ezkey.authattempt.domain.AuthAttemptRespondRequest;
import org.ezkey.authattempt.domain.AuthAttemptRespondResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.AuthenticationResult;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.NoPendingAuthAttemptException;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@Transactional
public class AuthAttemptService {

    private static final Logger logger = LoggerFactory.getLogger(AuthAttemptService.class);

    private final AuthAttemptRepository authAttemptRepository;

    private final EnrollmentRepository enrollmentRepository;

    private final SignatureService signatureService;

    @Value("${ezkey.auth-attempt.challenge-digits:2}")
    private int challengeDigits;

    @PersistenceContext
    private EntityManager entityManager;

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

        // Create the authorization attempt
        AuthAttempt authAttempt = new AuthAttempt();

        authAttempt.setEnrollmentId(enrollment.getEnrollmentId());
        authAttempt.setAuthAttemptRead(false);
        authAttempt.setAuthAttemptResponded(false);
        authAttempt.setAuthAttemptAccepted(false);

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
        authAttempt.setCreatedAt(LocalDateTime.now());
        authAttempt.setExpiresAt(LocalDateTime.now().plusSeconds(120)); // TTL: 120 seconds

        // Save the authorization attempt
        AuthAttempt savedAuthAttempt = authAttemptRepository.save(authAttempt);

        // Create response
        AuthAttemptCreateResponse response = new AuthAttemptCreateResponse();
        response.setAuthAttemptId(savedAuthAttempt.getAuthAttemptId());

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

    /**
     * Processes a pending authentication request.
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
     * <b>Implementation Strategy:</b>
     * <ul>
     * <li><b>Step 1:</b> Complete validation before any database modification</li>
     * <li><b>Step 2:</b> Atomic lock and marking only if validation succeeds</li>
     * <li><b>Step 3:</b> Immediate marking to preserve read-once guarantee</li>
     * <li><b>Step 4:</b> Response generation with signed proof token</li>
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
     * @param request the pending authentication request
     * @return the pending authentication response with proof token
     * @throws IllegalArgumentException if validation fails (with secure error messages)
     * @throws IllegalStateException if the authentication attempt is already processed
     * @throws NoPendingAuthAttemptException if no pending authentication attempt is found
     * @since 2025
     */
    public AuthAttemptPendingResponse pending(AuthAttemptPendingRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(request.getEnrollmentId()).orElse(null);
        if (enrollment == null){
            logger.warn("Enrollment not found for ID: {}",request.getEnrollmentId());
            throw new IllegalArgumentException("Authentication request failed");
        }
        // Validate device public key
        String devicePublicKey = enrollment.getDevicePublicKey();
        if (devicePublicKey == null){
            logger.warn("Device public key missing for enrollment: {}",request.getEnrollmentId());
            throw new IllegalStateException("Authentication request failed");
        }
        // Validate signature
        boolean isValid = signatureService.validateSignature(request.getDeviceProofToken(),request.getDeviceProofTokenSigned(),devicePublicKey);
        if (!isValid){
            logger.warn("Invalid signature for enrollment: {}",request.getEnrollmentId());
            throw new IllegalArgumentException("Authentication request failed");
        }
        // Check device proof token uniqueness
        if (authAttemptRepository.existsByDeviceProofToken(request.getDeviceProofToken())){
            logger.warn("Device proof token already used for enrollment: {}",request.getEnrollmentId());
            throw new IllegalArgumentException("Authentication request failed");
        }
        // Find valid (non-expired) unread auth attempt
        LocalDateTime now = LocalDateTime.now();
        AuthAttempt authAttempt = authAttemptRepository.findAndLockMostRecentValidUnreadByEnrollmentId(request.getEnrollmentId(),now).orElse(null);
        if (authAttempt == null){
            throw new NoPendingAuthAttemptException("No pending authentication request");
        }
        // Double-check if already read (protection against race condition)
        if (Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())){
            logger.warn("Auth attempt already processed: {}",authAttempt.getAuthAttemptId());
            throw new IllegalStateException("Authentication request failed");
        }
        // Record the device proof token to ensure unicity
        authAttempt.setDeviceProofToken(request.getDeviceProofToken());
        authAttempt.setAuthAttemptRead(true);
        authAttemptRepository.save(authAttempt);

        AuthAttemptPendingResponse response = new AuthAttemptPendingResponse();
        response.setAuthAttemptId(authAttempt.getAuthAttemptId());
        response.setAuthAttemptProofToken(authAttempt.getAuthAttemptProofToken());
        response.setAuthAttemptProofTokenSignedByIntegration(signatureService.generateSignature(authAttempt.getAuthAttemptProofToken(),enrollment.getIntegrationPrivateKey()));
        if (authAttempt.getAuthAttemptChallenge() != null){
            response.setAuthAttemptChallengeRequired(true);
        } else{
            response.setAuthAttemptChallengeRequired(enrollment.getAuthAttemptChallengeRequired());
        }
        return response;
    }

    public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
        AuthAttemptRespondResponse response = new AuthAttemptRespondResponse();

        Optional<AuthAttempt> authAttemptOpt = authAttemptRepository.findById(request.getAuthAttemptId());
        if (authAttemptOpt.isEmpty()){
            response.setResult(AuthenticationResult.FAILED);
            response.setMessage("Auth attempt record not found");
            return response;
        }
        AuthAttempt authAttempt = authAttemptOpt.get();

        // Check if read
        if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())){
            response.setResult(AuthenticationResult.FAILED);
            response.setMessage("Auth attempt not read by device");
            return response;
        }
        // Check if superseded by a newer authentication attempt for the same enrollment
        Optional<AuthAttempt> newerAttempt = authAttemptRepository.findNewerAttemptByEnrollmentId(authAttempt.getEnrollmentId(),authAttempt.getCreatedAt());
        if (newerAttempt.isPresent()){
            logger.info("Auth attempt {} superseded by newer attempt {} for enrollment {}",authAttempt.getAuthAttemptId(),newerAttempt.get().getAuthAttemptId(),
                    authAttempt.getEnrollmentId());
            response.setResult(AuthenticationResult.EXPIRED);
            response.setMessage("Authentication attempt superseded by newer request");
            return response;
        }
        // Check if expired
        LocalDateTime now = LocalDateTime.now();
        if (authAttempt.getExpiresAt() != null && now.isAfter(authAttempt.getExpiresAt())){
            response.setResult(AuthenticationResult.EXPIRED);
            response.setMessage("Authentication attempt expired");
            return response;
        }
        // Find the enrollment
        Enrollment enrollment = enrollmentRepository.findById(authAttempt.getEnrollmentId()).orElse(null);
        if (enrollment == null){
            response.setResult(AuthenticationResult.FAILED);
            response.setMessage("Enrollment record not found");
            return response;
        }
        // Validate device public key
        String devicePublicKey = enrollment.getDevicePublicKey();
        if (devicePublicKey == null){
            response.setResult(AuthenticationResult.FAILED);
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
            authAttemptRepository.save(authAttempt);
            response.setResult(AuthenticationResult.FAILED);
            response.setMessage("Invalid signature for auth attempt code");
            return response;
        }
        // Validate challenge if required
        if (Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())){
            if (request.getAuthAttemptChallengeResponse() == null || !request.getAuthAttemptChallengeResponse().equals(authAttempt.getAuthAttemptChallenge())){
                authAttempt.setAuthAttemptAccepted(false);
                authAttempt.setAuthAttemptValid(false);
                authAttempt.setAuthAttemptResponded(true);
                authAttemptRepository.save(authAttempt);
                response.setResult(AuthenticationResult.FAILED);
                response.setMessage("Challenge value mismatch");
                return response;
            }
        }
        // Update authorization attempt
        authAttempt.setAuthAttemptAccepted(request.getAuthAttemptAccepted());
        authAttempt.setAuthAttemptValid(true);
        authAttempt.setAuthAttemptResponded(true);
        authAttemptRepository.save(authAttempt);

        // Set result based on user's choice
        if (Boolean.TRUE.equals(request.getAuthAttemptAccepted())){
            response.setResult(AuthenticationResult.APPROVED);
        } else{
            response.setResult(AuthenticationResult.DENIED);
        }
        response.setMessage("Auth attempt completed");
        return response;
    }

    /**
     * Waits for authentication response completion with configurable timeout and polling.
     * This refactored version avoids holding a single long-running transaction / connection.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuthAttemptWaitResponse waitForResponse(Integer authAttemptId,AuthAttemptWaitRequest request) {
        logger.debug("Starting wait operation for auth attempt {} with timeout={}s, polling={}s",authAttemptId,request.getTimeout(),request.getPolling());

        validateWaitRequest(request);

        long startTime = System.currentTimeMillis();
        long deadline = startTime + (request.getTimeout() * 1000L);
        int pollCount = 0;
        while (true){
            // Short-lived fetch (no long transaction held between iterations)
            AuthAttempt authAttempt = authAttemptRepository.findById(authAttemptId).orElseThrow(() -> new ResourceNotFoundException("Authorization attempt",authAttemptId));

            // Superseded check
            Optional<AuthAttempt> newerAttempt = authAttemptRepository.findNewerAttemptByEnrollmentId(authAttempt.getEnrollmentId(),authAttempt.getCreatedAt());
            if (newerAttempt.isPresent()){
                int waitDuration = (int) ((System.currentTimeMillis() - startTime) / 1000);
                logger.info("Auth attempt {} superseded by newer attempt {} for enrollment {} during wait operation",authAttemptId,newerAttempt.get().getAuthAttemptId(),
                        authAttempt.getEnrollmentId());
                return buildWaitResponse(authAttempt,"EXPIRED",false,waitDuration);
            }
            // Completed?
            if (Boolean.TRUE.equals(authAttempt.getAuthAttemptResponded())){
                int waitDuration = (int) ((System.currentTimeMillis() - startTime) / 1000);
                logger.info("Auth attempt {} completed after {}s ({} polls)",authAttemptId,waitDuration,pollCount);
                return buildWaitResponse(authAttempt,false,waitDuration);
            }
            // Expired?
            LocalDateTime now = LocalDateTime.now();
            if (authAttempt.getExpiresAt() != null && now.isAfter(authAttempt.getExpiresAt())){
                int waitDuration = (int) ((System.currentTimeMillis() - startTime) / 1000);
                logger.info("Auth attempt {} expired during wait at {}",authAttemptId,authAttempt.getExpiresAt());
                return buildWaitResponse(authAttempt,"EXPIRED",false,waitDuration);
            }
            long nowMs = System.currentTimeMillis();
            if (nowMs >= deadline){
                int waitDuration = (int) ((nowMs - startTime) / 1000);
                logger.info("Auth attempt {} timed out after {}s ({} polls)",authAttemptId,waitDuration,pollCount);
                return buildWaitResponse(authAttempt,true,waitDuration);
            }
            try{
                Thread.sleep(request.getPolling() * 1000L);
            } catch (InterruptedException ie){
                Thread.currentThread().interrupt();
                logger.warn("Wait operation for auth attempt {} was interrupted",authAttemptId);
                throw new RuntimeException("Wait operation interrupted",ie);
            }
            pollCount++;
            if (pollCount % 10 == 0){
                logger.debug("Auth attempt {} still pending after {} polls",authAttemptId,pollCount);
            }
        }
    }

    /**
     * Validates the wait request parameters.
     * <p>
     * Ensures that timeout and polling parameters are within acceptable ranges
     * and that polling is not greater than timeout.
     * </p>
     *
     * @param request the wait request to validate
     * @throws IllegalArgumentException if parameters are invalid
     */
    private void validateWaitRequest(AuthAttemptWaitRequest request) {
        if (request.getTimeout() == null || request.getTimeout() <= 0 || request.getTimeout() > 300){
            throw new IllegalArgumentException("Timeout must be between 1 and 300 seconds");
        }
        if (request.getPolling() == null || request.getPolling() <= 0 || request.getPolling() > 60){
            throw new IllegalArgumentException("Polling must be between 1 and 60 seconds");
        }
        if (request.getPolling() > request.getTimeout()){
            throw new IllegalArgumentException("Polling interval cannot be greater than timeout");
        }
    }

    /**
     * Builds the wait response with calculated status and metadata.
     * <p>
     * Creates a complete AuthAttemptWaitResponse with the authentication attempt data,
     * calculated status based on ENDPOINT.md rules, and wait operation metadata.
     * </p>
     *
     * @param authAttempt the authentication attempt data
     * @param timeoutReached whether the wait operation timed out
     * @param waitDuration the actual duration waited in seconds
     * @return the complete AuthAttemptWaitResponse
     */
    private AuthAttemptWaitResponse buildWaitResponse(AuthAttempt authAttempt,boolean timeoutReached,int waitDuration) {
        String status = calculateStatus(authAttempt);
        boolean completed = Boolean.TRUE.equals(authAttempt.getAuthAttemptResponded());

        return new AuthAttemptWaitResponse(authAttempt,status,completed,timeoutReached,waitDuration,LocalDateTime.now());
    }

    /**
     * Builds the wait response with a specific status for special cases.
     * <p>
     * Creates a complete AuthAttemptWaitResponse with a specific status for cases
     * where the normal status calculation doesn't apply (e.g., superseded attempts).
     * </p>
     *
     * @param authAttempt the authentication attempt data
     * @param status the specific status to use
     * @param timeoutReached whether the wait operation timed out
     * @param waitDuration the actual duration waited in seconds
     * @return the complete AuthAttemptWaitResponse
     */
    private AuthAttemptWaitResponse buildWaitResponse(AuthAttempt authAttempt,String status,boolean timeoutReached,int waitDuration) {
        boolean completed = Boolean.TRUE.equals(authAttempt.getAuthAttemptResponded());

        return new AuthAttemptWaitResponse(authAttempt,status,completed,timeoutReached,waitDuration,LocalDateTime.now());
    }

    /**
     * Calculates the authentication status based on the rules defined in ENDPOINT.md.
     * <p>
     * This method implements the status calculation logic as specified in the project
     * documentation. The status is determined by checking the authentication attempt
     * flags in a specific order of priority.
     * </p>
     *
     * @param authAttempt the authentication attempt entity
     * @return the calculated status string (PENDING, READ, INVALID, REJECTED, ACCEPTED, EXPIRED)
     */
    private String calculateStatus(AuthAttempt authAttempt) {
        // Check if expired first (highest priority)
        LocalDateTime now = LocalDateTime.now();
        if (authAttempt.getExpiresAt() != null && now.isAfter(authAttempt.getExpiresAt())){
            return "EXPIRED";
        }
        // #1: authAttemptRead null or false : PENDING
        if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptRead())){
            return "PENDING";
        }
        // #2: authAttemptRead true and authAttemptResponded null or false : READ
        else if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptResponded())){
            return "READ";
        }
        // #3: authAttemptValid null or false : INVALID
        else if (!Boolean.TRUE.equals(authAttempt.getAuthAttemptValid())){
            return "INVALID";
        }
        // #4: authAttemptAccepted null or false : REJECTED else ACCEPTED
        else if (Boolean.TRUE.equals(authAttempt.getAuthAttemptAccepted())){
            return "ACCEPTED";
        } else{
            return "REJECTED";
        }
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