/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptPendingResponseDto
 * Description: Response DTO for pending authentication attempts in auth API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO for pending authentication attempts in auth API.
 *
 * <p>This DTO represents the response data returned to mobile devices when they check for pending
 * authentication attempts. It contains the authentication challenge details that the user needs to
 * approve or deny, including cryptographic codes and challenge requirements.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptPendingResponse
 * @see AuthAttemptPendingRequestDto
 * @see AuthAttemptRespondRequestDto
 */
@Schema(description = "Response DTO containing pending authentication attempt details")
public record AuthAttemptPendingResponseDto(
    /**
     * Unique identifier of the authentication attempt.
     *
     * <p>Used by the mobile device to reference this specific authentication attempt when
     * submitting a response.
     */
    @Schema(
            description = "Unique identifier of the authentication attempt",
            example = "123",
            requiredMode = RequiredMode.REQUIRED)
        Integer authAttemptId,

    /**
     * Authentication proof token containing challenge data.
     *
     * <p>Contains the challenge data and integration information that needs to be cryptographically
     * signed by the mobile device to prove possession of the private key and complete
     * authentication.
     */
    @Schema(
            description = "Authentication proof token containing challenge data",
            example = "eyJhbGciOiJSUzI1NiJ9...",
            requiredMode = RequiredMode.REQUIRED)
        String authAttemptProofToken,

    /**
     * Integration-signed authentication proof token.
     *
     * <p>Contains the cryptographically signed version of the proof token, signed by the
     * integration's private key. Provides integrity protection and prevents tampering.
     */
    @Schema(
            description = "Integration-signed authentication proof token for integrity",
            example = "eyJhbGciOiJSUzI1NiJ9...",
            requiredMode = RequiredMode.REQUIRED)
        String authAttemptProofTokenSignedByIntegration,

    /**
     * Indicates whether additional challenge validation is required.
     *
     * <p>When true, the mobile device must collect and provide additional challenge responses from
     * the user (e.g., numeric code verification).
     */
    @Schema(
            description = "Whether additional challenge validation is required",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        Boolean authAttemptChallengeRequired,

    /**
     * Optional short title for the approval request, displayed as the card header on the mobile
     * device. Null when no context was attached to this authentication attempt.
     */
    @Schema(
            description =
                "Optional short title for the approval request (null if no context provided)."
                    + " Example: \"Payment Approval\"",
            example = "Payment Approval",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String contextTitle,

    /**
     * Optional descriptive message providing the approver with full business context. Null when no
     * context was attached to this authentication attempt.
     */
    @Schema(
            description =
                "Optional descriptive message for the approver (null if no context provided)."
                    + " Example: \"Authorize payment batch #1497 to Acme Corp for $1,400\"",
            example = "Authorize payment batch #1497 to Acme Corp for $1,400",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String contextMessage) {}
