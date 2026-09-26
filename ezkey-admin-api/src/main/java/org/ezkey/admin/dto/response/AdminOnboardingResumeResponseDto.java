/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminOnboardingResumeResponseDto
 * Description: Response after redeeming onboarding-resume — reminted BOOTSTRAP only.
 */
package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Reminted BOOTSTRAP session after onboarding-resume redeem.
 *
 * <p>Does not include enrollment QR / proof — use authenticated {@code GET
 * /api/v1/admins/{id}/onboarding} under the BOOTSTRAP session.
 *
 * @param sessionToken opaque BOOTSTRAP bearer (Mode A); omit when cookie mode strips it
 * @param sessionExpiresAt absolute BOOTSTRAP expiry (UTC); no sliding
 * @param username administrator username
 * @param adminId administrator id for onboarding fetch
 * @since 2026
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Onboarding-resume redeem response (BOOTSTRAP remint only)")
public record AdminOnboardingResumeResponseDto(
    @Schema(
            description =
                "Opaque Admin UI BOOTSTRAP session token. Prefer HttpOnly cookie when browser"
                    + " session cookie mode is enabled.",
            example = "ezkey_bootstrap_…")
        String sessionToken,
    @Schema(description = "Absolute bootstrap session expiration (UTC); typically 2 hours")
        OffsetDateTime sessionExpiresAt,
    @Schema(description = "Administrator username", example = "eval-admin-a1b2c3d4")
        String username,
    @Schema(description = "Administrator id for GET /admins/{id}/onboarding", example = "42")
        Integer adminId) {}
