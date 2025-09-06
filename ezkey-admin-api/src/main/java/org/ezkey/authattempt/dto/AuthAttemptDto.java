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

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for authentication attempt data in admin API.
 * <p>
 * This DTO represents the complete authentication attempt data for administrative
 * purposes through the admin API. It provides comprehensive information about
 * authentication attempts while excluding sensitive information like private keys
 * for security purposes.
 * </p>
 *
 * <p>
 * <b>Usage Context:</b> Used by admin API endpoints to return authentication attempt
 * information to administrators for monitoring and management purposes. Contains
 * all non-sensitive data needed for authentication attempt administration.
 * </p>
 *
 * <p>
 * <b>Core Identification Fields:</b>
 * <ul>
 * <li><b>authAttemptId:</b> Unique identifier for the authentication attempt</li>
 * <li><b>enrollmentId:</b> Enrollment identifier this attempt belongs to</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Status Fields:</b>
 * <ul>
 * <li><b>authAttemptRead:</b> Flag indicating if the attempt has been read by the device</li>
 * <li><b>authAttemptResponded:</b> Flag indicating if the attempt has been responded to</li>
 * <li><b>authAttemptValid:</b> Flag indicating if the attempt is valid</li>
 * <li><b>authAttemptAccepted:</b> Flag indicating if the attempt was accepted</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Authentication Data:</b>
 * <ul>
 * <li><b>authAttemptChallenge:</b> Challenge value for the authentication attempt</li>
 * <li><b>authAttemptProofToken:</b> Proof token for the authentication attempt</li>
 * <li><b>deviceProofTokenValid:</b> Device proof token validation result</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Metadata:</b>
 * <ul>
 * <li><b>createdAt:</b> Timestamp when the attempt was created</li>
 * <li><b>expiresAt:</b> Timestamp when the attempt expires</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Security Note:</b> This DTO excludes sensitive cryptographic material
 * and provides only the information necessary for administrative operations.
 * All private keys are deliberately excluded for security purposes.
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
@Schema(description = "Response DTO containing complete authentication attempt information for administrative purposes")
public class AuthAttemptDto {

    /**
     * Unique identifier for the authentication attempt.
     * Auto-generated primary key from the database.
     */
    @Schema(description = "Unique identifier for the authentication attempt",example = "456")
    private Integer authAttemptId;

    /**
     * Enrollment identifier this authentication attempt belongs to.
     * Foreign key reference to the enrollment.
     */
    @Schema(description = "Enrollment identifier this authentication attempt belongs to",example = "123")
    private Integer enrollmentId;

    /**
     * Flag indicating if the authentication attempt has been read by the device.
     * Used to track the status of the authentication flow.
     */
    @Schema(description = "Flag indicating if the authentication attempt has been read by the device",example = "true")
    private Boolean authAttemptRead;

    /**
     * Flag indicating if the authentication attempt has been responded to.
     * Used to track whether the device has provided a response.
     */
    @Schema(description = "Flag indicating if the authentication attempt has been responded to",example = "false")
    private Boolean authAttemptResponded;

    /**
     * Flag indicating if the authentication attempt is valid.
     * Used to track the validity of the authentication attempt.
     */
    @Schema(description = "Flag indicating if the authentication attempt is valid",example = "true")
    private Boolean authAttemptValid;

    /**
     * Flag indicating if the authentication attempt was accepted.
     * Final result of the authentication process.
     */
    @Schema(description = "Flag indicating if the authentication attempt was accepted",example = "true")
    private Boolean authAttemptAccepted;

    /**
     * Challenge value for the authentication attempt.
     * Used in the challenge-response authentication process.
     */
    @Schema(description = "Challenge value for the authentication attempt",example = "789012")
    private Integer authAttemptChallenge;

    /**
     * Proof token for the authentication attempt.
     * Used for authentication verification.
     */
    @Schema(description = "Proof token for the authentication attempt",example = "EZK-XYZ789-ABC123")
    private String authAttemptProofToken;

    /**
     * Timestamp when the authentication attempt was created.
     * Used for auditing and tracking purposes.
     */
    @Schema(description = "Timestamp when the authentication attempt was created",example = "2025-01-27T10:30:00")
    private LocalDateTime createdAt;

    /**
     * Timestamp when the authentication attempt expires.
     * Used to determine if the authentication attempt is still valid.
     */
    @Schema(description = "Timestamp when the authentication attempt expires",example = "2025-01-27T10:35:00")
    private LocalDateTime expiresAt;

    /**
     * Gets the authentication attempt ID.
     *
     * @return the unique identifier for the authentication attempt
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the unique identifier for the authentication attempt to set
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the enrollment ID.
     *
     * @return the enrollment identifier this authentication attempt belongs to
     */
    public Integer getEnrollmentId() {
        return enrollmentId;
    }

    /**
     * Sets the enrollment ID.
     *
     * @param enrollmentId the enrollment identifier this authentication attempt belongs to
     */
    public void setEnrollmentId(Integer enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    /**
     * Gets the authentication attempt read status.
     *
     * @return true if the authentication attempt has been read by the device, false otherwise
     */
    public Boolean getAuthAttemptRead() {
        return authAttemptRead;
    }

    /**
     * Sets the authentication attempt read status.
     *
     * @param authAttemptRead true if the authentication attempt has been read by the device, false otherwise
     */
    public void setAuthAttemptRead(Boolean authAttemptRead) {
        this.authAttemptRead = authAttemptRead;
    }

    /**
     * Gets the authentication attempt responded status.
     *
     * @return true if the authentication attempt has been responded to, false otherwise
     */
    public Boolean getAuthAttemptResponded() {
        return authAttemptResponded;
    }

    /**
     * Sets the authentication attempt responded status.
     *
     * @param authAttemptResponded true if the authentication attempt has been responded to, false otherwise
     */
    public void setAuthAttemptResponded(Boolean authAttemptResponded) {
        this.authAttemptResponded = authAttemptResponded;
    }

    /**
     * Gets the authentication attempt validity status.
     *
     * @return true if the authentication attempt is valid, false otherwise
     */
    public Boolean getAuthAttemptValid() {
        return authAttemptValid;
    }

    /**
     * Sets the authentication attempt validity status.
     *
     * @param authAttemptValid true if the authentication attempt is valid, false otherwise
     */
    public void setAuthAttemptValid(Boolean authAttemptValid) {
        this.authAttemptValid = authAttemptValid;
    }

    /**
     * Gets the authentication attempt accepted status.
     *
     * @return true if the authentication attempt was accepted, false otherwise
     */
    public Boolean getAuthAttemptAccepted() {
        return authAttemptAccepted;
    }

    /**
     * Sets the authentication attempt accepted status.
     *
     * @param authAttemptAccepted true if the authentication attempt was accepted, false otherwise
     */
    public void setAuthAttemptAccepted(Boolean authAttemptAccepted) {
        this.authAttemptAccepted = authAttemptAccepted;
    }

    /**
     * Gets the authentication attempt challenge.
     *
     * @return the challenge value for the authentication attempt
     */
    public Integer getAuthAttemptChallenge() {
        return authAttemptChallenge;
    }

    /**
     * Sets the authentication attempt challenge.
     *
     * @param authAttemptChallenge the challenge value for the authentication attempt to set
     */
    public void setAuthAttemptChallenge(Integer authAttemptChallenge) {
        this.authAttemptChallenge = authAttemptChallenge;
    }

    /**
     * Gets the authentication attempt proof token.
     *
     * @return the proof token for the authentication attempt
     */
    public String getAuthAttemptProofToken() {
        return authAttemptProofToken;
    }

    /**
     * Sets the authentication attempt proof token.
     *
     * @param authAttemptProofToken the proof token for the authentication attempt to set
     */
    public void setAuthAttemptProofToken(String authAttemptProofToken) {
        this.authAttemptProofToken = authAttemptProofToken;
    }

    /**
     * Gets the creation timestamp.
     *
     * @return the timestamp when the authentication attempt was created
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation timestamp.
     *
     * @param createdAt the timestamp when the authentication attempt was created
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the expiration timestamp.
     *
     * @return the timestamp when the authentication attempt expires
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expiration timestamp.
     *
     * @param expiresAt the timestamp when the authentication attempt expires
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

}
