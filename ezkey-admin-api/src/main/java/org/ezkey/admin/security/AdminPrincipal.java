/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Principal: AdminPrincipal
 * Description: Immutable principal object representing an authenticated administrator.
 */

package org.ezkey.admin.security;

import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;

/**
 * Immutable principal object representing an authenticated administrator.
 *
 * <p>This record carries the essential identity and scope information needed for authorization
 * decisions throughout the request lifecycle. It is created during bearer token authentication and
 * stored in the Spring Security context.
 *
 * <p><b>Principal Fields:</b>
 *
 * <ul>
 *   <li><b>adminId:</b> Unique identifier for the administrator
 *   <li><b>adminType:</b> Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN)
 *   <li><b>tenantId:</b> Tenant scope (null for GLOBAL_ADMIN, required for others)
 *   <li><b>integrationId:</b> Integration scope (null for GLOBAL_ADMIN and TENANT_ADMIN, required
 *       for INTEGRATION_ADMIN)
 *   <li><b>tokenPurpose:</b> Session purpose ({@link AdminTokenPurpose#SESSION}, {@link
 *       AdminTokenPurpose#EVALUATOR_TEMP}, …)
 * </ul>
 *
 * <p><b>Usage:</b> This principal is used by AccessControlService to make scope-aware authorization
 * decisions. For example, a TENANT_ADMIN can only access resources within their tenant.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param adminId Unique identifier for the administrator
 * @param adminType Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN)
 * @param tenantId Tenant scope (null for GLOBAL_ADMIN, required for others)
 * @param integrationId Integration scope (null for GLOBAL_ADMIN and TENANT_ADMIN, required for
 *     INTEGRATION_ADMIN)
 * @param tokenPurpose Purpose of the authenticating token
 * @author Ezkey contributors
 * @since 2025
 */
public record AdminPrincipal(
    Integer adminId,
    AdminType adminType,
    Integer tenantId,
    Integer integrationId,
    AdminTokenPurpose tokenPurpose) {

  /**
   * Compatibility constructor defaulting purpose to {@link AdminTokenPurpose#SESSION}.
   *
   * @param adminId administrator id
   * @param adminType administrator type
   * @param tenantId tenant scope
   * @param integrationId integration scope
   */
  public AdminPrincipal(
      Integer adminId, AdminType adminType, Integer tenantId, Integer integrationId) {
    this(adminId, adminType, tenantId, integrationId, AdminTokenPurpose.SESSION);
  }

  /**
   * Checks if this principal represents a global administrator.
   *
   * @return true if adminType is GLOBAL_ADMIN
   */
  public boolean isGlobalAdmin() {
    return adminType == AdminType.GLOBAL_ADMIN;
  }

  /**
   * Checks if this principal represents a tenant administrator.
   *
   * @return true if adminType is TENANT_ADMIN
   */
  public boolean isTenantAdmin() {
    return adminType == AdminType.TENANT_ADMIN;
  }

  /**
   * Checks if this principal represents an integration administrator.
   *
   * @return true if adminType is INTEGRATION_ADMIN
   */
  public boolean isIntegrationAdmin() {
    return adminType == AdminType.INTEGRATION_ADMIN;
  }

  /**
   * Whether this authentication is a Mode C temporary evaluator console session.
   *
   * @return true when token purpose is {@link AdminTokenPurpose#EVALUATOR_TEMP}
   */
  public boolean isEvaluatorTemp() {
    return tokenPurpose == AdminTokenPurpose.EVALUATOR_TEMP;
  }
}
