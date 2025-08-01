package org.ezkey.enrollment.domain;

public class EnrollmentCreateResponse {

    private Integer enrollmentId;

    private Integer enrollmentChallenge;

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

    public Integer getEnrollmentChallenge() {
        return enrollmentChallenge;
    }

    public void setEnrollmentChallenge(Integer enrollmentChallenge) {
        this.enrollmentChallenge = enrollmentChallenge;
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