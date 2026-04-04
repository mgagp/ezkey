/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import java.io.Serial;

/** Raised when enrollment creation targets the protected system integration. */
public class SystemIntegrationEnrollmentCreationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public SystemIntegrationEnrollmentCreationException(String message) {
    super(message);
  }
}
