/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: SelfRevocationNotAllowedException
 * Description: Thrown when an administrator attempts to revoke or deactivate their own
 *              MFA enrollment, which would lock them out of the system.
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when an administrator attempts to revoke or deactivate their own MFA enrollment.
 *
 * <p>Allowing self-revocation would create a self-inflicted lockout: the administrator's bearer
 * token would be immediately invalidated (enrollment status check in {@code
 * AdminTokenValidationService}), and without a valid enrollment, the admin cannot log in again
 * without a recovery code. This is an irreversible or operationally disruptive outcome that must be
 * prevented.
 *
 * <p>This guard applies to <b>all admin types</b>:
 *
 * <ul>
 *   <li>Global Admins cannot revoke their own enrollment.
 *   <li>Tenant Admins cannot revoke their own enrollment.
 * </ul>
 *
 * <p>If an admin wishes to reset their own MFA enrollment (e.g., after device loss), the correct
 * flow is the recovery code path: {@code POST /api/v1/admin/enrollments/reset}.
 *
 * <p><b>HTTP Status:</b> 403 Forbidden
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI {@code
 * https://ezkey.io/problems/enrollment/self-revocation-not-allowed}.
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/enrollment/self-revocation-not-allowed",
 *   "title": "Self-Revocation Not Allowed",
 *   "status": 403,
 *   "detail": "Cannot revoke your own MFA enrollment. Use the recovery flow to reset it.",
 *   "path": "/api/v1/enrollments/42/revoke"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class SelfRevocationNotAllowedException extends RuntimeException {

  /**
   * Constructs a SelfRevocationNotAllowedException with the specified detail message.
   *
   * @param message the detail message
   */
  public SelfRevocationNotAllowedException(String message) {
    super(message);
  }

  /**
   * Constructs a SelfRevocationNotAllowedException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public SelfRevocationNotAllowedException(String message, Throwable cause) {
    super(message, cause);
  }
}
