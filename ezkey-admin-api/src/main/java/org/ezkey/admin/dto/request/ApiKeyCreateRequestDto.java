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
 * @param integrationId The integration ID to create the API key for (required)
 * @param description Optional human-readable description for the API key (max 255 chars)
 * @param expiresAt Optional expiration date for automatic key rotation enforcement
 * @param ipWhitelist Optional array of IP addresses or CIDR ranges allowed to use this key
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request to create a new API key for an integration")
public record ApiKeyCreateRequestDto(
    @NotNull(message = "Integration ID is required")
        @Schema(
            description = "Integration ID to create the API key for",
            example = "123",
            required = true)
        Integer integrationId,
    @Size(max = 255, message = "Description must not exceed 255 characters")
        @Schema(
            description =
                "Optional human-readable description to identify this API key (e.g., 'Production"
                    + " Server')",
            example = "Production Server API Key",
            maxLength = 255)
        String description,
    @Schema(
            description =
                "Optional expiration date for automatic key rotation enforcement (null = no"
                    + " expiration)",
            example = "2025-12-31T23:59:59Z")
        OffsetDateTime expiresAt,
    @Schema(
            description =
                "Optional array of IP addresses or CIDR ranges allowed to use this key (recommended"
                    + " for production)",
            example = "[\"192.168.1.0/24\", \"10.0.0.100\"]")
        String[] ipWhitelist) {
  /**
   * Returns a string representation of the request.
   *
   * @return string representation with IP whitelist count
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
