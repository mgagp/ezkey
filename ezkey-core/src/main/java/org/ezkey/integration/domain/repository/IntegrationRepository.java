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
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;

/**
 * Spring Data JPA repository for {@link Integration} entities.
 *
 * <p>
 * This repository provides standard CRUD operations for Integration entities
 * and can be extended
 * with custom query methods as needed. Extends JpaSpecificationExecutor to
 * support dynamic queries
 * with pagination and filtering.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * <p>
 * <b>Entity:</b> EzkeyIntegration
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
   * <p>
   * This method is used to retrieve the special system integration used for admin
   * MFA
   * authentication. There should only be one system integration per Ezkey
   * instance, marked with
   * isSystemIntegration=true.
   *
   * <p>
   * <b>Mutability note:</b> This method returns a fully tracked
   * {@code Integration} entity. Use
   * it only when you need to <em>persist changes</em> to the integration itself
   * (e.g., bootstrap
   * creation). For all read-only access to the system integration, use {@link
   * #findSystemIntegrationReadOnly()} instead to avoid Hibernate dirty-checking
   * and
   * shared-reference hazards on the {@code Integration.i18n} collection.
   *
   * @param isSystemIntegration true to find the system integration
   * @return Optional containing the system integration if found
   */
  Optional<Integration> findByIsSystemIntegrationAndActiveTrue(Boolean isSystemIntegration);

  /**
   * Check if an integration with the given code already exists for a specific
   * tenant.
   *
   * <p>
   * This method is used to validate uniqueness of integration codes per tenant
   * before creation.
   *
   * @param code   the integration code to check
   * @param tenant the tenant to check within
   * @return true if an integration with the code exists for the tenant, false
   *         otherwise
   */
  boolean existsByCodeAndTenant(String code, Tenant tenant);

  /**
   * Fetches an integration by ID with its {@code i18n} and {@code tenant}
   * associations eagerly
   * loaded, in <strong>read-only</strong> mode.
   *
   * <p>
   * <b>Why read-only?</b> {@code Integration.i18n} is mapped with
   * {@code CascadeType.ALL +
   * orphanRemoval = true}. With a standard {@code findById()}, every managed
   * {@code Integration}
   * instance carries a Hibernate-tracked {@code PersistentBag} for {@code i18n}.
   * Under concurrent
   * load, when two or more sessions independently load the same integration
   * (e.g., the system
   * integration used by all admin MFA enrollments) and then both flush within the
   * same persistence
   * context, Hibernate detects that multiple entity states reference the same
   * collection bag and
   * throws:
   *
   * <pre>
   * HibernateException: Found shared references to a collection:
   *     org.ezkey.integration.domain.entity.Integration.i18n
   * </pre>
   *
   * <p>
   * Marking the result read-only ({@code org.hibernate.readOnly = true}) tells
   * Hibernate not to
   * snapshot or track this entity for dirty-checking and cascades, eliminating
   * the ownership
   * conflict entirely.
   *
   * <p>
   * <b>Usage:</b> Use this method wherever the integration is only <em>read</em>
   * (e.g., bind
   * flow, response building). Use the standard {@code findById()} only when you
   * intend to
   * <em>persist changes</em> to the integration itself.
   *
   * <p>
   * <b>See also:</b> {@code agents.md} section "CascadeType.ALL + orphanRemoval
   * shared-reference
   * hazard" for the full design pattern documentation.
   *
   * @param id the integration ID
   * @return the integration with i18n and tenant loaded, or empty if not found
   * @since 2025
   */
  @Query("SELECT i FROM Integration i"
      + " LEFT JOIN FETCH i.i18n"
      + " LEFT JOIN FETCH i.tenant"
      + " WHERE i.id = :id")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  Optional<Integration> findByIdWithI18nAndTenant(@Param("id") Integer id);

  /**
   * Returns the tenant ID for the given integration ID as a scalar value, without
   * loading the
   * {@link Integration} entity into the persistence context.
   *
   * <p>
   * Use this method wherever only the tenant ID is needed (e.g., audit-log tenant
   * resolution in
   * controllers) to avoid loading a tracked {@code Integration} entity into the
   * session. Loading a
   * tracked entity in the controller layer, and then loading the same integration
   * again inside a
   * {@code @Transactional} service, can produce two managed instances sharing the
   * same {@code
   * Integration.i18n} {@code PersistentBag}, causing:
   *
   * <pre>
   * JpaSystemException: Found shared references to a collection: Integration.i18n
   * </pre>
   *
   * @param id the integration ID
   * @return the tenant ID for the integration, or empty if not found or tenant is
   *         null
   * @since 2025
   */
  @Query("SELECT i.tenant.tenantId FROM Integration i WHERE i.id = :id")
  Optional<Integer> findTenantIdByIntegrationId(@Param("id") Integer id);

  /**
   * Fetches the active system integration with its {@code tenant} association
   * eagerly loaded, in
   * <strong>read-only</strong> mode.
   *
   * <p>
   * Use this variant in all code paths that only <em>read</em> the system
   * integration (e.g.,
   * provisioning of new admin enrollments, bootstrap existence check). Read-only
   * mode prevents
   * Hibernate from tracking the {@code Integration.i18n} collection for
   * dirty-checking and cascade
   * operations, eliminating shared-reference conflicts under concurrent load.
   *
   * <p>
   * Note: {@code i18n} is NOT fetched here because callers only need
   * {@code getId()} or {@code
   * getTenant()}. Use {@link #findByIdWithI18nAndTenant(Integer)} when i18n data
   * is needed.
   *
   * <p>
   * Use the standard {@link #findByIsSystemIntegrationAndActiveTrue(Boolean)}
   * only when you
   * intend to <em>persist changes</em> to the system integration entity itself.
   *
   * @return the active system integration with tenant loaded, or empty if not
   *         found
   * @since 2025
   */
  @Query("SELECT i FROM Integration i"
      + " LEFT JOIN FETCH i.tenant"
      + " WHERE i.isSystemIntegration = true AND i.active = true")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  Optional<Integration> findSystemIntegrationReadOnly();
}
