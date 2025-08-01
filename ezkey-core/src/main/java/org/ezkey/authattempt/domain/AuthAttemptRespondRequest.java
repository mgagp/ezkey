package org.ezkey.authattempt.domain;

public class AuthAttemptRespondRequest {

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