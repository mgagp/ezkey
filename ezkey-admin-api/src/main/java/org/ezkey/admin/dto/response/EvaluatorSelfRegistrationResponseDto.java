/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EvaluatorSelfRegistrationResponseDto
 * Description: Response for anonymous evaluator self-registration.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Activation material and optional Admin UI bootstrap session for anonymous EXP1 evaluator
 * onboarding.
 *
 * @param activationCode one-time activation code for Tenant Admin onboarding
 * @param activationCodeExpiresAt activation code expiration (UTC)
 * @param adminUiUrl Admin UI entry URL for this preview instance
 * @param guidedTourUrl guided tour URL on ezkey.org
 * @param tenantLabel server-generated tenant identifier (slug)
 * @param sessionToken opaque BOOTSTRAP session token (distinct from activation code); present when
 *     self-registration is enabled
 * @param sessionExpiresAt absolute bootstrap session expiration (UTC); no sliding
 * @param username generated evaluator Tenant Admin username for the bootstrap session
 */
@Schema(description = "Anonymous evaluator self-registration response")
public record EvaluatorSelfRegistrationResponseDto(
    @Schema(description = "One-time activation code (save immediately)", example = "ABCD-1234")
        String activationCode,
    @Schema(description = "Activation code expiration instant (UTC)")
        OffsetDateTime activationCodeExpiresAt,
    @Schema(description = "Admin UI URL for this preview instance") String adminUiUrl,
    @Schema(description = "Guided tour URL on ezkey.org") String guidedTourUrl,
    @Schema(description = "Server-generated tenant slug", example = "eval-a1b2c3d4")
        String tenantLabel,
    @Schema(
            description =
                "Opaque Admin UI BOOTSTRAP session token (not a password; distinct from"
                    + " activationCode). Omit when feature disabled (404). Prefer HttpOnly cookie"
                    + " when browser session cookie mode is enabled.",
            example = "ezkey_bootstrap_…")
        String sessionToken,
    @Schema(description = "Absolute bootstrap session expiration (UTC); typically 8 hours")
        OffsetDateTime sessionExpiresAt,
    @Schema(description = "Generated evaluator admin username", example = "eval-admin-a1b2c3d4")
        String username) {}
