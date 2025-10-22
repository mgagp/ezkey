/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AuthAttemptDto
 * Description: Response DTO for authorization attempt data in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.ezkey.authattempt.domain.AuthAttemptStatus;

/**
 * Response DTO for authentication attempt data in admin API.
 *
 * <p>This DTO represents the complete authentication attempt data for administrative purposes
 * through the admin API. It provides comprehensive information about authentication attempts while
 * excluding sensitive information like private keys for security purposes.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return authentication attempt information
 * to administrators for monitoring and management purposes. Contains all non-sensitive data needed
 * for authentication attempt administration.
 *
 * <p><b>Core Identification Fields:</b>
 *
 * <ul>
 *   <li><b>authAttemptId:</b> Unique identifier for the authentication attempt
 *   <li><b>enrollmentId:</b> Enrollment identifier this attempt belongs to
 * </ul>
 *
 * <p><b>Status Field:</b>
 *
 * <ul>
 *   <li><b>authAttemptStatus:</b> Current lifecycle status of the authentication attempt (PENDING,
 *       READ, INVALID, REJECTED, ACCEPTED, EXPIRED)
 * </ul>
 *
 * <p><b>Authentication Data:</b>
 *
 * <ul>
 *   <li><b>authAttemptChallenge:</b> Challenge value for the authentication attempt
 *   <li><b>authAttemptProofToken:</b> Proof token for the authentication attempt
 * </ul>
 *
 * <p><b>Metadata:</b>
 *
 * <ul>
 *   <li><b>createdAt:</b> Timestamp when the attempt was created
 *   <li><b>expiresAt:</b> Timestamp when the attempt expires
 * </ul>
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive cryptographic material and provides only the
 * information necessary for administrative operations. All private keys are deliberately excluded
 * for security purposes.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param authAttemptId Unique identifier for the authentication attempt (auto-generated primary key)
 * @param enrollmentId Enrollment identifier this authentication attempt belongs to (foreign key reference)
 * @param authAttemptStatus Current lifecycle status of the authentication attempt (PENDING, READ, INVALID, REJECTED, ACCEPTED, EXPIRED)
 * @param authAttemptChallenge Challenge value for the authentication attempt used in challenge-response process
 * @param authAttemptProofToken Proof token for the authentication attempt used for verification
 * @param createdAt Timestamp when the authentication attempt was created (with timezone)
 * @param expiresAt Timestamp when the authentication attempt expires (with timezone)
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 * @see AuthAttemptCreateRequestDto
 * @see AuthAttemptCreateResponseDto
 */
@Schema(
    description =
        "Response DTO containing complete authentication attempt information for administrative purposes")
public record AuthAttemptDto(
    @Schema(description = "Unique identifier for the authentication attempt", example = "456")
        Integer authAttemptId,
    @Schema(
            description = "Enrollment identifier this authentication attempt belongs to",
            example = "123")
        Integer enrollmentId,
    @Schema(
            description = "Current lifecycle status of the authentication attempt",
            example = "PENDING")
        AuthAttemptStatus authAttemptStatus,
    @Schema(description = "Challenge value for the authentication attempt", example = "789012")
        Integer authAttemptChallenge,
    @Schema(
            description = "Proof token for the authentication attempt",
            example = "EZK-XYZ789-ABC123")
        String authAttemptProofToken,
    @Schema(
            description = "Timestamp when the authentication attempt was created (with timezone)",
            example = "2025-01-27T10:30:00+01:00")
        OffsetDateTime createdAt,
    @Schema(
            description = "Timestamp when the authentication attempt expires (with timezone)",
            example = "2025-01-27T10:35:00+01:00")
        OffsetDateTime expiresAt) {}