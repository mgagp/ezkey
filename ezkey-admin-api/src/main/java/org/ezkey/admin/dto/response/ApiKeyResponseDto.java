/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
 *   <li><b>revokedAt:</b> Timestamp when revoked (null if active)
 *   <li><b>revokedByUsername:</b> Admin who revoked this key (null if active)
 * </ul>
 *
 * <p><b>Security Note:</b> The secret key is never included in list responses for security. It is
 * only shown once during creation.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param apiKeyId Database ID of the API key
 * @param integrationId Integration ID this API key belongs to
 * @param integrationKey Public integration key (safe to display), format: ezkey_ikey_[20 hex chars]
 * @param description Optional human-readable description
 * @param active Flag indicating whether this API key is active (false if revoked)
 * @param createdAt Timestamp when the API key was created
 * @param expiresAt Optional expiration timestamp (null = no expiration)
 * @param lastUsedAt Timestamp of the last successful authentication (null if never used)
 * @param ipWhitelist Optional IP whitelist array (null = no restrictions)
 * @param revokedAt Timestamp when the API key was revoked (null if active)
 * @param revokedByUsername Username of the admin who revoked this key (null if active)
 * @param operational Whether this API key is fully operational (active and not expired, with an
 *     active integration and active tenant)
 * @author Ezkey contributors
 * @since 2025
 * @see ApiKeyCreateResponseDto
 */
@Schema(description = "Response containing API key details (secret key NOT included)")
public record ApiKeyResponseDto(
    @Schema(description = "Unique identifier for the API key record", example = "42")
        Integer apiKeyId,
    @Schema(
            description =
                "Optimistic lock version. Include in PATCH requests to prevent concurrent update",
            example = "0")
        Long version,
    @Schema(description = "Integration ID this API key authenticates for", example = "123")
        Integer integrationId,
    @Schema(
            description = "Public integration key (safe to display)",
            example = "ezkey_ikey_a1b2c3d4e5f6g7h8i9j0")
        String integrationKey,
    @Schema(
            description = "Optional description to identify this API key",
            example = "Production Server API Key")
        String description,
    @Schema(description = "Whether the key is active (false if revoked)", example = "true")
        Boolean active,
    @Schema(description = "Creation timestamp in UTC", example = "2025-10-17T10:30:00Z")
        OffsetDateTime createdAt,
    @Schema(
            description = "Optional expiration date (null = no expiration)",
            example = "2025-12-31T23:59:59Z")
        OffsetDateTime expiresAt,
    @Schema(
            description = "Last successful authentication timestamp (null if never used)",
            example = "2025-10-17T15:45:30Z")
        OffsetDateTime lastUsedAt,
    @Schema(
            description = "Optional IP whitelist (null = no restrictions)",
            example = "[\"192.168.1.0/24\", \"10.0.0.100\"]")
        String[] ipWhitelist,
    @Schema(description = "Revocation timestamp (null if active)", example = "2025-10-17T16:00:00Z")
        OffsetDateTime revokedAt,
    @Schema(description = "Admin who revoked this key (null if active)", example = "admin")
        String revokedByUsername,
    @Schema(
            description =
                "Whether this API key is fully operational (active, not expired, integration and"
                    + " tenant also active)",
            example = "true")
        Boolean operational) {}
