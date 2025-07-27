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
     * @return the authAttemptId
     */
    public Integer getAuthAttemptId(){
        return authAttemptId;
    }

    /**
     * @param authAttemptId the authAttemptId to set
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

    public Integer getSimulationAuthAttemptChallengeResponse(){
        return simulationAuthAttemptChallengeResponse;
    }

    public void setSimulationAuthAttemptChallengeResponse(Integer simulationAuthAttemptChallengeResponse){
        this.simulationAuthAttemptChallengeResponse = simulationAuthAttemptChallengeResponse;
    }

}