/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when a device calls respond while the auth attempt is still {@code PENDING} (not yet
 * claimed via pending). Protocol violation — distinct from terminal-state conflict (#844).
 *
 * <p>Message text is for server logs only.
 *
 * @since 2026
 */
public class AuthAttemptRespondBeforeReadException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public AuthAttemptRespondBeforeReadException(String message) {
    super(message);
  }
}
