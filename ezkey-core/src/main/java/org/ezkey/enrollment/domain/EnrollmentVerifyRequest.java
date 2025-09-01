/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentVerifyRequest
 * Description: Domain request object for enrollment verification and device binding.
 */

package org.ezkey.enrollment.domain;

/**
 * Domain request object for enrollment verification and device binding.
 * <p>
 * This domain object represents the request data sent by devices to complete
 * the enrollment verification process. It contains the challenge response,
 * device cryptographic information, and signed proof tokens required to
 * establish the secure binding between device and enrollment.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by the EnrollmentService when devices submit
 * verification data to complete the enrollment process. The service layer
 * transforms API DTOs into this domain object for business logic processing,
 * challenge validation, and cryptographic verification.
 * </p>
 *
 * <p>
 * <b>Enrollment Flow:</b> This request represents the final step in the
 * enrollment process where the device proves its identity and establishes
 * cryptographic credentials. After successful verification, the enrollment
 * becomes active and ready for authentication operations.
 * </p>
 *
 * <p>
 * <b>Security Model:</b> Contains device public key and cryptographically
 * signed proof tokens that establish device authenticity and prevent
 * enrollment hijacking. The challenge response validates that the device
 * received the legitimate enrollment challenge from the creation step.
 * </p>
 *
 * <p>
 * <b>Cryptographic Binding:</b> The device public key establishes the
 * cryptographic identity for future authentication operations, while the
 * signed proof token demonstrates the device's ability to perform
 * cryptographic operations required for secure authentication.
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
 * @see org.ezkey.enrollment.service.EnrollmentService
 * @see org.ezkey.enrollment.domain.EnrollmentVerifyResponse
 * @see org.ezkey.enrollment.domain.EnrollmentCreateResponse
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
public class EnrollmentVerifyRequest {

    /**
     * Unique identifier of the enrollment being verified.
     * <p>
     * Must reference an existing enrollment created through the enrollment
     * creation process. This ID links the verification request to the
     * specific enrollment and ensures proper tracking throughout the
     * enrollment lifecycle.
     * </p>
     */
    private Integer enrollmentId;

    /**
     * Response to the enrollment challenge.
     * <p>
     * Numeric value that must match the expected response to the enrollment
     * challenge provided during enrollment creation. This validates that the
     * device received the legitimate enrollment challenge and prevents
     * unauthorized completion of enrollment attempts by ensuring only
     * devices with the correct challenge can proceed.
     * </p>
     */
    private Integer challengeResponse;

    /**
     * Response to the enrollment email challenge.
     * <p>
     * Numeric value that must match the expected response to the enrollment
     * email challenge sent via email when email challenge feature is enabled.
     * This provides additional identity validation during enrollment verification
     * and helps ensure the enrollee has access to the specified email address.
     * </p>
     */
    private Integer emailChallengeResponse;

    /**
     * Device's public key for cryptographic operations.
     * <p>
     * The device's public key that will be used for all future authentication
     * operations. This key establishes the cryptographic identity of the device
     * and enables the system to verify signatures from the device during
     * authentication attempts. Must be a valid cryptographic public key
     * in the expected format.
     * </p>
     */
    private String devicePublicKey;

    /**
     * Cryptographically signed enrollment proof token.
     * <p>
     * Contains the enrollment proof token that has been cryptographically
     * signed by the device using its private key. This signature provides
     * proof of the device's cryptographic capabilities and validates that
     * the device possesses the private key corresponding to the provided
     * public key, ensuring secure enrollment completion.
     * </p>
     */
    private String enrollmentProofTokenSigned;

    /**
     * Gets the unique identifier of the enrollment being verified.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Sets the unique identifier of the enrollment being verified.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the response to the enrollment challenge.
     *
     * @return the challenge response value
     */
    public Integer getChallengeResponse() {
        return challengeResponse;
    }

    /**
     * Sets the response to the enrollment challenge.
     *
     * @param challengeResponse the challenge response value to set
     */
    public void setChallengeResponse(Integer challengeResponse) {
        this.challengeResponse = challengeResponse;
    }

    /**
     * Gets the response to the enrollment email challenge.
     *
     * @return the email challenge response value
     */
    public Integer getEmailChallengeResponse() {
        return emailChallengeResponse;
    }

    /**
     * Sets the response to the enrollment email challenge.
     *
     * @param emailChallengeResponse the email challenge response value to set
     */
    public void setEmailChallengeResponse(Integer emailChallengeResponse) {
        this.emailChallengeResponse = emailChallengeResponse;
    }

    /**
     * Gets the device's public key for cryptographic operations.
     *
     * @return the device public key
     */
    public String getDevicePublicKey() {
        return devicePublicKey;
    }

    /**
     * Sets the device's public key for cryptographic operations.
     *
     * @param devicePublicKey the device public key to set
     */
    public void setDevicePublicKey(String devicePublicKey) {
        this.devicePublicKey = devicePublicKey;
    }

    /**
     * Gets the cryptographically signed enrollment proof token.
     *
     * @return the signed enrollment proof token
     */
    public String getEnrollmentProofTokenSigned() {
        return enrollmentProofTokenSigned;
    }

    /**
     * Sets the cryptographically signed enrollment proof token.
     *
     * @param enrollmentProofTokenSigned the signed enrollment proof token to set
     */
    public void setEnrollmentProofTokenSigned(String enrollmentProofTokenSigned) {
        this.enrollmentProofTokenSigned = enrollmentProofTokenSigned;
    }

}