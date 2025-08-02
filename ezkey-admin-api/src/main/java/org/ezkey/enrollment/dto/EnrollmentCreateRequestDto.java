/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentCreateRequestDto
 * Description: Request DTO for creating enrollments in admin API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for creating enrollments in admin API.
 * <p>
 * This DTO represents the request data needed to create a new enrollment
 * through the admin API. An enrollment links a user device to an integration
 * and provides the foundation for MFA authentication.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by administrators to create enrollments that will
 * later be bound to mobile devices. The created enrollment generates codes and
 * challenges that mobile devices use to complete the enrollment process.
 * </p>
 *
 * <p>
 * <b>Fields:</b>
 * <ul>
 * <li><b>integrationId:</b> The integration this enrollment belongs to</li>
 * <li><b>name:</b> Human-readable name for the enrollment (e.g., "John's iPhone")</li>
 * <li><b>authAttemptChallengeRequired:</b> Whether challenges are required for auth attempts</li>
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
 * @see org.ezkey.enrollment.domain.EnrollmentCreateRequest
 * @see EnrollmentCreateResponseDto
 */
@Schema(description = "Request DTO for creating new enrollments")
public class EnrollmentCreateRequestDto {

    /**
     * The integration ID to which this enrollment belongs.
     * Must reference an existing and active integration.
     */
    @Schema(description = "The integration ID to which this enrollment belongs", 
            example = "1", 
            required = true)
    private Integer integrationId;

    /**
     * Human-readable name for the enrollment.
     * Helps identify the device or user associated with this enrollment.
     */
    @Schema(description = "Human-readable name for the enrollment", 
            example = "John's iPhone", 
            required = true)
    private String name;

    /**
     * Indicates whether authentication attempts require challenge validation.
     * When true, auth attempts will include additional challenge data for verification.
     */
    @Schema(description = "Whether authentication attempts require challenge validation", 
            example = "true")
    private Boolean authAttemptChallengeRequired;

    /**
     * Gets the integration ID.
     *
     * @return the integration ID
     */
    public Integer getIntegrationId(){
        return integrationId;
    }

    /**
     * Sets the integration ID.
     *
     * @param integrationId the integration ID to set
     */
    public void setIntegrationId(Integer integrationId){
        this.integrationId = integrationId;
    }

    /**
     * Gets the enrollment name.
     *
     * @return the enrollment name
     */
    public String getName(){
        return name;
    }

    /**
     * Sets the enrollment name.
     *
     * @param name the enrollment name to set
     */
    public void setName(String name){
        this.name = name;
    }

    /**
     * Gets whether authentication attempt challenges are required.
     *
     * @return true if challenges are required, false otherwise
     */
    public Boolean getAuthAttemptChallengeRequired(){
        return authAttemptChallengeRequired;
    }

    /**
     * Sets whether authentication attempt challenges are required.
     *
     * @param authAttemptChallengeRequired true if challenges are required, false otherwise
     */
    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired){
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

}