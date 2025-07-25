/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentResponseDto
 * Description: Response DTO for enrollment data in admin API.
 */

package org.ezkey.enrollment.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for enrollment data in admin API.
 * <p>
 * This DTO represents the complete enrollment information returned by the admin API
 * for administrative purposes. It provides comprehensive enrollment details including
 * status, configuration, and metadata while excluding sensitive cryptographic material
 * for security purposes.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by admin API endpoints to return enrollment information
 * to administrators for monitoring and management purposes. Contains all non-sensitive
 * data needed for enrollment administration.
 * </p>
 *
 * <p>
 * <b>Security Note:</b> This DTO excludes sensitive cryptographic keys and provides
 * only the information necessary for administrative operations.
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
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 * @see EnrollmentCreateRequestDto
 * @see EnrollmentCreateResponseDto
 */
public class EnrollmentResponseDto {

    /**
     * Unique identifier for the enrollment.
     * Auto-generated primary key from the database.
     */
    private Integer enrollmentId;

    /**
     * Integration identifier this enrollment belongs to.
     * Foreign key reference to the integration.
     */
    private Integer integrationId;

    /**
     * Human-readable name for the enrollment.
     * Used for display purposes in user interfaces.
     */
    private String enrollmentName;

    /**
     * Flag indicating if the enrollment has been read by the device.
     * Used to track enrollment status.
     */
    private Boolean enrollmentRead;

    /**
     * Flag indicating if the enrollment has been verified.
     * Used to track enrollment verification status.
     */
    private Boolean enrollmentVerified;

    /**
     * Flag indicating if the enrollment is currently active.
     * Used to enable/disable enrollment.
     */
    private Boolean enrollmentActive;

    /**
     * Challenge value for enrollment verification.
     * Used in the enrollment challenge-response process.
     */
    private Integer enrollmentChallenge;

    /**
     * Flag indicating if authentication attempts require challenge.
     * Used to configure authentication behavior.
     */
    private Boolean authAttemptChallengeRequired;

    /**
     * Public key for integration communication.
     * Used for verifying messages from the integration.
     */
    private String integrationPublicKey;

    /**
     * Public key for authentication attempts.
     * Used for device authentication verification.
     */
    private String authAttemptPublicKey;

    /**
     * Unique code for enrollment identification.
     * Used for enrollment lookup and verification.
     */
    private String enrollmentCode;

    /**
     * Timestamp when the enrollment was created.
     * Used for audit trails and sorting purposes.
     */
    private LocalDateTime createdAt;

    // Getters and Setters
    public Integer getEnrollmentId(){
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId){
        this.enrollmentId = enrollmentId;
    }

    public Integer getIntegrationId(){
        return integrationId;
    }

    public void setIntegrationId(Integer integrationId){
        this.integrationId = integrationId;
    }

    public String getEnrollmentName(){
        return enrollmentName;
    }

    public void setEnrollmentName(String enrollmentName){
        this.enrollmentName = enrollmentName;
    }

    public Boolean getEnrollmentRead(){
        return enrollmentRead;
    }

    public void setEnrollmentRead(Boolean enrollmentRead){
        this.enrollmentRead = enrollmentRead;
    }

    public Boolean getEnrollmentVerified(){
        return enrollmentVerified;
    }

    public void setEnrollmentVerified(Boolean enrollmentVerified){
        this.enrollmentVerified = enrollmentVerified;
    }

    public Boolean getEnrollmentActive(){
        return enrollmentActive;
    }

    public void setEnrollmentActive(Boolean enrollmentActive){
        this.enrollmentActive = enrollmentActive;
    }

    public Integer getEnrollmentChallenge(){
        return enrollmentChallenge;
    }

    public void setEnrollmentChallenge(Integer enrollmentChallenge){
        this.enrollmentChallenge = enrollmentChallenge;
    }

    public Boolean getAuthAttemptChallengeRequired(){
        return authAttemptChallengeRequired;
    }

    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired){
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

    public String getIntegrationPublicKey(){
        return integrationPublicKey;
    }

    public void setIntegrationPublicKey(String integrationPublicKey){
        this.integrationPublicKey = integrationPublicKey;
    }

    public String getAuthAttemptPublicKey(){
        return authAttemptPublicKey;
    }

    public void setAuthAttemptPublicKey(String authAttemptPublicKey){
        this.authAttemptPublicKey = authAttemptPublicKey;
    }

    public String getEnrollmentCode(){
        return enrollmentCode;
    }

    public void setEnrollmentCode(String enrollmentCode){
        this.enrollmentCode = enrollmentCode;
    }

    public LocalDateTime getCreatedAt(){
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt){
        this.createdAt = createdAt;
    }
}