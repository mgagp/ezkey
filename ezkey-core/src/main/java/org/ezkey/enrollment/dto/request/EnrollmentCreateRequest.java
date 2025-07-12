/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentCreateRequest
 * Description: Request DTO for enrollment creation.
 */

package org.ezkey.enrollment.dto.request;

/**
 * Request DTO for enrollment creation.
 * <p>
 * This DTO represents the data required to create a new enrollment,
 * providing a clean separation between the API request and the domain entity.
 * It contains only the necessary fields for enrollment creation.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 * <p>
 * <b>Usage:</b> Enrollment creation API requests
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class EnrollmentCreateRequest {
    private Integer integrationId;

    private String name;

    private Boolean authAttemptChallengeRequired;

    public Integer getIntegrationId(){
        return integrationId;
    }

    public void setIntegrationId(Integer integrationId){
        this.integrationId = integrationId;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public Boolean getAuthAttemptChallengeRequired(){
        return authAttemptChallengeRequired;
    }

    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired){
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

}