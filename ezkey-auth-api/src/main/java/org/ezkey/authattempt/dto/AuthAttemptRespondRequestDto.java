/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptRespondRequestDto
 * Description: Request DTO for submitting authentication attempt responses in auth API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for submitting authentication attempt responses in auth API.
 *
 * <p>This DTO represents the request data sent by mobile devices to submit their response to an
 * authentication attempt. It contains the user's decision (approve/deny), cryptographic signatures,
 * and challenge responses that complete the MFA authentication flow.
 *
 * <p><b>Usage Context:</b> Used by mobile devices to submit authentication responses to the
 * auth-api. After receiving a pending authentication request, the mobile app collects user approval
 * and submits this comprehensive response with all required cryptographic proofs.
 *
 * <p><b>Security Model:</b> Contains multiple layers of cryptographic validation including signed
 * codes and challenge responses. This ensures the authenticity of the user's decision and prevents
 * replay attacks or unauthorized responses.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>authAttemptId:</b> Reference to the authentication attempt being answered
 *   <li><b>authAttemptEnrolleeCode:</b> Device's enrollee code for validation
 *   <li><b>authAttemptEnrolleeCodeSigned:</b> Signed device enrollee code
 *   <li><b>authAttemptCode:</b> The authentication code being responded to
 *   <li><b>authAttemptCodeSigned:</b> Signed authentication code
 *   <li><b>authAttemptChallengeResponse:</b> Response to any additional challenges
 *   <li><b>authAttemptAccepted:</b> User's decision to approve or deny
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptRespondRequest
 * @see AuthAttemptPendingResponseDto
 * @see AuthAttemptRespondResponseDto
 */
@Schema(description = "Request DTO for submitting authentication attempt responses")
public record AuthAttemptRespondRequestDto(
    /**
     * The authentication attempt ID being responded to. Must reference a valid pending authentication
     * attempt.
     */
    @Schema(
        description = "Authentication attempt ID being responded to",
        example = "123",
        required = true)
    Integer authAttemptId,

    /**
     * Device-signed proof token for cryptographic validation.
     *
     * <p>Contains the cryptographically signed proof token that validates the device's identity and
     * proves possession of the private key associated with this enrollment.
     */
    @Schema(
        description = "Device-signed proof token for authentication validation",
        example = "eyJhbGciOiJSUzI1NiJ9...",
        required = true)
    String authAttemptProofTokenSignedByDevice,

    /**
     * Response to the authentication challenge.
     *
     * <p>Numeric response provided by the user when additional challenge verification is required.
     * Only needed when the authentication attempt has challengeRequired flag set to true.
     */
    @Schema(
        description = "User's response to authentication challenge (if required)",
        example = "123456",
        nullable = true)
    Integer authAttemptChallengeResponse,

    /**
     * User's decision to accept or deny the authentication attempt.
     *
     * <p>Boolean flag indicating whether the user approved (true) or denied (false) the
     * authentication request through the mobile app.
     */
    @Schema(
        description = "User's decision: true to approve, false to deny",
        example = "true",
        required = true)
    Boolean authAttemptAccepted
) {
}