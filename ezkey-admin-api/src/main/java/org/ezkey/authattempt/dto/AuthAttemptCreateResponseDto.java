/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AuthAttemptCreateResponseDto
 * Description: Response DTO for authorization attempt creation in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO for authentication attempt creation in admin API.
 *
 * <p>This DTO represents the response data returned when an authentication attempt is successfully
 * created through the admin API. It contains the created attempt's ID and optional challenge code.
 *
 * <p><b>Usage Context:</b> Returned by admin API when creating authentication requests.
 *
 * <p><b>Core Fields:</b>
 *
 * <ul>
 *   <li><b>authAttemptId:</b> Unique identifier of the created authentication attempt
 *   <li><b>authAttemptChallenge:</b> Optional challenge code (2 digits) if challenge was requested
 *   <li><b>timeoutSeconds:</b> Maximum time in seconds the user has to respond to the
 *       authentication request
 *   <li><b>expiresAt:</b> Absolute expiration timestamp when the authentication attempt will expire
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param authAttemptId Unique identifier of the created authentication attempt used to reference
 *     this attempt in subsequent operations
 * @param authAttemptChallenge Optional challenge code (2 digits) that must be entered on the device
 *     if challenge was requested. Null if no challenge was requested.
 * @param timeoutSeconds Maximum time in seconds the user has to respond to the authentication
 *     request (currently 120 seconds)
 * @param expiresAt Absolute expiration timestamp when the authentication attempt will expire
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateResponse
 * @see AuthAttemptCreateRequestDto
 */
@Schema(description = "Response DTO containing created authentication attempt details")
public record AuthAttemptCreateResponseDto(
    @Schema(description = "Unique identifier of the created authentication attempt", example = "11")
        Integer authAttemptId,
    @Schema(
            description =
                "Optional challenge code (2 digits) that must be entered on the device if challenge"
                    + " was requested",
            example = "42")
        Integer authAttemptChallenge,
    @Schema(
            description =
                "Maximum time in seconds the user has to respond to the authentication request",
            example = "120")
        Integer timeoutSeconds,
    @Schema(
            description =
                "Absolute expiration timestamp when the authentication attempt will expire",
            example = "2025-01-20T15:34:00.123456Z")
        OffsetDateTime expiresAt) {}
