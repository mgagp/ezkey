/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: ApiKeyCreateResponseDto
 * Description: Response DTO returned when creating a new API key.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @param apiKeyId Database ID of the created API key
 * @param integrationKey Public integration key (safe to display, used as HTTP Basic Auth username)
 * @param secretKey Secret key (SHOWN ONCE ONLY - save immediately! Used as HTTP Basic Auth
 *     password)
 * @param description Human-readable description of the API key
 * @param createdAt Timestamp when the API key was created
 * @param expiresAt Optional expiration timestamp
 * @param ipWhitelist Optional IP whitelist
 * @param warning Security warning message to remind admin to save the secret key
 */
@Schema(description = "Response containing newly created API key pair with secret key shown ONCE")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiKeyCreateResponseDto(
    @Schema(description = "Unique identifier for the API key record", example = "42")
        Integer apiKeyId,
    @Schema(
            description =
                "Public integration key (safe to display, used as HTTP Basic Auth username)",
            example = "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0")
        String integrationKey,
    @Schema(
            description =
                "Secret key (SHOWN ONCE ONLY - save immediately! Used as HTTP Basic Auth password)",
            example = "ezkey_skey_a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0")
        String secretKey,
    @Schema(
            description = "Optional description to identify this API key",
            example = "Production Server API Key")
        String description,
    @Schema(description = "Creation timestamp in UTC", example = "2025-10-17T10:30:00Z")
        OffsetDateTime createdAt,
    @Schema(
            description = "Optional expiration date (null = no expiration)",
            example = "2025-12-31T23:59:59Z")
        OffsetDateTime expiresAt,
    @Schema(
            description = "Optional IP whitelist (null = no restrictions)",
            example = "[\"192.168.1.0/24\", \"10.0.0.100\"]")
        String[] ipWhitelist,
    @Schema(
            description = "Security warning about saving the secret key",
            example = "IMPORTANT: Save the secret key now. It will not be shown again.")
        String warning) {

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
        + ", warning='"
        + warning
        + '\''
        + '}';
  }
}
