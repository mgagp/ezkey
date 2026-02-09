/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminLimitException
 * Description: Thrown when an operation would violate admin limits.
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when an operation would violate configured administrator limits.
 *
 * <p>This exception is raised when an administrator operation would violate business rules related
 * to limits, such as:
 *
 * <ul>
 *   <li>Deactivating an admin would fall below the minimum required number of global admins
 *   <li>Creating an admin would exceed the maximum allowed number
 * </ul>
 *
 * <p><b>HTTP Status:</b> 400 Bad Request
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/admin-limit-violation`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/admin-limit-violation",
 * "title": "Admin Limit Violation",
 * "status": 400,
 * "detail": "Cannot deactivate global admin - would violate minimum limit of
 * 2",
 * "path": "/api/v1/admins/2"
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
public class AdminLimitException extends RuntimeException {

  /**
   * Constructs an AdminLimitException with the specified detail message.
   *
   * @param message the detail message
   */
  public AdminLimitException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminLimitException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public AdminLimitException(String message, Throwable cause) {
    super(message, cause);
  }
}
