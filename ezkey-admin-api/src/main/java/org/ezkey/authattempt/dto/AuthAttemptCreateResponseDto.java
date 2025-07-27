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
    private String simulationDeviceProofToken;

    /**
     * Cryptographically signed simulation device proof token.
     * Provides authenticated simulation data for testing.
     */
    private String simulationDeviceProofTokenSigned;

    /**
     * Simulation challenge response for testing.
     * Pre-computed response for simulation authentication flows.
     */
    private Integer simulationAuthAttemptChallengeResponse;

    /**
     * Gets the authentication attempt ID.
     *
     * @return the authentication attempt ID
     */
    public Integer getAuthAttemptId(){
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the authentication attempt ID to set
     */
    public void setAuthAttemptId(Integer authAttemptId){
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the simulation device proof token.
     *
     * @return the simulation device proof token
     */
    public String getSimulationDeviceProofToken(){
        return simulationDeviceProofToken;
    }

    /**
     * Sets the simulation device proof token.
     *
     * @param simulationDeviceProofToken the simulation device proof token to set
     */
    public void setSimulationDeviceProofToken(String simulationDeviceProofToken){
        this.simulationDeviceProofToken = simulationDeviceProofToken;
    }

    /**
     * Gets the signed simulation device proof token.
     *
     * @return the signed simulation device proof token
     */
    public String getSimulationDeviceProofTokenSigned(){
        return simulationDeviceProofTokenSigned;
    }

    /**
     * Sets the signed simulation device proof token.
     *
     * @param simulationDeviceProofTokenSigned the signed simulation device proof token to set
     */
    public void setSimulationDeviceProofTokenSigned(String simulationDeviceProofTokenSigned){
        this.simulationDeviceProofTokenSigned = simulationDeviceProofTokenSigned;
    }

    /**
     * Gets the simulation challenge response.
     *
     * @return the simulation challenge response
     */
    public Integer getSimulationAuthAttemptChallengeResponse(){
        return simulationAuthAttemptChallengeResponse;
    }

    /**
     * Sets the simulation challenge response.
     *
     * @param simulationAuthAttemptChallengeResponse the simulation challenge response to set
     */
    public void setSimulationAuthAttemptChallengeResponse(Integer simulationAuthAttemptChallengeResponse){
        this.simulationAuthAttemptChallengeResponse = simulationAuthAttemptChallengeResponse;
    }

}