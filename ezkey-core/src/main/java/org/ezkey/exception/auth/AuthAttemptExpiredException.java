/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when a device responds to an authentication attempt after {@code expiresAt}.
 *
 * <p>Mapped to HTTP 409 with problem type {@code auth-attempt-expired}, distinct from superseded /
 * terminal state conflicts ({@link AuthAttemptStateConflictException}). Message text is for server
 * logs only.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public class AuthAttemptExpiredException extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to Auth API clients.
   *
   * @param message detail for operators and logs
   */
  public AuthAttemptExpiredException(String message) {
    super(message);
  }
}
