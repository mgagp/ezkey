/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Service for checking access control permissions based on authentication context.
 *
 * <p>This service provides methods to verify whether the current authentication context (API key or
 * admin) has permission to access specific resources. It implements the principle of least
 * privilege with tenant-aware scoping for multi-tenant isolation.
 *
 * <p><b>Access Control Rules:</b>
 *
 * <ul>
 *   <li><b>Global Admins (ROLE_GLOBAL_ADMIN):</b> Can access all resources across all tenants
 *   <li><b>Tenant Admins (ROLE_TENANT_ADMIN):</b> Can only access resources within their tenant
 *   <li><b>API Keys (ROLE_API_KEY):</b> Can only access auth attempts for their integration
 *   <li><b>Enrollments:</b> Always admin-only (API keys cannot access)
 * </ul>
 *
 * <p><b>Tenant Scoping:</b> Tenant admins are restricted to resources (integrations, enrollments,
 * auth attempts, API keys) that belong to their tenant. This is enforced by checking the tenant_id
 * of the target resource.
 *
 * <p><b>Integration Scope:</b> API keys are associated with a specific integration and can only
 * access auth attempts that belong to enrollments of that integration.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
  private final IntegrationRepository integrationRepository;

  /**
   * Constructs a new AccessControlService.
   *
   * @param authAttemptRepository repository for auth attempt data access
   * @param enrollmentRepository repository for enrollment data access
   * @param integrationRepository repository for integration data access
   */
  public AccessControlService(
      AuthAttemptRepository authAttemptRepository,
      EnrollmentRepository enrollmentRepository,
      IntegrationRepository integrationRepository) {
    this.authAttemptRepository = authAttemptRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.integrationRepository = integrationRepository;
  }

  /**
   * Checks if the authenticated user can access a specific auth attempt.
   *
   * <p><b>Access Rules:</b>
   *
   * <ul>
   *   <li><b>Global Admins:</b> Can access any auth attempt
   *   <li><b>Tenant Admins:</b> Can only access auth attempts for enrollments in their tenant
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

    // Global admins can access any auth attempt
    if (hasRole(auth, "ROLE_GLOBAL_ADMIN")) {
      return true;
    }

    // Tenant admins can only access auth attempts in their tenant
    if (hasRole(auth, "ROLE_TENANT_ADMIN")) {
      return canAccessAuthAttemptForTenant(auth, authAttemptId);
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
   *   <li><b>Global Admins:</b> Can access any enrollment
   *   <li><b>Tenant Admins:</b> Can only access enrollments for integrations in their tenant
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

    // Global admins can access any enrollment
    if (hasRole(auth, "ROLE_GLOBAL_ADMIN")) {
      return true;
    }

    // Tenant admins can only access enrollments in their tenant
    if (hasRole(auth, "ROLE_TENANT_ADMIN")) {
      return canAccessEnrollmentForTenant(auth, enrollmentId);
    }

    // API keys cannot access enrollments
    return false;
  }

  /**
   * Checks if the authenticated user can revoke or deactivate a specific enrollment.
   *
   * <p>The scoping rules for revocation are identical to those for read access: Global Admins can
   * revoke any enrollment; Tenant Admins can only revoke enrollments within their tenant; API Keys
   * cannot revoke enrollments.
   *
   * <p><b>Self-revocation prevention</b> is enforced at the service layer ({@link
   * org.ezkey.admin.service.EnrollmentRevocationService}), not here. This method only enforces
   * tenant scoping.
   *
   * <p><b>Non-impersonation note:</b> Revoking an enrollment is an administrative control action,
   * not impersonation. Global Admins may revoke any enrollment including regular user enrollments
   * across all integrations. Tenant Admins may revoke enrollments within their tenant.
   *
   * @param auth the authentication context
   * @param enrollmentId the enrollment ID to check
   * @return true if the caller may revoke this enrollment, false otherwise
   */
  public boolean canRevokeEnrollment(Authentication auth, Integer enrollmentId) {
    return canAccessEnrollment(auth, enrollmentId);
  }

  /**
   * Checks if the authenticated user can access a specific integration.
   *
   * <p><b>Access Rules:</b>
   *
   * <ul>
   *   <li><b>Global Admins:</b> Can access any integration
   *   <li><b>Tenant Admins:</b> Can only access integrations in their tenant
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

    // Global admins can access any integration
    if (hasRole(auth, "ROLE_GLOBAL_ADMIN")) {
      return true;
    }

    // Tenant admins can only access integrations in their tenant
    if (hasRole(auth, "ROLE_TENANT_ADMIN")) {
      return canAccessIntegrationForTenant(auth, integrationId);
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
   * <p>This method verifies that the auth attempt belongs to an enrollment of the API key's
   * integration.
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
      logger.debug(
          "Enrollment {} belongs to integration {}", enrollmentId, enrollmentIntegrationId);

      // Check if this matches the API key's integration
      boolean canAccess = canAccessOwnIntegration(auth, enrollmentIntegrationId);
      if (!canAccess) {
        Object principal = auth.getPrincipal();
        Integer apiKeyIntegrationId = principal instanceof Integer ? (Integer) principal : null;
        logger.warn(
            "API key from integration {} attempted to access auth attempt {} belonging to"
                + " integration {}",
            apiKeyIntegrationId,
            authAttemptId,
            enrollmentIntegrationId);
      } else {
        logger.debug("API key access to auth attempt {}: {}", authAttemptId, canAccess);
      }
      return canAccess;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error(
          "Error checking access to auth attempt {}: {}", authAttemptId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Checks if a tenant admin can access an auth attempt in their tenant.
   *
   * <p>This method verifies that the auth attempt belongs to an enrollment whose integration is in
   * the tenant admin's tenant.
   *
   * @param auth the authentication context (must be tenant admin)
   * @param authAttemptId the auth attempt ID
   * @return true if the auth attempt belongs to the tenant admin's tenant
   */
  private boolean canAccessAuthAttemptForTenant(Authentication auth, Integer authAttemptId) {
    try {
      AdminPrincipal principal = extractAdminPrincipal(auth);
      if (principal == null || principal.tenantId() == null) {
        logger.warn(
            "Cannot extract tenant ID from authentication for auth attempt {}", authAttemptId);
        return false;
      }

      // Get the auth attempt
      Optional<AuthAttempt> authAttemptOpt = authAttemptRepository.findById(authAttemptId);
      if (authAttemptOpt.isEmpty()) {
        logger.warn("Auth attempt {} not found for tenant access check", authAttemptId);
        return false;
      }

      AuthAttempt authAttempt = authAttemptOpt.get();
      Integer enrollmentId = authAttempt.getEnrollmentId();

      // Get the enrollment
      Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(enrollmentId);
      if (enrollmentOpt.isEmpty()) {
        logger.warn("Enrollment {} not found for auth attempt {}", enrollmentId, authAttemptId);
        return false;
      }

      Enrollment enrollment = enrollmentOpt.get();
      Integer integrationId = enrollment.getIntegrationId();

      // Check if integration belongs to tenant admin's tenant
      return canAccessIntegrationForTenant(auth, integrationId);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error(
          "Error checking tenant access to auth attempt {}: {}", authAttemptId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Checks if a tenant admin can access an enrollment in their tenant.
   *
   * <p>This method verifies that the enrollment belongs to an integration in the tenant admin's
   * tenant.
   *
   * @param auth the authentication context (must be tenant admin)
   * @param enrollmentId the enrollment ID
   * @return true if the enrollment belongs to the tenant admin's tenant
   */
  private boolean canAccessEnrollmentForTenant(Authentication auth, Integer enrollmentId) {
    try {
      AdminPrincipal principal = extractAdminPrincipal(auth);
      if (principal == null || principal.tenantId() == null) {
        logger.warn("Cannot extract tenant ID from authentication for enrollment {}", enrollmentId);
        return false;
      }

      // Get the enrollment
      Optional<Enrollment> enrollmentOpt = enrollmentRepository.findById(enrollmentId);
      if (enrollmentOpt.isEmpty()) {
        logger.warn("Enrollment {} not found for tenant access check", enrollmentId);
        return false;
      }

      Enrollment enrollment = enrollmentOpt.get();
      Integer integrationId = enrollment.getIntegrationId();

      // Check if integration belongs to tenant admin's tenant
      return canAccessIntegrationForTenant(auth, integrationId);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error(
          "Error checking tenant access to enrollment {}: {}", enrollmentId, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Checks if a tenant admin can access an integration in their tenant.
   *
   * <p>This method verifies that the integration belongs to the tenant admin's tenant by checking
   * the integration's tenant_id.
   *
   * @param auth the authentication context (must be tenant admin)
   * @param integrationId the integration ID
   * @return true if the integration belongs to the tenant admin's tenant
   */
  private boolean canAccessIntegrationForTenant(Authentication auth, Integer integrationId) {
    try {
      AdminPrincipal principal = extractAdminPrincipal(auth);
      if (principal == null || principal.tenantId() == null) {
        logger.warn(
            "Cannot extract tenant ID from authentication for integration {}", integrationId);
        return false;
      }

      // Get the integration
      Optional<Integration> integrationOpt = integrationRepository.findById(integrationId);
      if (integrationOpt.isEmpty()) {
        logger.warn("Integration {} not found for tenant access check", integrationId);
        return false;
      }

      Integration integration = integrationOpt.get();
      Integer integrationTenantId =
          integration.getTenant() != null ? integration.getTenant().getTenantId() : null;

      boolean matches = principal.tenantId().equals(integrationTenantId);
      logger.debug(
          "Tenant admin tenant {} matches integration tenant {}: {}",
          principal.tenantId(),
          integrationTenantId,
          matches);
      return matches;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error(
          "Error checking tenant access to integration {}: {}", integrationId, e.getMessage(), e);
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
      logger.debug(
          "Authentication principal type: {}, value: {}",
          principal != null ? principal.getClass().getSimpleName() : "null",
          principal);

      if (principal instanceof Integer authenticatedIntegrationId) {
        boolean matches = authenticatedIntegrationId.equals(integrationId);
        logger.debug(
            "API key integration {} matches requested integration {}: {}",
            authenticatedIntegrationId,
            integrationId,
            matches);
        return matches;
      }

      logger.warn("Authentication principal is not an Integer: {}", principal);
      return false;
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Error checking integration access: {}", e.getMessage(), e);
      return false;
    }
  }

  /**
   * Extracts AdminPrincipal from authentication context.
   *
   * @param auth the authentication context
   * @return AdminPrincipal if present, null otherwise
   */
  private AdminPrincipal extractAdminPrincipal(Authentication auth) {
    if (auth == null) {
      return null;
    }

    Object principal = auth.getPrincipal();
    if (principal instanceof AdminPrincipal adminPrincipal) {
      return adminPrincipal;
    }

    logger.debug(
        "Authentication principal is not AdminPrincipal: {}",
        principal != null ? principal.getClass().getSimpleName() : "null");
    return null;
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
