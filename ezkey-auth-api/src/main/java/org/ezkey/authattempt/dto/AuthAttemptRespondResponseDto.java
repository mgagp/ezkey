/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptRespondResponseDto
 * Description: Response DTO for authentication attempt submissions in auth API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO for authentication attempt submissions in auth API.
 *
 * <p>This DTO represents the response data returned to mobile devices after they submit their
 * authentication attempt response. The integration signs {@code
 * authAttemptProofTokenResultSignedByIntegration} over the canonical payload (see
 * docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md) so the outcome cannot be tampered with in transit.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptRespondResponse
 * @see AuthAttemptRespondRequestDto
 */
@Schema(description = "Response DTO for authentication attempt submissions")
public record AuthAttemptRespondResponseDto(
    /**
     * Identifier of the authentication attempt. Present for correlation and for verifying the
     * integration signature.
     */
    @Schema(
            description = "Authentication attempt identifier",
            example = "123",
            requiredMode = RequiredMode.REQUIRED)
        Integer authAttemptId,

    /** The authentication result indicating the outcome of the attempt. */
    @Schema(
            description = "Authentication result",
            example = "APPROVED",
            allowableValues = {"APPROVED", "DENIED", "FAILED", "EXPIRED"},
            requiredMode = RequiredMode.REQUIRED)
        String authAttemptResult,

    /** Human-readable message (included in the signed payload; NFC-normalized on the server). */
    @Schema(
            description = "Success confirmation or error details for user feedback",
            example = "Auth attempt completed",
            requiredMode = RequiredMode.REQUIRED)
        String authAttemptMessage,

    /**
     * Integration ECDSA signature (Base64) over the canonical Respond result payload. Null only
     * when the server could not sign (e.g. integration key unavailable).
     */
    @Schema(
            description =
                "Integration signature over proofToken|authAttemptId|result|message (see"
                    + " AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md)",
            example =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String authAttemptProofTokenResultSignedByIntegration) {}
