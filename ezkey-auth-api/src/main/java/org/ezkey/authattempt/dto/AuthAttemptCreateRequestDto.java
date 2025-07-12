package org.ezkey.authattempt.dto;

public class AuthAttemptCreateRequestDto {
    private Integer enrollmentId;

    private Boolean challengeRequested;

    private String simulationDevicePrivateKey;

    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    public Boolean getChallengeRequested(){
        return challengeRequested;
    }

    public void setChallengeRequested(Boolean challengeRequested){
        this.challengeRequested = challengeRequested;
    }

    public String getSimulationDevicePrivateKey(){
        return simulationDevicePrivateKey;
    }

    public void setSimulationDevicePrivateKey(String simulationDevicePrivateKey){
        this.simulationDevicePrivateKey = simulationDevicePrivateKey;
    }
}