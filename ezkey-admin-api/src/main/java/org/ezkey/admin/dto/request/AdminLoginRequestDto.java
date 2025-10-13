/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminLoginRequestDto
 * Description: Request DTO for administrator login.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for administrator login.
 * <p>
 * This DTO contains the credentials required for administrator authentication.
 * It includes username and password fields with appropriate validation.
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
public class AdminLoginRequestDto {

    /**
     * Administrator username.
     * <p>
     * This field is required and must not be blank.
     * </p>
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * Administrator password.
     * <p>
     * This field is required when authMode is "password" (default) or not specified.
     * Optional when authMode is "ezkey" (passwordless authentication).
     * </p>
     */
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    /**
     * Authentication mode.
     * <p>
     * Specifies the authentication method to use:
     * <ul>
     * <li><b>"password"</b> (default): Traditional password + optional MFA</li>
     * <li><b>"ezkey"</b>: Passwordless cryptographic authentication only</li>
     * </ul>
     * </p>
     * <p>
     * When "ezkey" mode is used, the password field is not required.
     * The admin must have passwordlessEnabled=true and a bound enrollment.
     * </p>
     */
    private String authMode;

    /**
     * Challenge requested flag for passwordless authentication.
     * <p>
     * When true and authMode is "ezkey", the authentication attempt will include
     * a 6-digit challenge code that must be verified on the device during approval.
     * This provides an additional security layer for high-security scenarios.
     * </p>
     * <p>
     * Only applies to passwordless authentication mode. Ignored for password-based auth.
     * </p>
     */
    private Boolean challengeRequested;

    /**
     * Default constructor for JSON deserialization.
     */
    public AdminLoginRequestDto() {
        // Default constructor
    }

    /**
     * Constructs a new login request with the specified credentials.
     *
     * @param username the administrator username
     * @param password the administrator password
     */
    public AdminLoginRequestDto(String username, String password) {
        this.username = username;
        this.password = password;
        this.authMode = "password"; // Default to password mode
    }

    /**
     * Constructs a new passwordless login request.
     *
     * @param username the administrator username
     * @param authMode the authentication mode ("ezkey" for passwordless)
     * @param challengeRequested whether to request challenge verification
     */
    public AdminLoginRequestDto(String username, String authMode, Boolean challengeRequested) {
        this.username = username;
        this.authMode = authMode;
        this.challengeRequested = challengeRequested;
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
     * Gets the password.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password.
     *
     * @param password the password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Gets the authentication mode.
     *
     * @return the authentication mode ("password" or "ezkey")
     */
    public String getAuthMode() {
        return authMode;
    }

    /**
     * Sets the authentication mode.
     *
     * @param authMode the authentication mode
     */
    public void setAuthMode(String authMode) {
        this.authMode = authMode;
    }

    /**
     * Gets the challenge requested flag.
     *
     * @return true if challenge verification is requested
     */
    public Boolean getChallengeRequested() {
        return challengeRequested;
    }

    /**
     * Sets the challenge requested flag.
     *
     * @param challengeRequested the challenge requested flag
     */
    public void setChallengeRequested(Boolean challengeRequested) {
        this.challengeRequested = challengeRequested;
    }

    /**
     * Returns a string representation of the login request.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "AdminLoginRequestDto{" +
                "username='" + username + '\'' +
                ", password='[PROTECTED]'" +
                ", authMode='" + authMode + '\'' +
                ", challengeRequested=" + challengeRequested +
                '}';
    }
}
