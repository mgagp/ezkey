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

import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
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
   * <p>This method is used to retrieve the special system integration used for admin MFA
   * authentication. There should only be one system integration per Ezkey instance, marked with
   * isSystemIntegration=true.
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
   * Fetches an integration by ID with its {@code i18n} and {@code tenant} associations eagerly
   * loaded, in <strong>read-only</strong> mode.
   *
   * <p><b>Why read-only?</b> {@code Integration.i18n} is mapped with {@code CascadeType.ALL +
   * orphanRemoval = true}. With a standard {@code findById()}, every managed {@code Integration}
   * instance carries a Hibernate-tracked {@code PersistentBag} for {@code i18n}. Under concurrent
   * load, when two or more sessions independently load the same integration (e.g., the system
   * integration used by all admin MFA enrollments) and then both flush within the same persistence
   * context, Hibernate detects that multiple entity states reference the same collection bag and
   * throws:
   *
   * <pre>
   * HibernateException: Found shared references to a collection:
   *     org.ezkey.integration.domain.entity.Integration.i18n
   * </pre>
   *
   * <p>Marking the result read-only ({@code org.hibernate.readOnly = true}) tells Hibernate not to
   * snapshot or track this entity for dirty-checking and cascades, eliminating the ownership
   * conflict entirely.
   *
   * <p><b>Usage:</b> Use this method wherever the integration is only <em>read</em> (e.g., bind
   * flow, response building). Use the standard {@code findById()} only when you intend to
   * <em>persist changes</em> to the integration itself.
   *
   * <p><b>See also:</b> {@code agents.md} section "CascadeType.ALL + orphanRemoval shared-reference
   * hazard" for the full design pattern documentation.
   *
   * @param id the integration ID
   * @return the integration with i18n and tenant loaded, or empty if not found
   * @since 2025
   */
  @Query(
      "SELECT i FROM Integration i"
          + " LEFT JOIN FETCH i.i18n"
          + " LEFT JOIN FETCH i.tenant"
          + " WHERE i.id = :id")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  Optional<Integration> findByIdWithI18nAndTenant(@Param("id") Integer id);
}
