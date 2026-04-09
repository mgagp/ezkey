/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when a pending or respond request fails validation or cryptographic checks.
 *
 * <p>{@link org.ezkey.authattempt.service.AuthAttemptRespondService} catches this type and returns
 * HTTP 200 with a signed FAILED business result for the respond contract. Other callers may let it
 * propagate to the Auth API {@code GlobalExceptionHandler} (typically HTTP 400). Message text is
 * for server logs only; clients must not rely on {@link #getMessage()} for security UX.
 */
public class AuthAttemptRequestFailedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public AuthAttemptRequestFailedException(String message) {
    super(message);
  }
}
