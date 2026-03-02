/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: EnrollmentInactiveException
 * Description: Thrown when an authentication attempt is rejected because the target enrollment
 *              is not active or not in VERIFIED status.
 */

package org.ezkey.exception;

/**
 * Exception thrown when an authentication attempt is rejected because the target enrollment is
 * inactive or not in {@code VERIFIED} status.
 *
 * <p>This exception is raised in two situations:
 *
 * <ul>
 *   <li>The enrollment {@code active} flag is {@code false} — the enrollment has been
 *       administratively deactivated or revoked.
 *   <li>The enrollment status is not {@code VERIFIED} — the enrollment has not completed the
 *       cryptographic binding process and cannot be used for authentication.
 * </ul>
 *
 * <p>This is a security gate: callers that know a valid {@code enrollmentId} but whose target
 * enrollment has been revoked or deactivated must be rejected with an explicit, auditable error
 * rather than silently failing or returning misleading results.
 *
 * <p><b>HTTP Status:</b> 403 Forbidden — the request is understood but the enrollment is not
 * permitted to participate in authentication.
 *
 * <p><b>Response Format:</b> RFC 9457 Problem Detail with type URI {@code
 * https://ezkey.io/problems/enrollment/enrollment-inactive}.
 *
 * <p><b>Example Response:</b>
 *
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/enrollment/enrollment-inactive",
 *   "title": "Enrollment Inactive",
 *   "status": 403,
 *   "detail": "Enrollment is not active for authentication.",
 *   "path": "/api/v1/auth-attempts"
 * }
 * }</pre>
 *
 * <p><b>SOC 2 Relevance:</b> CC7.1 — system monitoring; CC6.3 — logical access removal.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class EnrollmentInactiveException extends RuntimeException {

  /**
   * Constructs an EnrollmentInactiveException with the specified detail message.
   *
   * @param message the detail message describing why the enrollment is inactive
   */
  public EnrollmentInactiveException(String message) {
    super(message);
  }

  /**
   * Constructs an EnrollmentInactiveException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause
   */
  public EnrollmentInactiveException(String message, Throwable cause) {
    super(message, cause);
  }
}
