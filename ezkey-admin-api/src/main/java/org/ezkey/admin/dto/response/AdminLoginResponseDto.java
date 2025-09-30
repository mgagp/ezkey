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
                '}';
    }
}
