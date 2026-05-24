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
 * Activation material for anonymous EXP1 evaluator onboarding.
 *
 * @param activationCode one-time activation code for Tenant Admin onboarding
 * @param activationCodeExpiresAt activation code expiration (UTC)
 * @param adminUiUrl Admin UI entry URL for this preview instance
 * @param guidedTourUrl guided tour URL on ezkey.org
 * @param tenantLabel server-generated tenant identifier (slug)
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
        String tenantLabel) {}
