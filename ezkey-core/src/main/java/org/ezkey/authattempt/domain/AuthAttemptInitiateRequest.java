package org.ezkey.authattempt.domain;

public class AuthAttemptInitiateRequest {
    private Integer enrollmentId;

    private String authAttemptEnrolleeCode;

    private String authAttemptEnrolleeCodeSigned;

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

}