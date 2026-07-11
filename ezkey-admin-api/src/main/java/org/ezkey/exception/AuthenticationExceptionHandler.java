/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: AuthenticationExceptionHandler
 * Description: Handles all authentication-related exceptions in the Ezkey Admin API.
 */

package org.ezkey.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.admin.exception.AdminAccountInactiveException;
import org.ezkey.admin.exception.AdminAuthenticationException;
import org.ezkey.admin.exception.AdminAuthenticationExpiredException;
import org.ezkey.admin.exception.AdminAuthenticationRejectedException;
import org.ezkey.admin.exception.AdminAuthenticationTimeoutException;
import org.ezkey.admin.exception.AdminDeviceSignatureInvalidException;
import org.ezkey.admin.exception.AdminNoEnrollmentException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles all authentication-related exceptions in the Ezkey Admin REST API.
 *
 * <p><b>Responsibility:</b> This component intercepts authentication exceptions thrown during
 * login, enrollment, and password-less authentication flows and converts them into RFC 9457
 * ProblemDetail responses with appropriate HTTP status codes.
 *
 * <p><b>Exceptions Handled (7 total):</b>
 *
 * <ul>
 *   <li><b>AdminAuthenticationException (401):</b> Invalid credentials or login not permitted at
 *       {@code /login} (generic message for pre-auth failures — SEC-006)
 *   <li><b>AdminAccountInactiveException (403):</b> Reserved; login pre-auth failures are
 *       normalized in {@link org.ezkey.admin.service.AdminAuthService}
 *   <li><b>AdminNoEnrollmentException (403):</b> Reserved; login pre-auth failures are normalized
 *       in {@link org.ezkey.admin.service.AdminAuthService}
 *   <li><b>AdminAuthenticationExpiredException (400):</b> Authentication attempt expired
 *   <li><b>AdminAuthenticationRejectedException (400):</b> Device explicitly rejected the request
 *   <li><b>AdminDeviceSignatureInvalidException (400):</b> Device signature validation failed
 *   <li><b>AdminAuthenticationTimeoutException (408):</b> No device response within timeout period
 * </ul>
 *
 * <p><b>Response Format:</b> All responses conform to RFC 9457 (Problem Details for HTTP APIs) with
 * the following structure:
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authentication/...",
 * "title": "Error Category",
 * "status": HTTP_STATUS_CODE,
 * "detail": "Specific error message",
 * "path": "/api/v1/admin/auth/endpoint"
 * }
 * }</pre>
 *
 * <p><b>HTTP Status Codes:</b>
 *
 * <ul>
 *   <li>401 - Invalid credentials (authentication failed at verification stage)
 *   <li>403 - Account/enrollment issues (authentication blocked by state)
 *   <li>400 - Flow errors (expired, rejected, invalid signature)
 *   <li>408 - Timeout waiting for device response
 * </ul>
 *
 * <p><b>Integration:</b> This handler is registered as a Spring component and automatically picked
 * up by the @RestControllerAdvice scanning mechanism.
 *
 * <p><b>Design Notes:</b>
 *
 * <ul>
 *   <li>Each handler delegates response building to {@link ExceptionHandlerBase#buildProblemDetail}
 *       for consistency
 *   <li>Problem type URIs follow the convention:
 *       https://ezkey.io/problems/authentication/{error-category}
 *   <li>This allows future clients to programmatically identify error categories
 *   <li>All handlers are simple, focused, and easy to extend or maintain
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ExceptionHandlerBase
 * @see AdminAuthenticationException
 * @see AdminAccountInactiveException
 * @see AdminNoEnrollmentException
 * @see AdminAuthenticationExpiredException
 * @see AdminAuthenticationRejectedException
 * @see AdminDeviceSignatureInvalidException
 * @see AdminAuthenticationTimeoutException
 */
@RestControllerAdvice
@Component
@Order(20)
public class AuthenticationExceptionHandler extends ExceptionHandlerBase {

  /**
   * Handles AdminAuthenticationException and returns HTTP 401 Unauthorized.
   *
   * <p>Triggered when authentication fails due to invalid credentials (wrong username, invalid
   * device, or device rejection).
   *
   * <p><b>HTTP Status:</b> 401 Unauthorized
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/invalid-credentials",
   * "title": "Invalid Credentials",
   * "status": 401,
   * "detail": "Invalid username or password",
   * "path": "/api/v1/admin/auth/login"
   * }
   * }</pre>
   *
   * @param ex the AdminAuthenticationException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 401 status
   * @since 2025
   */
  @ExceptionHandler(AdminAuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleAdminAuthenticationException(
      AdminAuthenticationException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.UNAUTHORIZED,
        "https://ezkey.io/problems/authentication/invalid-credentials",
        "Invalid Credentials",
        request);
  }

  /**
   * Handles AdminAccountInactiveException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when an administrator account or tenant is inactive, preventing authentication.
   *
   * <p><b>HTTP Status:</b> 403 Forbidden
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/account-inactive",
   * "title": "Account Inactive",
   * "status": 403,
   * "detail": "Account has been deactivated",
   * "path": "/api/v1/admin/auth/login"
   * }
   * }</pre>
   *
   * @param ex the AdminAccountInactiveException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   * @since 2025
   */
  @ExceptionHandler(AdminAccountInactiveException.class)
  public ResponseEntity<ProblemDetail> handleAdminAccountInactiveException(
      AdminAccountInactiveException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/authentication/account-inactive",
        "Account Inactive",
        request);
  }

  /**
   * Handles AdminNoEnrollmentException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when an administrator has no verified device enrollment for password-less
   * authentication.
   *
   * <p><b>HTTP Status:</b> 403 Forbidden
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/no-enrollment",
   * "title": "No Device Enrollment",
   * "status": 403,
   * "detail": "No device enrollment found for passwordless authentication",
   * "path": "/api/v1/admin/auth/login"
   * }
   * }</pre>
   *
   * @param ex the AdminNoEnrollmentException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   * @since 2025
   */
  @ExceptionHandler(AdminNoEnrollmentException.class)
  public ResponseEntity<ProblemDetail> handleAdminNoEnrollmentException(
      AdminNoEnrollmentException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/authentication/no-enrollment",
        "No Device Enrollment",
        request);
  }

  /**
   * Handles AdminAuthenticationExpiredException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when an authentication attempt expires (timeout exceeded or superseded by a newer
   * attempt).
   *
   * <p><b>HTTP Status:</b> 400 Bad Request (Request is stale)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/auth-expired",
   * "title": "Authentication Expired",
   * "status": 400,
   * "detail": "Authentication attempt expired - please try again",
   * "path": "/api/v1/admin/auth/passwordless-wait"
   * }
   * }</pre>
   *
   * @param ex the AdminAuthenticationExpiredException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(AdminAuthenticationExpiredException.class)
  public ResponseEntity<ProblemDetail> handleAdminAuthenticationExpiredException(
      AdminAuthenticationExpiredException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/authentication/auth-expired",
        "Authentication Expired",
        request);
  }

  /**
   * Handles AdminAuthenticationRejectedException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when a device explicitly rejects an authentication request.
   *
   * <p><b>HTTP Status:</b> 400 Bad Request (User action rejected the request)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/auth-rejected",
   * "title": "Authentication Rejected",
   * "status": 400,
   * "detail": "Device rejected the authentication request",
   * "path": "/api/v1/admin/auth/passwordless-wait"
   * }
   * }</pre>
   *
   * @param ex the AdminAuthenticationRejectedException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(AdminAuthenticationRejectedException.class)
  public ResponseEntity<ProblemDetail> handleAdminAuthenticationRejectedException(
      AdminAuthenticationRejectedException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/authentication/auth-rejected",
        "Authentication Rejected",
        request);
  }

  /**
   * Handles AdminDeviceSignatureInvalidException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when device signature validation fails during authentication.
   *
   * <p><b>HTTP Status:</b> 400 Bad Request (Cryptographic validation failure)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/invalid-signature",
   * "title": "Invalid Signature",
   * "status": 400,
   * "detail": "Device signature validation failed",
   * "path": "/api/v1/admin/auth/passwordless-wait"
   * }
   * }</pre>
   *
   * @param ex the AdminDeviceSignatureInvalidException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(AdminDeviceSignatureInvalidException.class)
  public ResponseEntity<ProblemDetail> handleAdminDeviceSignatureInvalidException(
      AdminDeviceSignatureInvalidException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/authentication/invalid-signature",
        "Invalid Signature",
        request);
  }

  /**
   * Handles AdminAuthenticationTimeoutException and returns HTTP 408 Request Timeout.
   *
   * <p>Triggered when authentication times out waiting for a device response.
   *
   * <p><b>HTTP Status:</b> 408 Request Timeout
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authentication/auth-timeout",
   * "title": "Authentication Timeout",
   * "status": 408,
   * "detail": "No device response within timeout period",
   * "path": "/api/v1/admin/auth/passwordless-wait"
   * }
   * }</pre>
   *
   * @param ex the AdminAuthenticationTimeoutException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 408 status
   * @since 2025
   */
  @ExceptionHandler(AdminAuthenticationTimeoutException.class)
  public ResponseEntity<ProblemDetail> handleAdminAuthenticationTimeoutException(
      AdminAuthenticationTimeoutException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.REQUEST_TIMEOUT,
        "https://ezkey.io/problems/authentication/auth-timeout",
        "Authentication Timeout",
        request);
  }
}
