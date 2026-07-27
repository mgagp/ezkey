/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: EnrollmentCannotBeDeletedException
 * Description: Thrown when an enrollment cannot be deleted because it has dependent data
 *              (e.g. authentication history). The client should revoke instead.
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when an enrollment cannot be deleted because it has authentication history or
 * other dependent data.
 *
 * <p>Deletion is a physical remove; when an enrollment has linked auth attempts, the database would
 * raise a foreign key constraint violation. This exception is thrown before attempting deletion so
 * the API can return RFC 9457 Problem Detail instead of a generic constraint error.
 *
 * <p><b>HTTP Status:</b> 409 Conflict
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI {@code
 * https://ezkey.io/problems/enrollment/cannot-delete-with-history}.
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/enrollment/cannot-delete-with-history",
 *   "title": "Enrollment Cannot Be Deleted",
 *   "status": 409,
 *   "detail": "Enrollment cannot be deleted because it has authentication history. Revoke the enrollment instead.",
 *   "path": "/api/v1/enrollments/42"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SuppressWarnings("serial")
public class EnrollmentCannotBeDeletedException extends RuntimeException {

  /**
   * Constructs an EnrollmentCannotBeDeletedException with the specified detail message.
   *
   * @param message the detail message
   */
  public EnrollmentCannotBeDeletedException(String message) {
    super(message);
  }

  /**
   * Constructs an EnrollmentCannotBeDeletedException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public EnrollmentCannotBeDeletedException(String message, Throwable cause) {
    super(message, cause);
  }
}
