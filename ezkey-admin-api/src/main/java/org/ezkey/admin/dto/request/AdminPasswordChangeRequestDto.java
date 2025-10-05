/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminPasswordChangeRequestDto
 * Description: Request DTO for administrator password change.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for administrator password change.
 * <p>
 * This DTO contains the current and new passwords required for changing
 * an administrator's password. Both fields are validated for presence
 * and minimum length requirements.
 * </p>
 *
 * <p>
 * <b>Security Considerations:</b>
 * <ul>
 * <li>Current password is required to prevent unauthorized changes</li>
 * <li>New password must meet strength requirements (validated by PasswordValidator)</li>
 * <li>Passwords are transmitted over HTTPS to prevent interception</li>
 * <li>Old tokens are invalidated after successful password change</li>
 * </ul>
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
public class AdminPasswordChangeRequestDto {

    /**
     * Current password for verification.
     * <p>
     * This field is required to verify the administrator's identity
     * before allowing the password change.
     * </p>
     */
    @NotBlank(message = "Current password is required")
    @Size(min = 6, message = "Current password must be at least 6 characters")
    private String currentPassword;

    /**
     * New password to set.
     * <p>
     * This field must meet all password strength requirements
     * as defined in the PasswordValidator utility class.
     * </p>
     */
    @NotBlank(message = "New password is required")
    @Size(min = 12, max = 128, message = "New password must be between 12 and 128 characters")
    private String newPassword;

    /**
     * Default constructor for JSON deserialization.
     */
    public AdminPasswordChangeRequestDto() {
        // Default constructor
    }

    /**
     * Constructs a new password change request with the specified passwords.
     *
     * @param currentPassword the current password for verification
     * @param newPassword the new password to set
     */
    public AdminPasswordChangeRequestDto(String currentPassword, String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    /**
     * Gets the current password.
     *
     * @return the current password
     */
    public String getCurrentPassword() {
        return currentPassword;
    }

    /**
     * Sets the current password.
     *
     * @param currentPassword the current password
     */
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    /**
     * Gets the new password.
     *
     * @return the new password
     */
    public String getNewPassword() {
        return newPassword;
    }

    /**
     * Sets the new password.
     *
     * @param newPassword the new password
     */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    /**
     * Returns a string representation of the password change request.
     * <p>
     * Note: Passwords are never included in string representation for security.
     * </p>
     *
     * @return string representation
     */
    @Override
    public String toString() {
        return "AdminPasswordChangeRequestDto{" +
                "currentPassword='[PROTECTED]'" +
                ", newPassword='[PROTECTED]'" +
                '}';
    }
}

