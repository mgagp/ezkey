/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: EnrollmentExceptionHandler
 * Description: Handles enrollment lifecycle exceptions in the Ezkey Admin API,
 *              returning RFC 9457 ProblemDetail responses.
 */

package org.ezkey.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.admin.exception.EnrollmentCannotBeDeletedException;
import org.ezkey.admin.exception.EnrollmentLinkedAsAdminException;
import org.ezkey.admin.exception.SelfRevocationNotAllowedException;
import org.ezkey.admin.exception.SystemIntegrationRevocationException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles enrollment lifecycle exceptions in the Ezkey Admin REST API.
 *
 * <p><b>Responsibility:</b> Intercepts exceptions thrown during enrollment revocation, deactivation
 * and reactivation operations and converts them into RFC 9457 ProblemDetail responses.
 *
 * <p><b>Exceptions Handled (5 total):</b>
 *
 * <ul>
 *   <li><b>EnrollmentInactiveException (403):</b> Auth attempt rejected — enrollment is not active
 *       or not in VERIFIED status.
 *   <li><b>SelfRevocationNotAllowedException (403):</b> Admin attempted to revoke or deactivate
 *       their own MFA enrollment.
 *   <li><b>SystemIntegrationRevocationException (403):</b> Bulk revocation targeted a system
 *       integration, which is not permitted.
 *   <li><b>EnrollmentCannotBeDeletedException (409):</b> Enrollment cannot be deleted because it
 *       has authentication history; revoke instead.
 *   <li><b>EnrollmentLinkedAsAdminException (409):</b> Enrollment cannot be deleted because it is
 *       linked to an administrator; use recovery flow to reset that administrator enrollment first.
 * </ul>
 *
 * <p><b>Response Format:</b> All responses conform to RFC 9457 (Problem Details for HTTP APIs).
 *
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/enrollment/...",
 *   "title": "...",
 *   "status": 403,
 *   "detail": "...",
 *   "path": "/api/v1/enrollments/42/revoke"
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ExceptionHandlerBase
 * @see EnrollmentInactiveException
 * @see SelfRevocationNotAllowedException
 * @see SystemIntegrationRevocationException
 * @see EnrollmentCannotBeDeletedException
 * @see EnrollmentLinkedAsAdminException
 */
@RestControllerAdvice
@Component
@Order(35)
public class EnrollmentExceptionHandler extends ExceptionHandlerBase {

  /**
   * Handles EnrollmentInactiveException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when an authentication attempt targets an enrollment that is either not active
   * ({@code active=false}) or not in {@code VERIFIED} status. This is a security gate that prevents
   * new auth attempts from being created against revoked or deactivated enrollments.
   *
   * <p><b>HTTP Status:</b> 403 Forbidden
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
   * @param ex the EnrollmentInactiveException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   */
  @ExceptionHandler(EnrollmentInactiveException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentInactiveException(
      EnrollmentInactiveException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/enrollment/enrollment-inactive",
        "Enrollment Inactive",
        request);
  }

  /**
   * Handles SelfRevocationNotAllowedException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when an administrator attempts to revoke or deactivate their own MFA enrollment,
   * which would cause a self-inflicted lockout.
   *
   * <p><b>HTTP Status:</b> 403 Forbidden
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
   * @param ex the SelfRevocationNotAllowedException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   */
  @ExceptionHandler(SelfRevocationNotAllowedException.class)
  public ResponseEntity<ProblemDetail> handleSelfRevocationNotAllowedException(
      SelfRevocationNotAllowedException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/enrollment/self-revocation-not-allowed",
        "Self-Revocation Not Allowed",
        request);
  }

  /**
   * Handles SystemIntegrationRevocationException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when a bulk revocation operation targets a system integration. System integrations
   * host all administrator MFA enrollments and cannot be bulk-revoked to prevent a complete system
   * lockout.
   *
   * <p><b>HTTP Status:</b> 403 Forbidden
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   *   "type": "https://ezkey.io/problems/enrollment/system-integration-revocation",
   *   "title": "System Integration Revocation Not Allowed",
   *   "status": 403,
   *   "detail": "Bulk revocation cannot be applied to a system integration.",
   *   "path": "/api/v1/integrations/1/enrollments/revoke-all"
   * }
   * }</pre>
   *
   * @param ex the SystemIntegrationRevocationException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   */
  @ExceptionHandler(SystemIntegrationRevocationException.class)
  public ResponseEntity<ProblemDetail> handleSystemIntegrationRevocationException(
      SystemIntegrationRevocationException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/enrollment/system-integration-revocation",
        "System Integration Revocation Not Allowed",
        request);
  }

  /**
   * Handles EnrollmentCannotBeDeletedException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when an enrollment has authentication history (or other dependent data) and cannot
   * be physically deleted. The client should use revoke instead.
   *
   * <p><b>HTTP Status:</b> 409 Conflict
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   *   "type": "https://ezkey.io/problems/enrollment/cannot-delete-with-history",
   *   "title": "Enrollment Cannot Be Deleted",
   *   "status": 409,
   *   "detail": "Enrollment cannot be deleted because it has authentication history. Revoke the enrollment instead.",
   *   "path": "/api/v1/enrollments/42"
   * }
   * }</pre>
   *
   * @param ex the EnrollmentCannotBeDeletedException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 409 status
   */
  @ExceptionHandler(EnrollmentCannotBeDeletedException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentCannotBeDeletedException(
      EnrollmentCannotBeDeletedException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/enrollment/cannot-delete-with-history",
        "Enrollment Cannot Be Deleted",
        request);
  }

  /**
   * Handles EnrollmentLinkedAsAdminException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when an enrollment cannot be deleted because it is linked to an administrator
   * ({@code ezkey_admin.enrollment_id}). The client should use the recovery flow to reset that
   * administrator enrollment first.
   *
   * <p><b>HTTP Status:</b> 409 Conflict
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   *   "type": "https://ezkey.io/problems/enrollment/cannot-delete-linked-as-admin",
   *   "title": "Enrollment Linked to Administrator",
   *   "status": 409,
   *   "detail": "Enrollment cannot be deleted because it is linked to an administrator. Use the recovery flow to reset that administrator enrollment first.",
   *   "path": "/api/v1/enrollments/42"
   * }
   * }</pre>
   *
   * @param ex the EnrollmentLinkedAsAdminException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 409 status
   */
  @ExceptionHandler(EnrollmentLinkedAsAdminException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentLinkedAsAdminException(
      EnrollmentLinkedAsAdminException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/enrollment/cannot-delete-linked-as-admin",
        "Enrollment Linked to Administrator",
        request);
  }
}
