/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import java.io.Serial;

/** Raised when an enrollment create request is structurally invalid. */
public class EnrollmentCreateValidationException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  public EnrollmentCreateValidationException(String message) {
    super(message);
  }
}
