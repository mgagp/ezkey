/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentCreateResponseDto
 * Description: Response DTO for enrollment creation in admin API.
 */

package org.ezkey.enrollment.dto;

/**
 * Response DTO for enrollment creation in admin API.
 * <p>
 * This DTO represents the response data returned when an enrollment
 * is successfully created through the admin API. It contains the essential
 * information needed to identify and use the newly created enrollment.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Returned by admin API when creating enrollments.
 * The enrollment ID can be used for subsequent operations, and the challenge
 * can be used for enrollment verification processes.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>enrollmentId:</b> Unique identifier of the created enrollment</li>
 * <li><b>enrollmentChallenge:</b> Challenge number for enrollment verification</li>
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
 * @see org.ezkey.enrollment.domain.EnrollmentCreateResponse
 * @see EnrollmentCreateRequestDto
 */
public class EnrollmentCreateResponseDto {

    /**
     * Unique identifier of the created enrollment.
     * Used to reference this enrollment in subsequent operations.
     */
    private Integer enrollmentId;

    /**
     * Challenge number generated for enrollment verification.
     * Used during the enrollment binding and verification process.
     */
    private Integer enrollmentChallenge;

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
     * Gets the enrollment challenge.
     *
     * @return the enrollment challenge
     */
    public Integer getEnrollmentChallenge(){
        return enrollmentChallenge;
    }

    /**
     * Sets the enrollment challenge.
     *
     * @param enrollmentChallenge the enrollment challenge to set
     */
    public void setEnrollmentChallenge(Integer enrollmentChallenge){
        this.enrollmentChallenge = enrollmentChallenge;
    }

}