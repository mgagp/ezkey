/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
 *
 * <p>Used when an administrator has lost access to their enrolled device and needs to regain access
 * using one of their single-use recovery codes. The recovery code provides limited temporary access
 * (30 minutes) to re-bind a new enrollment.
 *
 * <p><b>Security:</b> Recovery codes are single-use and BCrypt hashed. Each successful use removes
 * the code from the administrator's recovery codes array.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param username Administrator username
 * @param recoveryCode Recovery code in format XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
 * @author Ezkey contributors
 * @since 2025
 */
public record AdminRecoveryRequestDto(
    @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,
    @NotBlank(message = "Recovery code is required")
        @Pattern(
            regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}$",
            message =
                "Recovery code must be 32 digits in format XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX")
        String recoveryCode) {
  /**
   * Returns a string representation of the recovery request.
   *
   * @return string representation with protected recovery code
   */
  @Override
  public String toString() {
    return "AdminRecoveryRequestDto{"
        + "username='"
        + username
        + '\''
        + ", recoveryCode='[PROTECTED]'"
        + '}';
  }
}
