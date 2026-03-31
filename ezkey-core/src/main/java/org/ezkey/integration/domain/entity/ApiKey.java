/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: ApiKey
 * Description: JPA entity representing an API key for machine-to-machine authentication.
 */

package org.ezkey.integration.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

/**
 * JPA entity representing an API key for machine-to-machine authentication.
 *
 * <p>This entity stores Duo-style API keys used for server-to-server authentication by integrated
 * applications. Each API key consists of a public integration key and a secret key (hashed), both
 * associated with a specific integration.
 *
 * <p><b>API Key Components:</b>
 *
 * <ul>
 *   <li><b>Integration Key:</b> Public identifier (ezkey_ikey_xxx) stored in plain text
 *   <li><b>Secret Key:</b> Private secret (ezkey_skey_xxx) stored as BCrypt hash
 * </ul>
 *
 * <p><b>Authentication Flow:</b>
 *
 * <ul>
 *   <li>Application sends HTTP Basic Auth with integration_key:secret_key
 *   <li>System validates secret against BCrypt hash
 *   <li>Optional IP whitelist and expiration checks
 *   <li>Rate limiting applied per integration key
 * </ul>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li><b>BCrypt Hashing:</b> Secret keys hashed like passwords, never stored in plain text
 *   <li><b>IP Whitelist:</b> Optional restriction to specific IP addresses or CIDR ranges
 *   <li><b>Expiration:</b> Optional expiration date for enforced key rotation
 *   <li><b>Revocation:</b> Immediate invalidation capability for compromised keys
 *   <li><b>Audit Trail:</b> Comprehensive tracking of creation, usage, and revocation
 * </ul>
 *
 * <p><b>Key Rotation:</b>
 *
 * <ul>
 *   <li>Manual rotation: Admin creates new key, application updates config, old key revoked
 *   <li>Automatic expiration: Optional expires_at enforces periodic rotation
 *   <li>Transition period: Multiple active keys supported during migration
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see Integration
 * @see EzkeyAdmin
 */
@Entity
@Table(name = "ezkey_api_key")
public class ApiKey {

  /**
   * Primary key identifier for the API key.
   *
   * <p>This field is auto-generated using the database identity column.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "api_key_id")
  private Integer apiKeyId;

  /**
   * Optimistic locking version for concurrent update protection.
   *
   * <p>Used by JPA to detect concurrent modifications. When a PATCH request includes a stale
   * version, the update fails and the API returns 409 Conflict. Client must re-fetch and retry.
   */
  @Version
  @Column(name = "version", nullable = false)
  private Long version = 0L;

  /**
   * Reference to the integration this API key belongs to.
   *
   * <p>This field establishes the relationship between the API key and the integration it
   * authenticates for. Each API key is tied to exactly one integration.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "integration_id", nullable = false)
  private Integration integration;

  /**
   * Public integration key identifier.
   *
   * <p>Format: ezkey_ikey_[20 hex chars]
   *
   * <p>This field stores the public portion of the API key pair. It can be safely displayed in
   * logs, UI, and documentation. Must be unique across all API keys.
   */
  @Column(name = "integration_key", nullable = false, unique = true, length = 30)
  private String integrationKey;

  /**
   * BCrypt hash of the secret key.
   *
   * <p>Format: BCrypt hash of ezkey_skey_[40 hex chars]
   *
   * <p>This field stores the hashed secret key. The plain text secret is shown only once during API
   * key creation and never stored or displayed again. Validated using BCrypt comparison.
   */
  @Column(name = "secret_key_hash", nullable = false, length = 255)
  private String secretKeyHash;

  /**
   * Human-readable description of this API key.
   *
   * <p>This field helps administrators identify the purpose or location of the API key (e.g.,
   * "Production Server API Key", "Staging Environment", "CI/CD Pipeline").
   */
  @Column(name = "description", length = 255)
  private String description;

  /**
   * Reference to the administrator who created this API key.
   *
   * <p>This field is required for audit trail and accountability. Tracks which admin generated the
   * key.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_admin_id", nullable = false)
  private EzkeyAdmin createdByAdmin;

  /**
   * Timestamp of the last successful authentication using this key.
   *
   * <p>This field is updated each time the API key is successfully used to authenticate. Useful for
   * monitoring key usage and detecting unused keys.
   */
  @Column(name = "last_used_at")
  private OffsetDateTime lastUsedAt;

  /**
   * Optional expiration timestamp for this API key.
   *
   * <p>When set, the key automatically becomes invalid after this date/time, enforcing periodic
   * rotation. Null means the key never expires.
   */
  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  /**
   * Optional array of IP addresses or CIDR ranges allowed to use this key.
   *
   * <p>When set, authentication requests are only accepted from IPs matching this whitelist. Format
   * examples: ["192.168.1.100", "10.0.0.0/24"]. Null means no IP restriction.
   */
  @Column(name = "ip_whitelist", columnDefinition = "TEXT[]")
  private String[] ipWhitelist;

  /**
   * Flag indicating whether this API key is active.
   *
   * <p>Inactive keys cannot be used for authentication but are preserved for audit purposes. Set to
   * false when key is revoked.
   */
  @Column(name = "active", nullable = false)
  private Boolean active = true;

  /**
   * Timestamp when this API key was created.
   *
   * <p>This field is automatically set to the current timestamp when the key is created and is
   * immutable for audit compliance.
   */
  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  /**
   * Timestamp when this API key was revoked.
   *
   * <p>This field is null for active keys and set to the revocation time when the key is explicitly
   * revoked by an administrator.
   */
  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  /**
   * Reference to the administrator who revoked this API key.
   *
   * <p>This field is null for active keys and required when a key is revoked, providing full audit
   * trail of who revoked the key and when.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "revoked_by_admin_id")
  private EzkeyAdmin revokedByAdmin;

  /**
   * Default constructor for JPA.
   *
   * <p>This constructor is required by JPA and should not be used for business logic. Use the
   * builder pattern or setters to construct instances.
   */
  public ApiKey() {
    // Default constructor for JPA
  }

  /**
   * Gets the API key ID.
   *
   * @return the API key ID
   */
  public Integer getApiKeyId() {
    return apiKeyId;
  }

  /**
   * Sets the API key ID.
   *
   * @param apiKeyId the API key ID
   */
  public void setApiKeyId(Integer apiKeyId) {
    this.apiKeyId = apiKeyId;
  }

  /**
   * Gets the integration.
   *
   * @return the integration
   */
  public Integration getIntegration() {
    return integration;
  }

  /**
   * Sets the integration.
   *
   * @param integration the integration
   */
  public void setIntegration(Integration integration) {
    this.integration = integration;
  }

  /**
   * Gets the integration key.
   *
   * @return the integration key (public identifier)
   */
  public String getIntegrationKey() {
    return integrationKey;
  }

  /**
   * Sets the integration key.
   *
   * @param integrationKey the integration key (public identifier)
   */
  public void setIntegrationKey(String integrationKey) {
    this.integrationKey = integrationKey;
  }

  /**
   * Gets the secret key hash.
   *
   * @return the BCrypt hash of the secret key
   */
  public String getSecretKeyHash() {
    return secretKeyHash;
  }

  /**
   * Sets the secret key hash.
   *
   * @param secretKeyHash the BCrypt hash of the secret key
   */
  public void setSecretKeyHash(String secretKeyHash) {
    this.secretKeyHash = secretKeyHash;
  }

  /**
   * Gets the description.
   *
   * @return the description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Sets the description.
   *
   * @param description the description
   */
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Gets the admin who created this key.
   *
   * @return the creating administrator
   */
  public EzkeyAdmin getCreatedByAdmin() {
    return createdByAdmin;
  }

  /**
   * Sets the admin who created this key.
   *
   * @param createdByAdmin the creating administrator
   */
  public void setCreatedByAdmin(EzkeyAdmin createdByAdmin) {
    this.createdByAdmin = createdByAdmin;
  }

  /**
   * Gets the last used timestamp.
   *
   * @return the last used timestamp
   */
  public OffsetDateTime getLastUsedAt() {
    return lastUsedAt;
  }

  /**
   * Sets the last used timestamp.
   *
   * @param lastUsedAt the last used timestamp
   */
  public void setLastUsedAt(OffsetDateTime lastUsedAt) {
    this.lastUsedAt = lastUsedAt;
  }

  /**
   * Gets the expiration timestamp.
   *
   * @return the expiration timestamp
   */
  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  /**
   * Sets the expiration timestamp.
   *
   * @param expiresAt the expiration timestamp
   */
  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  /**
   * Gets the IP whitelist.
   *
   * @return the IP whitelist array
   */
  public String[] getIpWhitelist() {
    return ipWhitelist;
  }

  /**
   * Sets the IP whitelist.
   *
   * @param ipWhitelist the IP whitelist array
   */
  public void setIpWhitelist(String[] ipWhitelist) {
    this.ipWhitelist = ipWhitelist;
  }

  /**
   * Gets the active status.
   *
   * @return true if the key is active
   */
  public Boolean getActive() {
    return active;
  }

  /**
   * Sets the active status.
   *
   * @param active the active status
   */
  public void setActive(Boolean active) {
    this.active = active;
  }

  /**
   * Gets the creation timestamp.
   *
   * @return the creation timestamp
   */
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the creation timestamp.
   *
   * @param createdAt the creation timestamp
   */
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Gets the revocation timestamp.
   *
   * @return the revocation timestamp
   */
  public OffsetDateTime getRevokedAt() {
    return revokedAt;
  }

  /**
   * Sets the revocation timestamp.
   *
   * @param revokedAt the revocation timestamp
   */
  public void setRevokedAt(OffsetDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  /**
   * Gets the admin who revoked this key.
   *
   * @return the revoking administrator
   */
  public EzkeyAdmin getRevokedByAdmin() {
    return revokedByAdmin;
  }

  /**
   * Sets the admin who revoked this key.
   *
   * @param revokedByAdmin the revoking administrator
   */
  public void setRevokedByAdmin(EzkeyAdmin revokedByAdmin) {
    this.revokedByAdmin = revokedByAdmin;
  }

  /**
   * Gets the optimistic lock version.
   *
   * @return the version
   */
  public Long getVersion() {
    return version;
  }

  /**
   * Sets the optimistic lock version (used by JPA; do not set manually).
   *
   * @param version the version
   */
  public void setVersion(Long version) {
    this.version = version;
  }

  /**
   * Returns a string representation of the API key.
   *
   * <p>Note: Secret key hash is not included in string representation for security.
   *
   * @return string representation of the API key
   */
  @Override
  public String toString() {
    return "ApiKey{"
        + "apiKeyId="
        + apiKeyId
        + ", integrationKey='"
        + integrationKey
        + '\''
        + ", description='"
        + description
        + '\''
        + ", active="
        + active
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + ", lastUsedAt="
        + lastUsedAt
        + '}';
  }
}
