/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentBindResponseDto
 * Description: Response DTO for enrollment binding information in auth API.
 */

package org.ezkey.enrollment.dto;

/**
 * Response DTO for enrollment binding information in auth API.
 * <p>
 * This DTO represents the response data returned to mobile devices when they
 * initiate enrollment binding. It contains all the information needed by the
 * mobile device to complete the enrollment process, including cryptographic
 * keys, enrollment codes, and simulation data for testing.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by auth-api when mobile devices request
 * enrollment binding information. The mobile app uses this data to generate
 * its own cryptographic keys, sign the enrollment code, and complete the
 * enrollment verification process.
 * </p>
 *
 * <p>
 * <b>Cryptographic Flow:</b> Contains the integration's public key and signed
 * enrollment code that the mobile device must validate and respond to with
 * its own generated keys and signature.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentBindResponse
 * @see EnrollmentBindRequestDto
 * @see EnrollmentVerifyRequestDto
 */
public class EnrollmentBindResponseDto {

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