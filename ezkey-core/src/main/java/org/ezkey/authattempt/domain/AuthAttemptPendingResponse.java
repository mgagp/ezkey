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
    private String authAttemptProofToken;

    /**
     * Cryptographically signed proof token.
     * Provides integrity protection and prevents tampering with the challenge.
     */
    private String authAttemptProofTokenSignedByIntegration;

    /**
     * Indicates whether additional challenge validation is required.
     * When true, the mobile device must provide additional challenge responses.
     */
    private Boolean authAttemptChallengeRequired;

    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    public String getAuthAttemptProofToken() {
        return authAttemptProofToken;
    }

    public void setAuthAttemptProofToken(String authAttemptProofToken) {
        this.authAttemptProofToken = authAttemptProofToken;
    }

    public String getAuthAttemptProofTokenSignedByIntegration() {
        return authAttemptProofTokenSignedByIntegration;
    }

    public void setAuthAttemptProofTokenSignedByIntegration(String authAttemptProofTokenSignedByIntegration) {
        this.authAttemptProofTokenSignedByIntegration = authAttemptProofTokenSignedByIntegration;
    }

    public Boolean getAuthAttemptChallengeRequired() {
        return authAttemptChallengeRequired;
    }

    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

}