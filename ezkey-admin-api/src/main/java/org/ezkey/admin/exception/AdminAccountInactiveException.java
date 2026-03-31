/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: AdminAccountInactiveException
 * Description: Thrown when an admin account or tenant is inactive.
 */

package org.ezkey.admin.exception;

import java.io.Serial;

/**
 * Exception thrown when an administrator account or tenant is inactive.
 *
 * <p>This exception is raised when authentication fails because:
 *
 * <ul>
 *   <li>The administrator account has been deactivated
 *   <li>The administrator's tenant has been deactivated
 * </ul>
 *
 * <p><b>HTTP Status:</b> 403 Forbidden
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI
 * `https://ezkey.io/problems/authentication/account-inactive`
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/account-inactive",
 * "title": "Account Inactive",
 * "status": 403,
 * "detail": "Account has been deactivated",
 * "instance": "/api/v1/admin/auth/login"
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
public class AdminAccountInactiveException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs an AdminAccountInactiveException with the specified detail message.
   *
   * @param message the detail message explaining why the account is inactive
   */
  public AdminAccountInactiveException(String message) {
    super(message);
  }

  /**
   * Constructs an AdminAccountInactiveException with the specified detail message and cause.
   *
   * @param message the detail message explaining why the account is inactive
   * @param cause the underlying cause of the exception
   */
  public AdminAccountInactiveException(String message, Throwable cause) {
    super(message, cause);
  }
}
