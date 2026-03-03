/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: EzkeyIntegrationRepository
 * Description: Spring Data JPA repository for EzkeyIntegration entities.
 */

package org.ezkey.integration.domain.repository;

import java.util.Optional;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Integration} entities.
 *
 * <p>This repository provides standard CRUD operations for Integration entities and can be extended
 * with custom query methods as needed. Extends JpaSpecificationExecutor to support dynamic queries
 * with pagination and filtering.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Entity:</b> EzkeyIntegration
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Repository
public interface IntegrationRepository
    extends JpaRepository<Integration, Integer>, JpaSpecificationExecutor<Integration> {

  // Standard CRUD operations are inherited from JpaRepository:
  // - save(EzkeyIntegration entity)
  // - findById(Integer id)
  // - findAll()
  // - deleteById(Integer id)
  // - count()
  // - existsById(Integer id)
  // etc.

  /**
   * Find system integration (Integration Zero) by system flag and active status.
   *
   * <p>Returns the special system integration used for admin MFA authentication. There should only
   * be one system integration per Ezkey instance, marked with isSystemIntegration=true.
   *
   * @param isSystemIntegration true to find the system integration
   * @return Optional containing the system integration if found
   */
  Optional<Integration> findByIsSystemIntegrationAndActiveTrue(Boolean isSystemIntegration);

  /**
   * Check if an integration with the given code already exists for a specific tenant.
   *
   * <p>This method is used to validate uniqueness of integration codes per tenant before creation.
   *
   * @param code the integration code to check
   * @param tenant the tenant to check within
   * @return true if an integration with the code exists for the tenant, false otherwise
   */
  boolean existsByCodeAndTenant(String code, Tenant tenant);

  /**
   * Returns the tenant ID for the given integration ID as a scalar value, without loading the
   * {@link Integration} entity into the persistence context.
   *
   * <p>Use this method wherever only the tenant ID is needed (e.g., audit-log tenant resolution in
   * controllers) to avoid loading a full {@code Integration} entity into the session.
   *
   * @param id the integration ID
   * @return the tenant ID for the integration, or empty if not found or tenant is null
   * @since 2025
   */
  @Query("SELECT i.tenant.tenantId FROM Integration i WHERE i.id = :id")
  Optional<Integer> findTenantIdByIntegrationId(@Param("id") Integer id);
}
