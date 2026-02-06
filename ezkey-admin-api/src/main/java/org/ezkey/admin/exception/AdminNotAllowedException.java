/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminNotAllowedException
 * Description: Thrown when an admin operation is not allowed (e.g., self-deactivation).
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when an admin operation is not allowed.
 *
 * <p>This exception is raised when an administrator attempts a restricted
 * operation, such as:
 * <ul>
 * <li>Attempting to deactivate their own account
 * <li>Attempting to modify self account in restricted ways
 * </ul>
 *
 * <p><b>HTTP Status:</b> 400 Bad Request
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/admin-not-allowed`
 *
 * <p><b>Example Response:</b>
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/admin-not-allowed",
 * "title": "Admin Operation Not Allowed",
 * "status": 400,
 * "detail": "Cannot deactivate your own account",
 * "path": "/api/v1/admins/1"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.http.ProblemDetail
 */
public class AdminNotAllowedException extends RuntimeException {

  /**
   * Constructs an AdminNotAllowedException with the specified detail message.
   *
   * @param message the detail message
   */
  public AdminNotAllowedException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminNotAllowedException with the specified detail message and
   * cause.
   *
   * @param message the detail message
   * @param cause   the cause
   */
  public AdminNotAllowedException(String message, Throwable cause) {
    super(message, cause);
  }
}
