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
 * admin API. It contains enrollment information and challenge settings required to initiate an MFA
 * authentication request.
 *
 * <p><b>Usage Context:</b> Used by administrators or integrating applications to create
 * authentication requests that will be consumed by mobile devices through auth-api.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentId:</b> Target enrollment for the authentication request
 *   <li><b>challengeRequested:</b> Whether a challenge is required for this attempt
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId The enrollment ID for which the authentication attempt is requested
 * @param challengeRequested Indicates whether a challenge is requested for this authentication
 *     attempt
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateRequest
 * @see AuthAttemptCreateResponseDto
 */
@Schema(description = "Request DTO for creating new authentication attempts")
public record AuthAttemptCreateRequestDto(
    @Schema(
            description = "The enrollment ID for which the authentication attempt is requested",
            example = "123",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Enrollment ID is required")
        @JsonProperty("enrollmentId")
        Integer enrollmentId,
    @Schema(
            description =
                "Indicates whether a challenge is requested for this authentication attempt",
            example = "false",
            requiredMode = RequiredMode.REQUIRED)
        @NotNull(message = "Challenge requested flag is required")
        @JsonProperty("challengeRequested")
        Boolean challengeRequested) {}
