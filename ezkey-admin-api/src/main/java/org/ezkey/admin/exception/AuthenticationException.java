/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AuthenticationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Operator/audit detail when {@link #getMessage()} is a client-safe generic string (SEC-006 /
   * SEC-024 anti-enumeration). Equals the client message when no separate internal detail was
   * supplied.
   */
  private final String internalDetail;

  /**
   * Constructs a new authentication exception with the specified detail message.
   *
   * @param message the detail message explaining the authentication failure
   */
  public AuthenticationException(String message) {
    this(message, message, null);
  }

  /**
   * Constructs a client-safe exception that retains a distinct internal reason for logs and audit.
   *
   * @param clientSafeMessage message safe to return to unauthenticated callers
   * @param internalDetail distinct reason for logs and structured audit ({@code reason_code}
   *     mapping)
   */
  public AuthenticationException(String clientSafeMessage, String internalDetail) {
    this(clientSafeMessage, internalDetail, null);
  }

  /**
   * Constructs a new authentication exception with the specified detail message and cause.
   *
   * @param message the detail message explaining the authentication failure
   * @param cause the cause of the authentication failure
   */
  public AuthenticationException(String message, Throwable cause) {
    this(message, message, cause);
  }

  private AuthenticationException(
      String clientSafeMessage, String internalDetail, Throwable cause) {
    super(clientSafeMessage, cause);
    this.internalDetail =
        internalDetail != null && !internalDetail.isBlank() ? internalDetail : clientSafeMessage;
  }

  /**
   * Returns the internal failure detail for logs and audit (may differ from {@link #getMessage()}).
   *
   * @return internal detail string; never {@code null}
   */
  public String getInternalDetail() {
    return internalDetail;
  }
}
