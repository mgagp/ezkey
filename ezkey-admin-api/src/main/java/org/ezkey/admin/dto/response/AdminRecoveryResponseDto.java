/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AdminRecoveryResponseDto
 * Description: Response DTO for admin account recovery.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * Response DTO for admin account recovery.
 *
 * <p>Contains a temporary recovery token that grants limited access (30 minutes) for re-binding
 * enrollment to a new device. The recovery token can only be used for enrollment binding
 * operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @param success indicates if the recovery was successful
 * @param message response message
 * @param recoveryToken temporary recovery token for limited access (30 minutes validity)
 * @param expiresAt recovery token expiration timestamp
 * @param codesRemaining number of recovery codes remaining after this use
 * @param enrollmentId MFA enrollment ID to use with POST /api/v1/admin/enrollments/reset (null on
 *     error)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminRecoveryResponseDto(
    Boolean success,
    String message,
    String recoveryToken,
    OffsetDateTime expiresAt,
    Integer codesRemaining,
    Integer enrollmentId) {

  /**
   * Constructor for successful recovery response.
   *
   * @param recoveryToken the temporary recovery token
   * @param expiresAt the token expiration time
   * @param codesRemaining number of codes remaining
   * @param enrollmentId MFA enrollment ID for the reset step
   */
  public AdminRecoveryResponseDto(
      String recoveryToken,
      OffsetDateTime expiresAt,
      Integer codesRemaining,
      Integer enrollmentId) {
    this(
        true,
        "Recovery successful. Token valid for 30 minutes. Re-bind enrollment immediately.",
        recoveryToken,
        expiresAt,
        codesRemaining,
        enrollmentId);
  }

  /**
   * Constructor for error recovery response.
   *
   * @param message the error message
   */
  public AdminRecoveryResponseDto(String message) {
    this(false, message, null, null, null, null);
  }

  /**
   * Factory method for success response.
   *
   * @param recoveryToken the temporary recovery token
   * @param expiresAt the token expiration time
   * @param codesRemaining number of codes remaining
   * @param enrollmentId MFA enrollment ID for reset
   * @return a success response
   */
  public static AdminRecoveryResponseDto success(
      String recoveryToken,
      OffsetDateTime expiresAt,
      Integer codesRemaining,
      Integer enrollmentId) {
    return new AdminRecoveryResponseDto(recoveryToken, expiresAt, codesRemaining, enrollmentId);
  }

  /**
   * Factory method for error response.
   *
   * @param message the error message
   * @return an error response
   */
  public static AdminRecoveryResponseDto error(String message) {
    return new AdminRecoveryResponseDto(message);
  }

  /**
   * Returns a string representation of the recovery response.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "AdminRecoveryResponseDto[success=%s, recoveryToken=%s, codesRemaining=%s, enrollmentId=%s]"
        .formatted(
            success, recoveryToken != null ? "[PROTECTED]" : "null", codesRemaining, enrollmentId);
  }
}
