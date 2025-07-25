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
 * <li><b>simulationAuthAttemptEnrolleeCode:</b> Simulation code for testing</li>
 * <li><b>simulationAuthAttemptEnrolleeCodeSigned:</b> Signed simulation code</li>
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
     * Simulation enrollee code for testing purposes.
     * Used in simulation mode to test authentication flows.
     */
    private String simulationAuthAttemptEnrolleeCode;

    /**
     * Cryptographically signed simulation enrollee code.
     * Provides authenticated simulation data for testing.
     */
    private String simulationAuthAttemptEnrolleeCodeSigned;

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
     * Gets the simulation enrollee code.
     *
     * @return the simulation enrollee code
     */
    public String getSimulationAuthAttemptEnrolleeCode(){
        return simulationAuthAttemptEnrolleeCode;
    }

    /**
     * Sets the simulation enrollee code.
     *
     * @param simulationAuthAttemptEnrolleeCode the simulation enrollee code to set
     */
    public void setSimulationAuthAttemptEnrolleeCode(String simulationAuthAttemptEnrolleeCode){
        this.simulationAuthAttemptEnrolleeCode = simulationAuthAttemptEnrolleeCode;
    }

    /**
     * Gets the signed simulation enrollee code.
     *
     * @return the signed simulation enrollee code
     */
    public String getSimulationAuthAttemptEnrolleeCodeSigned(){
        return simulationAuthAttemptEnrolleeCodeSigned;
    }

    /**
     * Sets the signed simulation enrollee code.
     *
     * @param simulationAuthAttemptEnrolleeCodeSigned the signed simulation enrollee code to set
     */
    public void setSimulationAuthAttemptEnrolleeCodeSigned(String simulationAuthAttemptEnrolleeCodeSigned){
        this.simulationAuthAttemptEnrolleeCodeSigned = simulationAuthAttemptEnrolleeCodeSigned;
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