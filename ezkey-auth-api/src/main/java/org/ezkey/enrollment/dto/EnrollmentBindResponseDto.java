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

    private String enrollmentProofToken;

    // Simulation mode only
    private String simulationEnrollmentProofTokenSigned;

    private String simulationDevicePublicKey;

    private String simulationDevicePrivateKey;

    private String simulationPendingDeviceProofToken;

    private String simulationPendingDeviceProofTokenSigned;

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

    public String getSimulationPendingDeviceProofToken() {
        return simulationPendingDeviceProofToken;
    }

    public void setSimulationPendingDeviceProofToken(String simulationPendingDeviceProofToken) {
        this.simulationPendingDeviceProofToken = simulationPendingDeviceProofToken;
    }

    public String getSimulationPendingDeviceProofTokenSigned() {
        return simulationPendingDeviceProofTokenSigned;
    }

    public void setSimulationPendingDeviceProofTokenSigned(String simulationPendingDeviceProofTokenSigned) {
        this.simulationPendingDeviceProofTokenSigned = simulationPendingDeviceProofTokenSigned;
    }

}