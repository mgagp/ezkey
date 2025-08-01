/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateResponseDto
 * Description: Response DTO for authorization attempt creation in admin API.
 */

package org.ezkey.authattempt.dto;

/**
 * Response DTO for authorization attempt creation in admin API.
 * <p>
 * This DTO represents the response data returned when an authorization attempt
 * is successfully created through the admin API. It contains the created attempt's
 * ID and simulation data for testing purposes.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by admin API when creating authentication requests.
 * Contains simulation data that can be used for testing authentication flows
 * without requiring a real mobile device.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>authAttemptId:</b> Unique identifier of the created authentication attempt</li>
 * <li><b>simulationDeviceProofToken:</b> Simulation proof token for testing</li>
 * <li><b>simulationDeviceProofTokenSigned:</b> Signed simulation proof token</li>
 * <li><b>simulationAuthAttemptChallengeResponse:</b> Simulation challenge response</li>
 * </ul>
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
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateResponse
 * @see AuthAttemptCreateRequestDto
 */
public class AuthAttemptCreateResponseDto {

    /**
     * Unique identifier of the created authentication attempt.
     * Used to reference this attempt in subsequent operations.
     */
    private Integer authAttemptId;

    /**
     * Simulation device proof token for testing purposes.
     * Used in simulation mode to test authentication flows.
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

    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    public String getSimulationAuthAttemptProofTokenSignedByDevice() {
        return simulationAuthAttemptProofTokenSignedByDevice;
    }

    public void setSimulationAuthAttemptProofTokenSignedByDevice(String simulationAuthAttemptProofTokenSignedByDevice) {
        this.simulationAuthAttemptProofTokenSignedByDevice = simulationAuthAttemptProofTokenSignedByDevice;
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