/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentVerifyRequestDto
 * Description: Request DTO for enrollment verification completion in auth API.
 */

package org.ezkey.enrollment.dto;

/**
 * Request DTO for enrollment verification completion in auth API.
 * <p>
 * This DTO represents the request data sent by mobile devices to complete
 * the enrollment verification process. It contains the device's generated
 * cryptographic keys, signed enrollment code, and challenge response that
 * finalize the enrollment and activate the device for MFA authentication.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by mobile devices to complete enrollment with
 * the auth-api after receiving binding information. The mobile app generates
 * its cryptographic key pair, signs the enrollment code, and submits this
 * verification request to activate the enrollment.
 * </p>
 *
 * <p>
 * <b>Cryptographic Completion:</b> Contains the mobile device's public key
 * and signature of the enrollment code, proving that the device has the
 * corresponding private key and can participate in future authentication flows.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>enrollmentId:</b> The enrollment being verified and activated</li>
 * <li><b>challengeResponse:</b> Response to the enrollment challenge</li>
 * <li><b>devicePublicKey:</b> Device's generated public key for authentication</li>
 * <li><b>enrollmentCode:</b> The enrollment code being signed</li>
 * <li><b>enrollmentCodeSigned:</b> Device's signature of the enrollment code</li>
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
 * @see org.ezkey.enrollment.domain.EnrollmentVerifyRequest
 * @see EnrollmentBindResponseDto
 * @see EnrollmentVerifyResponseDto
 */
public class EnrollmentVerifyRequestDto {

    /**
     * The enrollment ID being verified and activated.
     * Must reference an existing enrollment that was previously bound.
     */
    private Integer enrollmentId;

    /**
     * Response to the enrollment challenge.
     * Computed based on the challenge received during enrollment binding.
     */
    private Integer challengeResponse;

    /**
     * The mobile device's generated public key for future authentication.
     * Used by the system to verify signatures in subsequent authentication attempts.
     */
    private String devicePublicKey;

    /**
     * The enrollment code that must be signed by the mobile device.
     * Must match the code received during enrollment binding.
     */
    private String enrollmentCode;

    /**
     * The mobile device's cryptographic signature of the enrollment code.
     * Proves that the device possesses the private key corresponding to devicePublicKey.
     */
    private String enrollmentCodeSigned;

    /**
     * Gets the device public key.
     *
     * @return the device public key
     */
    public String getDevicePublicKey(){
        return devicePublicKey;
    }

    /**
     * Sets the device public key.
     *
     * @param devicePublicKey the device public key to set
     */
    public void setDevicePublicKey(String devicePublicKey){
        this.devicePublicKey = devicePublicKey;
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
     * Gets the challenge response.
     *
     * @return the challenge response
     */
    public Integer getChallengeResponse(){
        return challengeResponse;
    }

    /**
     * Sets the challenge response.
     *
     * @param challengeResponse the challenge response to set
     */
    public void setChallengeResponse(Integer challengeResponse){
        this.challengeResponse = challengeResponse;
    }

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

}