/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AuthenticationException
 * Description: Custom exception for authentication failures.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Custom exception thrown when admin authentication fails.
 *
 * <p>This exception is used to indicate various authentication failures such as:
 *
 * <ul>
 *   <li>Invalid credentials (username or password)
 *   <li>Inactive administrator account
 *   <li>Account locked due to excessive failed attempts
 *   <li>MFA validation failures (future)
 * </ul>
 *
 * <p><b>Usage:</b> This exception provides a clean separation between authentication logic and
 * error handling, making the code more readable and maintainable.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AuthenticationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs a new authentication exception with the specified detail message.
   *
   * @param message the detail message explaining the authentication failure
   */
  public AuthenticationException(String message) {
    super(message);
  }

  /**
   * Constructs a new authentication exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the authentication failure
   * @param cause the cause of the authentication failure
   */
  public AuthenticationException(String message, Throwable cause) {
    super(message, cause);
  }
}
