package org.ezkey.enrollment.domain;

public class EnrollmentVerifyRequest {

    private Integer enrollmentId;

    private Integer challengeResponse;

    private String devicePublicKey;

    private String enrollmentProofTokenSigned;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Integer getChallengeResponse() {
        return challengeResponse;
    }

    public void setChallengeResponse(Integer challengeResponse) {
        this.challengeResponse = challengeResponse;
    }

    public String getDevicePublicKey() {
        return devicePublicKey;
    }

    public void setDevicePublicKey(String devicePublicKey) {
        this.devicePublicKey = devicePublicKey;
    }

    public String getEnrollmentProofTokenSigned() {
        return enrollmentProofTokenSigned;
    }

    public void setEnrollmentProofTokenSigned(String enrollmentProofTokenSigned) {
        this.enrollmentProofTokenSigned = enrollmentProofTokenSigned;
    }

}