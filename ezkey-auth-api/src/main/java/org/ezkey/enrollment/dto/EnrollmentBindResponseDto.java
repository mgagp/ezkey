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

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Response DTO containing enrollment binding information")
public class EnrollmentBindResponseDto {

    /**
     * The enrollment ID for this binding operation.
     * <p>
     * Confirms the enrollment ID that was successfully bound to the mobile device.
     * Used for reference in subsequent verification requests.
     * </p>
     */
    @Schema(description = "Enrollment ID that was bound to the mobile device", 
            example = "123", 
            required = true)
    private Integer enrollmentId;

    /**
     * The integration's public key for cryptographic operations.
     * <p>
     * Contains the public key of the integration that created this enrollment.
     * Used by the mobile device to verify signatures and validate integration
     * authenticity during the enrollment process.
     * </p>
     */
    @Schema(description = "Integration's public key for cryptographic verification", 
            example = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...", 
            required = true)
    private String integrationPublicKey;

    /**
     * The enrollment proof token that needs to be signed by the device.
     * <p>
     * Contains the challenge data that the mobile device must sign with its
     * private key to complete enrollment verification. This token proves that
     * the device possesses the cryptographic keys it claims to have.
     * </p>
     */
    @Schema(description = "Enrollment proof token to be signed by the device", 
            example = "eyJhbGciOiJSUzI1NiJ9...", 
            required = true)
    private String enrollmentProofToken;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getIntegrationPublicKey() {
        return integrationPublicKey;
    }

    public void setIntegrationPublicKey(String integrationPublicKey) {
        this.integrationPublicKey = integrationPublicKey;
    }

    public String getEnrollmentProofToken() {
        return enrollmentProofToken;
    }

    public void setEnrollmentProofToken(String enrollmentProofToken) {
        this.enrollmentProofToken = enrollmentProofToken;
    }

}