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

    private Boolean authAttemptResponded;

    private Boolean authAttemptValid;

    private Boolean authAttemptAccepted;

    private Integer authAttemptChallenge;

    private String authAttemptProofToken;

    private String deviceProofTokenValid;

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

    public Boolean getAuthAttemptResponded() {
        return authAttemptResponded;
    }

    public void setAuthAttemptResponded(Boolean authAttemptResponded) {
        this.authAttemptResponded = authAttemptResponded;
    }

    public Boolean getAuthAttemptValid() {
        return authAttemptValid;
    }

    public void setAuthAttemptValid(Boolean authAttemptValid) {
        this.authAttemptValid = authAttemptValid;
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

    public String getAuthAttemptProofToken() {
        return authAttemptProofToken;
    }

    public void setAuthAttemptProofToken(String authAttemptProofToken) {
        this.authAttemptProofToken = authAttemptProofToken;
    }

    public String getDeviceProofTokenValid() {
        return deviceProofTokenValid;
    }

    public void setDeviceProofTokenValid(String deviceProofTokenValid) {
        this.deviceProofTokenValid = deviceProofTokenValid;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

}