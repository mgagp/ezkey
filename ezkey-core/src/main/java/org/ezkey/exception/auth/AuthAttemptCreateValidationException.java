/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when an auth attempt create request is invalid or cannot be resolved to a single target
 * enrollment.
 *
 * <p>This covers client-correctable request issues such as missing identifiers, missing integration
 * context, unresolved user identifiers, ambiguous user identifiers, or inconsistent enrollment
 * references across the Admin API and Integration API create endpoints.
 */
public class AuthAttemptCreateValidationException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a validation exception for an invalid auth attempt create request.
   *
   * @param message operator-safe validation detail
   */
  public AuthAttemptCreateValidationException(String message) {
    super(message);
  }
}
