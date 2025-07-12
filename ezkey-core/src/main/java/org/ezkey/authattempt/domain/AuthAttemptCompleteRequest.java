package org.ezkey.authattempt.domain;

public class AuthAttemptCompleteRequest {
    private Integer enrollmentId;

    private Integer authAttemptId;

    private String authAttemptEnrolleeCode;

    private String authAttemptEnrolleeCodeSigned;

    private String authAttemptCode;

    private String authAttemptCodeSigned;

    private Integer authAttemptChallengeResponse;

    private Boolean authAttemptAccepted;

    /**
     * @return the enrollmentId
     */
    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    /**
     * @param enrollmentId the enrollmentId to set
     */
    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

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
     * @return the authAttemptEnrolleeCode
     */
    public String getAuthAttemptEnrolleeCode(){
        return authAttemptEnrolleeCode;
    }

    /**
     * @param authAttemptEnrolleeCode the authAttemptEnrolleeCode to set
     */
    public void setAuthAttemptEnrolleeCode(String authAttemptEnrolleeCode){
        this.authAttemptEnrolleeCode = authAttemptEnrolleeCode;
    }

    /**
     * @return the authAttemptEnrolleeCodeSigned
     */
    public String getAuthAttemptEnrolleeCodeSigned(){
        return authAttemptEnrolleeCodeSigned;
    }

    /**
     * @param authAttemptEnrolleeCodeSigned the authAttemptEnrolleeCodeSigned to set
     */
    public void setAuthAttemptEnrolleeCodeSigned(String authAttemptEnrolleeCodeSigned){
        this.authAttemptEnrolleeCodeSigned = authAttemptEnrolleeCodeSigned;
    }

    /**
     * @return the authAttemptCode
     */
    public String getAuthAttemptCode(){
        return authAttemptCode;
    }

    /**
     * @param authAttemptCode the authAttemptCode to set
     */
    public void setAuthAttemptCode(String authAttemptCode){
        this.authAttemptCode = authAttemptCode;
    }

    /**
     * @return the authAttemptCodeSigned
     */
    public String getAuthAttemptCodeSigned(){
        return authAttemptCodeSigned;
    }

    /**
     * @param authAttemptCodeSigned the authAttemptCodeSigned to set
     */
    public void setAuthAttemptCodeSigned(String authAttemptCodeSigned){
        this.authAttemptCodeSigned = authAttemptCodeSigned;
    }

    /**
     * @return the authAttemptChallengeResponse
     */
    public Integer getAuthAttemptChallengeResponse(){
        return authAttemptChallengeResponse;
    }

    /**
     * @param authAttemptChallengeResponse the authAttemptChallengeResponse to set
     */
    public void setAuthAttemptChallengeResponse(Integer authAttemptChallengeResponse){
        this.authAttemptChallengeResponse = authAttemptChallengeResponse;
    }

    /**
     * @return the authAttemptAccepted
     */
    public Boolean getAuthAttemptAccepted(){
        return authAttemptAccepted;
    }

    /**
     * @param authAttemptAccepted the authAttemptAccepted to set
     */
    public void setAuthAttemptAccepted(Boolean authAttemptAccepted){
        this.authAttemptAccepted = authAttemptAccepted;
    }

}