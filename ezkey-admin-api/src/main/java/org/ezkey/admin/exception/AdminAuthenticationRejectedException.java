/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminAuthenticationRejectedException
 * Description: Thrown when a device explicitly rejects an authentication request.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when a device explicitly rejects an authentication request.
 *
 * <p>This exception is raised when:
 *
 * <ul>
 *   <li>The user denies the authentication on their enrolled device
 *   <li>The device explicitly sends a rejection response
 * </ul>
 *
 * <p><b>HTTP Status:</b> 400 Bad Request (User action rejected the request)
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/auth-rejected`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/auth-rejected",
 * "title": "Authentication Rejected",
 * "status": 400,
 * "detail": "Device rejected the authentication request",
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
public class AdminAuthenticationRejectedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs an AdminAuthenticationRejectedException with the specified detail message.
   *
   * @param message the detail message explaining the rejection
   */
  public AdminAuthenticationRejectedException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminAuthenticationRejectedException with the specified detail message and cause.
   *
   * @param message the detail message explaining the rejection
   * @param cause the underlying cause of the exception
   */
  public AdminAuthenticationRejectedException(String message, Throwable cause) {
    super(message, cause);
  }
}
