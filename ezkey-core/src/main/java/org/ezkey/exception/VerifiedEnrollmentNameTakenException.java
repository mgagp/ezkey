/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import java.io.Serial;

/**
 * Raised when enrollment creation targets a name that a VERIFIED enrollment already holds on the
 * same integration, active or deactivated.
 *
 * <p>Verify and the partial unique index {@code idx_enrollment_unique_verified_name} refuse a
 * second VERIFIED row with that name, so create refuses before an invitation leaves (#1404). The
 * existing enrollment id lets the operator open it: reactivate it, or revoke it to replace the
 * device.
 */
public class VerifiedEnrollmentNameTakenException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final Integer existingEnrollmentId;
  private final boolean existingActive;

  /**
   * Creates the exception.
   *
   * @param message operator-facing detail
   * @param existingEnrollmentId id of the VERIFIED enrollment that holds the name
   * @param existingActive whether that enrollment is active ({@code false}: deactivated)
   */
  public VerifiedEnrollmentNameTakenException(
      String message, Integer existingEnrollmentId, boolean existingActive) {
    super(message);
    this.existingEnrollmentId = existingEnrollmentId;
    this.existingActive = existingActive;
  }

  /**
   * Returns the id of the VERIFIED enrollment that holds the name.
   *
   * @return existing enrollment id
   */
  public Integer getExistingEnrollmentId() {
    return existingEnrollmentId;
  }

  /**
   * Returns whether the enrollment that holds the name is active.
   *
   * @return {@code true} when active, {@code false} when deactivated
   */
  public boolean isExistingActive() {
    return existingActive;
  }
}
