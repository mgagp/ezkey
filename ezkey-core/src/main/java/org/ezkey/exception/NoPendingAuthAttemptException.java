/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: NoPendingAuthAttemptException
 * Description: Exception thrown when no pending authentication attempt is found for an enrollment.
 */

package org.ezkey.exception;

/**
 * Exception thrown when no pending authentication attempt is found for an enrollment.
 *
 * <p>This exception represents a normal state in MFA systems where devices poll for pending
 * authentication attempts. The absence of a pending attempt is not an error condition but a normal
 * operational state that should be handled gracefully.
 *
 * <p><b>Usage Context:</b> Used by the AuthAttemptService when a device polls for pending
 * authentication attempts but none are found. This allows the controller to return appropriate HTTP
 * status codes (204 No Content) instead of error codes.
 *
 * <p><b>MFA Context:</b> In pull-based MFA systems, devices regularly poll for pending
 * authentication requests. The absence of pending requests is expected and should not be treated as
 * an error condition.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.service.AuthAttemptService
 */
public class NoPendingAuthAttemptException extends RuntimeException {

  /**
   * Constructs a new NoPendingAuthAttemptException with the specified detail message.
   *
   * @param message the detail message explaining why no pending attempt was found
   */
  public NoPendingAuthAttemptException(String message) {
    super(message);
  }

  /**
   * Constructs a new NoPendingAuthAttemptException with the specified detail message and cause.
   *
   * @param message the detail message explaining why no pending attempt was found
   * @param cause the cause of the exception
   */
  public NoPendingAuthAttemptException(String message, Throwable cause) {
    super(message, cause);
  }
}
