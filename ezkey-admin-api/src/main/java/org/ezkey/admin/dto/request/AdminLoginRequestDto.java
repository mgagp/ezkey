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
 * Request DTO for administrator login (passwordless-only).
 *
 * <p>This DTO contains the credentials required for passwordless administrator authentication using
 * Ezkey's cryptographic authentication system. Passwords are not supported.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AdminLoginRequestDto {

  /**
   * Administrator username.
   *
   * <p>This field is required and must not be blank.
   */
  @NotBlank(message = "Username is required")
  @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
  private String username;

  /**
   * Challenge requested flag for passwordless authentication.
   *
   * <p>When true, the authentication attempt will include a 6-digit challenge code that must be
   * verified on the device during approval. This provides an additional security layer for
   * high-security scenarios.
   *
   * <p>If not specified, defaults to the admin's challenge_required setting.
   */
  private Boolean challengeRequested;

  /** Default constructor for JSON deserialization. */
  public AdminLoginRequestDto() {
    // Default constructor
  }

  /**
   * Constructs a new passwordless login request.
   *
   * @param username the administrator username
   * @param challengeRequested whether to request challenge verification
   */
  public AdminLoginRequestDto(String username, Boolean challengeRequested) {
    this.username = username;
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
    return "AdminLoginRequestDto{"
        + "username='"
        + username
        + '\''
        + ", challengeRequested="
        + challengeRequested
        + '}';
  }
}
