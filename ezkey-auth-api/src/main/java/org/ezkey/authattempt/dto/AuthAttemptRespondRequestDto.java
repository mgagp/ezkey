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

    /**
     * The authentication attempt ID being responded to.
     * Must reference a valid pending authentication attempt.
     */
    private Integer authAttemptId;

    /**
     * The device's enrollee code for validation.
     * Used to verify the responding device's identity during the authentication flow.
     */
    private String authAttemptEnrolleeCode;

    /**
     * Cryptographically signed device enrollee code.
     * Provides proof of device authenticity and prevents impersonation.
     */
    private String authAttemptEnrolleeCodeSigned;

    /**
     * The authentication code being responded to.
     * Must match the code provided in the pending authentication request.
     */
    private String authAttemptCode;

    /**
     * Cryptographically signed authentication code.
     * Ensures the integrity of the authentication code and prevents tampering.
     */
    private String authAttemptCodeSigned;

    /**
     * Response to additional authentication challenges.
     * Required when the authentication attempt includes challenge validation.
     */
    private Integer authAttemptChallengeResponse;

    /**
     * User's decision to accept or deny the authentication attempt.
     * True indicates approval, false indicates denial of the authentication request.
     */
    private Boolean authAttemptAccepted;

    /**
     * Gets the authentication attempt ID.
     *
     * @return the authentication attempt ID
     */
    public Integer getAuthAttemptId(){
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the authentication attempt ID to set
     */
    public void setAuthAttemptId(Integer authAttemptId){
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the authentication attempt enrollee code.
     *
     * @return the authentication attempt enrollee code
     */
    public String getAuthAttemptEnrolleeCode(){
        return authAttemptEnrolleeCode;
    }

    /**
     * Sets the authentication attempt enrollee code.
     *
     * @param authAttemptEnrolleeCode the authentication attempt enrollee code to set
     */
    public void setAuthAttemptEnrolleeCode(String authAttemptEnrolleeCode){
        this.authAttemptEnrolleeCode = authAttemptEnrolleeCode;
    }

    /**
     * Gets the signed authentication attempt enrollee code.
     *
     * @return the signed authentication attempt enrollee code
     */
    public String getAuthAttemptEnrolleeCodeSigned(){
        return authAttemptEnrolleeCodeSigned;
    }

    /**
     * Sets the signed authentication attempt enrollee code.
     *
     * @param authAttemptEnrolleeCodeSigned the signed authentication attempt enrollee code to set
     */
    public void setAuthAttemptEnrolleeCodeSigned(String authAttemptEnrolleeCodeSigned){
        this.authAttemptEnrolleeCodeSigned = authAttemptEnrolleeCodeSigned;
    }

    /**
     * Gets the authentication attempt code.
     *
     * @return the authentication attempt code
     */
    public String getAuthAttemptCode(){
        return authAttemptCode;
    }

    /**
     * Sets the authentication attempt code.
     *
     * @param authAttemptCode the authentication attempt code to set
     */
    public void setAuthAttemptCode(String authAttemptCode){
        this.authAttemptCode = authAttemptCode;
    }

    /**
     * Gets the signed authentication attempt code.
     *
     * @return the signed authentication attempt code
     */
    public String getAuthAttemptCodeSigned(){
        return authAttemptCodeSigned;
    }

    /**
     * Sets the signed authentication attempt code.
     *
     * @param authAttemptCodeSigned the signed authentication attempt code to set
     */
    public void setAuthAttemptCodeSigned(String authAttemptCodeSigned){
        this.authAttemptCodeSigned = authAttemptCodeSigned;
    }

    /**
     * Gets the authentication attempt challenge response.
     *
     * @return the authentication attempt challenge response
     */
    public Integer getAuthAttemptChallengeResponse(){
        return authAttemptChallengeResponse;
    }

    /**
     * Sets the authentication attempt challenge response.
     *
     * @param authAttemptChallengeResponse the authentication attempt challenge response to set
     */
    public void setAuthAttemptChallengeResponse(Integer authAttemptChallengeResponse){
        this.authAttemptChallengeResponse = authAttemptChallengeResponse;
    }

    /**
     * Gets whether the authentication attempt was accepted.
     *
     * @return true if accepted, false if denied
     */
    public Boolean getAuthAttemptAccepted(){
        return authAttemptAccepted;
    }

    /**
     * Sets whether the authentication attempt was accepted.
     *
     * @param authAttemptAccepted true if accepted, false if denied
     */
    public void setAuthAttemptAccepted(Boolean authAttemptAccepted){
        this.authAttemptAccepted = authAttemptAccepted;
    }

}