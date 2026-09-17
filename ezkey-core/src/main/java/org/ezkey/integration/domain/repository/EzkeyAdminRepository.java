/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: EzkeyAdminRepository
 * Description: Spring Data JPA repository for EzkeyAdmin entity operations.
 */

package org.ezkey.integration.domain.repository;

import java.util.List;
import java.util.Optional;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for EzkeyAdmin entity operations.
 *
 * <p>This repository provides data access methods for managing administrators in the multi-tenant
 * Ezkey system. It includes methods for finding administrators by various criteria and managing
 * administrator lifecycle.
 *
 * <p><b>Administrator Operations:</b>
 *
 * <ul>
 *   <li><b>Authentication:</b> Find administrators by username for login
 *   <li><b>Tenant Management:</b> Find administrators by tenant and type
 *   <li><b>Integration Management:</b> Find administrators by integration
 *   <li><b>Audit Queries:</b> Find administrators created by specific admins
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see EzkeyAdmin
 */
@Repository
public interface EzkeyAdminRepository extends JpaRepository<EzkeyAdmin, Integer> {

  /**
   * Finds an administrator by username.
   *
   * <p>This method is used for authentication to find the administrator by their unique username
   * during login.
   *
   * @param username the unique username of the administrator
   * @return Optional containing the administrator if found, empty otherwise
   */
  Optional<EzkeyAdmin> findByUsername(String username);

  /**
   * Finds an administrator by username with enrollment eagerly loaded.
   *
   * <p>This method is used for authentication to find the administrator and load their enrollment
   * in a single query using JOIN FETCH.
   *
   * @param username the unique username of the administrator
   * @return Optional containing the administrator with enrollment if found, empty otherwise
   */
  @Query("SELECT a FROM EzkeyAdmin a LEFT JOIN FETCH a.enrollment WHERE a.username = :username")
  Optional<EzkeyAdmin> findByUsernameWithEnrollment(@Param("username") String username);

  /**
   * Finds an active administrator by username.
   *
   * <p>This method is used for authentication to find only active administrators by their username
   * during login.
   *
   * @param username the unique username of the administrator
   * @return Optional containing the active administrator if found, empty otherwise
   */
  Optional<EzkeyAdmin> findByUsernameAndActiveTrue(String username);

  /**
   * Finds administrators by tenant and type.
   *
   * <p>This method is used to find administrators within a specific tenant with a specific type
   * (tenant admin or integration admin).
   *
   * @param tenantId the ID of the tenant
   * @param adminType the type of administrator
   * @return list of administrators matching the criteria
   */
  List<EzkeyAdmin> findByTenantTenantIdAndAdminType(Integer tenantId, AdminType adminType);

  /**
   * Finds administrators by tenant.
   *
   * <p>This method returns all administrators belonging to a specific tenant, regardless of their
   * type.
   *
   * @param tenantId the ID of the tenant
   * @return list of administrators belonging to the tenant
   */
  List<EzkeyAdmin> findByTenantTenantId(Integer tenantId);

  /**
   * Finds administrators by tenant with pagination support.
   *
   * <p>This method returns a paginated list of administrators belonging to a specific tenant,
   * regardless of their type. Used for listing administrators with pagination support.
   *
   * @param tenantId the ID of the tenant
   * @param pageable pagination and sorting parameters
   * @return page of administrators belonging to the tenant
   */
  Page<EzkeyAdmin> findByTenantTenantId(Integer tenantId, Pageable pageable);

  /**
   * Paginates all administrators with {@code tenant} and {@code enrollment} associations loaded in
   * the same round-trip, avoiding N+1 queries when mapping administrator list DTOs.
   *
   * <p>Uses explicit JPQL so Spring Data does not parse {@code findAllFor…} as a derived property
   * path (which fails at repository bootstrap).
   *
   * @param pageable pagination and sorting parameters
   * @return page of administrators with associations initialized
   */
  @EntityGraph(attributePaths = {"tenant", "enrollment"})
  @Query("SELECT a FROM EzkeyAdmin a")
  Page<EzkeyAdmin> findAllForAdminProvisioningList(Pageable pageable);

  /**
   * Paginates administrators for a tenant with {@code tenant} and {@code enrollment} associations
   * loaded in the same round-trip.
   *
   * <p>Uses explicit JPQL for the same reason as {@link #findAllForAdminProvisioningList}.
   *
   * @param tenantId the tenant ID
   * @param pageable pagination and sorting parameters
   * @return page of administrators with associations initialized
   */
  @EntityGraph(attributePaths = {"tenant", "enrollment"})
  @Query("SELECT a FROM EzkeyAdmin a WHERE a.tenant.tenantId = :tenantId")
  Page<EzkeyAdmin> findByTenantTenantIdForAdminProvisioningList(
      @Param("tenantId") Integer tenantId, Pageable pageable);

  /**
   * Finds administrators by integration.
   *
   * <p>This method returns administrators who manage a specific integration.
   *
   * @param integrationId the ID of the integration
   * @return list of administrators managing the integration
   */
  List<EzkeyAdmin> findByIntegrationId(Integer integrationId);

  /**
   * Finds administrators by type.
   *
   * <p>This method returns all administrators of a specific type across all tenants.
   *
   * @param adminType the type of administrator
   * @return list of administrators of the specified type
   */
  List<EzkeyAdmin> findByAdminType(AdminType adminType);

  /**
   * Finds active administrators by type.
   *
   * <p>This method returns only active administrators of a specific type.
   *
   * @param adminType the type of administrator
   * @return list of active administrators of the specified type
   */
  List<EzkeyAdmin> findByAdminTypeAndActiveTrue(AdminType adminType);

  /**
   * Finds administrators created by a specific administrator.
   *
   * <p>This method is used for audit purposes to track which administrator created which other
   * administrators.
   *
   * @param createdByAdminId the ID of the creating administrator
   * @return list of administrators created by the specified administrator
   */
  List<EzkeyAdmin> findByCreatedByAdminAdminId(Integer createdByAdminId);

  /**
   * Checks if a username exists.
   *
   * <p>This method is used to validate username uniqueness before creating new administrators.
   *
   * @param username the username to check
   * @return true if an administrator with this username exists, false otherwise
   */
  boolean existsByUsername(String username);

  /**
   * Checks if an email exists.
   *
   * <p>This method is used to validate email uniqueness before creating new administrators. Email
   * is optional but must be unique when provided.
   *
   * @param email the email to check
   * @return true if an administrator with this email exists, false otherwise
   */
  boolean existsByEmail(String email);

  /**
   * Checks if an email exists for another administrator (excluding the given admin ID).
   *
   * <p>Used when updating an admin's email to ensure uniqueness while allowing the same admin to
   * keep their current email.
   *
   * @param email the email to check
   * @param excludeAdminId the admin ID to exclude from the check (the admin being updated)
   * @return true if another administrator with this email exists, false otherwise
   */
  boolean existsByEmailAndAdminIdNot(String email, Integer excludeAdminId);

  /**
   * Counts administrators by tenant and type.
   *
   * <p>This method is used for statistics and monitoring of administrator distribution across
   * tenants.
   *
   * @param tenantId the ID of the tenant
   * @param adminType the type of administrator
   * @return the count of administrators matching the criteria
   */
  long countByTenantTenantIdAndAdminType(Integer tenantId, AdminType adminType);

  /**
   * Counts active administrators by tenant and type.
   *
   * <p>This method is used for enforcing admin limits during provisioning. It counts only active
   * administrators to ensure limits are enforced correctly.
   *
   * @param tenantId the ID of the tenant
   * @param adminType the type of administrator
   * @return the count of active administrators matching the criteria
   */
  long countByTenantTenantIdAndAdminTypeAndActiveTrue(Integer tenantId, AdminType adminType);

  /**
   * Counts active administrators by type.
   *
   * <p>This method is used for statistics and monitoring of active administrator distribution.
   *
   * @param adminType the type of administrator
   * @return the count of active administrators of the specified type
   */
  long countByAdminTypeAndActiveTrue(AdminType adminType);

  /**
   * Finds administrators by tenant with specific admin type and active status.
   *
   * <p>This method combines multiple criteria for complex queries used in permission checking and
   * administration.
   *
   * @param tenantId the ID of the tenant
   * @param adminType the type of administrator
   * @param active the active status
   * @return list of administrators matching all criteria
   */
  @Query(
      "SELECT a FROM EzkeyAdmin a WHERE a.tenant.tenantId = :tenantId AND a.adminType = :adminType"
          + " AND a.active = :active")
  List<EzkeyAdmin> findByTenantAndAdminTypeAndActive(
      @Param("tenantId") Integer tenantId,
      @Param("adminType") AdminType adminType,
      @Param("active") Boolean active);

  /**
   * Find admin by enrollment ID.
   *
   * <p>Used during passwordless authentication to identify which admin is associated with a
   * specific enrollment when processing auth attempts. Eagerly loads {@code tenant} so
   * passwordless-wait can read {@code tenantName} after {@code AuthAttemptWaitService}'s
   * {@code NOT_SUPPORTED} wait suspends the outer persistence context (lazy proxies would otherwise
   * throw {@code LazyInitializationException}).
   *
   * @param enrollmentId the enrollment ID
   * @return Optional containing the admin if found
   */
  @EntityGraph(attributePaths = {"tenant"})
  @Query("SELECT a FROM EzkeyAdmin a WHERE a.enrollment.enrollmentId = :enrollmentId")
  Optional<EzkeyAdmin> findByEnrollmentId(@Param("enrollmentId") Integer enrollmentId);

  /**
   * Returns tenant info (id, name, description) for the admin whose enrollment matches.
   *
   * <p>Uses a native scalar query to avoid loading the Tenant entity. This prevents the "Found
   * shared references to a collection: Tenant.administrators" hazard when building bind responses
   * for system integrations (administrator enrollments).
   *
   * @param enrollmentId the enrollment ID
   * @return Object[] with [tenantId, tenantName, tenantDescription], or empty if admin has no
   *     tenant
   */
  @Query(
      value =
          "SELECT t.tenant_id, t.tenant_name, t.tenant_description FROM ezkey_tenant t"
              + " JOIN ezkey_admin a ON a.tenant_id = t.tenant_id"
              + " JOIN ezkey_enrollment e ON e.enrollment_id = a.enrollment_id"
              + " WHERE e.enrollment_id = :enrollmentId",
      nativeQuery = true)
  Optional<Object[]> findTenantInfoByAdminEnrollmentId(@Param("enrollmentId") Integer enrollmentId);

  /**
   * Returns the tenant ID of the admin whose enrollment is the given enrollment.
   *
   * <p>Used for audit log tenant attribution: when bind/verify or auth-attempt events occur for an
   * admin's enrollment, the audit entry should use the admin's tenant so tenant admins see those
   * events. Uses JPQL to avoid native-query type issues with Optional and nullable column.
   *
   * @param enrollmentId the enrollment ID
   * @return the admin's tenant ID (may be null for global admin), or empty if no admin has this
   *     enrollment
   */
  @Query(
      "SELECT t.tenantId FROM EzkeyAdmin a JOIN a.tenant t WHERE a.enrollment.enrollmentId ="
          + " :enrollmentId")
  Optional<Integer> findTenantIdByEnrollmentId(@Param("enrollmentId") Integer enrollmentId);
}
