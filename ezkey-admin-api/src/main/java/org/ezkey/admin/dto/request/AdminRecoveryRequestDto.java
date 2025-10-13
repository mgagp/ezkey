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
 *
 * <p>Used when an administrator has lost access to their enrolled device and needs to regain access
 * using one of their single-use recovery codes. The recovery code provides limited temporary access
 * (30 minutes) to re-bind a new enrollment.
 *
 * <p><b>Security:</b> Recovery codes are single-use and BCrypt hashed. Each successful use removes
 * the code from the administrator's recovery codes array.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AdminRecoveryRequestDto {

  /** Administrator username. */
  @NotBlank(message = "Username is required")
  @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
  private String username;

  /**
   * Recovery code in format XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX.
   *
   * <p>Recovery codes are 32-digit codes (0-9) separated by dashes into 8 groups of 4 digits. This
   * provides 106 bits of entropy (paranoia-level security).
   *
   * <p>Example: 1234-5678-9012-3456-7890-1234-5678-9012
   */
  @NotBlank(message = "Recovery code is required")
  @Pattern(
      regexp = "^\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}-\\d{4}$",
      message = "Recovery code must be 32 digits in format XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX")
  private String recoveryCode;

  /** Default constructor for JSON deserialization. */
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
    return "AdminRecoveryRequestDto{"
        + "username='"
        + username
        + '\''
        + ", recoveryCode='[PROTECTED]'"
        + '}';
  }
}
