/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminDeviceSignatureInvalidException
 * Description: Thrown when device signature validation fails.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when device signature validation fails during authentication.
 *
 * <p>This exception is raised when:
 *
 * <ul>
 *   <li>The cryptographic signature from the device is invalid
 *   <li>The signature cannot be verified with the public key
 *   <li>The challenge response signature is incorrect
 * </ul>
 *
 * <p><b>HTTP Status:</b> 400 Bad Request (Cryptographic validation failure)
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/invalid-signature`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/invalid-signature",
 * "title": "Invalid Signature",
 * "status": 400,
 * "detail": "Device signature validation failed",
 * "instance": "/api/v1/admin/auth/passwordless-wait"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.http.ProblemDetail
 */
public class AdminDeviceSignatureInvalidException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs an AdminDeviceSignatureInvalidException with the specified detail message.
   *
   * @param message the detail message explaining the signature validation failure
   */
  public AdminDeviceSignatureInvalidException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminDeviceSignatureInvalidException with the specified detail message and cause.
   *
   * @param message the detail message explaining the signature validation failure
   * @param cause the underlying cause of the exception
   */
  public AdminDeviceSignatureInvalidException(String message, Throwable cause) {
    super(message, cause);
  }
}
