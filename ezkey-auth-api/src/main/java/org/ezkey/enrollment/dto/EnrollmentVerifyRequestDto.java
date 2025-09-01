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

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Request DTO for enrollment verification completion")
public class EnrollmentVerifyRequestDto {

    /**
     * The enrollment ID being verified.
     * <p>
     * Must reference the same enrollment ID that was used in the binding
     * request. Links this verification request to the specific enrollment
     * that the mobile device is trying to complete.
     * </p>
     */
    @Schema(description = "Enrollment ID being verified", 
            example = "123", 
            required = true)
    private Integer enrollmentId;

    /**
     * User's response to the enrollment challenge.
     * <p>
     * Numeric response provided by the user for enrollment verification.
     * This is typically a code displayed on the integration's website
     * that the user must enter in the mobile app to prove enrollment intent.
     * </p>
     */
    @Schema(description = "User's response to the enrollment challenge", 
            example = "123456", 
            required = true)
    private Integer challengeResponse;

    /**
     * User's response to the enrollment email challenge.
     * <p>
     * Numeric response provided by the user for enrollment email verification
     * when email challenge feature is enabled. This code is sent via email
     * and must be entered in the mobile app to verify email access.
     * </p>
     */
    @Schema(description = "User's response to the enrollment email challenge", 
            example = "654321")
    private Integer emailChallengeResponse;

    /**
     * The mobile device's generated public key.
     * <p>
     * Contains the public key that the mobile device generated as part of
     * its cryptographic key pair. This public key will be stored on the server
     * and used to verify future authentication signatures from this device.
     * </p>
     */
    @Schema(description = "Mobile device's generated public key", 
            example = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...", 
            required = true)
    private String devicePublicKey;

    /**
     * Device-signed enrollment proof token.
     * <p>
     * Contains the enrollment proof token that was provided in the binding
     * response, signed by the mobile device's private key. This signature
     * proves that the device possesses the private key corresponding to
     * the public key being registered.
     * </p>
     */
    @Schema(description = "Device-signed enrollment proof token", 
            example = "eyJhbGciOiJSUzI1NiJ9...", 
            required = true)
    private String enrollmentProofTokenSigned;

    /**
     * Gets the enrollment ID.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the challenge response.
     *
     * @return the challenge response
     */
    public Integer getChallengeResponse() {
        return challengeResponse;
    }

    /**
     * Sets the challenge response.
     *
     * @param challengeResponse the challenge response to set
     */
    public void setChallengeResponse(Integer challengeResponse) {
        this.challengeResponse = challengeResponse;
    }

    /**
     * Gets the email challenge response.
     *
     * @return the email challenge response
     */
    public Integer getEmailChallengeResponse() {
        return emailChallengeResponse;
    }

    /**
     * Sets the email challenge response.
     *
     * @param emailChallengeResponse the email challenge response to set
     */
    public void setEmailChallengeResponse(Integer emailChallengeResponse) {
        this.emailChallengeResponse = emailChallengeResponse;
    }

    /**
     * Gets the device public key.
     *
     * @return the device public key
     */
    public String getDevicePublicKey() {
        return devicePublicKey;
    }

    /**
     * Sets the device public key.
     *
     * @param devicePublicKey the device public key to set
     */
    public void setDevicePublicKey(String devicePublicKey) {
        this.devicePublicKey = devicePublicKey;
    }

    /**
     * Gets the enrollment proof token signed.
     *
     * @return the enrollment proof token signed
     */
    public String getEnrollmentProofTokenSigned() {
        return enrollmentProofTokenSigned;
    }

    /**
     * Sets the enrollment proof token signed.
     *
     * @param enrollmentProofTokenSigned the enrollment proof token signed to set
     */
    public void setEnrollmentProofTokenSigned(String enrollmentProofTokenSigned) {
        this.enrollmentProofTokenSigned = enrollmentProofTokenSigned;
    }

}