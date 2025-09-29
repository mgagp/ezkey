/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: TenantRepository
 * Description: Spring Data JPA repository for Tenant entity operations.
 */

package org.ezkey.tenant.domain.repository;

import java.util.List;
import java.util.Optional;

import org.ezkey.integration.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for Tenant entity operations.
 * <p>
 * This repository provides data access methods for managing tenants
 * in the multi-tenant Ezkey system. It includes methods for finding
 * tenants by various criteria and managing tenant lifecycle.
 * </p>
 *
 * <p>
 * <b>Multi-Tenant Operations:</b>
 * <ul>
 * <li><b>Tenant Lookup:</b> Find tenants by name, status, and other criteria</li>
 * <li><b>Active Tenants:</b> Query only active tenants for operational use</li>
 * <li><b>Tenant Management:</b> Support for tenant creation, updates, and deactivation</li>
 * <li><b>Audit Queries:</b> Find tenants created by specific administrators</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Tenant
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Integer> {

    /**
     * Finds a tenant by its unique name.
     * <p>
     * This method is used to check for duplicate tenant names
     * and to find tenants by their display name.
     * </p>
     *
     * @param tenantName the unique name of the tenant
     * @return Optional containing the tenant if found, empty otherwise
     */
    Optional<Tenant> findByTenantName(String tenantName);

    /**
     * Finds a tenant by its unique name, ignoring case.
     * <p>
     * This method performs a case-insensitive search for tenant names,
     * useful for user-friendly tenant lookup.
     * </p>
     *
     * @param tenantName the name of the tenant (case-insensitive)
     * @return Optional containing the tenant if found, empty otherwise
     */
    Optional<Tenant> findByTenantNameIgnoreCase(String tenantName);

    /**
     * Finds all active tenants.
     * <p>
     * This method returns only tenants that are currently active
     * and available for use in the system.
     * </p>
     *
     * @return list of active tenants
     */
    List<Tenant> findByActiveTrue();

    /**
     * Finds all inactive tenants.
     * <p>
     * This method returns tenants that have been deactivated
     * but are preserved for audit purposes.
     * </p>
     *
     * @return list of inactive tenants
     */
    List<Tenant> findByActiveFalse();

    /**
     * Finds tenants created by a specific administrator.
     * <p>
     * This method is used for audit purposes to track which
     * administrator created which tenants.
     * </p>
     *
     * @param createdByAdminId the ID of the creating administrator
     * @return list of tenants created by the specified administrator
     */
    List<Tenant> findByCreatedByAdminAdminId(Integer createdByAdminId);

    /**
     * Finds tenants created by a specific administrator and are active.
     * <p>
     * This method combines administrator filtering with active status
     * for operational queries.
     * </p>
     *
     * @param createdByAdminId the ID of the creating administrator
     * @return list of active tenants created by the specified administrator
     */
    @Query("SELECT t FROM Tenant t WHERE t.createdByAdmin.adminId = :createdByAdminId AND t.active = true")
    List<Tenant> findActiveTenantsByCreatedByAdmin(@Param("createdByAdminId") Integer createdByAdminId);

    /**
     * Checks if a tenant name exists (case-insensitive).
     * <p>
     * This method is used to validate tenant name uniqueness
     * before creating new tenants.
     * </p>
     *
     * @param tenantName the name to check
     * @return true if a tenant with this name exists, false otherwise
     */
    boolean existsByTenantNameIgnoreCase(String tenantName);

    /**
     * Counts the total number of active tenants.
     * <p>
     * This method is used for system statistics and monitoring
     * of tenant usage in the system.
     * </p>
     *
     * @return the count of active tenants
     */
    long countByActiveTrue();

    /**
     * Counts the total number of tenants created by a specific administrator.
     * <p>
     * This method is used for audit and monitoring purposes
     * to track administrator activity.
     * </p>
     *
     * @param createdByAdminId the ID of the creating administrator
     * @return the count of tenants created by the specified administrator
     */
    long countByCreatedByAdminAdminId(Integer createdByAdminId);
}
