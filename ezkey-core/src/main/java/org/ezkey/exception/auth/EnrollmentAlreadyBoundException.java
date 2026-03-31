/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when an enrollment bind or pre-check finds the enrollment is no longer in {@code CREATED}
 * state (already bound or otherwise processed).
 *
 * <p>Message text is for server logs only; clients receive RFC 9457 Problem Details from the Auth
 * API without leaking this string.
 */
public class EnrollmentAlreadyBoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public EnrollmentAlreadyBoundException(String message) {
    super(message);
  }
}
