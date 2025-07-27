/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentResponse
 * Description: Response DTO for enrollment data.
 */

package org.ezkey.enrollment.domain;

import java.time.LocalDateTime;

/**
 * Response DTO for enrollment data.
 * <p>
 * This DTO represents enrollment information returned by the API,
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
public class EnrollmentResponse {

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
     * Public key for the device.
     * Used for device authentication verification.
     */
    private String devicePublicKey;

    /**
     * Unique code for enrollment verification.
     * Used for enrollment verification step.
     */
    private String deviceProofToken;

    /**
     * Timestamp when the enrollment was created.
     * Used for audit trails and sorting purposes.
     */
    private LocalDateTime createdAt;

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Integer getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(Integer integrationId) {
        this.integrationId = integrationId;
    }

    public String getEnrollmentName() {
        return enrollmentName;
    }

    public void setEnrollmentName(String enrollmentName) {
        this.enrollmentName = enrollmentName;
    }

    public Boolean getEnrollmentRead() {
        return enrollmentRead;
    }

    public void setEnrollmentRead(Boolean enrollmentRead) {
        this.enrollmentRead = enrollmentRead;
    }

    public Boolean getEnrollmentVerified() {
        return enrollmentVerified;
    }

    public void setEnrollmentVerified(Boolean enrollmentVerified) {
        this.enrollmentVerified = enrollmentVerified;
    }

    public Boolean getEnrollmentActive() {
        return enrollmentActive;
    }

    public void setEnrollmentActive(Boolean enrollmentActive) {
        this.enrollmentActive = enrollmentActive;
    }

    public Integer getEnrollmentChallenge() {
        return enrollmentChallenge;
    }

    public void setEnrollmentChallenge(Integer enrollmentChallenge) {
        this.enrollmentChallenge = enrollmentChallenge;
    }

    public Boolean getAuthAttemptChallengeRequired() {
        return authAttemptChallengeRequired;
    }

    public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
        this.authAttemptChallengeRequired = authAttemptChallengeRequired;
    }

    public String getIntegrationPublicKey() {
        return integrationPublicKey;
    }

    public void setIntegrationPublicKey(String integrationPublicKey) {
        this.integrationPublicKey = integrationPublicKey;
    }

    public String getDevicePublicKey() {
        return devicePublicKey;
    }

    public void setDevicePublicKey(String devicePublicKey) {
        this.devicePublicKey = devicePublicKey;
    }

    public String getDeviceProofToken() {
        return deviceProofToken;
    }

    public void setDeviceProofToken(String deviceProofToken) {
        this.deviceProofToken = deviceProofToken;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}