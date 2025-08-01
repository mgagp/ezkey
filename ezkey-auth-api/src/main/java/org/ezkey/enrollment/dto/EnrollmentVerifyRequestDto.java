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

    private Integer enrollmentId;

    private Integer challengeResponse;

    private String devicePublicKey;

    private String enrollmentProofTokenSigned;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Integer getChallengeResponse() {
        return challengeResponse;
    }

    public void setChallengeResponse(Integer challengeResponse) {
        this.challengeResponse = challengeResponse;
    }

    public String getDevicePublicKey() {
        return devicePublicKey;
    }

    public void setDevicePublicKey(String devicePublicKey) {
        this.devicePublicKey = devicePublicKey;
    }

    public String getEnrollmentProofTokenSigned() {
        return enrollmentProofTokenSigned;
    }

    public void setEnrollmentProofTokenSigned(String enrollmentProofTokenSigned) {
        this.enrollmentProofTokenSigned = enrollmentProofTokenSigned;
    }

}