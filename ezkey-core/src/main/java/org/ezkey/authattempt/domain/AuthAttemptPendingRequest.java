package org.ezkey.authattempt.domain;

public class AuthAttemptPendingRequest {

    private Integer enrollmentId;

    private String deviceProofToken;

    private String deviceProofTokenSigned;

    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
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

}