/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptRespondRequest
 * Description: Domain request object for submitting authentication attempt responses.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain request object for submitting authentication attempt responses.
 * <p>
 * This domain object represents the request data sent by mobile devices when
 * responding to authentication challenges. It contains the cryptographically
 * signed proof tokens and user decisions that complete the authentication flow,
 * providing both security validation and user consent information.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the AuthAttemptService when mobile devices
 * submit responses to pending authentication attempts. The service layer
 * transforms API DTOs into this domain object for business logic processing
 * and cryptographic validation.
 * </p>
 *
 * <p>
 * <b>Authentication Flow:</b> This request represents the final step in the
 * authentication challenge-response flow. After receiving a pending authentication
 * attempt, the mobile device processes the challenge, obtains user consent,
 * and submits this response with cryptographic proof of device authenticity.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> Contains device-signed proof tokens that validate
 * both device authenticity and challenge integrity. The cryptographic signature
 * ensures that only the legitimate enrolled device can respond to authentication
 * challenges and prevents replay attacks or unauthorized responses.
 * </p>
 *
 * <p>
 * <b>User Consent:</b> Captures explicit user decisions (accept/deny) along with
 * any additional challenge responses required by the authentication policy.
 * This ensures proper user authorization and supports multi-factor authentication
 * scenarios where additional verification steps are required.
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
 * @see org.ezkey.authattempt.service.AuthAttemptService
 * @see org.ezkey.authattempt.domain.AuthAttemptRespondResponse
 * @see org.ezkey.authattempt.domain.AuthAttemptPendingResponse
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptRespondRequest {

    /**
     * Unique identifier of the authentication attempt being responded to.
     * <p>
     * Must reference an existing pending authentication attempt. This ID
     * links the response back to the original authentication challenge and
     * ensures proper tracking throughout the authentication flow. Used by
     * the service layer to locate and update the corresponding authentication
     * attempt entity.
     * </p>
     */
    private Integer authAttemptId;

    /**
     * Device-signed proof token for cryptographic validation.
     * <p>
     * Contains the authentication challenge proof token that has been
     * cryptographically signed by the mobile device using its private key.
     * This signature provides proof of device authenticity and ensures that
     * only the legitimate enrolled device can respond to authentication
     * challenges, preventing unauthorized access and replay attacks.
     * </p>
     */
    private String authAttemptProofTokenSignedByDevice;

    /**
     * Additional challenge response data when required.
     * <p>
     * Numeric value representing the response to additional authentication
     * challenges when multi-factor authentication is required. This may
     * include biometric verification results, PIN validation, or other
     * challenge-specific data as determined by the authentication policy
     * and risk assessment. May be null when no additional challenges are required.
     * </p>
     */
    private Integer authAttemptChallengeResponse;

    /**
     * User's explicit decision on the authentication attempt.
     * <p>
     * Boolean flag indicating whether the user has accepted (true) or
     * denied (false) the authentication attempt. This represents the
     * user's explicit consent and is a critical component of the
     * authentication decision. Must be provided for all authentication
     * attempts to ensure proper user authorization.
     * </p>
     */
    private Boolean authAttemptAccepted;

    /**
     * Gets the unique identifier of the authentication attempt.
     *
     * @return the authentication attempt ID
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the unique identifier of the authentication attempt.
     *
     * @param authAttemptId the authentication attempt ID to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the device-signed proof token.
     *
     * @return the cryptographically signed proof token
     */
    public String getAuthAttemptProofTokenSignedByDevice() {
        return authAttemptProofTokenSignedByDevice;
    }

    /**
     * Sets the device-signed proof token.
     *
     * @param authAttemptProofTokenSignedByDevice the signed proof token to set
     */
    public void setAuthAttemptProofTokenSignedByDevice(String authAttemptProofTokenSignedByDevice) {
        this.authAttemptProofTokenSignedByDevice = authAttemptProofTokenSignedByDevice;
    }

    /**
     * Gets the additional challenge response data.
     *
     * @return the challenge response value, or null if no additional challenge was required
     */
    public Integer getAuthAttemptChallengeResponse() {
        return authAttemptChallengeResponse;
    }

    /**
     * Sets the additional challenge response data.
     *
     * @param authAttemptChallengeResponse the challenge response value to set
     */
    public void setAuthAttemptChallengeResponse(Integer authAttemptChallengeResponse) {
        this.authAttemptChallengeResponse = authAttemptChallengeResponse;
    }

    /**
     * Gets the user's decision on the authentication attempt.
     *
     * @return true if the user accepted the authentication, false if denied
     */
    public Boolean getAuthAttemptAccepted() {
        return authAttemptAccepted;
    }

    /**
     * Sets the user's decision on the authentication attempt.
     *
     * @param authAttemptAccepted true if the user accepts the authentication, false if denied
     */
    public void setAuthAttemptAccepted(Boolean authAttemptAccepted) {
        this.authAttemptAccepted = authAttemptAccepted;
    }

}