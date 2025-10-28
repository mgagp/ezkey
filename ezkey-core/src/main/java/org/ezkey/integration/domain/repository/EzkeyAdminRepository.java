/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
   * Finds an administrator by username with MFA enrollment eagerly loaded.
   *
   * <p>This method is used for authentication to find the administrator and load their MFA
   * enrollment in a single query using JOIN FETCH. This is essential for the MFA flow to correctly
   * determine if MFA should be required during login.
   *
   * @param username the unique username of the administrator
   * @return Optional containing the administrator with enrollment if found, empty otherwise
   */
  @Query("SELECT a FROM EzkeyAdmin a LEFT JOIN FETCH a.mfaEnrollment WHERE a.username = :username")
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
   * Find admin by MFA enrollment ID.
   *
   * <p>Used during passwordless authentication to identify which admin is associated with a
   * specific enrollment when processing auth attempts. This is critical for the passwordless-wait
   * flow where we need to determine the admin from the authAttempt's enrollment ID.
   *
   * @param enrollmentId the MFA enrollment ID
   * @return Optional containing the admin if found
   */
  @Query("SELECT a FROM EzkeyAdmin a WHERE a.mfaEnrollment.enrollmentId = :enrollmentId")
  Optional<EzkeyAdmin> findByMfaEnrollmentEnrollmentId(@Param("enrollmentId") Integer enrollmentId);
}
