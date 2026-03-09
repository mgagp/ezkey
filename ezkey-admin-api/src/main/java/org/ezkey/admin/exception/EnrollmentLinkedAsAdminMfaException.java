/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: EnrollmentLinkedAsAdminMfaException
 * Description: Thrown when an enrollment cannot be deleted because it is linked as an
 *              administrator's MFA. The client should use the recovery flow to reset that admin's MFA first.
 */

package org.ezkey.admin.exception;

/**
 * Exception thrown when an enrollment cannot be deleted because it is linked as an administrator's
 * MFA (ezkey_admin.mfa_enrollment_id).
 *
 * <p>Deleting such an enrollment would either cause a foreign key constraint violation or Hibernate
 * transient reference errors. This exception is thrown before attempting deletion so the API
 * returns RFC 9457 Problem Detail instead of an internal error.
 *
 * <p><b>HTTP Status:</b> 409 Conflict
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI {@code
 * https://ezkey.io/problems/enrollment/cannot-delete-linked-as-admin-mfa}.
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/enrollment/cannot-delete-linked-as-admin-mfa",
 *   "title": "Enrollment Linked as Admin MFA",
 *   "status": 409,
 *   "detail": "Enrollment cannot be deleted because it is linked as an administrator's MFA. Use the recovery flow to reset that admin's MFA first.",
 *   "path": "/api/v1/enrollments/42"
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
public class EnrollmentLinkedAsAdminMfaException extends RuntimeException {

  /**
   * Constructs an EnrollmentLinkedAsAdminMfaException with the specified detail message.
   *
   * @param message the detail message
   */
  public EnrollmentLinkedAsAdminMfaException(String message) {
    super(message);
  }

  /**
   * Constructs an EnrollmentLinkedAsAdminMfaException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public EnrollmentLinkedAsAdminMfaException(String message, Throwable cause) {
    super(message, cause);
  }
}
