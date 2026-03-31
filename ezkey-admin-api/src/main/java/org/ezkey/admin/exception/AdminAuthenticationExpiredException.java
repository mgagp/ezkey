/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminAuthenticationExpiredException
 * Description: Thrown when an authentication attempt has expired.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when an authentication attempt has expired.
 *
 * <p>This exception is raised when:
 *
 * <ul>
 *   <li>The authentication attempt timeout has been exceeded
 *   <li>The authentication attempt has been superseded by a newer attempt
 *   <li>The device response window has closed
 * </ul>
 *
 * <p><b>HTTP Status:</b> 400 Bad Request (Request is stale)
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/auth-expired`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/auth-expired",
 * "title": "Authentication Expired",
 * "status": 400,
 * "detail": "Authentication attempt expired - please try again",
 * "instance": "/api/v1/admin/auth/passwordless-wait"
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
public class AdminAuthenticationExpiredException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs an AdminAuthenticationExpiredException with the specified detail message.
   *
   * @param message the detail message explaining why the authentication expired
   */
  public AdminAuthenticationExpiredException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminAuthenticationExpiredException with the specified detail message and cause.
   *
   * @param message the detail message explaining why the authentication expired
   * @param cause the underlying cause of the exception
   */
  public AdminAuthenticationExpiredException(String message, Throwable cause) {
    super(message, cause);
  }
}
