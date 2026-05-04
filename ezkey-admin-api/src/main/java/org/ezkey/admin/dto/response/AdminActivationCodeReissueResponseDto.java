/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminActivationCodeReissueResponseDto
 * Description: Response DTO for Global Admin activation-code re-issue for pending administrators.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO when a Global Admin re-issues the one-time deferred onboarding activation code for
 * an administrator still in pending activation state.
 *
 * <p>A new plain-text activation code is returned exactly once and any remaining active issuance
 * tokens for that administrator row are deactivated first so only the latest unused code remains
 * valid until consumed or expiry.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(
    description =
        "Response DTO for re-issuing an activation code for a pending administrator. The"
            + " activation code is shown once and cannot be retrieved later.")
public record AdminActivationCodeReissueResponseDto(
    @Schema(description = "Unique identifier for the administrator", example = "42")
        Integer adminId,
    @Schema(description = "Username for the administrator", example = "new.tenant.admin")
        String username,
    @Schema(
            description =
                "Fresh one-time activation code in Ezkey activation format. Deliver securely; it"
                    + " cannot be shown again.")
        String activationCode,
    @Schema(
            description = "Expiry instant (UTC) for the new activation code",
            example = "2026-01-09T14:35:00Z")
        OffsetDateTime activationCodeExpiresAt,
    @Schema(
            description =
                "True when any prior active issuance tokens were deactivated during this operation",
            example = "true")
        boolean invalidatedPreviousTokens,
    @Schema(description = "Count of issuance tokens deactivated in this operation", example = "1")
        int deactivatedActiveTokenCount,
    @Schema(
            description = "Summary message for the operator",
            example =
                "New activation code generated. Previous unused activation codes no longer work.")
        String message) {}
