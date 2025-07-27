package org.ezkey.enrollment.domain;

public class EnrollmentBindResponse {

    private Integer enrollmentId;

    private String integrationPublicKey;

    private String deviceProofToken;

    private String deviceProofTokenSignedByIntegration;

    // Simulation mode only
    private String simulationDeviceProofTokenSigned;

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

    public String getDeviceProofToken() {
        return deviceProofToken;
    }

    public void setDeviceProofToken(String deviceProofToken) {
        this.deviceProofToken = deviceProofToken;
    }

    public String getDeviceProofTokenSignedByIntegration() {
        return deviceProofTokenSignedByIntegration;
    }

    public void setDeviceProofTokenSignedByIntegration(String deviceProofTokenSignedByIntegration) {
        this.deviceProofTokenSignedByIntegration = deviceProofTokenSignedByIntegration;
    }

    public String getSimulationDeviceProofTokenSigned() {
        return simulationDeviceProofTokenSigned;
    }

    public void setSimulationDeviceProofTokenSigned(String simulationDeviceProofTokenSigned) {
        this.simulationDeviceProofTokenSigned = simulationDeviceProofTokenSigned;
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