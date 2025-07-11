package org.ezkey.enrollment.dto;

public class EnrollmentBindResponse {
	private Integer enrollmentId;
	private String integrationPublicKey;
	private String enrollmentCode;
	private String enrollmentCodeSigned;

	// Simulation mode only
	private String simulationEnrollmentCodeSigned;
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

	public String getEnrollmentCode() {
		return enrollmentCode;
	}

	public void setEnrollmentCode(String enrollmentCode) {
		this.enrollmentCode = enrollmentCode;
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

	public String getEnrollmentCodeSigned() {
		return enrollmentCodeSigned;
	}

	public void setEnrollmentCodeSigned(String enrollmentCodeSigned) {
		this.enrollmentCodeSigned = enrollmentCodeSigned;
	}

	public String getSimulationEnrollmentCodeSigned() {
		return simulationEnrollmentCodeSigned;
	}

	public void setSimulationEnrollmentCodeSigned(String simulationEnrollmentCodeSigned) {
		this.simulationEnrollmentCodeSigned = simulationEnrollmentCodeSigned;
	}

}