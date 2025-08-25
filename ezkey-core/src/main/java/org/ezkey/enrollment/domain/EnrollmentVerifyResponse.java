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

public class EnrollmentVerifyResponse {

    private boolean active;

    public EnrollmentVerifyResponse(){
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
