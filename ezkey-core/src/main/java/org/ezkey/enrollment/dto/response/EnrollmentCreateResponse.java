/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentResponse
 * Description: Response DTO for enrollment data.
 */

package org.ezkey.enrollment.dto.response;

/**
 * Response DTO for created enrollment data.
 * <p>
 * This DTO represents enrollment information returned by the create API,
 * providing a clean separation between the domain entity and the API response.
 * It includes all necessary enrollment details for client consumption.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Enrollment API responses
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class EnrollmentCreateResponse {

    /**
     * Unique identifier for the enrollment.
     * Auto-generated primary key from the database.
     */
    private Integer enrollmentId;

    /**
     * Challenge value for enrollment verification.
     * Used in the enrollment challenge-response process.
     */
    private Integer enrollmentChallenge;

    // Getters and Setters
    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    public Integer getEnrollmentChallenge(){
        return enrollmentChallenge;
    }

    public void setEnrollmentChallenge(Integer enrollmentChallenge){
        this.enrollmentChallenge = enrollmentChallenge;
    }

}