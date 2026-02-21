/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EnrollmentBindService
 * Description: Specialized service for handling enrollment binding operations.
 */

package org.ezkey.enrollment.service;

import java.util.Optional;
import org.ezkey.enrollment.domain.EnrollmentBindRequest;
import org.ezkey.enrollment.domain.EnrollmentBindResponse;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Specialized service for handling enrollment binding operations.
 *
 * <p>
 * This service manages the enrollment binding process where a mobile device
 * establishes a secure
 * connection with an integration. It implements the read-once guarantee
 * security principle and
 * validates enrollment proof tokens.
 *
 * <p>
 * <b>Security Features:</b>
 *
 * <ul>
 * <li><b>Read-Once Guarantee:</b> Each enrollment can only be bound once
 * <li><b>Proof Token Validation:</b> Prevents unauthorized enrollment access
 * <li><b>State Validation:</b> Ensures enrollment is in correct state for
 * binding
 * <li><b>Atomic Operations:</b> Uses row-level locking for thread safety
 * </ul>
 *
 * <p>
 * <b>Transaction Management:</b> This service uses Spring's declarative
 * transaction management
 * to ensure data consistency during the enrollment binding process.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EnrollmentBindRequest
 * @see EnrollmentBindResponse
 * @see Enrollment
 * @see Integration
 */
@Service
@Transactional
public class EnrollmentBindService {

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentBindService.class);

    private final EnrollmentRepository enrollmentRepository;
    private final IntegrationRepository integrationRepository;
    private final EzkeyAdminRepository ezkeyAdminRepository;

    /**
     * Constructs the bind service with required dependencies.
     *
     * @param enrollmentRepository  the JPA repository for enrollment operations
     * @param integrationRepository the JPA repository for integration operations
     * @param ezkeyAdminRepository  the JPA repository for admin lookup (admin MFA
     *                              enrollments)
     */
    public EnrollmentBindService(
            EnrollmentRepository enrollmentRepository,
            IntegrationRepository integrationRepository,
            EzkeyAdminRepository ezkeyAdminRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.integrationRepository = integrationRepository;
        this.ezkeyAdminRepository = ezkeyAdminRepository;
    }

    /**
     * Binds an enrollment to a device.
     *
     * <p>
     * This method validates the request completely before locking the enrollment to
     * ensure the
     * read-once guarantee is maintained. The method follows the security principle
     * of validation
     * before modification to prevent transaction rollbacks that could compromise
     * the read-once
     * guarantee.
     *
     * @param request the bind request
     * @return the bind response
     * @throws IllegalArgumentException if validation fails (with secure error
     *                                  messages)
     * @throws IllegalStateException    if the enrollment is already processed
     */
    public EnrollmentBindResponse bind(EnrollmentBindRequest request) {
        logger.info(
                "Starting enrollment bind process for enrollment ID: {}", request.getEnrollmentId());

        // Step 1: Validate enrollment and proof token
        Enrollment enrollment = validateEnrollment(request);

        // Step 2: Load integration and resolve i18n
        Integration integration = loadIntegration(enrollment.getIntegrationId());
        String integrationName = resolveIntegrationName(integration, request.getLanguage());
        String integrationDescription = resolveIntegrationDescription(integration, request.getLanguage());

        // Step 3: Acquire lock and validate state
        Enrollment lockedEnrollment = acquireLockAndValidate(request);

        // Step 4: Mark as BOUND (read-once guarantee)
        markAsBound(lockedEnrollment);

        // Step 5: Build and return response
        return buildBindResponse(enrollment, integration, integrationName, integrationDescription);
    }

    /**
     * Validates the enrollment using the proof token and enrollment ID.
     *
     * <p>
     * This method ensures that the enrollment exists, is active, and that the
     * provided enrollment
     * ID matches the proof token to prevent unauthorized access.
     *
     * @param request the bind request containing enrollment proof token and ID
     * @return the validated enrollment
     * @throws IllegalArgumentException if enrollment validation fails
     */
    private Enrollment validateEnrollment(EnrollmentBindRequest request) {
        logger.debug(
                "Step 1: Performing read-only pre-checks with proof token validation for enrollment ID: {}",
                request.getEnrollmentId());

        String proofTokenHash = SensitiveDataHasher.sha256Hex(request.getEnrollmentProofToken());

        if (proofTokenHash == null) {
            logger.warn(
                    "Validation failed: Missing or blank enrollment proof token for ID: {}",
                    request.getEnrollmentId());
            throw new IllegalArgumentException("Enrollment binding failed");
        }

        Enrollment enrollment = enrollmentRepository
                .findByEnrollmentIdAndEnrollmentProofTokenHash(
                        request.getEnrollmentId(), proofTokenHash)
                .orElse(null);

        if (enrollment == null) {
            logger.warn(
                    "Validation failed: Enrollment not found or invalid proof token for ID: {}",
                    request.getEnrollmentId());
            throw new IllegalArgumentException("Enrollment binding failed");
        }

        logger.debug(
                "Enrollment found with valid proof token: ID={}, Status={}, IntegrationId={}",
                enrollment.getEnrollmentId(),
                enrollment.getStatus(),
                enrollment.getIntegrationId());

        // Short-circuit: if already processed, avoid acquiring a lock
        if (enrollment.getStatus() != EnrollmentStatus.CREATED) {
            logger.warn(
                    "Validation failed: Enrollment already processed - ID: {}, Status: {}",
                    enrollment.getEnrollmentId(),
                    enrollment.getStatus());
            throw new IllegalStateException("Enrollment already bound by a device");
        }

        logger.debug("Enrollment status validation passed: Status is CREATED");
        return enrollment;
    }

    /**
     * Loads the integration for the enrollment using a read-only, eagerly-fetched
     * query.
     *
     * <p>
     * <b>Concurrency safety:</b> This method intentionally uses {@link
     * IntegrationRepository#findByIdWithI18nAndTenant} instead of the standard
     * {@code findById()}.
     * The {@code Integration.i18n} association is mapped with
     * {@code CascadeType.ALL + orphanRemoval
     * = true}: if a standard managed entity is loaded by multiple concurrent
     * sessions (e.g., all
     * admin MFA enrollments share integration_id=1), Hibernate can detect that two
     * entity states hold
     * a reference to the same collection bag and raise:
     *
     * <pre>
     * HibernateException: Found shared references to a collection:
     *     org.ezkey.integration.domain.entity.Integration.i18n
     * </pre>
     *
     * <p>
     * The read-only hint prevents Hibernate from snapshotting or dirty-checking
     * this entity,
     * eliminating the ownership conflict. The JOIN FETCH also avoids separate
     * lazy-load round-trips
     * for {@code i18n} and {@code tenant}.
     *
     * <p>
     * <b>Rule:</b> Always use this method (or an equivalent read-only fetch) when
     * the integration
     * is only read. Only use {@code findById()} when you intend to persist changes
     * to the integration
     * record itself.
     *
     * @param integrationId the integration ID
     * @return the integration entity (read-only, with i18n and tenant fetched)
     * @throws IllegalStateException if integration is not found
     */
    private Integration loadIntegration(Integer integrationId) {
        logger.debug("Step 2: Loading integration (read-only) for ID: {}", integrationId);

        Optional<Integration> integrationOpt = integrationRepository.findByIdWithI18nAndTenant(integrationId);
        if (integrationOpt.isEmpty()) {
            logger.warn("Validation failed: Integration not found for integration ID: {}", integrationId);
            throw new IllegalStateException("Enrollment binding failed");
        }

        Integration integration = integrationOpt.get();
        logger.debug("Integration loaded successfully: ID={}", integration.getId());
        return integration;
    }

    /**
     * Resolves the integration name based on language preference.
     *
     * <p>
     * This method attempts to find the integration name in the requested language,
     * falling back to
     * the first available language if the requested language is not found.
     *
     * @param integration the integration entity
     * @param language    the requested language
     * @return the resolved integration name
     */
    private String resolveIntegrationName(Integration integration, String language) {
        logger.debug("Step 3: Resolving integration name for language: {}", language);

        if (integration.getI18n() != null && !integration.getI18n().isEmpty()) {
            // Try to find the requested language first
            Optional<String> name = integration.getI18n().stream()
                    .filter(i18n -> i18n.getLanguage().equals(language))
                    .map(i18n -> i18n.getName())
                    .findFirst();

            if (name.isPresent()) {
                logger.debug("Found integration name for language {}: {}", language, name.get());
                return name.get();
            }

            // Fallback to first available language
            String fallbackName = integration.getI18n().get(0).getName();
            logger.debug("Using fallback integration name: {}", fallbackName);
            return fallbackName;
        }

        logger.debug("No i18n entries found for integration");
        return null;
    }

    /**
     * Resolves the integration description based on language preference.
     *
     * <p>
     * This method attempts to find the integration description in the requested
     * language, falling
     * back to the first available language if the requested language is not found.
     *
     * @param integration the integration entity
     * @param language    the requested language
     * @return the resolved integration description
     */
    private String resolveIntegrationDescription(Integration integration, String language) {
        logger.debug("Resolving integration description for language: {}", language);

        if (integration.getI18n() != null && !integration.getI18n().isEmpty()) {
            // Try to find the requested language first
            Optional<String> description = integration.getI18n().stream()
                    .filter(i18n -> i18n.getLanguage().equals(language))
                    .map(i18n -> i18n.getDescription())
                    .findFirst();

            if (description.isPresent()) {
                logger.debug(
                        "Found integration description for language {}: {}", language, description.get());
                return description.get();
            }

            // Fallback to first available language
            String fallbackDescription = integration.getI18n().get(0).getDescription();
            logger.debug("Using fallback integration description: {}", fallbackDescription);
            return fallbackDescription;
        }

        logger.debug("No i18n entries found for integration");
        return null;
    }

    /**
     * Acquires a lock on the enrollment and validates its state.
     *
     * <p>
     * This method implements the read-once guarantee by atomically locking and
     * validating the
     * enrollment state.
     *
     * @param request the bind request
     * @return the locked enrollment
     * @throws IllegalArgumentException if enrollment is not found or already bound
     * @throws IllegalStateException    if enrollment is in invalid state
     */
    private Enrollment acquireLockAndValidate(EnrollmentBindRequest request) {
        logger.debug("Step 4: Acquiring lock for enrollment ID: {}", request.getEnrollmentId());

        Enrollment enrollment = enrollmentRepository.findAndLockUnreadById(request.getEnrollmentId()).orElse(null);

        if (enrollment == null) {
            logger.warn(
                    "Validation failed: Enrollment not found or already bound after lock acquisition for ID:"
                            + " {}",
                    request.getEnrollmentId());
            throw new IllegalArgumentException("Enrollment not found or already bound");
        }

        // Additional validation: ensure the proof token still matches after lock
        if (!enrollment.getEnrollmentProofToken().equals(request.getEnrollmentProofToken())) {
            logger.warn(
                    "Validation failed: Proof token mismatch after lock acquisition for ID: {}",
                    request.getEnrollmentId());
            throw new IllegalArgumentException("Enrollment binding failed");
        }

        logger.debug("Lock acquired successfully for enrollment ID: {}", enrollment.getEnrollmentId());

        if (enrollment.getStatus() != EnrollmentStatus.CREATED) {
            logger.warn(
                    "Validation failed: Enrollment already processed after lock - ID: {}, Status: {}",
                    enrollment.getEnrollmentId(),
                    enrollment.getStatus());
            throw new IllegalStateException("Enrollment already bound by a device");
        }

        logger.debug("Post-lock status validation passed: Status is CREATED");
        return enrollment;
    }

    /**
     * Marks the enrollment as BOUND to implement the read-once guarantee.
     *
     * <p>
     * This method updates the enrollment status to BOUND, ensuring that the
     * enrollment can only be
     * bound once.
     *
     * @param enrollment the enrollment to mark as bound
     */
    private void markAsBound(Enrollment enrollment) {
        logger.info("Step 5: Marking enrollment as BOUND - ID: {}", enrollment.getEnrollmentId());
        enrollment.setStatus(EnrollmentStatus.BOUND);
        enrollmentRepository.save(enrollment);
        logger.info(
                "Enrollment successfully bound - ID: {}, Status: BOUND", enrollment.getEnrollmentId());
    }

    /**
     * Builds the bind response with integration information.
     *
     * <p>
     * This method creates a complete response containing the enrollment data,
     * integration
     * information, and proof token for the mobile device.
     *
     * @param enrollment             the enrollment data
     * @param integration            the integration data
     * @param integrationName        the resolved integration name
     * @param integrationDescription the resolved integration description
     * @return the complete bind response
     */
    private EnrollmentBindResponse buildBindResponse(
            Enrollment enrollment,
            Integration integration,
            String integrationName,
            String integrationDescription) {
        logger.debug(
                "Step 5: Building bind response for enrollment ID: {}", enrollment.getEnrollmentId());

        EnrollmentBindResponse response = new EnrollmentBindResponse();
        response.setEnrollmentId(enrollment.getEnrollmentId());
        response.setEnrollmentName(enrollment.getEnrollmentName());
        response.setIntegrationPublicKey(enrollment.getIntegrationPublicKey());
        response.setEnrollmentProofToken(enrollment.getEnrollmentProofToken());
        response.setIntegrationLogo(integration.getLogo());
        response.setIntegrationName(integrationName);
        response.setIntegrationDescription(integrationDescription);

        // Tenant resolution:
        // - For regular (non-system) integrations, use the integration's tenant.
        // - For system integrations (used by admin passwordless MFA), the integration
        // belongs to the
        // system tenant, but the enrollment logically belongs to the admin's tenant (if
        // any).
        Tenant tenantToReturn = integration.getTenant();
        if (Boolean.TRUE.equals(integration.getIsSystemIntegration())) {
            Optional<EzkeyAdmin> adminOpt = ezkeyAdminRepository
                    .findByMfaEnrollmentEnrollmentId(enrollment.getEnrollmentId());
            if (adminOpt.isPresent() && adminOpt.get().getTenant() != null) {
                tenantToReturn = adminOpt.get().getTenant();
            }
        }

        if (tenantToReturn != null) {
            response.setTenantId(tenantToReturn.getTenantId());
            response.setTenantName(tenantToReturn.getTenantName());
            response.setTenantDescription(tenantToReturn.getTenantDescription());
        }

        logger.info(
                "Enrollment bind process completed successfully for ID: {}", enrollment.getEnrollmentId());
        return response;
    }
}
