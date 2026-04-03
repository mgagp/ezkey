/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when wait request parameters are invalid for an authentication attempt wait operation.
 *
 * <p>This covers invalid timeout and polling combinations shared by the Admin API and Integration
 * API wait endpoints. Message text is operator-safe and may be returned in RFC 9457 details for
 * client-correctable input errors.
 */
public class AuthAttemptWaitValidationException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a validation exception for invalid wait parameters.
   *
   * @param message operator-safe validation detail
   */
  public AuthAttemptWaitValidationException(String message) {
    super(message);
  }
}
