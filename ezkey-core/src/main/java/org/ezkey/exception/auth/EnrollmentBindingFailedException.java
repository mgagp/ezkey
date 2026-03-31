/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when enrollment binding cannot proceed due to invalid proof material, mismatch, or other
 * client-side input issues that must not be described in detail to the caller.
 *
 * <p>Message text is for server logs only.
 */
public class EnrollmentBindingFailedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public EnrollmentBindingFailedException(String message) {
    super(message);
  }
}
