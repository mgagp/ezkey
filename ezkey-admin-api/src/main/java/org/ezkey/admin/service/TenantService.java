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

import java.time.OffsetDateTime;

import org.ezkey.admin.dto.request.TenantUpdateRequestDto;
import org.ezkey.admin.exception.TenantInactiveException;
import org.ezkey.admin.exception.TenantNotAllowedException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for tenant lifecycle management.
 *
 * <p>
 * This service provides business logic for tenant operations including
 * deactivation with
 * cascading security enforcement. When a tenant is deactivated, all active
 * admin tokens for that
 * tenant are revoked, effectively logging out all tenant administrators.
 *
 * <p>
 * <b>Tenant Deactivation Rules:</b>
 *
 * <ul>
 * <li>The system tenant cannot be deactivated (safety check)
 * <li>Deactivation is idempotent (already-inactive tenants are silently
 * ignored)
 * <li>All active admin tokens for the tenant are revoked on deactivation
 * <li>Integrations, enrollments, and API keys are NOT explicitly cascaded — the
 * tenant {@code
 *       active} flag acts as a runtime master switch
 * </ul>
 *
 * <p>
 * <b>Tenant-Active Enforcement:</b>
 *
 * <p>
 * The {@link #ensureTenantActive(Integer)} method provides a reusable guard
 * that can be called
 * by other services (IntegrationService, EnrollmentService, ApiKeyService)
 * before write operations
 * to prevent resource creation for inactive tenants.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
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
  private final EzkeyAdminRepository adminRepository;

  /**
   * Constructs a new TenantService with required dependencies.
   *
   * @param tenantRepository the tenant repository
   * @param tokenRepository  the admin token repository for token revocation
   * @param adminRepository  the admin repository for audit references
   */
  public TenantService(
      TenantRepository tenantRepository,
      AdminTokenRepository tokenRepository,
      EzkeyAdminRepository adminRepository) {
    this.tenantRepository = tenantRepository;
    this.tokenRepository = tokenRepository;
    this.adminRepository = adminRepository;
  }

  /**
   * Deactivates a tenant and revokes all active tokens for its administrators.
   *
   * <p>
   * This method implements the full tenant deactivation lifecycle:
   *
   * <ol>
   * <li>Validates the tenant exists
   * <li>Prevents deactivation of the system tenant (safety check)
   * <li>If already inactive, returns silently (idempotent)
   * <li>Sets {@code active = false} on the tenant
   * <li>Revokes all active admin tokens for the tenant
   * </ol>
   *
   * <p>
   * <b>Security Impact:</b> After deactivation:
   *
   * <ul>
   * <li>Tenant admins cannot log in (checked at login time)
   * <li>Existing bearer tokens are immediately revoked
   * <li>API keys for tenant integrations are rejected at validation time
   * <li>New integrations, enrollments, and API keys cannot be created
   * </ul>
   *
   * @param tenantId  the ID of the tenant to deactivate
   * @param principal the admin principal performing the deactivation
   * @throws ResourceNotFoundException if the tenant is not found
   * @throws TenantNotAllowedException if attempting to deactivate the system
   *                                   tenant
   */
  @Transactional
  public void deactivateTenant(Integer tenantId, AdminPrincipal principal) {
    Tenant tenant = tenantRepository
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

    // Record deactivation audit trail (SOC 2 CC6.3)
    OffsetDateTime now = OffsetDateTime.now();
    tenant.setDeactivatedAt(now);
    tenant.setUpdatedAt(now);
    EzkeyAdmin actor = adminRepository
        .findById(principal.adminId())
        .orElse(null);
    tenant.setDeactivatedByAdmin(actor);
    tenant.setUpdatedByAdmin(actor);

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
   * Updates a tenant with partial-update semantics.
   *
   * <p>
   * Only non-null fields from the request are applied. The tenant
   * must be active. If {@code tenantName} is changed, uniqueness is
   * validated. Audit fields {@code updatedAt} and {@code updatedByAdmin}
   * are set automatically.
   *
   * @param tenantId  the ID of the tenant to update
   * @param request   the update request (partial fields)
   * @param principal the admin performing the update
   * @return the updated tenant
   * @throws ResourceNotFoundException if tenant not found
   * @throws TenantInactiveException   if tenant is inactive
   * @throws IllegalArgumentException  if new name already exists
   */
  @Transactional
  public Tenant updateTenant(
      Integer tenantId,
      TenantUpdateRequestDto request,
      AdminPrincipal principal) {

    Tenant tenant = tenantRepository
        .findById(tenantId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Tenant", tenantId));

    // Block updates on inactive tenants
    ensureTenantActive(tenantId);

    // Rename check: verify uniqueness excluding current tenant
    if (request.tenantName() != null
        && !request.tenantName().equals(tenant.getTenantName())) {
      if (tenantRepository.existsByTenantNameAndTenantIdNot(
          request.tenantName(), tenantId)) {
        throw new IllegalArgumentException(
            "Tenant name already exists: " + request.tenantName());
      }
      tenant.setTenantName(request.tenantName());
    }

    // Apply non-null fields (partial update)
    if (request.tenantDescription() != null) {
      tenant.setTenantDescription(request.tenantDescription());
    }
    if (request.organizationName() != null) {
      tenant.setOrganizationName(request.organizationName());
    }
    if (request.organizationDomain() != null) {
      tenant.setOrganizationDomain(request.organizationDomain());
    }
    if (request.countryCode() != null) {
      tenant.setCountryCode(request.countryCode());
    }
    if (request.timezone() != null) {
      tenant.setTimezone(request.timezone());
    }
    if (request.primaryContactName() != null) {
      tenant.setPrimaryContactName(request.primaryContactName());
    }
    if (request.primaryContactEmail() != null) {
      tenant.setPrimaryContactEmail(request.primaryContactEmail());
    }

    // Audit trail (SOC 2 CC7.2)
    tenant.setUpdatedAt(OffsetDateTime.now());
    EzkeyAdmin actor = adminRepository
        .findById(principal.adminId())
        .orElse(null);
    tenant.setUpdatedByAdmin(actor);

    tenant = tenantRepository.save(tenant);

    logger.info(
        "Tenant '{}' (ID: {}) updated by admin {}",
        tenant.getTenantName(),
        tenantId,
        principal.adminId());

    return tenant;
  }

  /**
   * Ensures a tenant is active, throwing an exception if it is not.
   *
   * <p>
   * This method is a reusable guard intended to be called by other services
   * before performing
   * write operations (creating integrations, enrollments, API keys) to enforce
   * tenant-active
   * integrity rules.
   *
   * @param tenantId the ID of the tenant to check
   * @throws TenantInactiveException   if the tenant is inactive
   * @throws ResourceNotFoundException if the tenant is not found
   */
  @Transactional(readOnly = true)
  public void ensureTenantActive(Integer tenantId) {
    if (tenantId == null) {
      return; // Global admin operations without tenant scope
    }

    Tenant tenant = tenantRepository
        .findById(tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));

    if (!tenant.getActive()) {
      logger.warn(
          "Operation blocked: tenant '{}' (ID: {}) is inactive", tenant.getTenantName(), tenantId);
      throw new TenantInactiveException("Tenant is inactive. Contact your Ezkey administrator.");
    }
  }
}
