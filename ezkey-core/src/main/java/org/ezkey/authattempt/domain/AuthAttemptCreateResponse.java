package org.ezkey.authattempt.domain;

public class AuthAttemptCreateResponse {

    private Integer authAttemptId;

    private String simulationAuthAttemptEnrolleeCode;

    private String simulationAuthAttemptEnrolleeCodeSigned;

    private Integer simulationAuthAttemptChallengeResponse;

    /**
     * @return the authAttemptId
     */
    public Integer getAuthAttemptId(){
        return authAttemptId;
    }

    /**
     * @param authAttemptId the authAttemptId to set
     */
    public void setAuthAttemptId(Integer authAttemptId){
        this.authAttemptId = authAttemptId;
    }

    /**
     * @return the simulationAuthAttemptEnrolleeCode
     */
    public String getSimulationAuthAttemptEnrolleeCode(){
        return simulationAuthAttemptEnrolleeCode;
    }

    /**
     * @param simulationAuthAttemptEnrolleeCode the
     * simulationAuthAttemptEnrolleeCode to
     * set
     */
    public void setSimulationAuthAttemptEnrolleeCode(String simulationAuthAttemptEnrolleeCode){
        this.simulationAuthAttemptEnrolleeCode = simulationAuthAttemptEnrolleeCode;
    }

    /**
     * @return the simulationAuthAttemptEnrolleeCodeSigned
     */
    public String getSimulationAuthAttemptEnrolleeCodeSigned(){
        return simulationAuthAttemptEnrolleeCodeSigned;
    }

    /**
     * @param simulationAuthAttemptEnrolleeCodeSigned the
     * simulationAuthAttemptEnrolleeCodeSigned
     * to set
     */
    public void setSimulationAuthAttemptEnrolleeCodeSigned(String simulationAuthAttemptEnrolleeCodeSigned){
        this.simulationAuthAttemptEnrolleeCodeSigned = simulationAuthAttemptEnrolleeCodeSigned;
    }

    public Integer getSimulationAuthAttemptChallengeResponse(){
        return simulationAuthAttemptChallengeResponse;
    }

    public void setSimulationAuthAttemptChallengeResponse(Integer simulationAuthAttemptChallengeResponse){
        this.simulationAuthAttemptChallengeResponse = simulationAuthAttemptChallengeResponse;
    }

}