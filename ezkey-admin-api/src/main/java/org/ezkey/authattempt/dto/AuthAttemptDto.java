/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptDto
 * Description: Response DTO for authorization attempt data in admin API.
 */

package org.ezkey.authattempt.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for authorization attempt data in admin API.
 * <p>
 * This DTO represents the complete authorization attempt data for administrative
 * purposes through the admin API. It provides comprehensive information about
 * authentication attempts while excluding sensitive information like private keys
 * for security purposes.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by admin API endpoints to return authorization attempt
 * information to administrators for monitoring and management purposes.
 * </p>
 *
 * <p>
 * <b>Security Note:</b> This DTO excludes sensitive cryptographic material
 * and provides only the information necessary for administrative operations.
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
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 * @see AuthAttemptCreateRequestDto
 * @see AuthAttemptCreateResponseDto
 */
public class AuthAttemptDto {

    private Integer authAttemptId;

    private Integer enrollmentId;

    private Boolean authAttemptRead;

    private Boolean authAttemptAccepted;

    private Integer authAttemptChallenge;

    private String integrationProofToken;

    private String deviceProofToken;

    private LocalDateTime createdAt;

    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public Boolean getAuthAttemptRead() {
        return authAttemptRead;
    }

    public void setAuthAttemptRead(Boolean authAttemptRead) {
        this.authAttemptRead = authAttemptRead;
    }

    public Boolean getAuthAttemptAccepted() {
        return authAttemptAccepted;
    }

    public void setAuthAttemptAccepted(Boolean authAttemptAccepted) {
        this.authAttemptAccepted = authAttemptAccepted;
    }

    public Integer getAuthAttemptChallenge() {
        return authAttemptChallenge;
    }

    public void setAuthAttemptChallenge(Integer authAttemptChallenge) {
        this.authAttemptChallenge = authAttemptChallenge;
    }

    public String getIntegrationProofToken() {
        return integrationProofToken;
    }

    public void setIntegrationProofToken(String integrationProofToken) {
        this.integrationProofToken = integrationProofToken;
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