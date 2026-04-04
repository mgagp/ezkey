/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import java.io.Serial;

/** Raised when an active VERIFIED enrollment already exists for the same integration and name. */
public class ActiveVerifiedEnrollmentExistsException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public ActiveVerifiedEnrollmentExistsException(String message) {
    super(message);
  }
}
