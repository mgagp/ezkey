/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminAuthenticationException
 * Description: Thrown when admin authentication fails due to invalid credentials.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when administrator authentication fails due to invalid credentials.
 *
 * <p>This exception is raised when authentication fails because of:
 *
 * <ul>
 *   <li>Invalid or non-existent username
 *   <li>Invalid device credentials
 *   <li>Device rejection of authentication request
 * </ul>
 *
 * <p><b>HTTP Status:</b> 401 Unauthorized
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/invalid-credentials`
 *
 * <p><b>Example Responses:</b>
 *
 * <pre>{@code
 * // Case 1: Invalid username
 * {
 * "type": "https://ezkey.io/problems/authentication/invalid-credentials",
 * "title": "Invalid Credentials",
 * "status": 401,
 * "detail": "Invalid username or password",
 * "instance": "/api/v1/admin/auth/login"
 * }
 *
 * // Case 2: Device rejection
 * {
 * "type": "https://ezkey.io/problems/authentication/invalid-credentials",
 * "title": "Invalid Credentials",
 * "status": 401,
 * "detail": "Device rejected authentication",
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
public class AdminAuthenticationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs an AdminAuthenticationException with the specified detail message.
   *
   * @param message the detail message explaining the authentication failure
   */
  public AdminAuthenticationException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminAuthenticationException with the specified detail message and cause.
   *
   * @param message the detail message explaining the authentication failure
   * @param cause the underlying cause of the exception
   */
  public AdminAuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
