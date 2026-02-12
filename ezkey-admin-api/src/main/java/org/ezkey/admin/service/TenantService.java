/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: TenantService
 * Description: Business logic for tenant lifecycle management including deactivation and
 *              tenant-active enforcement.
 */

package org.ezkey.admin.service;

import org.ezkey.admin.exception.TenantInactiveException;
import org.ezkey.admin.exception.TenantNotAllowedException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for tenant lifecycle management.
 *
 * <p>This service provides business logic for tenant operations including deactivation with
 * cascading security enforcement. When a tenant is deactivated, all active admin tokens for that
 * tenant are revoked, effectively logging out all tenant administrators.
 *
 * <p><b>Tenant Deactivation Rules:</b>
 *
 * <ul>
 *   <li>The system tenant cannot be deactivated (safety check)
 *   <li>Deactivation is idempotent (already-inactive tenants are silently ignored)
 *   <li>All active admin tokens for the tenant are revoked on deactivation
 *   <li>Integrations, enrollments, and API keys are NOT explicitly cascaded — the tenant {@code
 *       active} flag acts as a runtime master switch
 * </ul>
 *
 * <p><b>Tenant-Active Enforcement:</b>
 *
 * <p>The {@link #ensureTenantActive(Integer)} method provides a reusable guard that can be called
 * by other services (IntegrationService, EnrollmentService, ApiKeyService) before write operations
 * to prevent resource creation for inactive tenants.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Tenant
 * @see TenantNotAllowedException
 * @see TenantInactiveException
 */
@Service
public class TenantService {

  private static final Logger logger = LoggerFactory.getLogger(TenantService.class);

  private final TenantRepository tenantRepository;
  private final AdminTokenRepository tokenRepository;

  /**
   * Constructs a new TenantService with required dependencies.
   *
   * @param tenantRepository the tenant repository
   * @param tokenRepository the admin token repository for token revocation
   */
  public TenantService(TenantRepository tenantRepository, AdminTokenRepository tokenRepository) {
    this.tenantRepository = tenantRepository;
    this.tokenRepository = tokenRepository;
  }

  /**
   * Deactivates a tenant and revokes all active tokens for its administrators.
   *
   * <p>This method implements the full tenant deactivation lifecycle:
   *
   * <ol>
   *   <li>Validates the tenant exists
   *   <li>Prevents deactivation of the system tenant (safety check)
   *   <li>If already inactive, returns silently (idempotent)
   *   <li>Sets {@code active = false} on the tenant
   *   <li>Revokes all active admin tokens for the tenant
   * </ol>
   *
   * <p><b>Security Impact:</b> After deactivation:
   *
   * <ul>
   *   <li>Tenant admins cannot log in (checked at login time)
   *   <li>Existing bearer tokens are immediately revoked
   *   <li>API keys for tenant integrations are rejected at validation time
   *   <li>New integrations, enrollments, and API keys cannot be created
   * </ul>
   *
   * @param tenantId the ID of the tenant to deactivate
   * @param principal the admin principal performing the deactivation
   * @throws ResourceNotFoundException if the tenant is not found
   * @throws TenantNotAllowedException if attempting to deactivate the system tenant
   */
  @Transactional
  public void deactivateTenant(Integer tenantId, AdminPrincipal principal) {
    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

    // Safety check: cannot deactivate the system tenant
    if (Boolean.TRUE.equals(tenant.getIsSystemTenant())) {
      logger.warn(
          "Admin {} attempted to deactivate the system tenant (ID: {})",
          principal.adminId(),
          tenantId);
      throw new TenantNotAllowedException("Cannot deactivate the system tenant");
    }

    // Idempotent: already inactive
    if (!tenant.getActive()) {
      logger.info("Tenant {} is already inactive", tenantId);
      return;
    }

    // Deactivate the tenant
    tenant.setActive(false);
    tenantRepository.save(tenant);

    // Revoke all active tokens for administrators in this tenant
    int tokensRevoked = tokenRepository.deactivateAllTokensForTenant(tenantId);

    logger.info(
        "✅ Tenant '{}' (ID: {}) deactivated by admin {} ({} tokens revoked)",
        tenant.getTenantName(),
        tenantId,
        principal.adminId(),
        tokensRevoked);
  }

  /**
   * Ensures a tenant is active, throwing an exception if it is not.
   *
   * <p>This method is a reusable guard intended to be called by other services before performing
   * write operations (creating integrations, enrollments, API keys) to enforce tenant-active
   * integrity rules.
   *
   * @param tenantId the ID of the tenant to check
   * @throws TenantInactiveException if the tenant is inactive
   * @throws ResourceNotFoundException if the tenant is not found
   */
  @Transactional(readOnly = true)
  public void ensureTenantActive(Integer tenantId) {
    if (tenantId == null) {
      return; // Global admin operations without tenant scope
    }

    Tenant tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

    if (!tenant.getActive()) {
      logger.warn(
          "Operation blocked: tenant '{}' (ID: {}) is inactive", tenant.getTenantName(), tenantId);
      throw new TenantInactiveException("Tenant is inactive. Contact your Ezkey administrator.");
    }
  }
}
