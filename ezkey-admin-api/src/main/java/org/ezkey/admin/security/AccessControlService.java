/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AccessControlService
 * Description: Service for checking access control permissions based on authentication context.
 */

package org.ezkey.admin.security;

import java.util.Optional;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.Integration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Service for checking access control permissions based on authentication context.
 *
 * <p>This service provides methods to verify whether the current authentication context
 * (API key or admin) has permission to access specific resources. It implements the
 * principle of least privilege by restricting API keys to their associated integration
 * scope while allowing admins full access.
 *
 * <p><b>Access Control Rules:</b>
 *
 * <ul>
 *   <li><b>API Keys (ROLE_API_KEY):</b> Can only access auth attempts for their integration
 *   <li><b>Admins (ROLE_ADMIN):</b> Can access all resources
 *   <li><b>Enrollments:</b> Always admin-only (API keys cannot access)
 * </ul>
 *
 * <p><b>Integration Scope:</b> API keys are associated with a specific integration and
 * can only access auth attempts that belong to enrollments of that integration.
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
   * Constructs a new AccessControlService.
   *
   * @param authAttemptRepository repository for auth attempt data access
   * @param enrollmentRepository repository for enrollment data access
   */
  public AccessControlService(
      AuthAttemptRepository authAttemptRepository,
      EnrollmentRepository enrollmentRepository) {
    this.authAttemptRepository = authAttemptRepository;
    this.enrollmentRepository = enrollmentRepository;
  }

  /**
   * Checks if the authenticated user can access a specific auth attempt.
   *
   * <p><b>Access Rules:</b>
   *
   * <ul>
   *   <li><b>Admins:</b> Can access any auth attempt
   *   <li><b>API Keys:</b> Can only access auth attempts for their integration
   * </ul>
   *
   * @param auth the authentication context
   * @param authAttemptId the auth attempt ID to check
   * @return true if access is allowed, false otherwise
   */
  public boolean canAccessAuthAttempt(Authentication auth, Integer authAttemptId) {
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }

    // Admins can access any auth attempt
    if (hasRole(auth, "ROLE_ADMIN")) {
      return true;
    }

    // API keys can only access auth attempts for their integration
    if (hasRole(auth, "ROLE_API_KEY")) {
      return canAccessAuthAttemptForIntegration(auth, authAttemptId);
    }

    return false;
  }

  /**
   * Checks if the authenticated user can access a specific enrollment.
   *
   * <p><b>Access Rules:</b>
   *
   * <ul>
   *   <li><b>Admins:</b> Can access any enrollment
   *   <li><b>API Keys:</b> Cannot access enrollments (always false)
   * </ul>
   *
   * @param auth the authentication context
   * @param enrollmentId the enrollment ID to check
   * @return true if access is allowed, false otherwise
   */
  public boolean canAccessEnrollment(Authentication auth, Integer enrollmentId) {
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }

    // Only admins can access enrollments
    return hasRole(auth, "ROLE_ADMIN");
  }

  /**
   * Checks if the authenticated user can access a specific integration.
   *
   * <p><b>Access Rules:</b>
   *
   * <ul>
   *   <li><b>Admins:</b> Can access any integration
   *   <li><b>API Keys:</b> Can only access their own integration
   * </ul>
   *
   * @param auth the authentication context
   * @param integrationId the integration ID to check
   * @return true if access is allowed, false otherwise
   */
  public boolean canAccessIntegration(Authentication auth, Integer integrationId) {
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }

    // Admins can access any integration
    if (hasRole(auth, "ROLE_ADMIN")) {
      return true;
    }

    // API keys can only access their own integration
    if (hasRole(auth, "ROLE_API_KEY")) {
      return canAccessOwnIntegration(auth, integrationId);
    }

    return false;
  }

  /**
   * Checks if an API key can access an auth attempt for its integration.
   *
   * <p>This method verifies that the auth attempt belongs to an enrollment of the
   * API key's integration.
   *
   * @param auth the authentication context (must be API key)
   * @param authAttemptId the auth attempt ID
   * @return true if the auth attempt belongs to the API key's integration
   */
  private boolean canAccessAuthAttemptForIntegration(Authentication auth, Integer authAttemptId) {
    try {
      // Get the auth attempt
      Optional<AuthAttempt> authAttemptOpt = authAttemptRepository.findById(authAttemptId);
      if (authAttemptOpt.isEmpty()) {
        logger.warn("Auth attempt {} not found for access control check", authAttemptId);
        return false;
      }

      AuthAttempt authAttempt = authAttemptOpt.get();
      Integer enrollmentId = authAttempt.getEnrollmentId();
      logger.debug("Auth attempt {} belongs to enrollment {}", authAttemptId, enrollmentId);

      // Get the enrollment
      Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(enrollmentId);
      if (enrollmentOpt.isEmpty()) {
        logger.warn("Enrollment {} not found for auth attempt {}", enrollmentId, authAttemptId);
        return false;
      }

      Enrollment enrollment = enrollmentOpt.get();
      Integer enrollmentIntegrationId = enrollment.getIntegrationId();
      logger.debug("Enrollment {} belongs to integration {}", enrollmentId, enrollmentIntegrationId);

      // Check if this matches the API key's integration
      boolean canAccess = canAccessOwnIntegration(auth, enrollmentIntegrationId);
      logger.debug("API key access to auth attempt {}: {}", authAttemptId, canAccess);
      return canAccess;
    } catch (Exception e) {
      logger.error("Error checking access to auth attempt {}: {}", authAttemptId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Checks if an API key can access its own integration.
   *
   * @param auth the authentication context (must be API key)
   * @param integrationId the integration ID to check
   * @return true if this is the API key's integration
   */
  private boolean canAccessOwnIntegration(Authentication auth, Integer integrationId) {
    try {
      // Extract integration ID from authentication principal
      Object principal = auth.getPrincipal();
      logger.debug("Authentication principal type: {}, value: {}", 
          principal != null ? principal.getClass().getSimpleName() : "null", principal);
      
      if (principal instanceof Integer authenticatedIntegrationId) {
        boolean matches = authenticatedIntegrationId.equals(integrationId);
        logger.debug("API key integration {} matches requested integration {}: {}", 
            authenticatedIntegrationId, integrationId, matches);
        return matches;
      }
      
      logger.warn("Authentication principal is not an Integer: {}", principal);
      return false;
    } catch (Exception e) {
      logger.error("Error checking integration access: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * Checks if the authentication has a specific role.
   *
   * @param auth the authentication context
   * @param role the role to check for
   * @return true if the authentication has the role
   */
  private boolean hasRole(Authentication auth, String role) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch(authority -> authority.equals(role));
  }
}
