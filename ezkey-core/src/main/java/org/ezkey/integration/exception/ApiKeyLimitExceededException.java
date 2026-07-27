/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.exception;

/** Raised when an integration has already reached its maximum number of active API keys. */
@SuppressWarnings("serial")
public class ApiKeyLimitExceededException extends RuntimeException {

  public ApiKeyLimitExceededException(String message) {
    super(message);
  }
}
