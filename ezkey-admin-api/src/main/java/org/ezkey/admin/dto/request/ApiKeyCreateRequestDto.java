/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ApiKeyCreateRequestDto
 * Description: Request DTO for creating a new API key.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Request DTO for creating a new API key.
 *
 * <p>This DTO captures the parameters needed to create a new API key pair for an integration. The
 * system will generate both the integration key (public) and secret key (private) and return them
 * in the response.
 *
 * <p><b>Request Fields:</b>
 *
 * <ul>
 *   <li><b>integrationId:</b> Required - Which integration to create the key for
 *   <li><b>description:</b> Optional - Human-readable description
 *   <li><b>expiresAt:</b> Optional - Expiration date for automatic rotation
 *   <li><b>ipWhitelist:</b> Optional - IP restrictions for enhanced security
 * </ul>
 *
 * <p><b>Security Considerations:</b>
 *
 * <ul>
 *   <li>The secret key will be shown ONLY ONCE in the response
 *   <li>Admin must save the secret key immediately
 *   <li>Lost secret keys cannot be recovered (create new key instead)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyCreateResponseDto
 */
@Schema(description = "Request to create a new API key for an integration")
public class ApiKeyCreateRequestDto {

  /**
   * The integration ID to create the API key for.
   *
   * <p>This field is required and must reference an existing, active integration. The API key will
   * be tied to this integration and can only authenticate for it.
   */
  @NotNull(message = "Integration ID is required")
  @Schema(
      description = "Integration ID to create the API key for",
      example = "123",
      required = true)
  private Integer integrationId;

  /**
   * Human-readable description for the API key.
   *
   * <p>This field helps administrators identify the purpose or location of the API key (e.g.,
   * "Production Server", "Staging Environment", "CI/CD Pipeline"). Maximum 255 characters.
   */
  @Size(max = 255, message = "Description must not exceed 255 characters")
  @Schema(
      description =
          "Optional human-readable description to identify this API key (e.g., 'Production"
              + " Server')",
      example = "Production Server API Key",
      maxLength = 255)
  private String description;

  /**
   * Optional expiration date for the API key.
   *
   * <p>When set, the key will automatically become invalid after this date/time, enforcing periodic
   * key rotation. Null means the key never expires.
   *
   * <p><b>Recommended:</b> Set expiration dates to enforce regular key rotation (e.g., 90 days)
   */
  @Schema(
      description =
          "Optional expiration date for automatic key rotation enforcement (null = no"
              + " expiration)",
      example = "2025-12-31T23:59:59Z")
  private OffsetDateTime expiresAt;

  /**
   * Optional IP whitelist for enhanced security.
   *
   * <p>When set, authentication requests will only be accepted from IP addresses matching this
   * whitelist. Supports both individual IP addresses and CIDR ranges.
   *
   * <p><b>Examples:</b>
   *
   * <ul>
   *   <li>Individual IP: "192.168.1.100"
   *   <li>CIDR range: "10.0.0.0/24"
   *   <li>Multiple entries: ["192.168.1.100", "10.0.0.0/24"]
   * </ul>
   *
   * <p><b>Recommended:</b> Use IP whitelisting for production API keys
   */
  @Schema(
      description =
          "Optional array of IP addresses or CIDR ranges allowed to use this key (recommended for"
              + " production)",
      example = "[\"192.168.1.0/24\", \"10.0.0.100\"]")
  private String[] ipWhitelist;

  /** Default constructor for JPA and Jackson. */
  public ApiKeyCreateRequestDto() {
    // Default constructor
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
   * Gets the expiration date.
   *
   * @return the expiration date
   */
  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  /**
   * Sets the expiration date.
   *
   * @param expiresAt the expiration date
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
   * Returns a string representation of the request.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "ApiKeyCreateRequestDto{"
        + "integrationId="
        + integrationId
        + ", description='"
        + description
        + '\''
        + ", expiresAt="
        + expiresAt
        + ", ipWhitelistCount="
        + (ipWhitelist != null ? ipWhitelist.length : 0)
        + '}';
  }
}
