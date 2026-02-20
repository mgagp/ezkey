/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AccessControlService
 * Description: Access control service for M2M API — API key scope only.
 */

package org.ezkey.m2m.security;

import java.util.Optional;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Access control service for M2M API operations.
 *
 * <p>This service is a simplified variant of the admin-api's {@code AccessControlService} scoped
 * exclusively to {@code ROLE_API_KEY} authentication. There are no admin or tenant contexts in the
 * M2M module.
 *
 * <p><b>Rule:</b> An API key can only access auth attempts that belong to an enrollment of its own
 * integration.
 *
 * <p>Extraction to a shared module ({@code ezkey-security-common}) is deferred to a future phase.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class AccessControlService {

  private static final Logger logger = LoggerFactory.getLogger(AccessControlService.class);

  private final AuthAttemptRepository authAttemptRepository;
  private final EnrollmentRepository enrollmentRepository;

  /**
   * Constructs the access control service.
   *
   * @param authAttemptRepository repository for auth attempt data access
   * @param enrollmentRepository repository for enrollment data access
   */
  public AccessControlService(
      AuthAttemptRepository authAttemptRepository, EnrollmentRepository enrollmentRepository) {
    this.authAttemptRepository = authAttemptRepository;
    this.enrollmentRepository = enrollmentRepository;
  }

  /**
   * Checks whether the authenticated API key can access the given auth attempt.
   *
   * <p>Access is granted only when the auth attempt belongs to an enrollment of the API key's
   * integration.
   *
   * @param auth the current authentication context
   * @param authAttemptId the auth attempt ID to check
   * @return {@code true} if access is allowed, {@code false} otherwise
   */
  public boolean canAccessAuthAttempt(Authentication auth, Integer authAttemptId) {
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }

    if (!hasRole(auth, "ROLE_API_KEY")) {
      return false;
    }

    return canAccessAuthAttemptForIntegration(auth, authAttemptId);
  }

  /**
   * Verifies that the auth attempt's enrollment belongs to the API key's integration.
   *
   * @param auth the API key authentication context — principal is the integration ID
   * @param authAttemptId the auth attempt ID
   * @return {@code true} if the attempt belongs to the API key's integration
   */
  private boolean canAccessAuthAttemptForIntegration(Authentication auth, Integer authAttemptId) {
    Object principal = auth.getPrincipal();
    if (!(principal instanceof Integer integrationId)) {
      logger.warn("Unexpected principal type in API key context: {}", principal);
      return false;
    }

    Optional<AuthAttempt> attemptOpt = authAttemptRepository.findById(authAttemptId);
    if (attemptOpt.isEmpty()) {
      logger.debug("Auth attempt {} not found for access check", authAttemptId);
      return false;
    }

    AuthAttempt attempt = attemptOpt.get();
    Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(attempt.getEnrollmentId());

    if (enrollmentOpt.isEmpty()) {
      logger.debug(
          "Enrollment {} not found for auth attempt access check", attempt.getEnrollmentId());
      return false;
    }

    boolean allowed = enrollmentOpt.get().getIntegrationId().equals(integrationId);

    if (!allowed) {
      logger.warn(
          "API key (integration {}) attempted to access auth attempt {} belonging to"
              + " integration {}",
          integrationId,
          authAttemptId,
          enrollmentOpt.get().getIntegrationId());
    }

    return allowed;
  }

  /**
   * Checks whether the authentication context carries the given role.
   *
   * @param auth the authentication context
   * @param role the role to check (e.g., {@code "ROLE_API_KEY"})
   * @return {@code true} if the role is present
   */
  private boolean hasRole(Authentication auth, String role) {
    return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
  }
}
