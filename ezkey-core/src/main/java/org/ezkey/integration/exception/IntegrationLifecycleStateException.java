/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.exception;

/** Raised when an integration lifecycle operation is not valid for the current state. */
public class IntegrationLifecycleStateException extends RuntimeException {

  public IntegrationLifecycleStateException(String message) {
    super(message);
  }
}
