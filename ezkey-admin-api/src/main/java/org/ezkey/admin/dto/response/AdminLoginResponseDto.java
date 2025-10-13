/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminLoginResponseDto
 * Description: Response DTO for administrator login.
 */

package org.ezkey.admin.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for administrator login.
 * <p>
 * This DTO contains the authentication response including the bearer token
 * and administrator information after successful login.
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
 */
public class AdminLoginResponseDto {

    /**
     * Indicates if the authentication was successful.
     */
    private Boolean success;

    /**
     * Bearer token for subsequent API calls.
     */
    private String token;

    /**
     * Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN).
     */
    private String adminType;

    /**
     * Administrator username.
     */
    private String username;

    /**
     * Token expiration timestamp.
     */
    private LocalDateTime expiresAt;

    /**
     * Response message.
     */
    private String message;

    /**
     * Indicates if password change is required.
     * <p>
     * When true, the administrator must change their password
     * before being able to use other API endpoints.
     * </p>
     */
    private Boolean passwordChangeRequired;

    /**
     * Temporary token used to proceed with MFA validation when required.
     * <p>
     * Present when the system requires MFA and the login step issued
     * a temporary token instead of a bearer token.
     * </p>
     */
    private String tempToken;

    /**
     * Indicates if MFA is required to complete authentication.
     */
    private Boolean mfaRequired;

    /**
     * Authentication attempt ID for passwordless two-step flow.
     * <p>
     * Present when passwordless login is initiated with challengeRequested=true.
     * The client must use this ID along with the challengeCode to call
     * /passwordless-wait endpoint.
     * </p>
     */
    private Integer authAttemptId;

    /**
     * Challenge code for passwordless two-step flow.
     * <p>
     * A 6-digit code that the user must enter on their device during approval.
     * Also serves as proof of legitimate authentication initiation when calling
     * /passwordless-wait (anti-enumeration protection).
     * </p>
     */
    private Integer challengeCode;

    /**
     * Status of the authentication attempt.
     * <p>
     * Used in passwordless two-step flow to indicate authentication state:
     * <ul>
     * <li><b>"pending"</b> - Waiting for device approval</li>
     * <li><b>"accepted"</b> - Device approved (not used, returns token directly)</li>
     * <li><b>"rejected"</b> - Device rejected (not used, returns error)</li>
     * </ul>
     * </p>
     */
    private String status;

    /**
     * Default constructor for JSON serialization.
     */
    public AdminLoginResponseDto() {
        // Default constructor
    }

    /**
     * Constructs a successful login response.
     *
     * @param token the bearer token
     * @param adminType the administrator type
     * @param username the administrator username
     * @param expiresAt the token expiration time
     */
    public AdminLoginResponseDto(String token, String adminType, String username, LocalDateTime expiresAt) {
        this.success = true;
        this.token = token;
        this.adminType = adminType;
        this.username = username;
        this.expiresAt = expiresAt;
        this.message = "Authentication successful";
    }

    /**
     * Constructs an error login response.
     *
     * @param message the error message
     */
    public AdminLoginResponseDto(String message) {
        this.success = false;
        this.message = message;
        this.passwordChangeRequired = false;
    }

    /**
     * Constructs an error login response for password change requirement.
     *
     * @param message the error message
     * @param passwordChangeRequired true if password change is required
     */
    public AdminLoginResponseDto(String message, Boolean passwordChangeRequired) {
        this.success = false;
        this.message = message;
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Constructs a temp-token response for MFA flow.
     *
     * @param tempToken temporary token for MFA continuation
     * @param message informational message
     * @param expiresAt temp token expiry
     */
    public AdminLoginResponseDto(String tempToken, String message, LocalDateTime expiresAt) {
        this.success = true;
        this.mfaRequired = true;
        this.tempToken = tempToken;
        this.message = message;
        this.expiresAt = expiresAt;
    }

    /**
     * Gets the success status.
     *
     * @return true if authentication was successful
     */
    public Boolean getSuccess() {
        return success;
    }

    /**
     * Sets the success status.
     *
     * @param success the success status
     */
    public void setSuccess(Boolean success) {
        this.success = success;
    }

    /**
     * Gets the bearer token.
     *
     * @return the bearer token
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the bearer token.
     *
     * @param token the bearer token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Gets the administrator type.
     *
     * @return the administrator type
     */
    public String getAdminType() {
        return adminType;
    }

    /**
     * Sets the administrator type.
     *
     * @param adminType the administrator type
     */
    public void setAdminType(String adminType) {
        this.adminType = adminType;
    }

    /**
     * Gets the username.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username.
     *
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the token expiration time.
     *
     * @return the expiration time
     */
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the token expiration time.
     *
     * @param expiresAt the expiration time
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Gets the response message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the response message.
     *
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Gets the password change required status.
     *
     * @return true if password change is required
     */
    public Boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    /**
     * Sets the password change required status.
     *
     * @param passwordChangeRequired the password change required status
     */
    public void setPasswordChangeRequired(Boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    /**
     * Gets the temporary token for MFA.
     *
     * @return temp token string
     */
    public String getTempToken() {
        return tempToken;
    }

    /**
     * Sets the temporary token for MFA.
     *
     * @param tempToken temp token
     */
    public void setTempToken(String tempToken) {
        this.tempToken = tempToken;
    }

    /**
     * Gets the MFA required flag.
     *
     * @return true if MFA required
     */
    public Boolean getMfaRequired() {
        return mfaRequired;
    }

    /**
     * Sets the MFA required flag.
     *
     * @param mfaRequired mfa required flag
     */
    public void setMfaRequired(Boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }

    /**
     * Gets the authentication attempt ID.
     *
     * @return the auth attempt ID
     */
    public Integer getAuthAttemptId() {
        return authAttemptId;
    }

    /**
     * Sets the authentication attempt ID.
     *
     * @param authAttemptId the auth attempt ID
     */
    public void setAuthAttemptId(Integer authAttemptId) {
        this.authAttemptId = authAttemptId;
    }

    /**
     * Gets the challenge code.
     *
     * @return the challenge code
     */
    public Integer getChallengeCode() {
        return challengeCode;
    }

    /**
     * Sets the challenge code.
     *
     * @param challengeCode the challenge code
     */
    public void setChallengeCode(Integer challengeCode) {
        this.challengeCode = challengeCode;
    }

    /**
     * Gets the authentication status.
     *
     * @return the status
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the authentication status.
     *
     * @param status the status
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns a string representation of the login response.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "AdminLoginResponseDto{" +
                "success=" + success +
                ", token='" + (token != null ? "[PROTECTED]" : "null") + '\'' +
                ", adminType='" + adminType + '\'' +
                ", username='" + username + '\'' +
                ", expiresAt=" + expiresAt +
                ", message='" + message + '\'' +
                ", passwordChangeRequired=" + passwordChangeRequired +
                ", mfaRequired=" + mfaRequired +
                ", tempToken='" + (tempToken != null ? "[PROTECTED]" : "null") + '\'' +
                ", authAttemptId=" + authAttemptId +
                ", challengeCode=" + (challengeCode != null ? "[PROTECTED]" : "null") +
                ", status='" + status + '\'' +
                '}';
    }
}
