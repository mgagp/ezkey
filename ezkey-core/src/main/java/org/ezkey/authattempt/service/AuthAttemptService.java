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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.NoPendingAuthAttemptException;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.signature.SignatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for managing authentication attempts in the Ezkey MFA system.
 *
 * <p>This service acts as the main coordinator for authentication attempt operations, delegating
 * complex business logic to specialized services while maintaining a unified public API. It
 * implements the mobile-first authentication model where devices poll for pending requests and
 * submit cryptographically signed responses.
 *
 * <p><b>Architecture:</b> This service uses a delegation pattern to specialized services:
 *
 * <ul>
 *   <li><b>AuthAttemptPendingService:</b> Handles pending authentication request processing
 *   <li><b>AuthAttemptRespondService:</b> Processes authentication responses from mobile devices
 *   <li><b>AuthAttemptWaitService:</b> Manages polling and wait operations
 * </ul>
 *
 * <p><b>Authentication Flow Support:</b>
 *
 * <ul>
 *   <li><b>Admin Flow:</b> Creates authentication attempts via Admin API for web applications
 *   <li><b>Mobile Flow:</b> Processes pending requests and responses via Auth API for mobile
 *       devices
 *   <li><b>Wait Flow:</b> Provides synchronous polling for web applications awaiting authentication
 * </ul>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li><b>Cryptographic Validation:</b> Verifies device and integration signatures using Ed25519
 *   <li><b>Enrollment Proof Tokens:</b> Prevents enumeration attacks through secure token-based
 *       identification
 *   <li><b>Anti-Replay Protection:</b> Ensures device proof tokens are used only once
 *   <li><b>Challenge-Based Security:</b> Implements configurable numeric challenges for additional
 *       verification
 * </ul>
 *
 * <p><b>Transaction Management:</b> This service uses Spring's declarative transaction management
 * with appropriate propagation settings. Specialized services handle their own transaction
 * boundaries as needed.
 *
 * <p><b>Integration Points:</b>
 *
 * <ul>
 *   <li><b>AuthAttemptRepository:</b> Data persistence layer for authentication attempts
 *   <li><b>EnrollmentRepository:</b> Enrollment data access for validation and device management
 *   <li><b>SignatureService:</b> Cryptographic operations for signature validation
 *   <li><b>Specialized Services:</b> Delegated business logic for specific operations
 * </ul>
 *
 * <p><b>Error Handling:</b> Implements comprehensive error handling with secure error messages to
 * prevent information leakage while providing detailed logging for debugging and monitoring
 * purposes.
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li><b>Read-Once Guarantee:</b> Authentication attempts can only be read once to prevent replay
 *       attacks
 *   <li><b>Efficient Polling:</b> Optimized database queries for pending request checking
 *   <li><b>Timeout Management:</b> Configurable timeouts prevent resource exhaustion
 *   <li><b>Service Separation:</b> Specialized services allow for targeted optimization
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
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
public class AuthAttemptService {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptService.class);

  private final AuthAttemptRepository authAttemptRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final SignatureService signatureService;
  private final EzkeyCoreProperties ezkeyCoreProperties;

  // Specialized services for specific operations
  private final AuthAttemptPendingService pendingService;
  private final AuthAttemptRespondService respondService;
  private final AuthAttemptWaitService waitService;

  @PersistenceContext
  private EntityManager entityManager; // TODO Mandatory for unit tests, remove when possible

  /**
   * Constructs the authentication attempt service with required dependencies.
   *
   * @param authAttemptRepository the JPA repository for authentication attempts
   * @param enrollmentRepository the JPA repository for enrollments
   * @param signatureService the signature service for cryptographic operations
   * @param ezkeyCoreProperties the ezkey core configuration properties
   * @param pendingService the specialized service for pending operations
   * @param respondService the specialized service for respond operations
   * @param waitService the specialized service for wait operations
   */
  public AuthAttemptService(
      AuthAttemptRepository authAttemptRepository,
      EnrollmentRepository enrollmentRepository,
      SignatureService signatureService,
      EzkeyCoreProperties ezkeyCoreProperties,
      AuthAttemptPendingService pendingService,
      AuthAttemptRespondService respondService,
      AuthAttemptWaitService waitService) {
    this.authAttemptRepository = authAttemptRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.signatureService = signatureService;
    this.ezkeyCoreProperties = ezkeyCoreProperties;
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
  @Transactional(readOnly = true)
  public AuthAttempt getById(Integer id) {
    return authAttemptRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Authentication attempt", id));
  }

  /**
   * Retrieves all authentication attempts.
   *
   * @return list of all authentication attempts
   */
  @Transactional(readOnly = true)
  public List<AuthAttempt> getAll() {
    return authAttemptRepository.findAll();
  }

  /**
   * Searches authentication attempts with optional filters and pagination.
   *
   * <p>This method supports multi-criteria search for administrative and operational purposes. All
   * filter parameters are optional - if null, they are ignored in the query. Results are ordered by
   * creation date descending (newest first) for operational relevance.
   *
   * <p><b>Tenant Scoping:</b> If tenantId is provided (non-null), results are filtered to only
   * include auth attempts whose enrollment's integration belongs to that tenant. This enables
   * tenant isolation for TenantAdmins while allowing GlobalAdmins to see all auth attempts (by
   * passing null).
   *
   * <p><b>Use Case:</b> Security operators monitoring authentication attempts, forensic analysis,
   * and compliance reporting.
   *
   * @param status optional authentication attempt status filter
   * @param enrollmentId optional enrollment ID filter
   * @param integrationId optional integration ID filter
   * @param createdAfter optional start of date range filter
   * @param createdBefore optional end of date range filter
   * @param tenantId optional tenant ID filter for tenant scoping (null = all tenants, for
   *     GlobalAdmin)
   * @param pageable pagination parameters
   * @return page of authentication attempts matching criteria
   */
  @Transactional(readOnly = true)
  public Page<AuthAttempt> findByFilters(
      AuthAttemptStatus status,
      Integer enrollmentId,
      Integer integrationId,
      OffsetDateTime createdAfter,
      OffsetDateTime createdBefore,
      Integer tenantId,
      Pageable pageable) {

    Specification<AuthAttempt> spec =
        (root, query, cb) -> {
          List<Predicate> predicates = new ArrayList<>();

          if (status != null) {
            predicates.add(cb.equal(root.get("authAttemptStatus"), status));
          }

          if (enrollmentId != null) {
            predicates.add(cb.equal(root.get("enrollmentId"), enrollmentId));
          }

          if (integrationId != null) {
            // Join with Enrollment to filter by integrationId
            // Assuming AuthAttempt has a relationship to Enrollment or we subquery
            // Since AuthAttempt entity only has enrollmentId (Integer) and no @ManyToOne
            // relationship defined in the code I saw earlier, we must use a subquery
            // or rely on the repository to have added the relationship.
            // Let's check AuthAttempt entity again. It has enrollmentId field.
            // If no relationship, we use a subquery.

            // Subquery: enrollmentId IN
            // (SELECT e.enrollmentId FROM Enrollment e WHERE e.integrationId = :integrationId)
            var subquery = query.subquery(Integer.class);
            var enrollmentRoot = subquery.from(Enrollment.class);
            subquery.select(enrollmentRoot.get("enrollmentId"));
            subquery.where(cb.equal(enrollmentRoot.get("integrationId"), integrationId));

            predicates.add(root.get("enrollmentId").in(subquery));
          }

          if (createdAfter != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
          }

          if (createdBefore != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
          }

          // Tenant scoping: filter by enrollment's integration's tenant if tenantId is provided
          // Since AuthAttempt doesn't have direct JPA relations, we use nested subqueries:
          // AuthAttempt.enrollmentId -> Enrollment.integrationId -> Integration.tenant.tenantId
          if (tenantId != null) {
            // Subquery 1: Get integrationIds for the tenant
            var integrationSubquery = query.subquery(Integer.class);
            var integrationRoot = integrationSubquery.from(Integration.class);
            integrationSubquery.select(integrationRoot.get("id"));
            integrationSubquery.where(
                cb.equal(integrationRoot.get("tenant").get("tenantId"), tenantId));

            // Subquery 2: Get enrollmentIds for those integrations
            var enrollmentSubquery = query.subquery(Integer.class);
            var enrollmentRoot = enrollmentSubquery.from(Enrollment.class);
            enrollmentSubquery.select(enrollmentRoot.get("enrollmentId"));
            enrollmentSubquery.where(enrollmentRoot.get("integrationId").in(integrationSubquery));

            // Filter AuthAttempts by enrollmentId in the subquery
            predicates.add(root.get("enrollmentId").in(enrollmentSubquery));
          }

          // Force ordering by createdAt DESC if not specified in pageable
          if (pageable.getSort().isUnsorted()) {
            query.orderBy(cb.desc(root.get("createdAt")));
          }

          return cb.and(predicates.toArray(new Predicate[0]));
        };

    return authAttemptRepository.findAll(spec, pageable);
  }

  /**
   * Creates a new authentication attempt. TTL from {@code ezkey.core.auth-attempt.ttl-seconds}.
   *
   * @param authRequest the creation request
   * @return the create response
   * @throws IllegalArgumentException if enrollment not found or validation fails
   */
  @Transactional
  public AuthAttemptCreateResponse create(AuthAttemptCreateRequest authRequest) {
    // Find the enrollment
    Enrollment enrollment =
        enrollmentRepository
            .findById(authRequest.getEnrollmentId())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Enrollment not found for ID: " + authRequest.getEnrollmentId()));

    // Security gate: reject auth attempts for inactive or non-verified enrollments.
    // This covers enrollments that have been administratively deactivated or revoked.
    // Logged at WARN because an Integration API caller using a known enrollmentId against a revoked
    // enrollment is a security-relevant event (stale integration or suspicious probe).
    if (!Boolean.TRUE.equals(enrollment.getActive())) {
      logger.warn(
          "Auth attempt creation rejected: enrollment {} is not active (status={})",
          authRequest.getEnrollmentId(),
          enrollment.getStatus());
      throw new EnrollmentInactiveException(
          "Enrollment is not active for authentication. Enrollment ID: "
              + authRequest.getEnrollmentId());
    }
    if (!EnrollmentStatus.VERIFIED.equals(enrollment.getStatus())) {
      logger.warn(
          "Auth attempt creation rejected: enrollment {} is not in VERIFIED status (status={})",
          authRequest.getEnrollmentId(),
          enrollment.getStatus());
      throw new EnrollmentInactiveException(
          "Enrollment is not in VERIFIED status. Current status: "
              + enrollment.getStatus()
              + ". Enrollment ID: "
              + authRequest.getEnrollmentId());
    }

    // Supersession: Mark existing non-final attempts as EXPIRED
    List<AuthAttempt> existingAttempts =
        authAttemptRepository.findByEnrollmentIdAndStatusIn(
            authRequest.getEnrollmentId(),
            List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ));
    if (!existingAttempts.isEmpty()) {
      List<Integer> attemptIds =
          existingAttempts.stream().map(AuthAttempt::getAuthAttemptId).toList();

      int updatedCount =
          authAttemptRepository.updateStatusForMultipleAttempts(
              attemptIds, AuthAttemptStatus.EXPIRED);
      logger.info(
          "Supersession: Marked {} existing auth attempts as EXPIRED for enrollment {}",
          updatedCount,
          authRequest.getEnrollmentId());
    }
    // Create the authorization attempt
    AuthAttempt authAttempt = new AuthAttempt();

    authAttempt.setEnrollmentId(enrollment.getEnrollmentId());
    authAttempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);

    // Generate challenge if required
    boolean shouldGenerateChallenge =
        Boolean.TRUE.equals(enrollment.getAuthAttemptChallengeRequired())
            || Boolean.TRUE.equals(authRequest.getChallengeRequested());
    if (shouldGenerateChallenge) {
      authAttempt.setAuthAttemptChallenge(generateChallenge());
      logger.debug(
          "Generated challenge code for auth attempt: {}", authAttempt.getAuthAttemptChallenge());
    } else {
      authAttempt.setAuthAttemptChallenge(null);
      logger.debug("No challenge code generated for auth attempt");
    }
    authAttempt.setAuthAttemptProofToken(signatureService.generateProofToken());

    // Propagate optional contextual authentication fields
    authAttempt.setContextTitle(authRequest.getContextTitle());
    authAttempt.setContextMessage(authRequest.getContextMessage());
    authAttempt.setDemoMitmSignatureEnabled(
        Boolean.TRUE.equals(authRequest.getDemoMitmSignatureRequested()));

    int ttlSeconds = ezkeyCoreProperties.getAuthAttempt().getTtlSeconds();
    authAttempt.setCreatedAt(OffsetDateTime.now());
    authAttempt.setExpiresAt(OffsetDateTime.now().plusSeconds(ttlSeconds));

    // Save the authorization attempt
    AuthAttempt savedAuthAttempt = authAttemptRepository.save(authAttempt);

    // Create response — echo context back for confirmation
    AuthAttemptCreateResponse response = new AuthAttemptCreateResponse();
    response.setAuthAttemptId(savedAuthAttempt.getAuthAttemptId());
    response.setAuthAttemptChallenge(
        savedAuthAttempt.getAuthAttemptChallenge()); // Include challenge if generated
    response.setCreatedAt(savedAuthAttempt.getCreatedAt()); // Required for FK to partitioned table
    response.setTimeoutSeconds(ttlSeconds);
    response.setExpiresAt(savedAuthAttempt.getExpiresAt()); // Absolute expiration timestamp
    response.setContextTitle(savedAuthAttempt.getContextTitle());
    response.setContextMessage(savedAuthAttempt.getContextMessage());

    return response;
  }

  /**
   * Updates an authentication attempt.
   *
   * @param authAttempt the authentication attempt to update
   * @return the updated authentication attempt
   */
  @Transactional
  public AuthAttempt update(AuthAttempt authAttempt) {
    return authAttemptRepository.save(authAttempt);
  }

  /**
   * Cancels a pending or read authentication attempt by marking it as expired.
   *
   * <p>This method allows client applications to proactively cancel authentication requests that
   * are still waiting for user response. Only attempts in PENDING or READ status can be cancelled;
   * attempts that are already in a final state (ACCEPTED, REJECTED, INVALID, EXPIRED) cannot be
   * cancelled.
   *
   * <p><b>Use Case:</b> When a user decides to abort the authentication flow (e.g., clicks "Cancel"
   * or navigates away), the client application can call this endpoint to immediately mark the
   * attempt as expired, allowing any waiting threads to terminate promptly.
   *
   * @param authAttemptId the authentication attempt ID to cancel
   * @return the updated authentication attempt with EXPIRED status
   * @throws ResourceNotFoundException if the authentication attempt is not found
   * @throws IllegalArgumentException if the authentication attempt is already in a final state
   */
  @Transactional
  public AuthAttempt cancel(Integer authAttemptId) {
    AuthAttempt authAttempt =
        authAttemptRepository
            .findById(authAttemptId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Authentication attempt", authAttemptId));

    // Only allow cancellation of non-final states
    if (authAttempt.getAuthAttemptStatus() != AuthAttemptStatus.PENDING
        && authAttempt.getAuthAttemptStatus() != AuthAttemptStatus.READ) {
      throw new IllegalArgumentException(
          "Cannot cancel authentication attempt with status: "
              + authAttempt.getAuthAttemptStatus()
              + ". Only PENDING or READ attempts can be cancelled.");
    }

    // Mark as expired
    authAttempt.setAuthAttemptStatus(AuthAttemptStatus.EXPIRED);
    AuthAttempt updatedAttempt = authAttemptRepository.save(authAttempt);

    logger.info(
        "Authentication attempt {} cancelled (marked as EXPIRED) by user request", authAttemptId);

    return updatedAttempt;
  }

  /**
   * Process pending authentication attempt request using secure enrollment proof token.
   *
   * <p>Security Enhancement: Validates enrollment ownership through cryptographic proof token
   * instead of relying on URL-based enrollment ID, preventing enumeration attacks.
   *
   * <p>This method implements the fundamental Ezkey security principle of read-once guarantee. It
   * validates the request completely before locking the authentication attempt to ensure that each
   * authentication attempt can only be read once by a legitimate device.
   *
   * <p><b>Security Principle - Read-Once Guarantee:</b>
   *
   * <ul>
   *   <li>Each authentication attempt can only be read once by a legitimate device
   *   <li>Once read, the attempt is marked as processed and cannot be read again
   *   <li>This prevents replay attacks and ensures proof-of-possession
   *   <li>The proof token returned can only be obtained by the legitimate reader
   * </ul>
   *
   * <p><b>Security Enhancement:</b> This method now uses enrollmentProofToken to find the
   * enrollment instead of relying on enrollmentId from the URL path. This prevents enumeration
   * attacks while maintaining the security of the authentication flow.
   *
   * <p><b>Implementation Strategy:</b>
   *
   * <ul>
   *   <li><b>Step 1:</b> Find enrollment by proof token and validate it's active
   *   <li><b>Step 2:</b> Validate enrollment ID matches the proof token
   *   <li><b>Step 3:</b> Complete validation before any database modification
   *   <li><b>Step 4:</b> Atomic lock and marking only if validation succeeds
   *   <li><b>Step 5:</b> Immediate marking to preserve read-once guarantee
   *   <li><b>Step 6:</b> Response generation with signed proof token
   * </ul>
   *
   * <p><b>Error Handling:</b>
   *
   * <ul>
   *   <li>Uses secure error messages to prevent information leakage
   *   <li>Logs detailed information for debugging purposes
   *   <li>Maintains read-once guarantee even in error scenarios
   * </ul>
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
   *
   * <p>This method delegates to the specialized AuthAttemptPendingService to handle the complex
   * logic of processing pending authentication requests while maintaining the same public API for
   * backward compatibility.
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
   *
   * <p>This method handles the completion of an authentication attempt when a mobile device submits
   * its response (approve/deny) with cryptographic proof. It validates the device signature, checks
   * challenge responses if required, and updates the authentication attempt status accordingly.
   *
   * <p><b>Security Validations:</b>
   *
   * <ul>
   *   <li><b>Signature Verification:</b> Validates device signature using enrollment public key
   *   <li><b>Challenge Validation:</b> Verifies challenge response if required by enrollment
   *   <li><b>Status Validation:</b> Ensures authentication attempt is in PENDING status
   *   <li><b>Proof Token Validation:</b> Verifies authentication attempt proof token signature
   * </ul>
   *
   * <p><b>Response Processing:</b> Based on the user's decision (accept/reject) and validation
   * results, the method updates the authentication attempt status to ACCEPTED, REJECTED, or
   * INVALID.
   *
   * @param request the authentication response request from mobile device
   * @return the authentication response result with status and message
   * @see AuthAttemptRespondRequest
   * @see AuthAttemptRespondResponse
   * @see AuthenticationResult
   */
  /**
   * Processes an authentication response from a mobile device.
   *
   * <p>This method delegates to the specialized AuthAttemptRespondService to handle the complex
   * logic of processing authentication responses while maintaining the same public API for backward
   * compatibility.
   *
   * @param request the authentication response request from mobile device
   * @return the authentication response result with status and message
   */
  public AuthAttemptRespondResponse respond(AuthAttemptRespondRequest request) {
    return respondService.respond(request);
  }

  /**
   * Waits for authentication response completion with configurable timeout and polling.
   *
   * <p>This method delegates to the specialized AuthAttemptWaitService to handle the complex logic
   * of polling and waiting while maintaining the same public API for backward compatibility.
   *
   * @param authAttemptId the ID of the authentication attempt to wait for
   * @param request the wait request containing timeout and polling configuration
   * @return the wait response with final status and metadata
   */
  public AuthAttemptWaitResponse waitForResponse(
      Integer authAttemptId, AuthAttemptWaitRequest request) {
    return waitService.waitForResponse(authAttemptId, request);
  }

  /**
   * Generates a challenge code with configurable number of digits using secure random generation.
   *
   * <p>This method generates a cryptographically secure challenge code based on the configured
   * number of digits. The default is 2 digits, with a maximum of 6 digits. If a value greater than
   * 6 is configured, it will be truncated to 6 with a trace log message. Challenge generation is
   * delegated to {@link SignatureService} to ensure cryptographic security using SecureRandom.
   *
   * @return the generated challenge code as an integer
   * @since 2025
   */
  private Integer generateChallenge() {
    int effectiveDigits = ezkeyCoreProperties.getAuthAttempt().getChallengeDigits();

    // Validate and truncate if necessary
    if (effectiveDigits > 6) {
      logger.trace(
          "Challenge digits configured as {} exceeds maximum of 6, truncating to 6",
          ezkeyCoreProperties.getAuthAttempt().getChallengeDigits());
      effectiveDigits = 6;
    }
    // Ensure minimum of 1 digit
    if (effectiveDigits < 1) {
      effectiveDigits = 1;
    }
    // Use SignatureService for cryptographically secure challenge generation
    return signatureService.generateSecureChallenge(effectiveDigits);
  }
}
