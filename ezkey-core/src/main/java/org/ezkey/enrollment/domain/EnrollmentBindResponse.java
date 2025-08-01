package org.ezkey.enrollment.domain;

public class EnrollmentBindResponse {

    private Integer enrollmentId;

    private String integrationPublicKey;

    private String enrollmentProofToken;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getIntegrationPublicKey() {
        return integrationPublicKey;
    }

    public void setIntegrationPublicKey(String integrationPublicKey) {
        this.integrationPublicKey = integrationPublicKey;
    }

    public String getEnrollmentProofToken() {
        return enrollmentProofToken;
    }

    public void setEnrollmentProofToken(String enrollmentProofToken) {
        this.enrollmentProofToken = enrollmentProofToken;
    }

}