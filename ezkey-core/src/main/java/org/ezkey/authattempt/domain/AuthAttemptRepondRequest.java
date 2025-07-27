package org.ezkey.authattempt.domain;

public class AuthAttemptRepondRequest {

    private Integer authAttemptId;

    private String deviceProofToken;

    private String deviceProofTokenSigned;

    private String integrationProofToken;

    private String integrationProofTokenSigned;

    private Integer authAttemptChallengeResponse;

    private Boolean authAttemptAccepted;

    public Integer getAuthAttemptId(){
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId){
        this.authAttemptId = authAttemptId;
    }

    public String getDeviceProofToken(){
        return deviceProofToken;
    }

    public void setDeviceProofToken(String deviceProofToken){
        this.deviceProofToken = deviceProofToken;
    }

    public String getDeviceProofTokenSigned(){
        return deviceProofTokenSigned;
    }

    public void setDeviceProofTokenSigned(String deviceProofTokenSigned){
        this.deviceProofTokenSigned = deviceProofTokenSigned;
    }

    public String getIntegrationProofToken(){
        return integrationProofToken;
    }

    public void setIntegrationProofToken(String integrationProofToken){
        this.integrationProofToken = integrationProofToken;
    }

    public String getIntegrationProofTokenSigned(){
        return integrationProofTokenSigned;
    }

    public void setIntegrationProofTokenSigned(String integrationProofTokenSigned){
        this.integrationProofTokenSigned = integrationProofTokenSigned;
    }

    public Integer getAuthAttemptChallengeResponse(){
        return authAttemptChallengeResponse;
    }

    public void setAuthAttemptChallengeResponse(Integer authAttemptChallengeResponse){
        this.authAttemptChallengeResponse = authAttemptChallengeResponse;
    }

    public Boolean getAuthAttemptAccepted(){
        return authAttemptAccepted;
    }

    public void setAuthAttemptAccepted(Boolean authAttemptAccepted){
        this.authAttemptAccepted = authAttemptAccepted;
    }

}