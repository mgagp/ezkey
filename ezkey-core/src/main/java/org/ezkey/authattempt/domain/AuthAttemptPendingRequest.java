package org.ezkey.authattempt.domain;

public class AuthAttemptPendingRequest {

    private Integer enrollmentId;

    private String deviceProofToken;

    private String deviceProofTokenSigned;

    // Simulation mode only

    private String simulationDevicePrivateKey;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getDeviceProofToken() {
        return deviceProofToken;
    }

    public void setDeviceProofToken(String deviceProofToken) {
        this.deviceProofToken = deviceProofToken;
    }

    public String getDeviceProofTokenSigned() {
        return deviceProofTokenSigned;
    }

    public void setDeviceProofTokenSigned(String deviceProofTokenSigned) {
        this.deviceProofTokenSigned = deviceProofTokenSigned;
    }

    public String getSimulationDevicePrivateKey() {
        return simulationDevicePrivateKey;
    }

    public void setSimulationDevicePrivateKey(String simulationDevicePrivateKey) {
        this.simulationDevicePrivateKey = simulationDevicePrivateKey;
    }

}