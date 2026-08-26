/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Configuration: InitialGlobalAdminProperties
 * Description: Configuration properties for initial global administrator creation with
 *              identifiable operator identity.
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
 * initialization. The username must identify a specific individual (not a generic account like
 * "admin"), and an email address is required for the operator-visible audit trail.
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
 * <p><b>Identifiable operator identity:</b>
 *
 * <ul>
 *   <li><b>Username:</b> Must identify a specific individual (NOT "admin" or generic)
 *   <li><b>Email:</b> Required for audit trail and accountability
 *   <li><b>First Name:</b> Required for GLOBAL_ADMIN identification
 *   <li><b>Last Name:</b> Required for GLOBAL_ADMIN identification
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
   * Generic usernames that are not allowed for identifiable operator identity.
   *
   * <p>These usernames are too generic and do not identify a specific individual.
   */
  private static final String[] GENERIC_USERNAMES = {
    "admin", "administrator", "root", "superuser", "super", "user", "test", "demo"
  };

  /**
   * Username for the initial global administrator.
   *
   * <p>This username must identify a specific individual. Generic usernames like "admin" are not
   * allowed.
   *
   * <p><b>Examples of valid usernames:</b> "john.doe", "jane.smith", "admin.john"
   *
   * <p><b>Examples of invalid usernames:</b> "admin", "administrator", "root"
   */
  @NotBlank(
      message =
          "Initial global admin username is required for identifiable operator identity. "
              + "Must identify a specific individual, not a generic account.")
  @Pattern(
      regexp = "^(?!(admin|administrator|root|superuser|super|user|test|demo)$).*",
      flags = Pattern.Flag.CASE_INSENSITIVE,
      message =
          "Username cannot be a generic identifier (admin, administrator, root, etc.). "
              + "Must identify a specific individual.")
  private String username;

  /**
   * Email address for the initial global administrator.
   *
   * <p>Required for identifiable Global Admin identity so the operator-visible audit trail can name
   * a person. The email must be valid and unique.
   */
  @NotBlank(message = "Initial global admin email is required for identifiable operator identity.")
  @Email(message = "Initial global admin email must be a valid email address.")
  private String email;

  /**
   * First name of the initial global administrator.
   *
   * <p>Required for GLOBAL_ADMIN type for identifiable operator identity. Used for audit trail and
   * display purposes.
   */
  @NotBlank(
      message = "Initial global admin first name is required for identifiable operator identity.")
  private String firstName;

  /**
   * Last name of the initial global administrator.
   *
   * <p>Required for GLOBAL_ADMIN type for identifiable operator identity. Used for audit trail and
   * display purposes.
   */
  @NotBlank(
      message = "Initial global admin last name is required for identifiable operator identity.")
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
   * @return the email address (required for identifiable operator identity)
   */
  public String getEmail() {
    return email;
  }

  /**
   * Sets the email address for the initial global administrator.
   *
   * @param email the email address (required for identifiable operator identity)
   */
  public void setEmail(String email) {
    this.email = email;
  }

  /**
   * Gets the first name of the initial global administrator.
   *
   * @return the first name (required for identifiable operator identity)
   */
  public String getFirstName() {
    return firstName;
  }

  /**
   * Sets the first name of the initial global administrator.
   *
   * @param firstName the first name (required for identifiable operator identity)
   */
  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  /**
   * Gets the last name of the initial global administrator.
   *
   * @return the last name (required for identifiable operator identity)
   */
  public String getLastName() {
    return lastName;
  }

  /**
   * Sets the last name of the initial global administrator.
   *
   * @param lastName the last name (required for identifiable operator identity)
   */
  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  /**
   * Validates that the username is not generic.
   *
   * <p>This method performs additional validation beyond the pattern annotation to reject generic
   * usernames.
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
