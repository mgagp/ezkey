/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentBindResponse
 * Description: Domain object for enrollment binding information in the core domain.
 */

package org.ezkey.enrollment.domain;

public class EnrollmentCreateResponse {

    private Integer enrollmentId;

    private Integer enrollmentChallenge;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Integer getEnrollmentChallenge() {
        return enrollmentChallenge;
    }

    public void setEnrollmentChallenge(Integer enrollmentChallenge) {
        this.enrollmentChallenge = enrollmentChallenge;
    }

}