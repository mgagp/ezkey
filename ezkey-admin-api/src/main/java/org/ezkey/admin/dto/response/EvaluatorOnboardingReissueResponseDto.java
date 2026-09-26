/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EvaluatorOnboardingReissueResponseDto
 * Description: Response for bounded public evaluator onboarding re-issue.
 */
package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Fresh activation or enrollment QR material plus a new Admin UI BOOTSTRAP session.
 *
 * <p>{@code phase} is {@code PENDING_ACTIVATION} when a new activation code is returned, or {@code
 * DEVICE_BIND} when enrollment proof is returned for an already-activated incomplete enrollment.
 *
 * @param phase onboarding phase this re-issue covers
 * @param username evaluator Tenant Admin username
 * @param activationCode one-time activation code when still pending activation; null otherwise
 * @param activationCodeExpiresAt activation expiry when {@code activationCode} is present
 * @param enrollmentId enrollment id when device bind material is returned
 * @param enrollmentProofToken enrollment proof for QR / manual bind when device bind material is
 *     returned
 * @param enrollmentChallenge binding challenge when device bind material is returned
 * @param sessionToken opaque BOOTSTRAP session token (not a password)
 * @param sessionExpiresAt absolute bootstrap session expiration (UTC)
 * @param adminUiUrl Admin UI entry URL for this preview instance
 * @param guidedTourUrl guided tour URL on ezkey.org
 * @since 2026
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Anonymous evaluator onboarding re-issue response")
public record EvaluatorOnboardingReissueResponseDto(
    @Schema(
            description = "Onboarding phase covered by this re-issue",
            allowableValues = {"PENDING_ACTIVATION", "DEVICE_BIND"},
            example = "DEVICE_BIND")
        String phase,
    @Schema(description = "Evaluator admin username", example = "eval-admin-a1b2c3d4")
        String username,
    @Schema(description = "One-time activation code when still pending activation")
        String activationCode,
    @Schema(description = "Activation code expiration instant (UTC)")
        OffsetDateTime activationCodeExpiresAt,
    @Schema(description = "Enrollment ID for device bind") Integer enrollmentId,
    @Schema(description = "Enrollment proof token for QR / manual bind")
        String enrollmentProofToken,
    @Schema(description = "Enrollment binding challenge") Integer enrollmentChallenge,
    @Schema(
            description =
                "Opaque Admin UI BOOTSTRAP session token (not a password; distinct from"
                    + " activationCode). Prefer HttpOnly cookie when browser session cookie mode is"
                    + " enabled.",
            example = "ezkey_bootstrap_…")
        String sessionToken,
    @Schema(description = "Absolute bootstrap session expiration (UTC); typically 8 hours")
        OffsetDateTime sessionExpiresAt,
    @Schema(description = "Admin UI URL for this preview instance") String adminUiUrl,
    @Schema(description = "Guided tour URL on ezkey.org") String guidedTourUrl) {}
