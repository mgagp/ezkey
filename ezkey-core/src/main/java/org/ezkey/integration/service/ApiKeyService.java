/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: ApiKeyService
 * Description: Business logic for API key management and validation.
 */

package org.ezkey.integration.service;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.commons.codec.binary.Hex;
import org.ezkey.integration.domain.entity.ApiKey;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.ApiKeyRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for API key management and validation.
 *
 * <p>This service provides business logic for the complete lifecycle of API keys including
 * generation, validation, rotation, and revocation. It handles security aspects such as BCrypt
 * hashing, IP whitelist validation, and expiration checks.
 *
 * <p><b>Key Operations:</b>
 *
 * <ul>
 *   <li><b>Generation:</b> Create new API key pairs with secure random generation
 *   <li><b>Validation:</b> Authenticate applications using integration key + secret key
 *   <li><b>Management:</b> List, revoke, and monitor API keys
 *   <li><b>Cleanup:</b> Scheduled tasks for expired key handling
 * </ul>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li>BCrypt hashing for secret keys (same security as passwords)
 *   <li>IP whitelist validation with CIDR support
 *   <li>Expiration enforcement for automatic rotation
 *   <li>Rate limiting integration (configured externally)
 *   <li>Comprehensive audit logging
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKey
 * @see ApiKeyRepository
 */
@Service
public class ApiKeyService {

  private static final Logger logger = LoggerFactory.getLogger(ApiKeyService.class);

  // API key format constants
  private static final String INTEGRATION_KEY_PREFIX = "ezkey_ikey_";
  private static final String SECRET_KEY_PREFIX = "ezkey_skey_";
  private static final int INTEGRATION_KEY_HEX_LENGTH = 20;
  private static final int SECRET_KEY_HEX_LENGTH = 40;
  private static final int MAX_ACTIVE_KEYS_PER_INTEGRATION = 5;

  private final ApiKeyRepository apiKeyRepository;
  private final IntegrationRepository integrationRepository;
  private final BCryptPasswordEncoder passwordEncoder;
  private final SecureRandom secureRandom;

  /**
   * Constructs a new ApiKeyService with required dependencies.
   *
   * @param apiKeyRepository the API key repository
   * @param integrationRepository the integration repository
   * @param passwordEncoder the BCrypt password encoder for secret key hashing
   */
  public ApiKeyService(
      ApiKeyRepository apiKeyRepository,
      IntegrationRepository integrationRepository,
      BCryptPasswordEncoder passwordEncoder) {
    this.apiKeyRepository = apiKeyRepository;
    this.integrationRepository = integrationRepository;
    this.passwordEncoder = passwordEncoder;
    this.secureRandom = new SecureRandom();
  }

  /**
   * Creates a new API key pair for an integration.
   *
   * <p>This method generates a secure API key pair consisting of a public integration key and a
   * private secret key. The secret key is shown once in the response and then hashed with BCrypt
   * for storage.
   *
   * <p><b>Generation Process:</b>
   *
   * <ol>
   *   <li>Validate integration exists and is active
   *   <li>Check active key limit (max 5 per integration)
   *   <li>Generate unique integration key (ezkey_ikey_xxx)
   *   <li>Generate secure secret key (ezkey_skey_xxx)
   *   <li>Hash secret key with BCrypt
   *   <li>Save to database with audit metadata
   * </ol>
   *
   * <p><b>Security:</b> Secret key uses SecureRandom with 40 hex chars (160 bits entropy)
   *
   * @param integrationId the integration ID to create the key for
   * @param createdByAdmin the admin creating the key (for audit)
   * @param description optional description for the key
   * @param expiresAt optional expiration date for automatic rotation
   * @param ipWhitelist optional array of IP addresses or CIDR ranges
   * @return ApiKeyCreationResult containing both keys and metadata
   * @throws IllegalArgumentException if integration not found or inactive
   * @throws IllegalStateException if maximum active keys limit reached
   */
  @Transactional
  public ApiKeyCreationResult createApiKey(
      Integer integrationId,
      EzkeyAdmin createdByAdmin,
      String description,
      OffsetDateTime expiresAt,
      String[] ipWhitelist) {

    logger.info("Creating API key for integration: {}", integrationId);

    // Validate integration exists and is active
    Integration integration =
        integrationRepository
            .findById(integrationId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Integration not found with ID: " + integrationId));

    if (!integration.getActive()) {
      throw new IllegalArgumentException(
          "Cannot create API key for inactive integration: " + integrationId);
    }

    // Security: Block API key creation for inactive tenants
    if (integration.getTenant() != null && !integration.getTenant().getActive()) {
      logger.warn(
          "API key creation blocked: tenant (ID: {}) is inactive for integration {}",
          integration.getTenant().getTenantId(),
          integrationId);
      throw new IllegalStateException(
          "Cannot create API key for inactive tenant. Contact your Ezkey administrator.");
    }

    // Check active key limit
    long activeKeyCount = apiKeyRepository.countByIntegration_IdAndActiveTrue(integrationId);
    if (activeKeyCount >= MAX_ACTIVE_KEYS_PER_INTEGRATION) {
      throw new IllegalStateException(
          "Maximum active keys limit (%d) reached for integration: %d"
              .formatted(MAX_ACTIVE_KEYS_PER_INTEGRATION, integrationId));
    }

    // Generate unique integration key
    String integrationKey = generateIntegrationKey();

    // Ensure uniqueness (very rare collision but check anyway)
    while (apiKeyRepository.findByIntegrationKeyAndActiveTrue(integrationKey).isPresent()) {
      logger.warn("Integration key collision detected, regenerating");
      integrationKey = generateIntegrationKey();
    }

    // Generate secret key
    String secretKey = generateSecretKey();

    // Hash secret key with BCrypt
    String secretKeyHash = passwordEncoder.encode(secretKey);

    // Validate IP whitelist if provided
    if (ipWhitelist != null && ipWhitelist.length > 0) {
      validateIpWhitelist(ipWhitelist);
    }

    // Create and save API key entity
    ApiKey apiKey = new ApiKey();
    apiKey.setIntegration(integration);
    apiKey.setIntegrationKey(integrationKey);
    apiKey.setSecretKeyHash(secretKeyHash);
    apiKey.setDescription(description);
    apiKey.setCreatedByAdmin(createdByAdmin);
    apiKey.setExpiresAt(expiresAt);
    apiKey.setIpWhitelist(ipWhitelist);
    apiKey.setCreatedAt(OffsetDateTime.now());
    apiKey.setActive(true);

    apiKey = apiKeyRepository.save(apiKey);

    logger.info(
        "API key created successfully - ID: {}, Integration: {}, Admin: {}",
        apiKey.getApiKeyId(),
        integrationId,
        createdByAdmin.getUsername());

    // Return result with plain text secret key (shown only once)
    return new ApiKeyCreationResult(
        apiKey.getApiKeyId(),
        integrationKey,
        secretKey, // Plain text - SHOWN ONLY ONCE
        description,
        apiKey.getCreatedAt(),
        expiresAt,
        ipWhitelist);
  }

  /**
   * Validates an API key for authentication.
   *
   * <p>This method validates an API key authentication attempt by checking the integration key,
   * verifying the secret key against the BCrypt hash, and validating security constraints.
   *
   * <p><b>Validation Steps:</b>
   *
   * <ol>
   *   <li>Look up API key by integration key
   *   <li>Verify key is active (not revoked)
   *   <li>Check expiration if set
   *   <li>Validate secret key against BCrypt hash
   *   <li>Check IP whitelist if configured
   *   <li>Update last used timestamp
   * </ol>
   *
   * <p><b>Performance:</b> Uses BCrypt comparison which is intentionally slow (~100ms) to prevent
   * brute force attacks
   *
   * @param integrationKey the public integration key
   * @param secretKey the secret key (plain text)
   * @param clientIp the client IP address for whitelist validation
   * @return Optional containing the Integration if validation successful, empty otherwise
   */
  @Transactional
  public Optional<Integration> validateApiKey(
      String integrationKey, String secretKey, String clientIp) {

    logger.debug("Validating API key: {}...", integrationKey.substring(0, 15));

    // Look up API key
    Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByIntegrationKeyAndActiveTrue(integrationKey);

    if (apiKeyOpt.isEmpty()) {
      logger.warn("API key not found or inactive: {}", integrationKey);
      return Optional.empty();
    }

    ApiKey apiKey = apiKeyOpt.get();

    // Check expiration
    if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(OffsetDateTime.now())) {
      logger.warn("API key expired: {} (expired at: {})", integrationKey, apiKey.getExpiresAt());
      return Optional.empty();
    }

    // Validate secret key with BCrypt
    if (!passwordEncoder.matches(secretKey, apiKey.getSecretKeyHash())) {
      logger.warn("Invalid secret key for integration key: {}", integrationKey);
      return Optional.empty();
    }

    // Check IP whitelist if configured
    if (apiKey.getIpWhitelist() != null
        && apiKey.getIpWhitelist().length > 0
        && !isIpWhitelisted(clientIp, apiKey.getIpWhitelist())) {
      logger.warn("IP address {} not whitelisted for API key: {}", clientIp, integrationKey);
      return Optional.empty();
    }

    // Update last used timestamp
    apiKey.setLastUsedAt(OffsetDateTime.now());
    apiKeyRepository.save(apiKey);

    // Check if the parent integration's tenant is active
    Integration integration = apiKey.getIntegration();
    if (integration.getTenant() != null && !integration.getTenant().getActive()) {
      logger.warn("API key rejected: tenant inactive for integration: {}", integration.getId());
      return Optional.empty();
    }

    logger.info(
        "API key validated successfully: {} for integration: {}",
        integrationKey,
        apiKey.getIntegration().getId());

    return Optional.of(apiKey.getIntegration());
  }

  /**
   * Lists all API keys for integrations belonging to a specific tenant.
   *
   * <p>This method is used for tenant-scoped admin operations to list all API keys (active and
   * revoked) for integrations within a specific tenant.
   *
   * @param tenantId the tenant ID to filter by
   * @return List of API keys for the tenant's integrations
   */
  @Transactional(readOnly = true)
  public List<ApiKey> listApiKeysByTenant(Integer tenantId) {
    return apiKeyRepository.findByIntegration_Tenant_TenantId(tenantId);
  }

  /**
   * Lists all API keys across all integrations and tenants.
   *
   * <p>This method is used by GlobalAdmin to view all API keys in the system. Should be used with
   * caution in production due to potential large result sets.
   *
   * @return List of all API keys in the system
   */
  @Transactional(readOnly = true)
  public List<ApiKey> findAll() {
    return apiKeyRepository.findAll();
  }

  /**
   * Lists all active API keys for an integration.
   *
   * <p>This method returns all active (non-revoked) API keys for a specific integration. Used for
   * administration and monitoring purposes.
   *
   * @param integrationId the integration ID
   * @return List of active API keys
   */
  @Transactional(readOnly = true)
  public List<ApiKey> listActiveApiKeys(Integer integrationId) {
    return apiKeyRepository.findByIntegration_IdAndActiveTrue(integrationId);
  }

  /**
   * Lists all API keys (including revoked) for an integration.
   *
   * <p>This method returns the complete history of API keys for audit purposes.
   *
   * @param integrationId the integration ID
   * @return List of all API keys (active and revoked)
   */
  @Transactional(readOnly = true)
  public List<ApiKey> listAllApiKeys(Integer integrationId) {
    return apiKeyRepository.findByIntegration_Id(integrationId);
  }

  /**
   * Gets a specific API key by ID.
   *
   * @param keyId the API key ID
   * @return Optional containing the API key if found
   */
  @Transactional(readOnly = true)
  public Optional<ApiKey> getApiKey(Integer keyId) {
    return apiKeyRepository.findById(keyId);
  }

  /**
   * Partially updates an API key's configuration.
   *
   * <p>Only non-null fields in the request are applied. Supports ipWhitelist and description. Only
   * active (non-revoked) keys can be updated.
   *
   * @param keyId the API key ID to update
   * @param description new description (null = don't change)
   * @param ipWhitelist new IP whitelist (null = don't change; empty array = remove restrictions)
   * @param version optimistic lock version (null = skip check)
   * @return the updated API key
   * @throws org.ezkey.exception.ResourceNotFoundException if key not found
   * @throws IllegalArgumentException if key is revoked or ipWhitelist validation fails
   * @throws org.springframework.orm.ObjectOptimisticLockingFailureException if version mismatch
   */
  @Transactional
  public ApiKey updateApiKey(
      Integer keyId, String description, String[] ipWhitelist, Long version) {

    ApiKey apiKey =
        apiKeyRepository
            .findById(keyId)
            .orElseThrow(() -> new org.ezkey.exception.ResourceNotFoundException("API key", keyId));

    if (!Boolean.TRUE.equals(apiKey.getActive())) {
      throw new IllegalArgumentException(
          "API key cannot be updated: key has been revoked. Create a new key instead.");
    }

    if (version != null && !version.equals(apiKey.getVersion())) {
      throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
          ApiKey.class, keyId);
    }

    if (description != null) {
      apiKey.setDescription(description);
    }

    if (ipWhitelist != null) {
      if (ipWhitelist.length > 0) {
        validateIpWhitelist(ipWhitelist);
      }
      apiKey.setIpWhitelist(ipWhitelist.length == 0 ? null : ipWhitelist);
    }

    apiKey = apiKeyRepository.save(apiKey);
    logger.info(
        "API key {} config updated (integration: {})", keyId, apiKey.getIntegration().getId());
    return apiKey;
  }

  /**
   * Revokes an API key immediately.
   *
   * <p>This method performs immediate revocation of an API key, making it unusable for
   * authentication. The key is preserved for audit purposes with revocation metadata.
   *
   * <p><b>Revocation Process:</b>
   *
   * <ol>
   *   <li>Set active = false
   *   <li>Record revocation timestamp
   *   <li>Record revoking admin for audit
   * </ol>
   *
   * <p><b>Usage:</b> Use for compromised keys or during key rotation cleanup
   *
   * @param keyId the API key ID to revoke
   * @param revokedByAdmin the admin performing the revocation
   * @return true if revoked successfully, false if key not found
   */
  @Transactional
  public boolean revokeApiKey(Integer keyId, EzkeyAdmin revokedByAdmin) {
    logger.info("Revoking API key ID: {} by admin: {}", keyId, revokedByAdmin.getUsername());

    int rowsAffected =
        apiKeyRepository.revokeKey(keyId, OffsetDateTime.now(), revokedByAdmin.getAdminId());

    if (rowsAffected > 0) {
      logger.info("API key revoked successfully: {}", keyId);
      return true;
    } else {
      logger.warn("API key not found for revocation: {}", keyId);
      return false;
    }
  }

  /**
   * Cleans up expired API keys (scheduled task).
   *
   * <p>This method finds and deactivates all API keys that have passed their expiration date but
   * are still marked as active. Should be run periodically (e.g., hourly).
   *
   * <p><b>Note:</b> Expired keys are deactivated but preserved for audit purposes
   *
   * @return count of keys deactivated
   */
  @Transactional
  public int cleanupExpiredKeys() {
    logger.info("Running expired API keys cleanup");

    List<ApiKey> expiredKeys = apiKeyRepository.findExpiredKeys(OffsetDateTime.now());

    for (ApiKey key : expiredKeys) {
      key.setActive(false);
      key.setRevokedAt(OffsetDateTime.now());
      // Note: revoked_by_admin_id is null for automatic expiration (vs manual
      // revocation)
      apiKeyRepository.save(key);

      logger.info(
          "Expired API key deactivated: {} (integration: {})",
          key.getIntegrationKey(),
          key.getIntegration().getId());
    }

    logger.info("Cleanup complete - {} expired keys deactivated", expiredKeys.size());
    return expiredKeys.size();
  }

  /**
   * Finds API keys expiring within a specified time window.
   *
   * <p>This method is used for proactive notifications to admins about keys expiring soon.
   *
   * @param days number of days to look ahead
   * @return List of keys expiring within the specified days
   */
  @Transactional(readOnly = true)
  public List<ApiKey> findKeysExpiringSoon(int days) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime threshold = now.plusDays(days);
    return apiKeyRepository.findKeysExpiringBetween(now, threshold);
  }

  /**
   * Generates a unique integration key.
   *
   * <p>Format: ezkey_ikey_[20 hex chars] Example: ezkey_ikey_a1b2c3d4e5f6g7h8i9j0
   *
   * <p><b>Security:</b> Uses SecureRandom with 10 bytes (80 bits entropy)
   *
   * @return the generated integration key
   */
  private String generateIntegrationKey() {
    String hex = generateSecureHex(INTEGRATION_KEY_HEX_LENGTH);
    return INTEGRATION_KEY_PREFIX + hex;
  }

  /**
   * Generates a secure secret key.
   *
   * <p>Format: ezkey_skey_[40 hex chars] Example:
   * ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0
   *
   * <p><b>Security:</b> Uses SecureRandom with 20 bytes (160 bits entropy)
   *
   * @return the generated secret key
   */
  private String generateSecretKey() {
    String hex = generateSecureHex(SECRET_KEY_HEX_LENGTH);
    return SECRET_KEY_PREFIX + hex;
  }

  /**
   * Generates a secure hex string of specified length.
   *
   * <p><b>Security:</b> Uses SecureRandom for cryptographically secure random generation
   *
   * @param length the desired hex string length (must be even)
   * @return hex string of specified length
   */
  private String generateSecureHex(int length) {
    byte[] bytes = new byte[length / 2];
    secureRandom.nextBytes(bytes);
    return Hex.encodeHexString(bytes);
  }

  /**
   * Validates IP whitelist format.
   *
   * <p>Checks that all entries are valid IP addresses or CIDR ranges.
   *
   * @param ipWhitelist the IP whitelist to validate
   * @throws IllegalArgumentException if any entry is invalid
   */
  private void validateIpWhitelist(String[] ipWhitelist) {
    for (String ip : ipWhitelist) {
      IPAddressString ipAddressString = new IPAddressString(ip);
      if (!ipAddressString.isValid()) {
        throw new IllegalArgumentException("Invalid IP address or CIDR in whitelist: " + ip);
      }
    }
  }

  /**
   * Checks if a client IP is whitelisted.
   *
   * <p>Supports both individual IP addresses and CIDR ranges.
   *
   * @param clientIp the client IP address
   * @param whitelist the IP whitelist
   * @return true if IP is whitelisted, false otherwise
   */
  private boolean isIpWhitelisted(String clientIp, String[] whitelist) {
    if (clientIp == null || whitelist == null || whitelist.length == 0) {
      return false;
    }

    IPAddressString clientIpAddress = new IPAddressString(clientIp);
    if (!clientIpAddress.isValid()) {
      logger.warn("Invalid client IP address: {}", clientIp);
      return false;
    }

    IPAddress clientAddr = clientIpAddress.getAddress();

    for (String whitelistedIp : whitelist) {
      IPAddressString whitelistedAddress = new IPAddressString(whitelistedIp);
      if (whitelistedAddress.isValid()) {
        IPAddress whitelistAddr = whitelistedAddress.getAddress();

        // Check if client IP matches or is within CIDR range
        if (whitelistAddr.contains(clientAddr)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Result object returned when creating an API key.
   *
   * <p>Contains both the public integration key and the plain text secret key. The secret key is
   * ONLY shown in this response and never again.
   */
  public static class ApiKeyCreationResult {
    private final Integer apiKeyId;
    private final String integrationKey;
    private final String secretKey; // Plain text - SHOWN ONCE ONLY
    private final String description;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime expiresAt;
    private final String[] ipWhitelist;

    public ApiKeyCreationResult(
        Integer apiKeyId,
        String integrationKey,
        String secretKey,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        String[] ipWhitelist) {
      this.apiKeyId = apiKeyId;
      this.integrationKey = integrationKey;
      this.secretKey = secretKey;
      this.description = description;
      this.createdAt = createdAt;
      this.expiresAt = expiresAt;
      this.ipWhitelist = ipWhitelist;
    }

    public Integer getApiKeyId() {
      return apiKeyId;
    }

    public String getIntegrationKey() {
      return integrationKey;
    }

    public String getSecretKey() {
      return secretKey;
    }

    public String getDescription() {
      return description;
    }

    public OffsetDateTime getCreatedAt() {
      return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
      return expiresAt;
    }

    public String[] getIpWhitelist() {
      return ipWhitelist;
    }
  }
}
