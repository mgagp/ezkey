/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when enrollment verification fails due to invalid cryptographic or input validation.
 *
 * <p>Message text is for server logs only.
 */
public class EnrollmentVerifyFailedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public EnrollmentVerifyFailedException(String message) {
    super(message);
  }
}
