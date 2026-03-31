/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: AdminSecurityProperties
 * Description: Configuration properties for admin security limits and constraints.
 */

package org.ezkey.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for admin security limits and constraints.
 *
 * <p>These properties control the maximum and minimum number of administrators allowed in the
 * system to balance security (redundancy) with operational simplicity.
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>
 * ezkey.security.admin.max-global-admins=3
 * ezkey.security.admin.min-global-admins=1
 * ezkey.security.admin.max-tenant-admins-per-tenant=3
 * ezkey.security.admin.min-tenant-admins-per-tenant=1
 * </pre>
 *
 * <p><b>Security Rationale:</b>
 *
 * <ul>
 *   <li><b>Maximum Limits:</b> Prevent excessive admin proliferation while allowing redundancy for
 *       operational continuity
 *   <li><b>Minimum Limits:</b> Safety floor to prevent accidental lockout (cannot deactivate below
 *       minimum)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.security.admin")
public class AdminSecurityProperties {

  /**
   * Maximum number of global administrators allowed in the system.
   *
   * <p>Default: 3. This limit prevents excessive global admin proliferation while allowing
   * redundancy for operational continuity. Each global admin requires Ezkey MFA enrollment,
   * providing strong security.
   */
  private int maxGlobalAdmins = 3;

  /**
   * Minimum number of global administrators required in the system.
   *
   * <p>Default: 1. This is a safety floor to prevent accidental lockout. Deactivation operations
   * cannot reduce the number of active global admins below this minimum.
   */
  private int minGlobalAdmins = 1;

  /**
   * Maximum number of tenant administrators allowed per tenant.
   *
   * <p>Default: 3. This limit prevents excessive tenant admin proliferation while allowing
   * redundancy for operational continuity within each tenant.
   */
  private int maxTenantAdminsPerTenant = 3;

  /**
   * Minimum number of tenant administrators required per tenant.
   *
   * <p>Default: 1. This is a safety floor to prevent accidental lockout within a tenant.
   * Deactivation operations cannot reduce the number of active tenant admins below this minimum.
   */
  private int minTenantAdminsPerTenant = 1;

  /**
   * Gets the maximum number of global administrators.
   *
   * @return the maximum number of global administrators
   */
  public int getMaxGlobalAdmins() {
    return maxGlobalAdmins;
  }

  /**
   * Sets the maximum number of global administrators.
   *
   * @param maxGlobalAdmins the maximum number of global administrators
   */
  public void setMaxGlobalAdmins(int maxGlobalAdmins) {
    this.maxGlobalAdmins = maxGlobalAdmins;
  }

  /**
   * Gets the minimum number of global administrators.
   *
   * @return the minimum number of global administrators
   */
  public int getMinGlobalAdmins() {
    return minGlobalAdmins;
  }

  /**
   * Sets the minimum number of global administrators.
   *
   * @param minGlobalAdmins the minimum number of global administrators
   */
  public void setMinGlobalAdmins(int minGlobalAdmins) {
    this.minGlobalAdmins = minGlobalAdmins;
  }

  /**
   * Gets the maximum number of tenant administrators per tenant.
   *
   * @return the maximum number of tenant administrators per tenant
   */
  public int getMaxTenantAdminsPerTenant() {
    return maxTenantAdminsPerTenant;
  }

  /**
   * Sets the maximum number of tenant administrators per tenant.
   *
   * @param maxTenantAdminsPerTenant the maximum number of tenant administrators per tenant
   */
  public void setMaxTenantAdminsPerTenant(int maxTenantAdminsPerTenant) {
    this.maxTenantAdminsPerTenant = maxTenantAdminsPerTenant;
  }

  /**
   * Gets the minimum number of tenant administrators per tenant.
   *
   * @return the minimum number of tenant administrators per tenant
   */
  public int getMinTenantAdminsPerTenant() {
    return minTenantAdminsPerTenant;
  }

  /**
   * Sets the minimum number of tenant administrators per tenant.
   *
   * @param minTenantAdminsPerTenant the minimum number of tenant administrators per tenant
   */
  public void setMinTenantAdminsPerTenant(int minTenantAdminsPerTenant) {
    this.minTenantAdminsPerTenant = minTenantAdminsPerTenant;
  }
}
