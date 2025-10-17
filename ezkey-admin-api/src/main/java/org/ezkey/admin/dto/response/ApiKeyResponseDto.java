/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ApiKeyResponseDto
 * Description: Response DTO for listing existing API keys (without secret).
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO for listing existing API keys.
 *
 * <p>This DTO contains API key information for listing and detail views. The secret key is NEVER
 * included in this response - it is only shown once during creation.
 *
 * <p><b>Response Fields:</b>
 *
 * <ul>
 *   <li><b>apiKeyId:</b> Database ID for the key record
 *   <li><b>integrationId:</b> Integration this key belongs to
 *   <li><b>integrationKey:</b> Public integration key (always visible)
 *   <li><b>description:</b> Human-readable description
 *   <li><b>active:</b> Whether the key is active or revoked
 *   <li><b>createdAt:</b> Creation timestamp
 *   <li><b>expiresAt:</b> Expiration date (null if never expires)
 *   <li><b>lastUsedAt:</b> Last successful authentication timestamp
 *   <li><b>ipWhitelist:</b> IP restrictions (null if no restrictions)
 * </ul>
 *
 * <p><b>Security Note:</b> The secret key is never included in list responses for security. It is
 * only shown once during creation.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyCreateResponseDto
 */
@Schema(description = "Response containing API key details (secret key NOT included)")
public class ApiKeyResponseDto {

  /** Database ID of the API key. */
  @Schema(description = "Unique identifier for the API key record", example = "42")
  private Integer apiKeyId;

  /** Integration ID this API key belongs to. */
  @Schema(description = "Integration ID this API key authenticates for", example = "123")
  private Integer integrationId;

  /**
   * Public integration key.
   *
   * <p>Format: ezkey_ikey_[20 hex chars]
   *
   * <p>This key is safe to display and log. It is used as the username in HTTP Basic Auth.
   */
  @Schema(
      description = "Public integration key (safe to display)",
      example = "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0")
  private String integrationKey;

  /** Human-readable description of the API key. */
  @Schema(
      description = "Optional description to identify this API key",
      example = "Production Server API Key")
  private String description;

  /**
   * Flag indicating whether this API key is active.
   *
   * <p>Inactive keys cannot be used for authentication but are preserved for audit purposes.
   */
  @Schema(description = "Whether the key is active (false if revoked)", example = "true")
  private Boolean active;

  /** Timestamp when the API key was created. */
  @Schema(description = "Creation timestamp in UTC", example = "2025-10-17T10:30:00Z")
  private OffsetDateTime createdAt;

  /** Optional expiration timestamp. */
  @Schema(
      description = "Optional expiration date (null = no expiration)",
      example = "2025-12-31T23:59:59Z")
  private OffsetDateTime expiresAt;

  /**
   * Timestamp of the last successful authentication using this key.
   *
   * <p>This field is null if the key has never been used. Updated each time the key successfully
   * authenticates.
   */
  @Schema(
      description = "Last successful authentication timestamp (null if never used)",
      example = "2025-10-17T15:45:30Z")
  private OffsetDateTime lastUsedAt;

  /** Optional IP whitelist. */
  @Schema(
      description = "Optional IP whitelist (null = no restrictions)",
      example = "[\"192.168.1.0/24\", \"10.0.0.100\"]")
  private String[] ipWhitelist;

  /** Timestamp when the API key was revoked (if applicable). */
  @Schema(description = "Revocation timestamp (null if active)", example = "2025-10-17T16:00:00Z")
  private OffsetDateTime revokedAt;

  /** Username of the admin who revoked this key (if applicable). */
  @Schema(description = "Admin who revoked this key (null if active)", example = "admin")
  private String revokedByUsername;

  /** Default constructor for Jackson. */
  public ApiKeyResponseDto() {
    // Default constructor
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
   * Gets the integration ID.
   *
   * @return the integration ID
   */
  public Integer getIntegrationId() {
    return integrationId;
  }

  /**
   * Sets the integration ID.
   *
   * @param integrationId the integration ID
   */
  public void setIntegrationId(Integer integrationId) {
    this.integrationId = integrationId;
  }

  /**
   * Gets the integration key.
   *
   * @return the integration key
   */
  public String getIntegrationKey() {
    return integrationKey;
  }

  /**
   * Sets the integration key.
   *
   * @param integrationKey the integration key
   */
  public void setIntegrationKey(String integrationKey) {
    this.integrationKey = integrationKey;
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
   * Gets the active status.
   *
   * @return the active status
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
   * Gets the IP whitelist.
   *
   * @return the IP whitelist
   */
  public String[] getIpWhitelist() {
    return ipWhitelist;
  }

  /**
   * Sets the IP whitelist.
   *
   * @param ipWhitelist the IP whitelist
   */
  public void setIpWhitelist(String[] ipWhitelist) {
    this.ipWhitelist = ipWhitelist;
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
   * Gets the revoking admin username.
   *
   * @return the revoking admin username
   */
  public String getRevokedByUsername() {
    return revokedByUsername;
  }

  /**
   * Sets the revoking admin username.
   *
   * @param revokedByUsername the revoking admin username
   */
  public void setRevokedByUsername(String revokedByUsername) {
    this.revokedByUsername = revokedByUsername;
  }

  /**
   * Returns a string representation of the response.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "ApiKeyResponseDto{"
        + "apiKeyId="
        + apiKeyId
        + ", integrationId="
        + integrationId
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
