/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import java.io.Serial;

/**
 * Raised when the Ezkey system tenant row is missing from the database.
 *
 * <p>This indicates a deployment or migration problem (empty or partially seeded database), not a
 * client request error. Callers should map this to HTTP 500 and avoid exposing internal details to
 * API clients.
 */
public class SystemTenantNotConfiguredException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public SystemTenantNotConfiguredException(String message) {
    super(message);
  }
}
