/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.exception;

import java.io.Serial;

/**
 * Raised when an integration create request cannot be processed due to invalid administrator
 * context or configuration.
 *
 * <p>Extends {@link IllegalArgumentException} so layered exception handling can treat it as a
 * client-correctable validation failure where appropriate.
 */
public class IntegrationCreateValidationException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  public IntegrationCreateValidationException(String message) {
    super(message);
  }
}
