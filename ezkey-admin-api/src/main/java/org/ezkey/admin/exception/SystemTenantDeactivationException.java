/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: SystemTenantDeactivationException
 * Description: Exception thrown when attempting to deactivate a system tenant.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when attempting to deactivate a system tenant.
 *
 * <p>System tenants host global administrators and represent the organization hosting the Ezkey
 * instance. They cannot be deactivated through the API to ensure system integrity and prevent
 * accidental loss of administrative access.
 *
 * <p><b>Usage Example:</b>
 * <code>throw new SystemTenantDeactivationException()</code>
 *
 * <p><b>Exception Handling:</b> This exception is automatically caught by the centralized {@link
 * org.ezkey.exception.GlobalExceptionHandler} and converted to a standardized HTTP 400 Bad Request
 * response with appropriate error details.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.exception.GlobalExceptionHandler
 */
public class SystemTenantDeactivationException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs a new SystemTenantDeactivationException with a default error message.
   *
   * <p>The exception message clearly explains why the operation cannot be performed, helping both
   * developers and API consumers understand the constraint.
   */
  public SystemTenantDeactivationException() {
    super("Cannot deactivate system tenant. System tenant hosts global administrators and must remain active.");
  }
}
