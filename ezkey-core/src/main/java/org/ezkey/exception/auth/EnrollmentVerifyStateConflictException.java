/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when enrollment verification cannot proceed due to enrollment state (including obfuscated
 * "not found" cases that must not be distinguished from conflicts for clients).
 *
 * <p>Message text is for server logs only.
 */
public class EnrollmentVerifyStateConflictException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public EnrollmentVerifyStateConflictException(String message) {
    super(message);
  }
}
