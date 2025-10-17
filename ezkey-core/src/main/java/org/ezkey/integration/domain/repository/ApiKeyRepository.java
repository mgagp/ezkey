/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Repository: ApiKeyRepository
 * Description: Spring Data JPA repository for ApiKey entity operations.
 */

package org.ezkey.integration.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.integration.domain.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for ApiKey entity operations.
 *
 * <p>This repository provides data access methods for API key management, including key lookup,
 * validation, and lifecycle operations. Supports both manual queries and derived query methods.
 *
 * <p><b>Key Operations:</b>
 *
 * <ul>
 *   <li><b>Lookup:</b> Find active keys by integration key for authentication
 *   <li><b>Management:</b> List keys by integration or admin for administration
 *   <li><b>Cleanup:</b> Find expired keys for scheduled cleanup tasks
 *   <li><b>Revocation:</b> Immediate invalidation of compromised keys
 * </ul>
 *
 * <p><b>Performance Considerations:</b>
 *
 * <ul>
 *   <li>findByIntegrationKeyAndActiveTrue uses partial index for fast lookup
 *   <li>findExpiredKeys optimized with conditional index
 *   <li>Revocation uses UPDATE query to avoid entity loading overhead
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKey
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Integer> {

  /**
   * Finds an active API key by its public integration key.
   *
   * <p>This method is used for authentication validation. It uses a partial index for fast lookup
   * of active keys only.
   *
   * <p><b>Performance:</b> Uses idx_api_key_lookup partial index
   *
   * @param integrationKey the public integration key (ezkey_ikey_xxx)
   * @return Optional containing the API key if found and active, empty otherwise
   */
  Optional<ApiKey> findByIntegrationKeyAndActiveTrue(String integrationKey);

  /**
   * Finds all active API keys for a specific integration.
   *
   * <p>This method is used to list all active keys for an integration, supporting key rotation
   * scenarios where multiple keys may be active simultaneously during transition periods.
   *
   * <p><b>Performance:</b> Uses idx_api_key_integration index
   *
   * @param integrationId the integration ID
   * @return List of active API keys for the integration
   */
  List<ApiKey> findByIntegration_IdAndActiveTrue(Integer integrationId);

  /**
   * Finds all API keys (active and revoked) for a specific integration.
   *
   * <p>This method is used for administrative views showing complete key history including revoked
   * keys.
   *
   * @param integrationId the integration ID
   * @return List of all API keys for the integration
   */
  List<ApiKey> findByIntegration_Id(Integer integrationId);

  /**
   * Finds all API keys created by a specific administrator.
   *
   * <p>This method is used for audit purposes to track which keys were created by which admin.
   *
   * <p><b>Performance:</b> Uses idx_api_key_admin index
   *
   * @param adminId the administrator ID
   * @return List of API keys created by the specified admin
   */
  List<ApiKey> findByCreatedByAdmin_AdminId(Integer adminId);

  /**
   * Finds all expired API keys that are still marked as active.
   *
   * <p>This method is used by scheduled cleanup tasks to identify and deactivate expired keys. Keys
   * are considered expired if expires_at is in the past and active is still true.
   *
   * <p><b>Performance:</b> Uses idx_api_key_expiration index
   *
   * <p><b>Usage:</b> Scheduled task runs periodically (e.g., hourly) to cleanup expired keys
   *
   * @param now the current timestamp for comparison
   * @return List of expired but still active API keys
   */
  @Query("SELECT a FROM ApiKey a WHERE a.expiresAt < :now AND a.active = true")
  List<ApiKey> findExpiredKeys(@Param("now") OffsetDateTime now);

  /**
   * Revokes an API key by setting it inactive and recording revocation metadata.
   *
   * <p>This method performs an immediate revocation using a direct UPDATE query, avoiding the
   * overhead of loading the entity. It sets active=false, records revocation timestamp, and
   * captures which admin performed the revocation.
   *
   * <p><b>Transaction:</b> This method must be called within a transaction
   *
   * <p><b>Return Value:</b> Number of rows affected (1 if successful, 0 if key not found)
   *
   * @param keyId the API key ID to revoke
   * @param now the revocation timestamp
   * @param adminId the administrator ID performing the revocation
   * @return number of rows affected (1 if revoked, 0 if not found)
   */
  @Modifying
  @Query(
      "UPDATE ApiKey a SET a.active = false, a.revokedAt = :now, a.revokedByAdmin.adminId ="
          + " :adminId WHERE a.apiKeyId = :keyId")
  int revokeKey(
      @Param("keyId") Integer keyId,
      @Param("now") OffsetDateTime now,
      @Param("adminId") Integer adminId);

  /**
   * Counts the number of active API keys for a specific integration.
   *
   * <p>This method is used to enforce limits on the number of active keys per integration (e.g.,
   * maximum 5 active keys).
   *
   * @param integrationId the integration ID
   * @return count of active API keys for the integration
   */
  long countByIntegration_IdAndActiveTrue(Integer integrationId);

  /**
   * Finds all API keys that have never been used.
   *
   * <p>This method is useful for identifying potentially unused or forgotten keys that may be safe
   * to revoke.
   *
   * @return List of API keys where lastUsedAt is null
   */
  List<ApiKey> findByLastUsedAtIsNull();

  /**
   * Finds active API keys that haven't been used since a specific date.
   *
   * <p>This method helps identify stale keys that may be safe to revoke or notify admins about.
   *
   * @param threshold the timestamp threshold (keys not used since this time)
   * @return List of API keys not used since the threshold
   */
  @Query(
      "SELECT a FROM ApiKey a WHERE a.active = true AND a.lastUsedAt < :threshold ORDER BY"
          + " a.lastUsedAt ASC")
  List<ApiKey> findStaleKeys(@Param("threshold") OffsetDateTime threshold);

  /**
   * Finds active API keys expiring within a specified time window.
   *
   * <p>This method is used for proactive notifications to admins about keys expiring soon, allowing
   * time for key rotation before expiration.
   *
   * <p><b>Usage:</b> Send warning emails for keys expiring in 30/7/1 days
   *
   * @param startTime the start of the time window (typically now)
   * @param endTime the end of the time window (e.g., now + 30 days)
   * @return List of API keys expiring within the specified window
   */
  @Query(
      "SELECT a FROM ApiKey a WHERE a.active = true AND a.expiresAt >= :startTime AND a.expiresAt"
          + " < :endTime ORDER BY a.expiresAt ASC")
  List<ApiKey> findKeysExpiringBetween(
      @Param("startTime") OffsetDateTime startTime, @Param("endTime") OffsetDateTime endTime);
}
