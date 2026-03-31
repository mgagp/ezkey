/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: TenantNotAllowedException
 * Description: Thrown when a tenant operation is not allowed (e.g., deactivating the system tenant).
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when a tenant operation is not allowed.
 *
 * <p>This exception is raised when an administrator attempts a restricted tenant operation, such
 * as:
 *
 * <ul>
 *   <li>Attempting to deactivate the system tenant
 *   <li>Attempting to modify the system tenant in restricted ways
 * </ul>
 *
 * <p><b>HTTP Status:</b> 400 Bad Request
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI {@code
 * https://ezkey.io/problems/tenant-not-allowed}
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/tenant-not-allowed",
 * "title": "Tenant Operation Not Allowed",
 * "status": 400,
 * "detail": "Cannot deactivate the system tenant",
 * "path": "/api/v1/tenants/1/deactivate"
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
public class TenantNotAllowedException extends RuntimeException {

  /**
   * Constructs a TenantNotAllowedException with the specified detail message.
   *
   * @param message the detail message
   */
  public TenantNotAllowedException(String message) {
    super(message);
  }

  /**
   * Constructs a TenantNotAllowedException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public TenantNotAllowedException(String message, Throwable cause) {
    super(message, cause);
  }
}
