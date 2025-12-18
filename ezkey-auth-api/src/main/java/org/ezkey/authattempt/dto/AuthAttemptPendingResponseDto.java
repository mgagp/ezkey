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
 * <p><b>Usage Context:</b> Returned by auth-api when mobile devices poll for pending authentication
 * requests. Contains all information needed by the mobile app to display the authentication request
 * to the user and proceed with the approval/denial flow.
 *
 * <p><b>Security Features:</b> Includes signed authentication codes that ensure the integrity of
 * the authentication request and prevent tampering during transmission to the mobile device.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>authAttemptId:</b> Unique identifier of the authentication attempt
 *   <li><b>integrationProofToken:</b> Integration proof token for this attempt
 *   <li><b>integrationProofTokenSigned:</b> Cryptographically signed integration proof token
 *   <li><b>authAttemptChallengeRequired:</b> Whether additional challenge is required
 * </ul>
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
     * submitting a response. This ID links the pending request to the user's approval or denial
     * decision.
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
     * integration's private key. Provides integrity protection and prevents tampering with the
     * authentication challenge data.
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
     * the user (e.g., numeric code verification). When false, only cryptographic signature
     * validation is needed.
     */
    @Schema(
            description = "Whether additional challenge validation is required",
            example = "true",
            requiredMode = RequiredMode.REQUIRED)
        Boolean authAttemptChallengeRequired) {}
