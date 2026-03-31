/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminNoEnrollmentException
 * Description: Thrown when admin has no device enrollment for passwordless auth.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when an administrator has no device enrollment for passwordless authentication.
 *
 * <p>This exception is raised when authentication fails because:
 *
 * <ul>
 *   <li>The administrator has not enrolled any device
 *   <li>The administrator's enrollment is not verified/bound
 * </ul>
 *
 * <p>Passwordless authentication requires a device public key to be present.
 *
 * <p><b>HTTP Status:</b> 403 Forbidden
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/no-enrollment`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/no-enrollment",
 * "title": "No Device Enrollment",
 * "status": 403,
 * "detail": "No device enrollment found for passwordless authentication",
 * "instance": "/api/v1/admin/auth/login"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.http.ProblemDetail
 */
public class AdminNoEnrollmentException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs an AdminNoEnrollmentException with the specified detail message.
   *
   * @param message the detail message explaining the missing enrollment
   */
  public AdminNoEnrollmentException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminNoEnrollmentException with the specified detail message and cause.
   *
   * @param message the detail message explaining the missing enrollment
   * @param cause the underlying cause of the exception
   */
  public AdminNoEnrollmentException(String message, Throwable cause) {
    super(message, cause);
  }
}
