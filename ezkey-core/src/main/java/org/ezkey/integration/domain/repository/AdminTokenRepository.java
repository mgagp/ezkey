/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: AdminTokenRepository
 * Description: Spring Data JPA repository for AdminToken entity operations.
 */

package org.ezkey.integration.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.integration.domain.entity.AdminToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for AdminToken entity operations.
 *
 * <p>This repository provides data access methods for managing bearer tokens used for administrator
 * authentication in the Ezkey system. It includes methods for finding tokens by various criteria
 * and managing token lifecycle.
 *
 * <p><b>Token Operations:</b>
 *
 * <ul>
 *   <li><b>Authentication:</b> Find tokens by bearer token string for validation
 *   <li><b>Administrator Tokens:</b> Find all tokens for a specific administrator
 *   <li><b>Token Cleanup:</b> Find and remove expired tokens
 *   <li><b>Security:</b> Find active tokens for security monitoring
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminToken
 */
@Repository
public interface AdminTokenRepository extends JpaRepository<AdminToken, Integer> {

  /**
   * Finds a token by its bearer token string.
   *
   * <p>This method is used for token validation during API authentication to find the token record
   * by its bearer token string.
   *
   * @param bearerToken the bearer token string
   * @return Optional containing the token if found, empty otherwise
   */
  Optional<AdminToken> findByBearerToken(String bearerToken);

  /**
   * Finds an active token by its bearer token string.
   *
   * <p>This method is used for token validation during API authentication to find only active
   * tokens by their bearer token string.
   *
   * @param bearerToken the bearer token string
   * @return Optional containing the active token if found, empty otherwise
   */
  Optional<AdminToken> findByBearerTokenAndActiveTrue(String bearerToken);

  /**
   * Finds an active token by its bearer token string with admin eagerly loaded.
   *
   * <p>This method is used in filters where Hibernate session may be closed. It uses JOIN FETCH to
   * eagerly load the associated admin to avoid LazyInitializationException.
   *
   * @param bearerToken the bearer token string
   * @return Optional containing the active token with admin loaded, empty otherwise
   */
  @Query(
      "SELECT t FROM AdminToken t JOIN FETCH t.admin WHERE t.bearerToken = :bearerToken AND t.active = true")
  Optional<AdminToken> findByBearerTokenAndActiveTrueWithAdmin(
      @Param("bearerToken") String bearerToken);

  /**
   * Finds all tokens for a specific administrator.
   *
   * <p>This method is used to find all tokens belonging to a specific administrator for token
   * management and security purposes.
   *
   * @param adminId the ID of the administrator
   * @return list of tokens belonging to the administrator
   */
  List<AdminToken> findByAdminAdminId(Integer adminId);

  /**
   * Finds active tokens for a specific administrator.
   *
   * <p>This method is used to find only active tokens belonging to a specific administrator for
   * security monitoring and token management.
   *
   * @param adminId the ID of the administrator
   * @return list of active tokens belonging to the administrator
   */
  List<AdminToken> findByAdminAdminIdAndActiveTrue(Integer adminId);

  /**
   * Finds tokens by administrator type.
   *
   * <p>This method is used to find tokens for administrators of a specific type for security
   * monitoring and administration.
   *
   * @param adminType the type of administrator
   * @return list of tokens for administrators of the specified type
   */
  List<AdminToken> findByAdminType(String adminType);

  /**
   * Finds active tokens by administrator type.
   *
   * <p>This method is used to find only active tokens for administrators of a specific type for
   * security monitoring.
   *
   * @param adminType the type of administrator
   * @return list of active tokens for administrators of the specified type
   */
  List<AdminToken> findByAdminTypeAndActiveTrue(String adminType);

  /**
   * Finds tokens by tenant.
   *
   * <p>This method is used to find tokens for administrators belonging to a specific tenant for
   * tenant-specific security monitoring.
   *
   * @param tenantId the ID of the tenant
   * @return list of tokens for administrators in the tenant
   */
  List<AdminToken> findByTenantTenantId(Integer tenantId);

  /**
   * Finds active tokens by tenant.
   *
   * <p>This method is used to find only active tokens for administrators belonging to a specific
   * tenant for security monitoring.
   *
   * @param tenantId the ID of the tenant
   * @return list of active tokens for administrators in the tenant
   */
  List<AdminToken> findByTenantTenantIdAndActiveTrue(Integer tenantId);

  /**
   * Finds tokens by integration.
   *
   * <p>This method is used to find tokens for administrators managing a specific integration for
   * integration-specific security monitoring.
   *
   * @param integrationId the ID of the integration
   * @return list of tokens for administrators managing the integration
   */
  List<AdminToken> findByIntegrationId(Integer integrationId);

  /**
   * Finds active tokens by integration.
   *
   * <p>This method is used to find only active tokens for administrators managing a specific
   * integration for security monitoring.
   *
   * @param integrationId the ID of the integration
   * @return list of active tokens for administrators managing the integration
   */
  List<AdminToken> findByIntegrationIdAndActiveTrue(Integer integrationId);

  /**
   * Finds expired tokens.
   *
   * <p>This method is used for token cleanup to find tokens that have expired and should be removed
   * from the system.
   *
   * @param currentTime the current timestamp
   * @return list of expired tokens
   */
  List<AdminToken> findByExpiresAtBefore(OffsetDateTime currentTime);

  /**
   * Finds expired active tokens.
   *
   * <p>This method is used for token cleanup to find active tokens that have expired and should be
   * deactivated.
   *
   * @param currentTime the current timestamp
   * @return list of expired active tokens
   */
  List<AdminToken> findByExpiresAtBeforeAndActiveTrue(OffsetDateTime currentTime);

  /**
   * Finds tokens that have not been used recently.
   *
   * <p>This method is used for security monitoring to find tokens that have not been used for a
   * specified period of time.
   *
   * @param lastUsedBefore the timestamp before which tokens should not have been used
   * @return list of tokens not used since the specified time
   */
  List<AdminToken> findByLastUsedAtBefore(OffsetDateTime lastUsedBefore);

  /**
   * Deactivates expired tokens.
   *
   * <p>This method is used for token cleanup to deactivate tokens that have expired, marking them
   * as inactive for security purposes.
   *
   * @param currentTime the current timestamp
   * @return the number of tokens deactivated
   */
  @Modifying
  @Query(
      "UPDATE AdminToken t SET t.active = false WHERE t.expiresAt < :currentTime AND t.active = true")
  int deactivateExpiredTokens(@Param("currentTime") OffsetDateTime currentTime);

  /**
   * Deactivates all tokens for a specific administrator.
   *
   * <p>This method is used for security purposes to deactivate all tokens belonging to a specific
   * administrator, effectively logging them out.
   *
   * @param adminId the ID of the administrator
   * @return the number of tokens deactivated
   */
  @Modifying
  @Query(
      "UPDATE AdminToken t SET t.active = false WHERE t.admin.adminId = :adminId AND t.active = true")
  int deactivateTokensByAdmin(@Param("adminId") Integer adminId);

  /**
   * Counts active tokens for a specific administrator.
   *
   * <p>This method is used for security monitoring to count how many active tokens a specific
   * administrator has.
   *
   * @param adminId the ID of the administrator
   * @return the count of active tokens for the administrator
   */
  long countByAdminAdminIdAndActiveTrue(Integer adminId);

  /**
   * Counts active tokens by tenant.
   *
   * <p>This method is used for security monitoring to count how many active tokens exist for
   * administrators in a specific tenant.
   *
   * @param tenantId the ID of the tenant
   * @return the count of active tokens for the tenant
   */
  long countByTenantTenantIdAndActiveTrue(Integer tenantId);

  /**
   * Counts expired tokens.
   *
   * <p>This method is used for system monitoring to count how many tokens have expired and need
   * cleanup.
   *
   * @param currentTime the current timestamp
   * @return the count of expired tokens
   */
  long countByExpiresAtBefore(OffsetDateTime currentTime);

  /**
   * Deletes tokens that are expired AND inactive.
   *
   * <p>This method is used for token cleanup to remove tokens that are both expired and inactive
   * from the database. Active tokens are preserved even if expired for audit trail purposes.
   *
   * @param cutoff the cutoff timestamp (tokens expired before this are deleted)
   * @return the number of tokens deleted
   */
  @Modifying
  @Query("DELETE FROM AdminToken t WHERE t.expiresAt < :cutoff AND t.active = false")
  int deleteByExpiresAtBeforeAndActiveFalse(@Param("cutoff") OffsetDateTime cutoff);

  /**
   * Counts tokens that are expired AND inactive.
   *
   * <p>This method is used for monitoring to count how many tokens are eligible for cleanup
   * (expired and inactive).
   *
   * @param cutoff the cutoff timestamp
   * @return the count of expired and inactive tokens
   */
  long countByExpiresAtBeforeAndActiveFalse(OffsetDateTime cutoff);

  /**
   * Deactivates all active tokens for a specific administrator.
   *
   * <p>This method is used during login to enforce "one active token per admin" policy by
   * deactivating all previous tokens when an administrator logs in.
   *
   * @param adminId the ID of the administrator
   * @return the number of tokens deactivated
   */
  @Modifying
  @Query(
      "UPDATE AdminToken t SET t.active = false WHERE t.admin.adminId = :adminId AND t.active = true")
  int deactivateAllTokensForAdmin(@Param("adminId") Integer adminId);
}
