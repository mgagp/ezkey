/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EntityEligibilityService
 * Description: Centralized eligibility checks for core domain entities.
 */

package org.ezkey.service;

import java.time.OffsetDateTime;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Centralized eligibility service for verifying the operational state of core domain entities.
 *
 * <p>This service provides two families of methods:
 *
 * <ul>
 *   <li><b>Boolean checks:</b> {@code is*Operational} methods return whether an entity is
 *       operational, suitable for computing the {@code operational} field in response DTOs.
 *   <li><b>Guard methods:</b> {@code ensure*Operational} methods throw typed exceptions when an
 *       entity is not operational, suitable for guarding write operations in service layers.
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class EntityEligibilityService {

  private static final Logger logger = LoggerFactory.getLogger(EntityEligibilityService.class);

  /**
   * Returns {@code true} when the given tenant is active.
   *
   * @param tenant the tenant to check; may be {@code null}
   * @return {@code true} if the tenant is non-null and its active flag is {@code true}
   */
  public boolean isTenantOperational(Tenant tenant) {
    return tenant != null && Boolean.TRUE.equals(tenant.getActive());
  }

  /**
   * Returns {@code true} when the integration itself is in the {@code ACTIVE} lifecycle state
   * <em>and</em> its parent tenant is also active.
   *
   * @param integration the integration to check; may be {@code null}
   * @return {@code true} if the integration and its tenant are both operational
   */
  public boolean isIntegrationOperational(Integration integration) {
    if (integration == null) {
      return false;
    }
    return integration.isOperational() && isTenantOperational(integration.getTenant());
  }

  /**
   * Returns {@code true} when the enrollment is in the {@code VERIFIED} status and its active flag
   * is {@code true}.
   *
   * <p>This is a <em>local</em> check that does not consider the parent integration or tenant.
   *
   * @param enrollment the enrollment to check; may be {@code null}
   * @return {@code true} if the enrollment itself is operational
   */
  public boolean isEnrollmentOperational(Enrollment enrollment) {
    if (enrollment == null) {
      return false;
    }
    return EnrollmentStatus.VERIFIED.equals(enrollment.getStatus())
        && Boolean.TRUE.equals(enrollment.getActive());
  }

  /**
   * Returns {@code true} when the enrollment is locally operational <em>and</em> its parent
   * integration chain (integration + tenant) is also operational.
   *
   * @param enrollment the enrollment to check; may be {@code null}
   * @param integration the parent integration; may be {@code null}
   * @return {@code true} if the full chain is operational
   */
  public boolean isEnrollmentFullyOperational(Enrollment enrollment, Integration integration) {
    return isEnrollmentOperational(enrollment) && isIntegrationOperational(integration);
  }

  /**
   * Returns {@code true} when the API key is active and not expired.
   *
   * <p>This is a <em>local</em> check that does not consider the parent integration or tenant.
   *
   * @param apiKey the API key to check; may be {@code null}
   * @return {@code true} if the API key itself is operational
   */
  public boolean isApiKeyOperational(ApiKey apiKey) {
    if (apiKey == null) {
      return false;
    }
    return Boolean.TRUE.equals(apiKey.getActive())
        && (apiKey.getExpiresAt() == null || !apiKey.getExpiresAt().isBefore(OffsetDateTime.now()));
  }

  /**
   * Returns {@code true} when the API key is locally operational <em>and</em> its parent
   * integration chain is also operational.
   *
   * @param apiKey the API key to check; may be {@code null}
   * @param integration the parent integration; may be {@code null}
   * @return {@code true} if the full chain is operational
   */
  public boolean isApiKeyFullyOperational(ApiKey apiKey, Integration integration) {
    return isApiKeyOperational(apiKey) && isIntegrationOperational(integration);
  }

  /**
   * Returns {@code true} when the administrator account is in the ACTIVE lifecycle state, locally
   * active, and, for tenant-scoped admins, when their tenant is also active.
   *
   * <p>Global admins (type {@code GLOBAL_ADMIN}) are considered operational as long as their
   * account is active, regardless of tenant state.
   *
   * @param admin the administrator to check; may be {@code null}
   * @return {@code true} if the administrator is operational
   */
  public boolean isAdminOperational(EzkeyAdmin admin) {
    if (admin == null) {
      return false;
    }
    if (admin.getLifecycleStatus() != AdminLifecycleStatus.ACTIVE) {
      return false;
    }
    if (!Boolean.TRUE.equals(admin.getActive())) {
      return false;
    }
    if (admin.getAdminType() == EzkeyAdmin.AdminType.GLOBAL_ADMIN) {
      return true;
    }
    return isTenantOperational(admin.getTenant());
  }

  /**
   * Returns {@code true} when an admin-linked enrollment is allowed to progress through bind or
   * verify.
   *
   * <p>The rule is intentionally simple: if the enrollment belongs to an administrator, that
   * administrator must currently be operational. This prevents suspended, pending-activation, or
   * tenant-blocked administrators from progressing their MFA enrollment toward use.
   *
   * @param admin the admin linked to the enrollment; may be {@code null}
   * @return {@code true} if no admin is linked or the linked admin is operational
   */
  public boolean isAdminLinkedEnrollmentEligible(EzkeyAdmin admin) {
    return adminLinkedEnrollmentIneligibilityReason(admin) == null;
  }

  /**
   * Returns the ineligibility reason for an admin-linked enrollment, or {@code null} when the
   * linked administrator is currently operational.
   *
   * @param admin the admin linked to the enrollment; may be {@code null}
   * @return a human-readable operator/audit reason, or {@code null} when eligible
   */
  public String adminLinkedEnrollmentIneligibilityReason(EzkeyAdmin admin) {
    if (admin == null) {
      return null;
    }
    if (admin.getLifecycleStatus() != AdminLifecycleStatus.ACTIVE) {
      return "linked administrator lifecycle status is " + admin.getLifecycleStatus();
    }
    if (!Boolean.TRUE.equals(admin.getActive())) {
      return "linked administrator is inactive";
    }
    if (admin.getAdminType() != EzkeyAdmin.AdminType.GLOBAL_ADMIN
        && !isTenantOperational(admin.getTenant())) {
      return "linked administrator tenant is inactive";
    }
    return null;
  }

  /**
   * Throws {@link TenantInactiveException} when the given tenant is not operational.
   *
   * @param tenant the tenant to check
   * @throws TenantInactiveException if the tenant is inactive
   */
  public void ensureTenantOperational(Tenant tenant) {
    if (!isTenantOperational(tenant)) {
      if (tenant != null) {
        logger.warn(
            "Tenant '{}' (ID: {}) is inactive", tenant.getTenantName(), tenant.getTenantId());
      }
      throw new TenantInactiveException(
          "Cannot perform operation for inactive tenant. Contact your Ezkey administrator.");
    }
  }

  /**
   * Throws a typed exception when the given integration (or its parent tenant) is not operational.
   *
   * <ul>
   *   <li>Throws {@link IntegrationLifecycleStateException} when the integration is not in the
   *       {@code ACTIVE} lifecycle state.
   *   <li>Throws {@link TenantInactiveException} when the parent tenant is inactive.
   * </ul>
   *
   * @param integration the integration to check
   * @throws IntegrationLifecycleStateException if the integration is not active
   * @throws TenantInactiveException if the parent tenant is inactive
   */
  public void ensureIntegrationOperational(Integration integration) {
    if (integration == null) {
      throw new IntegrationLifecycleStateException("Integration is null.");
    }
    if (!integration.isOperational()) {
      logger.warn(
          "Integration ID {} is not operational (lifecycleStatus: {})",
          integration.getId(),
          integration.getLifecycleStatus());
      throw new IntegrationLifecycleStateException(
          "Integration is not in the ACTIVE lifecycle state: " + integration.getLifecycleStatus());
    }
    ensureTenantOperational(integration.getTenant());
  }

  /**
   * Throws {@link EnrollmentInactiveException} when the enrollment is not locally operational.
   *
   * @param enrollment the enrollment to check
   * @throws EnrollmentInactiveException if the enrollment is not VERIFIED + active
   */
  public void ensureEnrollmentOperational(Enrollment enrollment) {
    if (!isEnrollmentOperational(enrollment)) {
      throw new EnrollmentInactiveException(
          "Enrollment is not operational (must be VERIFIED and active).");
    }
  }
}
