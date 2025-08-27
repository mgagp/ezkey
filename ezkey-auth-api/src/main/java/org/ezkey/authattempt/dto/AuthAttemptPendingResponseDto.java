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

/**
 * Response DTO for pending authentication attempts in auth API.
 * <p>
 * This DTO represents the response data returned to mobile devices when they
 * check for pending authentication attempts. It contains the authentication
 * challenge details that the user needs to approve or deny, including
 * cryptographic codes and challenge requirements.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by auth-api when mobile devices poll for
 * pending authentication requests. Contains all information needed by the
 * mobile app to display the authentication request to the user and proceed
 * with the approval/denial flow.
 * </p>
 *
 * <p>
 * <b>Security Features:</b> Includes signed authentication codes that ensure
 * the integrity of the authentication request and prevent tampering during
 * transmission to the mobile device.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>authAttemptId:</b> Unique identifier of the authentication attempt</li>
 * <li><b>integrationProofToken:</b> Integration proof token for this attempt</li>
 * <li><b>integrationProofTokenSigned:</b> Cryptographically signed integration proof token</li>
 * <li><b>authAttemptChallengeRequired:</b> Whether additional challenge is required</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptPendingResponse
 * @see AuthAttemptPendingRequestDto
 * @see AuthAttemptRespondRequestDto
 */
@Schema(description = "Response DTO containing pending authentication attempt details")
public class AuthAttemptPendingResponseDto {

    /**
     * Unique identifier of the authentication attempt.
     * <p>
     * Used by the mobile device to reference this specific authentication
     * attempt when submitting a response. This ID links the pending request
     * to the user's approval or denial decision.
     * </p>
     */
    @Schema(description = "Unique identifier of the authentication attempt",example = "123",required = true)
    private Integer authAttemptId;

    /**
     * Authentication proof token containing challenge data.
     * <p>
     * Contains the challenge data and integration information that needs
     * to be cryptographically signed by the mobile device to prove
     * possession of the private key and complete authentication.
     * </p>
     */
    @Schema(description = "Authentication proof token containing challenge data",example = "eyJhbGciOiJSUzI1NiJ9...",required = true)
    private String authAttemptProofToken;

    /**
     * Integration-signed authentication proof token.
     * <p>
     * Contains the cryptographically signed version of the proof token,
     * signed by the integration's private key. Provides integrity protection
     * and prevents tampering with the authentication challenge data.
     * </p>
     */
    @Schema(description = "Integration-signed authentication proof token for integrity",example = "eyJhbGciOiJSUzI1NiJ9...",required = true)
    private String authAttemptProofTokenSignedByIntegration;

    /**
     * Indicates whether additional challenge validation is required.
     * <p>
     * When true, the mobile device must collect and provide additional
     * challenge responses from the user (e.g., numeric code verification).
     * When false, only cryptographic signature validation is needed.
     * </p>
     */
    @Schema(description = "Whether additional challenge validation is required",example = "true",required = true)
    private Boolean authAttemptChallengeRequired;

    /**
     * Gets the authentication attempt ID.
     *
     * @return the authentication attempt ID
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the authentication attempt ID to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the authentication proof token.
     *
     * @return the authentication proof token
     */
    public String getAuthAttemptProofToken() {
        return authAttemptProofToken;
    }

    /**
     * Sets the authentication proof token.
     *
     * @param authAttemptProofToken the authentication proof token to set
     */
    public void setAuthAttemptProofToken(String authAttemptProofToken) {
        this.authAttemptProofToken = authAttemptProofToken;
    }

    /**
     * Gets the integration-signed authentication proof token.
     *
     * @return the integration-signed authentication proof token
     */
    public String getAuthAttemptProofTokenSignedByIntegration() {
        return authAttemptProofTokenSignedByIntegration;
    }

    /**
     * Sets the integration-signed authentication proof token.
     *
     * @param authAttemptProofTokenSignedByIntegration the integration-signed authentication proof token to set
     */
    public void setAuthAttemptProofTokenSignedByIntegration(String authAttemptProofTokenSignedByIntegration) {
        this.authAttemptProofTokenSignedByIntegration = authAttemptProofTokenSignedByIntegration;
    }

    /**
     * Gets the challenge validation requirement.
     *
     * @return the challenge validation requirement
     */
    public Boolean getAuthAttemptChallengeRequired() {
        return authAttemptChallengeRequired;
    }

    /**
     * Sets the challenge validation requirement.
     *
     * @param authAttemptChallengeRequired the challenge validation requirement to set
     */
    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

}