/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

/**
 * An {@code /enrollments} operation that does not apply to an administrator's MFA enrollment
 * (#1313): writing a contact email, or fetching the bind QR.
 *
 * <p>The administrator's email ({@code ezkey_admin.email}) is the single email of an administrator,
 * and {@code /admins/{id}/onboarding*} is the single source of an administrator's bind material.
 * Mapped to {@code 409} with the code {@code admin-enrollment}, the same code QR delivery already
 * uses for the same reason.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public class AdminEnrollmentOperationException extends RuntimeException {

  /** Typed code shared with the QR delivery refusal. */
  public static final String CODE = "admin-enrollment";

  private static final long serialVersionUID = 1L;

  /**
   * Builds the exception.
   *
   * @param message what was refused, for the problem detail and the audit row
   */
  public AdminEnrollmentOperationException(String message) {
    super(message);
  }
}
