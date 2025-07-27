package org.ezkey.authattempt.domain;

public class AuthAttemptPendingResponse {

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