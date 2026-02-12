/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: TenantInactiveException
 * Description: Thrown when an operation targets a resource belonging to an inactive tenant.
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when an operation targets a resource belonging to an inactive tenant.
 *
 * <p>This exception is raised when an attempt is made to create or modify resources for an inactive
 * tenant, such as:
 *
 * <ul>
 *   <li>Creating an integration for an inactive tenant
 *   <li>Creating an enrollment for an integration belonging to an inactive tenant
 *   <li>Creating an API key for an integration belonging to an inactive tenant
 * </ul>
 *
 * <p><b>HTTP Status:</b> 403 Forbidden
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI {@code
 * https://ezkey.io/problems/tenant-inactive}
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/tenant-inactive",
 * "title": "Tenant Inactive",
 * "status": 403,
 * "detail": "Tenant is inactive. Contact your Ezkey administrator.",
 * "path": "/api/v1/integrations"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.http.ProblemDetail
 */
public class TenantInactiveException extends RuntimeException {

  /**
   * Constructs a TenantInactiveException with the specified detail message.
   *
   * @param message the detail message
   */
  public TenantInactiveException(String message) {
    super(message);
  }

  /**
   * Constructs a TenantInactiveException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public TenantInactiveException(String message, Throwable cause) {
    super(message, cause);
  }
}
