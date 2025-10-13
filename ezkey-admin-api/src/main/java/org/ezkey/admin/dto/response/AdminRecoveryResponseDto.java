/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminRecoveryResponseDto
 * Description: Response DTO for admin account recovery.
 */

package org.ezkey.admin.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for admin account recovery.
 *
 * <p>Contains a temporary recovery token that grants limited access (30 minutes) for re-binding
 * enrollment to a new device. The recovery token can only be used for enrollment binding
 * operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AdminRecoveryResponseDto {

  /** Indicates if the recovery was successful. */
  private Boolean success;

  /**
   * Temporary recovery token for limited access.
   *
   * <p>This token is valid for 30 minutes and can only be used for enrollment binding operations.
   * It cannot access other admin endpoints.
   */
  private String recoveryToken;

  /** Recovery token expiration timestamp. */
  private LocalDateTime expiresAt;

  /** Response message. */
  private String message;

  /**
   * Number of recovery codes remaining after this use.
   *
   * <p>Informs the administrator how many codes they have left. When this reaches 0, they should
   * generate new codes.
   */
  private Integer codesRemaining;

  /** Default constructor for JSON serialization. */
  public AdminRecoveryResponseDto() {
    // Default constructor
  }

  /**
   * Constructs a successful recovery response.
   *
   * @param recoveryToken the temporary recovery token
   * @param expiresAt the token expiration time
   * @param codesRemaining number of codes remaining
   */
  public AdminRecoveryResponseDto(
      String recoveryToken, LocalDateTime expiresAt, Integer codesRemaining) {
    this.success = true;
    this.recoveryToken = recoveryToken;
    this.expiresAt = expiresAt;
    this.codesRemaining = codesRemaining;
    this.message =
        "Recovery successful. Token valid for 30 minutes. Re-bind enrollment immediately.";
  }

  /**
   * Constructs an error recovery response.
   *
   * @param message the error message
   */
  public AdminRecoveryResponseDto(String message) {
    this.success = false;
    this.message = message;
  }

  /**
   * Gets the success status.
   *
   * @return true if recovery was successful
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
   * Gets the recovery token.
   *
   * @return the recovery token
   */
  public String getRecoveryToken() {
    return recoveryToken;
  }

  /**
   * Sets the recovery token.
   *
   * @param recoveryToken the recovery token
   */
  public void setRecoveryToken(String recoveryToken) {
    this.recoveryToken = recoveryToken;
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
   * Gets the number of recovery codes remaining.
   *
   * @return codes remaining count
   */
  public Integer getCodesRemaining() {
    return codesRemaining;
  }

  /**
   * Sets the number of recovery codes remaining.
   *
   * @param codesRemaining codes remaining count
   */
  public void setCodesRemaining(Integer codesRemaining) {
    this.codesRemaining = codesRemaining;
  }

  /**
   * Returns a string representation of the recovery response.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "AdminRecoveryResponseDto{"
        + "success="
        + success
        + ", recoveryToken='"
        + (recoveryToken != null ? "[PROTECTED]" : "null")
        + '\''
        + ", expiresAt="
        + expiresAt
        + ", message='"
        + message
        + '\''
        + ", codesRemaining="
        + codesRemaining
        + '}';
  }
}
