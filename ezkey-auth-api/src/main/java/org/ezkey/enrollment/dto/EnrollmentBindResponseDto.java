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
 * <b>Fields:</b>
 * <ul>
 * <li><b>enrollmentId:</b> The enrollment identifier being bound</li>
 * <li><b>integrationPublicKey:</b> Integration's public key for verification</li>
 * <li><b>enrollmentCode:</b> Code that must be signed by the mobile device</li>
 * <li><b>enrollmentCodeSigned:</b> Integration's signature of the enrollment code</li>
 * <li><b>simulationEnrollmentCodeSigned:</b> Simulation signature for testing</li>
 * <li><b>simulationDevicePublicKey:</b> Simulation public key for testing</li>
 * <li><b>simulationDevicePrivateKey:</b> Simulation private key for testing</li>
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
 * @see org.ezkey.enrollment.domain.EnrollmentBindResponse
 * @see EnrollmentBindRequestDto
 * @see EnrollmentVerifyRequestDto
 */
public class EnrollmentBindResponseDto {

    /**
     * The enrollment identifier being bound to the mobile device.
     * Used to reference this enrollment in subsequent verification steps.
     */
    private Integer enrollmentId;

    /**
     * The integration's public key for cryptographic verification.
     * Used by the mobile device to validate the enrollment code signature.
     */
    private String integrationPublicKey;

    /**
     * The enrollment code that must be signed by the mobile device.
     * Contains the challenge data that proves device participation in enrollment.
     */
    private String enrollmentCode;

    /**
     * The integration's cryptographic signature of the enrollment code.
     * Provides proof of authenticity and prevents enrollment code tampering.
     */
    private String enrollmentCodeSigned;

    /**
     * Simulation signature of the enrollment code for testing purposes.
     * Used in simulation mode to test enrollment flows without real cryptography.
     */
    private String simulationEnrollmentCodeSigned;

    /**
     * Simulation device public key for testing purposes.
     * Provides pre-generated key material for enrollment flow testing.
     */
    private String simulationDevicePublicKey;

    /**
     * Simulation device private key for testing purposes.
     * Enables complete simulation of device cryptographic operations.
     */
    private String simulationDevicePrivateKey;

    /**
     * Gets the enrollment ID.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the integration public key.
     *
     * @return the integration public key
     */
    public String getIntegrationPublicKey(){
        return integrationPublicKey;
    }

    /**
     * Sets the integration public key.
     *
     * @param integrationPublicKey the integration public key to set
     */
    public void setIntegrationPublicKey(String integrationPublicKey){
        this.integrationPublicKey = integrationPublicKey;
    }

    /**
     * Gets the enrollment code.
     *
     * @return the enrollment code
     */
    public String getEnrollmentCode(){
        return enrollmentCode;
    }

    /**
     * Sets the enrollment code.
     *
     * @param enrollmentCode the enrollment code to set
     */
    public void setEnrollmentCode(String enrollmentCode){
        this.enrollmentCode = enrollmentCode;
    }

    /**
     * Gets the simulation device public key.
     *
     * @return the simulation device public key
     */
    public String getSimulationDevicePublicKey(){
        return simulationDevicePublicKey;
    }

    /**
     * Sets the simulation device public key.
     *
     * @param simulationDevicePublicKey the simulation device public key to set
     */
    public void setSimulationDevicePublicKey(String simulationDevicePublicKey){
        this.simulationDevicePublicKey = simulationDevicePublicKey;
    }

    /**
     * Gets the simulation device private key.
     *
     * @return the simulation device private key
     */
    public String getSimulationDevicePrivateKey(){
        return simulationDevicePrivateKey;
    }

    /**
     * Sets the simulation device private key.
     *
     * @param simulationDevicePrivateKey the simulation device private key to set
     */
    public void setSimulationDevicePrivateKey(String simulationDevicePrivateKey){
        this.simulationDevicePrivateKey = simulationDevicePrivateKey;
    }

    /**
     * Gets the signed enrollment code.
     *
     * @return the signed enrollment code
     */
    public String getEnrollmentCodeSigned(){
        return enrollmentCodeSigned;
    }

    /**
     * Sets the signed enrollment code.
     *
     * @param enrollmentCodeSigned the signed enrollment code to set
     */
    public void setEnrollmentCodeSigned(String enrollmentCodeSigned){
        this.enrollmentCodeSigned = enrollmentCodeSigned;
    }

    /**
     * Gets the simulation signed enrollment code.
     *
     * @return the simulation signed enrollment code
     */
    public String getSimulationEnrollmentCodeSigned(){
        return simulationEnrollmentCodeSigned;
    }

    /**
     * Sets the simulation signed enrollment code.
     *
     * @param simulationEnrollmentCodeSigned the simulation signed enrollment code to set
     */
    public void setSimulationEnrollmentCodeSigned(String simulationEnrollmentCodeSigned){
        this.simulationEnrollmentCodeSigned = simulationEnrollmentCodeSigned;
    }

}