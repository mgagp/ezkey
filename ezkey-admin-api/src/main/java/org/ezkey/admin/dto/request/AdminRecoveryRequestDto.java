/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminRecoveryRequestDto
 * Description: Request DTO for admin account recovery using recovery code.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for admin account recovery using recovery code.
 * <p>
 * Used when an administrator has lost access to their enrolled device and needs
 * to regain access using one of their single-use recovery codes. The recovery
 * code provides limited temporary access (30 minutes) to re-bind a new enrollment.
 * </p>
 * <p>
 * <b>Security:</b> Recovery codes are single-use and BCrypt hashed. Each successful
 * use removes the code from the administrator's recovery codes array.
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
public class AdminRecoveryRequestDto {

    /**
     * Administrator username.
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    private String username;

    /**
     * Recovery code in format XXX-XXX-XXX.
     * <p>
     * Recovery codes are 9-character alphanumeric codes (excluding ambiguous
     * characters O/0, I/1) separated by dashes for easy entry.
     * </p>
     */
    @NotBlank(message = "Recovery code is required")
    @Pattern(regexp = "^[A-Z2-9]{3}-[A-Z2-9]{3}-[A-Z2-9]{3}$", 
             message = "Recovery code must be in format XXX-XXX-XXX")
    private String recoveryCode;

    /**
     * Default constructor for JSON deserialization.
     */
    public AdminRecoveryRequestDto() {
        // Default constructor
    }

    /**
     * Constructs a new recovery request.
     *
     * @param username the administrator username
     * @param recoveryCode the recovery code
     */
    public AdminRecoveryRequestDto(String username, String recoveryCode) {
        this.username = username;
        this.recoveryCode = recoveryCode;
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
     * Gets the recovery code.
     *
     * @return the recovery code
     */
    public String getRecoveryCode() {
        return recoveryCode;
    }

    /**
     * Sets the recovery code.
     *
     * @param recoveryCode the recovery code
     */
    public void setRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }

    /**
     * Returns a string representation of the recovery request.
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "AdminRecoveryRequestDto{" +
                "username='" + username + '\'' +
                ", recoveryCode='[PROTECTED]'" +
                '}';
    }
}

