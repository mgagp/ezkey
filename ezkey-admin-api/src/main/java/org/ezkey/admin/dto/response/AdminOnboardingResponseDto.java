/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminOnboardingResponseDto
 * Description: Response DTO for retrieving administrator onboarding credentials.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for retrieving administrator onboarding credentials.
 *
 * <p>This DTO contains all sensitive credentials needed for the administrator to complete
 * passwordless enrollment, including enrollment proof token, challenge code, and recovery codes.
 * These credentials are retrieved via a separate endpoint for security purposes.
 *
 * <p><b>Security Note:</b> These credentials should be retrieved securely and shown only once. They
 * cannot be retrieved again without database access. Access to this endpoint should be restricted
 * to authorized administrators.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId Enrollment ID for passwordless authentication
 * @param enrollmentProofToken Enrollment proof token (shown once - save securely)
 * @param enrollmentChallenge Enrollment challenge code (6 digits, shown once - save securely)
 * @param recoveryCodes List of recovery codes (shown once, single-use - save securely)
 * @param enrollmentCode The exact JSON of the enrollment QR code, for an authenticator that cannot
 *     scan (#1494); null without a proof token or without a configured Auth API address
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(
    description =
        "Response DTO containing administrator onboarding credentials for passwordless enrollment")
public record AdminOnboardingResponseDto(
    @Schema(description = "Enrollment ID for passwordless authentication", example = "123")
        Integer enrollmentId,
    @Schema(
            description =
                "Enrollment proof token. Available only while the enrollment is CREATED; null"
                    + " afterwards.",
            example = "EZK-ABC123-DEF456",
            nullable = true)
        String enrollmentProofToken,
    @Schema(
            description =
                "Enrollment challenge code (6 digits). Available only while the enrollment is"
                    + " CREATED or BOUND; null afterwards.",
            example = "654321",
            nullable = true)
        Integer enrollmentChallenge,
    @Schema(
            description = "List of recovery codes (shown once, single-use - save securely)",
            example = "[\"1234-5678-9012-3456-7890-1234-5678-9012\"]")
        List<String> recoveryCodes,
    @Schema(
            description =
                "Enrollment code: the exact JSON the enrollment QR code holds"
                    + " ({enrollmentProofToken, authUrl}), for an authenticator that cannot scan."
                    + " Present only with the proof token and when ezkey.qr.auth-base-url is"
                    + " configured; never contains the challenge code (#1494)",
            nullable = true)
        String enrollmentCode) {}
