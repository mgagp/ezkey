/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: TenantRepository
 * Description: Spring Data JPA repository for Tenant entity operations.
 */

package org.ezkey.integration.domain.repository;

import java.util.List;
import java.util.Optional;
import org.ezkey.integration.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Tenant entity operations.
 *
 * <p>
 * This repository provides data access methods for managing tenants in the
 * multi-tenant Ezkey
 * system. It includes methods for finding tenants by various criteria and
 * managing tenant
 * lifecycle.
 *
 * <p>
 * <b>Tenant Operations:</b>
 *
 * <ul>
 * <li><b>Search:</b> Find tenants by name or description
 * <li><b>Administrator Management:</b> Find tenants created by specific admins
 * <li><b>Status Queries:</b> Find active/inactive tenants
 * <li><b>Validation:</b> Check tenant name uniqueness
 * </ul>
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
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Integer> {

  /**
   * Finds a tenant by its unique name.
   *
   * <p>
   * This method is used to find a tenant by its unique name, which is the primary
   * identifier for
   * tenants.
   *
   * @param tenantName the unique name of the tenant
   * @return Optional containing the tenant if found, empty otherwise
   */
  Optional<Tenant> findByTenantName(String tenantName);

  /**
   * Finds an active tenant by its name.
   *
   * <p>
   * This method returns only active tenants by name, filtering out
   * inactive/disabled tenants.
   *
   * @param tenantName the unique name of the tenant
   * @return Optional containing the active tenant if found, empty otherwise
   */
  Optional<Tenant> findByTenantNameAndActiveTrue(String tenantName);

  /**
   * Finds all active tenants.
   *
   * <p>
   * This method returns all tenants that are currently active in the system.
   *
   * @return list of active tenants
   */
  List<Tenant> findByActiveTrue();

  /**
   * Finds tenants created by a specific administrator.
   *
   * <p>
   * This method is used for audit purposes to track which administrator created
   * which tenants.
   *
   * @param createdByAdminId the ID of the creating administrator
   * @return list of tenants created by the specified administrator
   */
  List<Tenant> findByCreatedByAdminAdminId(Integer createdByAdminId);

  /**
   * Checks if a tenant name exists.
   *
   * <p>
   * This method is used to validate tenant name uniqueness before creating new
   * tenants.
   *
   * @param tenantName the tenant name to check
   * @return true if a tenant with this name exists, false otherwise
   */
  boolean existsByTenantName(String tenantName);

  /**
   * Checks if a tenant name exists, excluding a specific tenant.
   *
   * <p>
   * Used for rename uniqueness validation: ensures no other tenant
   * has the new name, excluding the tenant being renamed.
   *
   * @param tenantName the tenant name to check
   * @param tenantId   the tenant ID to exclude
   * @return true if another tenant with this name exists
   */
  boolean existsByTenantNameAndTenantIdNot(
      String tenantName, Integer tenantId);

  /**
   * Finds a tenant by its organization domain.
   *
   * <p>
   * Used for domain-based organization lookup and future SSO
   * integration.
   *
   * @param organizationDomain the organization domain to search
   * @return Optional containing the tenant if found
   */
  Optional<Tenant> findByOrganizationDomain(
      String organizationDomain);

  /**
   * Counts all active tenants.
   *
   * <p>
   * This method is used for statistics and monitoring of the number of active
   * tenants in the
   * system.
   *
   * @return the count of active tenants
   */
  long countByActiveTrue();
}
