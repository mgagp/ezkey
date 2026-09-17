/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Non-secret metadata for the currently authenticated administrator browser session. */
@Schema(description = "Current administrator session metadata")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminSessionResponseDto(
    @Schema(description = "Administrator username", example = "admin") String username,
    @Schema(
            description = "Type of administrator",
            example = "GLOBAL_ADMIN",
            allowableValues = {"GLOBAL_ADMIN", "TENANT_ADMIN", "INTEGRATION_ADMIN"})
        String adminType,
    @Schema(description = "Current session expiration timestamp", example = "2026-04-28T14:00:00Z")
        OffsetDateTime expiresAt,
    @Schema(description = "Administrator ID for the authenticated session", example = "2")
        Integer adminId,
    @Schema(description = "Tenant scope ID when applicable; null for global administrators")
        Integer tenantId,
    @Schema(
            description =
                "Tenant display name when the administrator is tenant- or integration-scoped; null"
                    + " for global administrators",
            example = "Acme Corp")
        String tenantName,
    @Schema(
            description =
                "Non-secret CSRF token to send in X-CSRF-TOKEN for cookie-authenticated unsafe"
                    + " requests")
        String csrfToken) {}
