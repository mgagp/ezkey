/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: InitialGlobalAdminProperties
 * Description: Configuration properties for initial global administrator creation with SOC 2 compliance.
 */

package org.ezkey.admin.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for initial global administrator creation.
 *
 * <p>These properties control the creation of the first global administrator during system
 * initialization. For SOC 2 compliance, the username must identify a specific individual (not a
 * generic account like "admin"), and an email address is required for audit trail purposes.
 *
 * <p><b>Configuration Example:</b>
 *
 * <pre>
 * ezkey.admin.initial.username=john.doe
 * ezkey.admin.initial.email=john.doe@example.com
 * ezkey.admin.initial.first-name=John
 * ezkey.admin.initial.last-name=Doe
 * </pre>
 *
 * <p><b>SOC 2 Compliance Requirements:</b>
 *
 * <ul>
 *   <li><b>Username:</b> Must identify a specific individual (NOT "admin" or generic)
 *   <li><b>Email:</b> Required for audit trail and accountability (CC6.1, CC7.2)
 *   <li><b>First Name:</b> Required for GLOBAL_ADMIN for SOC 2 compliance (CC6.1, CC7.2)
 *   <li><b>Last Name:</b> Required for GLOBAL_ADMIN for SOC 2 compliance (CC6.1, CC7.2)
 * </ul>
 *
 * <p><b>Validation Rules:</b>
 *
 * <ul>
 *   <li>Username cannot be "admin", "administrator", "root", or other generic identifiers
 *   <li>Email must be valid format
 *   <li>System will fail to start if requirements are not met
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConfigurationProperties(prefix = "ezkey.admin.initial")
@Validated
public class InitialGlobalAdminProperties {

  /**
   * Generic usernames that are not allowed for SOC 2 compliance.
   *
   * <p>These usernames are too generic and do not identify a specific individual, violating SOC 2
   * requirements for individual accountability.
   */
  private static final String[] GENERIC_USERNAMES = {
    "admin", "administrator", "root", "superuser", "super", "user", "test", "demo"
  };

  /**
   * Username for the initial global administrator.
   *
   * <p>This username must identify a specific individual for SOC 2 compliance. Generic usernames
   * like "admin" are not allowed.
   *
   * <p><b>Examples of valid usernames:</b> "john.doe", "jane.smith", "admin.john"
   *
   * <p><b>Examples of invalid usernames:</b> "admin", "administrator", "root"
   */
  @NotBlank(
      message =
          "Initial global admin username is REQUIRED for SOC 2 compliance. "
              + "Must identify a specific individual, not a generic account.")
  @Pattern(
      regexp = "^(?!(admin|administrator|root|superuser|super|user|test|demo)$).*",
      flags = Pattern.Flag.CASE_INSENSITIVE,
      message =
          "Username cannot be a generic identifier (admin, administrator, root, etc.) "
              + "for SOC 2 compliance. Must identify a specific individual.")
  private String username;

  /**
   * Email address for the initial global administrator.
   *
   * <p>Required for SOC 2 compliance (CC6.1, CC7.2) to ensure proper audit trail and
   * accountability. The email must be valid and unique.
   */
  @NotBlank(message = "Initial global admin email is REQUIRED for SOC 2 compliance (CC6.1, CC7.2).")
  @Email(message = "Initial global admin email must be a valid email address.")
  private String email;

  /**
   * First name of the initial global administrator.
   *
   * <p>Required for GLOBAL_ADMIN type for SOC 2 compliance (CC6.1, CC7.2) to ensure proper
   * identification and accountability. Used for audit trail and display purposes.
   */
  @NotBlank(message = "Initial global admin first name is REQUIRED for SOC 2 compliance.")
  private String firstName;

  /**
   * Last name of the initial global administrator.
   *
   * <p>Required for GLOBAL_ADMIN type for SOC 2 compliance (CC6.1, CC7.2) to ensure proper
   * identification and accountability. Used for audit trail and display purposes.
   */
  @NotBlank(message = "Initial global admin last name is REQUIRED for SOC 2 compliance.")
  private String lastName;

  /**
   * Gets the username for the initial global administrator.
   *
   * @return the username (must identify a specific individual)
   */
  public String getUsername() {
    return username;
  }

  /**
   * Sets the username for the initial global administrator.
   *
   * @param username the username (must identify a specific individual, not generic)
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Gets the email address for the initial global administrator.
   *
   * @return the email address (required for SOC 2 compliance)
   */
  public String getEmail() {
    return email;
  }

  /**
   * Sets the email address for the initial global administrator.
   *
   * @param email the email address (required for SOC 2 compliance)
   */
  public void setEmail(String email) {
    this.email = email;
  }

  /**
   * Gets the first name of the initial global administrator.
   *
   * @return the first name (required for SOC 2 compliance)
   */
  public String getFirstName() {
    return firstName;
  }

  /**
   * Sets the first name of the initial global administrator.
   *
   * @param firstName the first name (required for SOC 2 compliance)
   */
  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  /**
   * Gets the last name of the initial global administrator.
   *
   * @return the last name (required for SOC 2 compliance)
   */
  public String getLastName() {
    return lastName;
  }

  /**
   * Sets the last name of the initial global administrator.
   *
   * @param lastName the last name (required for SOC 2 compliance)
   */
  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  /**
   * Validates that the username is not generic.
   *
   * <p>This method performs additional validation beyond the pattern annotation to ensure SOC 2
   * compliance.
   *
   * @return true if username is valid (not generic), false otherwise
   */
  public boolean isUsernameValid() {
    if (username == null || username.isBlank()) {
      return false;
    }
    String lowerUsername = username.toLowerCase().trim();
    for (String generic : GENERIC_USERNAMES) {
      if (lowerUsername.equals(generic)) {
        return false;
      }
    }
    return true;
  }
}
