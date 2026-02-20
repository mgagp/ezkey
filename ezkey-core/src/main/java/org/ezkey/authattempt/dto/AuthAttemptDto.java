/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptDto
 * Description: Authentication attempt response DTO shared across all API modules.
 */

package org.ezkey.authattempt.dto;

import java.time.OffsetDateTime;

import org.ezkey.authattempt.domain.AuthAttemptStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Data Transfer Object representing the state of an authentication attempt.
 *
 * <p>
 * This record is shared across all API modules (admin-api, m2m-api) to expose
 * authentication
 * attempt data at API boundaries. It provides a clean, immutable view of the
 * attempt without
 * exposing internal domain or persistence details.
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p>
 * <b>License:</b> MIT
 *
 * @param authAttemptId         unique identifier of the authentication attempt
 * @param enrollmentId          identifier of the enrollment associated with
 *                              this attempt
 * @param authAttemptStatus     current status of the authentication attempt
 * @param authAttemptChallenge  numeric challenge code sent to the mobile device
 *                              (null if not
 *                              requested)
 * @param authAttemptProofToken signed JWT proof token (present only when
 *                              ACCEPTED)
 * @param createdAt             timestamp when the authentication attempt was
 *                              created
 * @param expiresAt             timestamp when the authentication attempt
 *                              expires
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Authentication attempt details")
public record AuthAttemptDto(
    /** Unique identifier of the authentication attempt. */
    @Schema(description = "Unique auth attempt ID", example = "42", requiredMode = RequiredMode.REQUIRED) Integer authAttemptId,
    /** Identifier of the enrollment associated with this attempt. */
    @Schema(description = "Associated enrollment ID", example = "7", requiredMode = RequiredMode.REQUIRED) Integer enrollmentId,
    /** Current status of the authentication attempt. */
    @Schema(description = "Current status of the authentication attempt", example = "PENDING", requiredMode = RequiredMode.REQUIRED) AuthAttemptStatus authAttemptStatus,
    /**
     * Numeric challenge code sent to the mobile device (null if challenge not
     * requested).
     */
    @Schema(description = "Numeric challenge displayed to user for verification (null if not requested)", example = "123456", requiredMode = RequiredMode.NOT_REQUIRED) Integer authAttemptChallenge,
    /**
     * Signed JWT proof token present only when authentication is accepted. Contains
     * verifiable
     * evidence of the authentication event.
     */
    @Schema(description = "Signed JWT proof token (present only when status is ACCEPTED)", requiredMode = RequiredMode.NOT_REQUIRED) String authAttemptProofToken,
    /** Timestamp when the authentication attempt was created. */
    @Schema(description = "Creation timestamp with timezone", example = "2025-01-27T10:00:00+01:00", requiredMode = RequiredMode.REQUIRED) OffsetDateTime createdAt,
    /** Timestamp when the authentication attempt expires. */
    @Schema(description = "Expiration timestamp with timezone", example = "2025-01-27T10:05:00+01:00", requiredMode = RequiredMode.REQUIRED) OffsetDateTime expiresAt) {
}
