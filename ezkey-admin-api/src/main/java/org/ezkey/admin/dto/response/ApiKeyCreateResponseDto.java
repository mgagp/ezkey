/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ApiKeyCreateResponseDto
 * Description: Response DTO returned when creating a new API key.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO returned when creating a new API key.
 *
 * <p>This DTO contains the complete API key pair including the plain text secret key. The secret
 * key is shown ONLY ONCE in this response and will never be displayed again.
 *
 * <p><b>CRITICAL SECURITY WARNING:</b>
 *
 * <ul>
 *   <li>The secret key is shown ONLY ONCE in this response
 *   <li>It cannot be retrieved later - it is hashed with BCrypt for storage
 *   <li>Administrator MUST save the secret key immediately
 *   <li>Lost secret keys cannot be recovered - create a new key instead
 * </ul>
 *
 * <p><b>Response Fields:</b>
 *
 * <ul>
 *   <li><b>apiKeyId:</b> Database ID for the key record
 *   <li><b>integrationKey:</b> Public integration key (always visible)
 *   <li><b>secretKey:</b> Private secret key (SHOWN ONCE - save immediately!)
 *   <li><b>description:</b> Human-readable description
 *   <li><b>createdAt:</b> Creation timestamp
 *   <li><b>expiresAt:</b> Expiration date (null if never expires)
 *   <li><b>ipWhitelist:</b> IP restrictions (null if no restrictions)
 *   <li><b>warning:</b> Security warning message
 * </ul>
 *
 * <p><b>Usage Example:</b>
 *
 * <pre>
 * Authorization: Basic base64(ezkey_ikey_xxx:ezkey_skey_xxx)
 * </pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyCreateRequestDto
 */
@Schema(description = "Response containing newly created API key pair with secret key shown ONCE")
public class ApiKeyCreateResponseDto {

  /** Database ID of the created API key. */
  @Schema(description = "Unique identifier for the API key record", example = "42")
  private Integer apiKeyId;

  /**
   * Public integration key.
   *
   * <p>Format: ezkey_ikey_[20 hex chars]
   *
   * <p>This key is safe to display and log. It is used as the username in HTTP Basic Auth.
   */
  @Schema(
      description = "Public integration key (safe to display, used as HTTP Basic Auth username)",
      example = "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0")
  private String integrationKey;

  /**
   * Secret key (plain text - SHOWN ONCE ONLY).
   *
   * <p>Format: ezkey_skey_[40 hex chars]
   *
   * <p><b>CRITICAL:</b> This field contains the plain text secret key. It will NEVER be shown
   * again. The administrator MUST save this immediately in a secure location.
   *
   * <p>Used as the password in HTTP Basic Auth.
   */
  @Schema(
      description =
          "Secret key (SHOWN ONCE ONLY - save immediately! Used as HTTP Basic Auth password)",
      example = "ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0")
  private String secretKey;

  /** Human-readable description of the API key. */
  @Schema(
      description = "Optional description to identify this API key",
      example = "Production Server API Key")
  private String description;

  /** Timestamp when the API key was created. */
  @Schema(description = "Creation timestamp in UTC", example = "2025-10-17T10:30:00Z")
  private OffsetDateTime createdAt;

  /** Optional expiration timestamp. */
  @Schema(
      description = "Optional expiration date (null = no expiration)",
      example = "2025-12-31T23:59:59Z")
  private OffsetDateTime expiresAt;

  /** Optional IP whitelist. */
  @Schema(
      description = "Optional IP whitelist (null = no restrictions)",
      example = "[\"192.168.1.0/24\", \"10.0.0.100\"]")
  private String[] ipWhitelist;

  /**
   * Security warning message.
   *
   * <p>This field contains a critical warning reminding the administrator to save the secret key
   * immediately as it will never be shown again.
   */
  @Schema(
      description = "Security warning about saving the secret key",
      example = "IMPORTANT: Save the secret key now. It will not be shown again.")
  private String warning = "IMPORTANT: Save the secret key now. It will not be shown again.";

  /** Default constructor for Jackson. */
  public ApiKeyCreateResponseDto() {
    // Default constructor
  }

  /**
   * Full constructor for creating response.
   *
   * @param apiKeyId the API key ID
   * @param integrationKey the integration key
   * @param secretKey the secret key (plain text)
   * @param description the description
   * @param createdAt the creation timestamp
   * @param expiresAt the expiration timestamp
   * @param ipWhitelist the IP whitelist
   */
  public ApiKeyCreateResponseDto(
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
   * Gets the secret key.
   *
   * @return the secret key (plain text)
   */
  public String getSecretKey() {
    return secretKey;
  }

  /**
   * Sets the secret key.
   *
   * @param secretKey the secret key (plain text)
   */
  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
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
   * Gets the warning message.
   *
   * @return the warning message
   */
  public String getWarning() {
    return warning;
  }

  /**
   * Sets the warning message.
   *
   * @param warning the warning message
   */
  public void setWarning(String warning) {
    this.warning = warning;
  }

  /**
   * Returns a string representation of the response.
   *
   * <p>Note: Secret key is masked for security.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "ApiKeyCreateResponseDto{"
        + "apiKeyId="
        + apiKeyId
        + ", integrationKey='"
        + integrationKey
        + '\''
        + ", secretKey='***MASKED***'"
        + ", description='"
        + description
        + '\''
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + '}';
  }
}
