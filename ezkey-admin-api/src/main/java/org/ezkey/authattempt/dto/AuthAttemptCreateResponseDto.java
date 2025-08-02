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
 * Response DTO for authentication attempt creation in admin API.
 * <p>
 * This DTO represents the response data returned when an authentication attempt
 * is successfully created through the admin API. It contains the created attempt's
 * ID and simulation data for testing purposes, enabling end-to-end testing
 * of authentication flows without requiring actual mobile devices.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by admin API when creating authentication requests.
 * Contains simulation data that can be used for testing authentication flows
 * without requiring a real mobile device. All simulation fields are populated
 * only in test/development environments.
 * </p>
 *
 * <p>
 * <b>Core Fields:</b>
 * <ul>
 * <li><b>authAttemptId:</b> Unique identifier of the created authentication attempt</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Simulation Fields (Testing/Development Only):</b>
 * <ul>
 * <li><b>simulationAuthAttemptProofTokenSignedByDevice:</b> Device-signed proof token for simulation</li>
 * <li><b>simulationAuthAttemptChallengeResponse:</b> Pre-computed challenge response for simulation</li>
 * <li><b>simulationPendingDeviceProofToken:</b> Pending device proof token for simulation</li>
 * <li><b>simulationPendingDeviceProofTokenSigned:</b> Signed pending device proof token for simulation</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Note:</b> All simulation fields should never be populated in production
 * environments and are strictly for testing and development purposes only.
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

    /**
     * Pending device proof token used for simulation purposes.
     * This field is populated only in test/development environments
     * to simulate pending authentication state during testing.
     */
    private String simulationPendingDeviceProofToken;

    /**
     * Signed pending device proof token used for simulation purposes.
     * This field is populated only in test/development environments
     * to simulate signed pending authentication tokens during testing.
     * <p>
     * <b>Security Note:</b> This field should never be populated in production environments.
     * </p>
     */
    private String simulationPendingDeviceProofTokenSigned;

    /**
     * Gets the authentication attempt ID.
     *
     * @return the unique identifier of the created authentication attempt
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the unique identifier of the created authentication attempt to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the simulation authentication attempt proof token signed by device.
     * This method is used only in test/development environments.
     *
     * @return the device-signed proof token for simulation, or null if not in simulation mode
     */
    public String getSimulationAuthAttemptProofTokenSignedByDevice() {
        return simulationAuthAttemptProofTokenSignedByDevice;
    }

    /**
     * Sets the simulation authentication attempt proof token signed by device.
     * This method should only be used in test/development environments.
     *
     * @param simulationAuthAttemptProofTokenSignedByDevice the device-signed proof token for simulation to set
     */
    public void setSimulationAuthAttemptProofTokenSignedByDevice(String simulationAuthAttemptProofTokenSignedByDevice) {
        this.simulationAuthAttemptProofTokenSignedByDevice = simulationAuthAttemptProofTokenSignedByDevice;
    }

    /**
     * Gets the simulation authentication attempt challenge response.
     * This method is used only in test/development environments.
     *
     * @return the pre-computed challenge response for simulation, or null if not in simulation mode
     */
    public Integer getSimulationAuthAttemptChallengeResponse() {
        return simulationAuthAttemptChallengeResponse;
    }

    /**
     * Sets the simulation authentication attempt challenge response.
     * This method should only be used in test/development environments.
     *
     * @param simulationAuthAttemptChallengeResponse the pre-computed challenge response for simulation to set
     */
    public void setSimulationAuthAttemptChallengeResponse(Integer simulationAuthAttemptChallengeResponse) {
        this.simulationAuthAttemptChallengeResponse = simulationAuthAttemptChallengeResponse;
    }

    /**
     * Gets the simulation pending device proof token.
     * This method is used only in test/development environments.
     *
     * @return the pending device proof token for simulation, or null if not in simulation mode
     */
    public String getSimulationPendingDeviceProofToken() {
        return simulationPendingDeviceProofToken;
    }

    /**
     * Sets the simulation pending device proof token.
     * This method should only be used in test/development environments.
     *
     * @param simulationPendingDeviceProofToken the pending device proof token for simulation to set
     */
    public void setSimulationPendingDeviceProofToken(String simulationPendingDeviceProofToken) {
        this.simulationPendingDeviceProofToken = simulationPendingDeviceProofToken;
    }

    /**
     * Gets the simulation pending device proof token signed.
     * This method is used only in test/development environments.
     *
     * @return the signed pending device proof token for simulation, or null if not in simulation mode
     */
    public String getSimulationPendingDeviceProofTokenSigned() {
        return simulationPendingDeviceProofTokenSigned;
    }

    /**
     * Sets the simulation pending device proof token signed.
     * This method should only be used in test/development environments.
     * <p>
     * <b>Security Warning:</b> This should never be called in production environments.
     * </p>
     *
     * @param simulationPendingDeviceProofTokenSigned the signed pending device proof token for simulation to set
     */
    public void setSimulationPendingDeviceProofTokenSigned(String simulationPendingDeviceProofTokenSigned) {
        this.simulationPendingDeviceProofTokenSigned = simulationPendingDeviceProofTokenSigned;
    }

}