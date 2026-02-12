/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminAuthenticationTimeoutException
 * Description: Thrown when authentication times out waiting for device response.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when authentication times out waiting for a device response.
 *
 * <p>This exception is raised when:
 *
 * <ul>
 *   <li>The device does not respond within the configured timeout period (typically 5 minutes)
 *   <li>The authentication wait window closes without receiving a device response
 *   <li>No mobile app is running to receive the authentication notification
 * </ul>
 *
 * <p><b>HTTP Status:</b> 408 Request Timeout
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/auth-timeout`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/auth-timeout",
 * "title": "Authentication Timeout",
 * "status": 408,
 * "detail": "No device response within timeout period",
 * "instance": "/api/v1/admin/auth/passwordless-wait",
 * "timeout_seconds": 300
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
public class AdminAuthenticationTimeoutException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs an AdminAuthenticationTimeoutException with the specified detail message.
   *
   * @param message the detail message explaining the timeout
   */
  public AdminAuthenticationTimeoutException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminAuthenticationTimeoutException with the specified detail message and cause.
   *
   * @param message the detail message explaining the timeout
   * @param cause the underlying cause of the exception
   */
  public AdminAuthenticationTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
