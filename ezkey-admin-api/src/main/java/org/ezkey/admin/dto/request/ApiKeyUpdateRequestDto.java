/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ApiKeyUpdateRequestDto
 * Description: Request DTO for partial update of API key configuration.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for partial update of API key configuration.
 *
 * <p>Uses partial-update semantics: only non-null fields are applied. Fields set to {@code null} in
 * the JSON body are ignored and the existing values are preserved.
 *
 * <p><b>Updatable Fields:</b>
 *
 * <ul>
 *   <li>ipWhitelist: IP addresses or CIDR ranges (null/empty = remove restrictions)
 *   <li>description: Human-readable description for documentation
 * </ul>
 *
 * <p><b>Optimistic Locking:</b> Include {@code version} from the GET response to prevent concurrent
 * update conflicts. When version mismatch occurs, the API returns 409 Conflict.
 *
 * <p><b>Constraints:</b> Only active (non-revoked) keys can be updated.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param version optimistic lock version from GET response (optional; when provided, enforces
 *     concurrency check)
 * @param ipWhitelist new IP whitelist (null or empty = no restrictions)
 * @param description new description
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Request DTO for partial update of API key configuration")
public record ApiKeyUpdateRequestDto(
    @Schema(
            description =
                "Optimistic lock version from GET response. When provided, update fails with 409 if"
                    + " resource was modified since last fetch.",
            example = "0",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Long version,
    @Schema(
            description =
                "IP whitelist (null or empty = no restrictions). Each entry: IP address or CIDR"
                    + " (e.g. 192.168.1.0/24)",
            example = "[\"192.168.1.0/24\", \"10.0.0.100\"]",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String[] ipWhitelist,
    @Schema(
            description = "Human-readable description for this API key",
            example = "Production Server API Key",
            requiredMode = RequiredMode.NOT_REQUIRED)
        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description) {}
