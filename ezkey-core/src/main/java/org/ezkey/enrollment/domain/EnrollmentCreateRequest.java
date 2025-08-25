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

public class EnrollmentCreateRequest {

    private Integer integrationId;

    private String name;

    private Boolean authAttemptChallengeRequired;

    public Integer getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(Integer integrationId) {
        this.integrationId = integrationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getAuthAttemptChallengeRequired() {
        return authAttemptChallengeRequired;
    }

    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

}