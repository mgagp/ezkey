/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown for non-enumerating Integration API create request field errors (e.g. {@code ttlSeconds}
 * below the floor) that must map to the same HTTP 400 ProblemDetail as Bean Validation failures
 * ({@code @Valid}) and unreadable JSON (#895 Christophe nit).
 *
 * <p>Message text is for server logs only; clients receive a stable catalog detail.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public class AuthAttemptRequestInvalidException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates an exception with a stable log message for the TTL floor gate. */
  public AuthAttemptRequestInvalidException() {
    super("Auth attempt create request fields are invalid");
  }

  /**
   * Creates an exception with a custom log message.
   *
   * @param message detail for operators and logs (not copied to clients)
   */
  public AuthAttemptRequestInvalidException(String message) {
    super(message);
  }
}
