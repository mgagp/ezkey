/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.exception;

import java.io.Serial;

/** Raised when an API key update is rejected (for example, the key is revoked). */
public class ApiKeyUpdateValidationException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  public ApiKeyUpdateValidationException(String message) {
    super(message);
  }
}
