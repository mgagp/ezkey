package org.ezkey.enrollment.dto;

public class EnrollmentConfirmRequest {
    private Integer enrollmentId;

    private Integer challengeResponse;

    private String devicePublicKey;

    private String enrollmentCode;

    private String enrollmentCodeSigned;

    public String getDevicePublicKey(){
        return devicePublicKey;
    }

    public void setDevicePublicKey(String devicePublicKey){
        this.devicePublicKey = devicePublicKey;
    }

    public String getEnrollmentCode(){
        return enrollmentCode;
    }

    public void setEnrollmentCode(String enrollmentCode){
        this.enrollmentCode = enrollmentCode;
    }

    public Integer getChallengeResponse(){
        return challengeResponse;
    }

    public void setChallengeResponse(Integer challengeResponse){
        this.challengeResponse = challengeResponse;
    }

    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    public String getEnrollmentCodeSigned(){
        return enrollmentCodeSigned;
    }

    public void setEnrollmentCodeSigned(String enrollmentCodeSigned){
        this.enrollmentCodeSigned = enrollmentCodeSigned;
    }

}