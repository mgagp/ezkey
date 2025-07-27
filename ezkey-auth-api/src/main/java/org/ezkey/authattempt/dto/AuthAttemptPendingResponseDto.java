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
public class AuthAttemptPendingResponseDto {

    /**
     * Unique identifier of the authentication attempt.
     * Used by the mobile device to reference this attempt when submitting a response.
     */
    private Integer authAttemptId;

    /**
     * Integration proof token for this attempt.
     * Contains the challenge data that needs to be signed by the mobile device.
     */
    private String integrationProofToken;

    /**
     * Cryptographically signed proof token.
     * Provides integrity protection and prevents tampering with the challenge.
     */
    private String integrationProofTokenSigned;

    /**
     * Indicates whether additional challenge validation is required.
     * When true, the mobile device must provide additional challenge responses.
     */
    private Boolean authAttemptChallengeRequired;

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
     * Gets the integration proof token.
     *
     * @return the integration proof token
     */
    public String getIntegrationProofToken(){
        return integrationProofToken;
    }

    /**
     * Sets the integration proof token.
     *
     * @param integrationProofToken the integration proof token to set
     */
    public void setIntegrationProofToken(String integrationProofToken){
        this.integrationProofToken = integrationProofToken;
    }

    /**
     * Gets the signed integration proof token.
     *
     * @return the signed integration proof token
     */
    public String getIntegrationProofTokenSigned(){
        return integrationProofTokenSigned;
    }

    /**
     * Sets the signed integration proof token.
     *
     * @param authAttemptCodeSigned the signed integration proof token to set
     */
    public void setIntegrationProofTokenSigned(String integrationProofTokenSigned){
        this.integrationProofTokenSigned = integrationProofTokenSigned;
    }

    /**
     * Gets whether authentication attempt challenge is required.
     *
     * @return true if challenge is required, false otherwise
     */
    public Boolean getAuthAttemptChallengeRequired(){
        return authAttemptChallengeRequired;
    }

    /**
     * Sets whether authentication attempt challenge is required.
     *
     * @param authAttemptChallengeRequired true if challenge is required, false otherwise
     */
    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired){
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

}