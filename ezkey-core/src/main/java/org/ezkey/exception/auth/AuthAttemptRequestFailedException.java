/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when a pending or respond request fails validation or cryptographic checks.
 *
 * <p>Extends {@link IllegalArgumentException} so {@link
 * org.ezkey.authattempt.service.AuthAttemptRespondService} can return HTTP 200 with a signed FAILED
 * business result where that contract applies. Message text is for server logs only; clients must
 * not rely on {@link #getMessage()} for security UX.
 */
public class AuthAttemptRequestFailedException extends IllegalArgumentException {

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
