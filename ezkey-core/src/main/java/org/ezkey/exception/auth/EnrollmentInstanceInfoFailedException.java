/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when enrolled instance-info cannot be returned due to invalid proof material or missing
 * signing capability. Message text is for server logs only (anti-enumeration).
 *
 * @since 2026
 */
public class EnrollmentInstanceInfoFailedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public EnrollmentInstanceInfoFailedException(String message) {
    super(message);
  }
}
