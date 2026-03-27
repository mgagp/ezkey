/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when an authentication attempt is in a state that does not allow the requested operation
 * (e.g. superseded, expired).
 *
 * <p>Message text is for server logs only.
 */
public class AuthAttemptStateConflictException extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public AuthAttemptStateConflictException(String message) {
    super(message);
  }
}
