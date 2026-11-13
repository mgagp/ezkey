/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception.auth;

import java.io.Serial;

/**
 * Thrown when an Auth API response that must be integration-signed cannot be signed because the
 * enrollment's integration private key is missing or blank. Maps to HTTP 503 at respond time — the
 * server must never emit an unsigned HTTP 200 decision body (#844). Keys are per enrollment; there
 * is no application-startup validation for them. Callers rely on the outer respond transaction to
 * roll back.
 *
 * <p>Message text is for server logs only.
 *
 * @since 2026
 */
public class IntegrationSigningUnavailableException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates an exception for logging; message is not exposed to API clients.
   *
   * @param message detail for operators and logs
   */
  public IntegrationSigningUnavailableException(String message) {
    super(message);
  }
}
