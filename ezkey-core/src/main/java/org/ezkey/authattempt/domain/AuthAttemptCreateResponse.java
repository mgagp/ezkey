/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptCreateResponse
 * Description: Domain response object for creating authentication attempts.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain response object for creating authentication attempts.
 * <p>
 * This domain object represents the response data returned internally by the service layer
 * after successfully creating an authentication attempt. It contains the created attempt's
 * ID and simulation data for testing purposes, enabling end-to-end testing
 * of authentication flows without requiring actual mobile devices.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the AuthAttemptService to return authentication
 * attempt creation results to both admin and auth API controllers. The service layer
 * transforms this domain object into appropriate API DTOs for external consumption.
 * </p>
 *
 * <p>
 * <b>Authentication Flow:</b> This response confirms that an authentication attempt
 * has been successfully created and is now pending user approval through their
 * enrolled mobile device. The authentication attempt ID can be used for tracking
 * and monitoring the authentication process.
 * </p>
 *
 * <p>
 * <b>Simulation Support:</b> Includes comprehensive simulation data for testing
 * and development environments, allowing complete end-to-end authentication
 * flow validation without requiring actual mobile devices. All simulation
 * fields are populated only when simulation mode is enabled.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> Simulation fields contain sensitive cryptographic
 * material that should never be exposed in production environments. These
 * fields enable automated testing of the complete authentication flow.
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
 * @see org.ezkey.authattempt.service.AuthAttemptService
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateRequest
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptCreateResponse {

    /**
     * Unique identifier of the created authentication attempt.
     * <p>
     * Used to reference this specific authentication attempt in subsequent
     * operations and tracking. This ID links the authentication request
     * to the user's approval or denial decision and enables monitoring
     * of the authentication flow status.
     * </p>
     */
    private Integer authAttemptId;

    /**
     * Device-signed proof token for simulation purposes.
     * <p>
     * Contains a cryptographically signed proof token that simulates
     * device authentication responses. Used in simulation mode to test
     * authentication flows without requiring actual mobile devices.
     * This token provides realistic authentication flow testing.
     * </p>
     * <p>
     * <b>Security Warning:</b> This field contains sensitive cryptographic
     * data and should never be populated in production environments.
     * </p>
     */
    private String simulationAuthAttemptProofTokenSignedByDevice;

    /**
     * Pre-computed challenge response for simulation purposes.
     * <p>
     * Contains the expected challenge response value for testing authentication
     * flows. Used in simulation mode to provide automated testing of
     * challenge-response mechanisms without user interaction.
     * </p>
     * <p>
     * <b>Security Warning:</b> This field should never be populated in
     * production environments and is exclusively for testing purposes.
     * </p>
     */
    private Integer simulationAuthAttemptChallengeResponse;

    /**
     * Pending device proof token for simulation purposes.
     * <p>
     * Generated token used to simulate pending authentication states during testing.
     * Enables realistic multi-step authentication flow simulation by providing
     * appropriate proof tokens for each stage of the authentication process.
     * </p>
     * <p>
     * <b>Security Warning:</b> This field contains sensitive simulation data
     * and should never be populated in production environments.
     * </p>
     */
    private String simulationPendingDeviceProofToken;

    /**
     * Cryptographically signed pending device proof token for simulation purposes.
     * <p>
     * Contains the signed version of the pending device proof token for complete
     * authentication flow simulation. Enables end-to-end testing of signature
     * verification and cryptographic validation processes.
     * </p>
     * <p>
     * <b>Security Warning:</b> This field contains highly sensitive cryptographic
     * material and should never be populated in production environments.
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
     * @param authAttemptId the authentication attempt ID to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the device-signed proof token for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @return the simulation device-signed proof token
     */
    public String getSimulationAuthAttemptProofTokenSignedByDevice() {
        return simulationAuthAttemptProofTokenSignedByDevice;
    }

    /**
     * Sets the device-signed proof token for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @param simulationAuthAttemptProofTokenSignedByDevice the simulation device-signed proof token to set
     */
    public void setSimulationAuthAttemptProofTokenSignedByDevice(String simulationAuthAttemptProofTokenSignedByDevice) {
        this.simulationAuthAttemptProofTokenSignedByDevice = simulationAuthAttemptProofTokenSignedByDevice;
    }

    /**
     * Gets the challenge response for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @return the simulation challenge response
     */
    public Integer getSimulationAuthAttemptChallengeResponse() {
        return simulationAuthAttemptChallengeResponse;
    }

    /**
     * Sets the challenge response for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @param simulationAuthAttemptChallengeResponse the simulation challenge response to set
     */
    public void setSimulationAuthAttemptChallengeResponse(Integer simulationAuthAttemptChallengeResponse) {
        this.simulationAuthAttemptChallengeResponse = simulationAuthAttemptChallengeResponse;
    }

    /**
     * Gets the pending device proof token for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @return the simulation pending device proof token
     */
    public String getSimulationPendingDeviceProofToken() {
        return simulationPendingDeviceProofToken;
    }

    /**
     * Sets the pending device proof token for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @param simulationPendingDeviceProofToken the simulation pending device proof token to set
     */
    public void setSimulationPendingDeviceProofToken(String simulationPendingDeviceProofToken) {
        this.simulationPendingDeviceProofToken = simulationPendingDeviceProofToken;
    }

    /**
     * Gets the signed pending device proof token for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @return the simulation signed pending device proof token
     */
    public String getSimulationPendingDeviceProofTokenSigned() {
        return simulationPendingDeviceProofTokenSigned;
    }

    /**
     * Sets the signed pending device proof token for simulation.
     * <p>
     * <b>Security Warning:</b> This method should only be used in testing
     * and development environments. Never call this in production code.
     * </p>
     *
     * @param simulationPendingDeviceProofTokenSigned the simulation signed pending device proof token to set
     */
    public void setSimulationPendingDeviceProofTokenSigned(String simulationPendingDeviceProofTokenSigned) {
        this.simulationPendingDeviceProofTokenSigned = simulationPendingDeviceProofTokenSigned;
    }

}