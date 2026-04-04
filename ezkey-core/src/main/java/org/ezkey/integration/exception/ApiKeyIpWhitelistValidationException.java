/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.exception;

import java.io.Serial;

/** Raised when an API key IP whitelist entry is not a valid IP address or CIDR range. */
public class ApiKeyIpWhitelistValidationException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  public ApiKeyIpWhitelistValidationException(String message) {
    super(message);
  }
}
