/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateResponseDto
 * Description: Response DTO returned after successfully creating an authentication attempt.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import java.time.OffsetDateTime;

/**
 * Response DTO returned after successfully creating an authentication attempt.
 *
 * <p>Contains the information needed by the caller to present the authentication request to the
 * user and subsequently poll for the result. The {@code authAttemptId} is the key field used to
 * track and interact with this attempt via the wait and cancel endpoints.
 *
 * <p>Shared across admin-api and integration-api.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param authAttemptId unique identifier of the newly created authentication attempt
 * @param authAttemptChallenge numeric challenge code to display to the user (null if not requested)
 * @param timeoutSeconds duration in seconds before this attempt expires
 * @param expiresAt absolute expiration timestamp for this attempt
 * @param contextTitle optional short title echoed back from the request
 * @param contextMessage optional descriptive message echoed back from the request
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Response returned after creating a new authentication attempt")
public record AuthAttemptCreateResponseDto(
    /** Unique identifier of the newly created authentication attempt. */
    @Schema(
            description = "Unique ID of the created auth attempt (use for wait/cancel calls)",
            example = "42",
            requiredMode = RequiredMode.REQUIRED)
        Integer authAttemptId,
    /**
     * Numeric challenge code to display to the user for verification. Null when challenge was not
     * requested.
     */
    @Schema(
            description = "Numeric challenge code to display to the user (null if not requested)",
            example = "123456",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Integer authAttemptChallenge,
    /** Duration in seconds until this authentication attempt expires. */
    @Schema(
            description = "Seconds until this attempt expires",
            example = "300",
            requiredMode = RequiredMode.REQUIRED)
        Integer timeoutSeconds,
    /** Absolute timestamp when this authentication attempt expires. */
    @Schema(
            description = "Absolute expiration timestamp with timezone",
            example = "2025-01-27T10:05:00+01:00",
            requiredMode = RequiredMode.REQUIRED)
        OffsetDateTime expiresAt,
    /**
     * Optional short title echoed back for confirmation. Null if no context was provided in the
     * creation request.
     */
    @Schema(
            description = "Context title echoed back (null if not provided)",
            example = "Payment Approval",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String contextTitle,
    /**
     * Optional descriptive message echoed back for confirmation. Null if no context was provided.
     */
    @Schema(
            description = "Context message echoed back (null if not provided)",
            example = "Authorize payment batch #1497 to Acme Corp for $1,400",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String contextMessage) {}
