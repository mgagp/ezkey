/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateResponseDto
 * Description: Response DTO for authorization attempt creation in admin API.
 */

package org.ezkey.authattempt.domain;

public class AuthAttemptCreateResponse {

    /**
     * Unique identifier of the created authentication attempt.
     * Used to reference this attempt in subsequent operations.
     */
    private Integer authAttemptId;

    /**
     * Cryptographically signed simulation device proof token.
     * Provides authenticated simulation data for testing.
     */
    private String simulationAuthAttemptProofTokenSignedByDevice;

    /**
     * Simulation challenge response for testing.
     * Pre-computed response for simulation authentication flows.
     */
    private Integer simulationAuthAttemptChallengeResponse;

    // Simulation only

    private String simulationPendingDeviceProofToken;

    private String simulationPendingDeviceProofTokenSigned;

    /**
     * @return the authAttemptId
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public String getSimulationAuthAttemptProofTokenSignedByDevice() {
        return simulationAuthAttemptProofTokenSignedByDevice;
    }

    public void setSimulationAuthAttemptProofTokenSignedByDevice(String simulationAuthAttemptProofTokenSignedByDevice) {
        this.simulationAuthAttemptProofTokenSignedByDevice = simulationAuthAttemptProofTokenSignedByDevice;
    }

    /**
     * @param authAttemptId the authAttemptId to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    public Integer getSimulationAuthAttemptChallengeResponse() {
        return simulationAuthAttemptChallengeResponse;
    }

    public void setSimulationAuthAttemptChallengeResponse(Integer simulationAuthAttemptChallengeResponse) {
        this.simulationAuthAttemptChallengeResponse = simulationAuthAttemptChallengeResponse;
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