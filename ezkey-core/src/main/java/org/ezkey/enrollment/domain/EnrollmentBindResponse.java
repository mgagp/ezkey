package org.ezkey.enrollment.domain;

public class EnrollmentBindResponse {

    private Integer enrollmentId;

    private String integrationPublicKey;

    private String enrollmentProofToken;

    // Simulation mode only
    private String simulationEnrollmentProofTokenSigned;

    private String simulationDevicePublicKey;

    private String simulationDevicePrivateKey;

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

    public String getSimulationEnrollmentProofTokenSigned() {
        return simulationEnrollmentProofTokenSigned;
    }

    public void setSimulationEnrollmentProofTokenSigned(String simulationEnrollmentProofTokenSigned) {
        this.simulationEnrollmentProofTokenSigned = simulationEnrollmentProofTokenSigned;
    }

    public String getSimulationDevicePublicKey() {
        return simulationDevicePublicKey;
    }

    public void setSimulationDevicePublicKey(String simulationDevicePublicKey) {
        this.simulationDevicePublicKey = simulationDevicePublicKey;
    }

    public String getSimulationDevicePrivateKey() {
        return simulationDevicePrivateKey;
    }

    public void setSimulationDevicePrivateKey(String simulationDevicePrivateKey) {
        this.simulationDevicePrivateKey = simulationDevicePrivateKey;
    }

}