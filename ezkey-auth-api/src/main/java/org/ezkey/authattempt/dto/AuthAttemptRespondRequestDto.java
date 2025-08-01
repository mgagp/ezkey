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

/**
 * Request DTO for submitting authentication attempt responses in auth API.
 * <p>
 * This DTO represents the request data sent by mobile devices to submit their
 * response to an authentication attempt. It contains the user's decision
 * (approve/deny), cryptographic signatures, and challenge responses that
 * complete the MFA authentication flow.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by mobile devices to submit authentication responses
 * to the auth-api. After receiving a pending authentication request, the mobile
 * app collects user approval and submits this comprehensive response with all
 * required cryptographic proofs.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> Contains multiple layers of cryptographic validation
 * including signed codes and challenge responses. This ensures the authenticity
 * of the user's decision and prevents replay attacks or unauthorized responses.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>authAttemptId:</b> Reference to the authentication attempt being answered</li>
 * <li><b>authAttemptEnrolleeCode:</b> Device's enrollee code for validation</li>
 * <li><b>authAttemptEnrolleeCodeSigned:</b> Signed device enrollee code</li>
 * <li><b>authAttemptCode:</b> The authentication code being responded to</li>
 * <li><b>authAttemptCodeSigned:</b> Signed authentication code</li>
 * <li><b>authAttemptChallengeResponse:</b> Response to any additional challenges</li>
 * <li><b>authAttemptAccepted:</b> User's decision to approve or deny</li>
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
 * @see org.ezkey.authattempt.domain.AuthAttemptRespondRequest
 * @see AuthAttemptPendingResponseDto
 * @see AuthAttemptRespondResponseDto
 */
public class AuthAttemptRespondRequestDto {

    private Integer authAttemptId;

    private String authAttemptProofTokenSignedByDevice;

    private Integer authAttemptChallengeResponse;

    private Boolean authAttemptAccepted;

    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    public String getAuthAttemptProofTokenSignedByDevice() {
        return authAttemptProofTokenSignedByDevice;
    }

    public void setAuthAttemptProofTokenSignedByDevice(String authAttemptProofTokenSignedByDevice) {
        this.authAttemptProofTokenSignedByDevice = authAttemptProofTokenSignedByDevice;
    }

    public Integer getAuthAttemptChallengeResponse() {
        return authAttemptChallengeResponse;
    }

    public void setAuthAttemptChallengeResponse(Integer authAttemptChallengeResponse) {
        this.authAttemptChallengeResponse = authAttemptChallengeResponse;
    }

    public Boolean getAuthAttemptAccepted() {
        return authAttemptAccepted;
    }

    public void setAuthAttemptAccepted(Boolean authAttemptAccepted) {
        this.authAttemptAccepted = authAttemptAccepted;
    }

}