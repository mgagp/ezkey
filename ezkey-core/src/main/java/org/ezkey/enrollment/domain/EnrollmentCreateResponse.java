/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentCreateResponse
 * Description: Domain response object containing new enrollment initialization data.
 */

package org.ezkey.enrollment.domain;

/**
 * Domain response object containing new enrollment initialization data.
 * <p>
 * This domain object represents the response data returned by the service layer
 * after successfully creating a new enrollment. It provides the enrollment
 * identifier and challenge data required for the device to complete the
 * enrollment verification and binding process.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by the EnrollmentService after processing
 * enrollment creation requests. The service layer creates this response object
 * to provide the necessary enrollment initialization data for consumption by
 * API layers and client applications.
 * </p>
 *
 * <p>
 * <b>Enrollment Flow:</b> This response represents the first step outcome in
 * the device enrollment process. The enrollment ID and challenge provided here
 * are used by the device to proceed through verification and binding phases,
 * establishing the cryptographic relationship between device and enrollment.
 * </p>
 *
 * <p>
 * <b>Challenge Mechanism:</b> The enrollment challenge serves as a security
 * token that must be validated during the verification process. This ensures
 * that only legitimate enrollment attempts can proceed to device binding and
 * prevents unauthorized enrollment completions.
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
 * @see org.ezkey.enrollment.domain.EnrollmentCreateRequest
 * @see org.ezkey.enrollment.domain.EnrollmentVerifyRequest
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
public class EnrollmentCreateResponse {

    /**
     * Unique identifier of the newly created enrollment.
     * <p>
     * Auto-generated identifier that uniquely identifies this enrollment
     * within the system. This ID is used throughout the enrollment lifecycle
     * for verification, binding, and subsequent authentication operations.
     * Devices must reference this ID in all enrollment-related requests.
     * </p>
     */
    private Integer enrollmentId;

    /**
     * Challenge value for enrollment verification.
     * <p>
     * Numeric challenge token that must be validated during the enrollment
     * verification process. This challenge serves as a security mechanism
     * to ensure that only legitimate enrollment attempts can proceed to
     * device binding. The device must provide the correct response to this
     * challenge to complete the enrollment process.
     * </p>
     */
    private Integer enrollmentChallenge;

    /**
     * Gets the unique identifier of the newly created enrollment.
     *
     * @return the enrollment ID
     */
    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Sets the unique identifier of the newly created enrollment.
     *
     * @param enrollmentId the enrollment ID to set
     */
    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the challenge value for enrollment verification.
     *
     * @return the enrollment challenge token
     */
    public Integer getEnrollmentChallenge() {
        return enrollmentChallenge;
    }

    /**
     * Sets the challenge value for enrollment verification.
     *
     * @param enrollmentChallenge the enrollment challenge token to set
     */
    public void setEnrollmentChallenge(Integer enrollmentChallenge) {
        this.enrollmentChallenge = enrollmentChallenge;
    }

}