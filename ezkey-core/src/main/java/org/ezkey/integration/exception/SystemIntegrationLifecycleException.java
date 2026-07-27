/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.exception;

/** Raised when an operation tries to modify the lifecycle of the protected system integration. */
@SuppressWarnings("serial")
public class SystemIntegrationLifecycleException extends RuntimeException {

  public SystemIntegrationLifecycleException(String message) {
    super(message);
  }
}
