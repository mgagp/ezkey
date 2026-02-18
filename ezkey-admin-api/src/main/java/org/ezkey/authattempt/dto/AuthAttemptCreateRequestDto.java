/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AuthAttemptCreateRequestDto
 * Description: Request DTO for creating authorization attempts in admin API.
 */

package org.ezkey.authattempt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating authorization attempts in admin API.
 *
 * <p>This DTO represents the request data needed to create a new authorization attempt through the
 * admin API. It supports two identification modes:
 *
 * <ul>
 *   <li><b>By enrollmentId:</b> Direct reference to an enrollment (legacy, SDK, admin)
 *   <li><b>By userIdentifier:</b> User reference (e.g. username); resolves to enrollment within
 *       integration scope. When using API key, integrationId is derived from the key. When using
 *       admin token, integrationId must be provided in the request.
 * </ul>
 *
 * <p>When both enrollmentId and userIdentifier are provided, the API validates that they resolve to
 * the same enrollment (consistency check). If they conflict, returns 400.
 *
 * <p><b>Multi-device:</b> When userIdentifier matches multiple enrollments (user has multiple
 * devices), the API returns 400 with a message to specify enrollmentId or deviceHint.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId The enrollment ID (optional if userIdentifier provided)
 * @param userIdentifier The user identifier for lookup within integration scope (optional if
 *     enrollmentId provided)
 * @param integrationId Required when userIdentifier is used with admin token; ignored when API key
 *     (derived from key)
 * @param challengeRequested Whether a challenge is requested for this attempt
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateRequest
 * @see AuthAttemptCreateResponseDto
 */
@Schema(
    description =
        "Request to create an auth attempt. Provide enrollmentId OR userIdentifier. "
            + "When userIdentifier is used with admin token, integrationId is required.")
public record AuthAttemptCreateRequestDto(
    @Schema(
            description =
                "Enrollment ID (use this OR userIdentifier). Direct reference to enrollment.",
            example = "123")
        @JsonProperty("enrollmentId")
        Integer enrollmentId,
    @Schema(
            description =
                "User identifier (username, user_id) for lookup within integration scope. Use this"
                    + " OR enrollmentId. When used with admin token, integrationId is required.",
            example = "alice")
        @JsonProperty("userIdentifier")
        String userIdentifier,
    @Schema(
            description =
                "Integration ID. Required when userIdentifier is used with admin token. "
                    + "Ignored when API key (derived from credentials).",
            example = "1")
        @JsonProperty("integrationId")
        Integer integrationId,
    @Schema(
            description = "Whether a challenge code is requested for this attempt",
            example = "false",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Challenge requested flag is required")
        @JsonProperty("challengeRequested")
        Boolean challengeRequested) {}
