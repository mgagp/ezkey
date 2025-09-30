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
     * This field is required and must not be blank.
     * </p>
     */
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

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
     * Returns a string representation of the login request.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "AdminLoginRequestDto{" +
                "username='" + username + '\'' +
                ", password='[PROTECTED]'" +
                '}';
    }
}
