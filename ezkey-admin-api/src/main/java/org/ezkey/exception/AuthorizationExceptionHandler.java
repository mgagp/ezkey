/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: AuthorizationExceptionHandler
 * Description: Handles all authorization-related exceptions in the Ezkey Admin API.
 */

package org.ezkey.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.admin.exception.AdminLimitException;
import org.ezkey.admin.exception.AdminNotAllowedException;
import org.ezkey.admin.exception.TenantNotAllowedException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles all authorization-related exceptions in the Ezkey Admin REST API.
 *
 * <p><b>Responsibility:</b> This component intercepts authorization exceptions thrown during
 * operation permission checks and converts them into RFC 9457 ProblemDetail responses with
 * appropriate HTTP status codes.
 *
 * <p><b>Exceptions Handled (5 total):</b>
 *
 * <ul>
 *   <li><b>AdminNotAllowedException (400):</b> Administrator operation restricted (e.g.,
 *       self-deactivation)
 *   <li><b>AdminLimitException (400):</b> Operation violates configured admin limits
 *   <li><b>TenantNotAllowedException (400):</b> Tenant operation restricted (e.g., system tenant
 *       deactivation)
 *   <li><b>TenantInactiveException (403):</b> Target tenant is inactive
 *   <li><b>AuthorizationDeniedException (403):</b> Insufficient permissions via @PreAuthorize
 * </ul>
 *
 * <p><b>Response Format:</b> All responses conform to RFC 9457 (Problem Details for HTTP APIs) with
 * the following structure:
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/authorization/...",
 * "title": "Error Category",
 * "status": HTTP_STATUS_CODE,
 * "detail": "Specific error message",
 * "path": "/api/v1/admin/endpoint"
 * }
 * }</pre>
 *
 * <p><b>HTTP Status Codes:</b>
 *
 * <ul>
 *   <li>400 - Business rule violations (admin/tenant restrictions, limits)
 *   <li>403 - Permission issues (inactive tenant, access denied)
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
 *       https://ezkey.io/problems/authorization/{error-category}
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
 * @see AdminNotAllowedException
 * @see AdminLimitException
 * @see TenantNotAllowedException
 * @see TenantInactiveException
 * @see AuthorizationDeniedException
 */
@RestControllerAdvice
@Component
@Order(30)
public class AuthorizationExceptionHandler extends ExceptionHandlerBase {

  /**
   * Handles AdminNotAllowedException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when an administrator attempts a restricted operation (e.g., self-deactivation,
   * modifying own enrollment).
   *
   * <p><b>HTTP Status:</b> 400 Bad Request (Operation violates business rules)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authorization/admin-not-allowed",
   * "title": "Admin Operation Not Allowed",
   * "status": 400,
   * "detail": "Administrators cannot deactivate themselves",
   * "path": "/api/v1/admin/admins/123"
   * }
   * }</pre>
   *
   * @param ex the AdminNotAllowedException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(AdminNotAllowedException.class)
  public ResponseEntity<ProblemDetail> handleAdminNotAllowedException(
      AdminNotAllowedException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/authorization/admin-not-allowed",
        "Admin Operation Not Allowed",
        request);
  }

  /**
   * Handles AdminLimitException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when an operation would violate configured administrator limits (e.g., maximum
   * number of admins per tenant, maximum enrollments per admin).
   *
   * <p><b>HTTP Status:</b> 400 Bad Request (Limit violation)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authorization/admin-limit-violation",
   * "title": "Admin Limit Violation",
   * "status": 400,
   * "detail": "Maximum number of administrators reached",
   * "path": "/api/v1/admin/admins"
   * }
   * }</pre>
   *
   * @param ex the AdminLimitException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(AdminLimitException.class)
  public ResponseEntity<ProblemDetail> handleAdminLimitException(
      AdminLimitException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/authorization/admin-limit-violation",
        "Admin Limit Violation",
        request);
  }

  /**
   * Handles TenantNotAllowedException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when an administrator attempts a restricted tenant operation (e.g., deactivating
   * the system tenant, modifying tenant configuration).
   *
   * <p><b>HTTP Status:</b> 400 Bad Request (Operation violates business rules)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authorization/tenant-not-allowed",
   * "title": "Tenant Operation Not Allowed",
   * "status": 400,
   * "detail": "System tenant cannot be deactivated",
   * "path": "/api/v1/admin/tenants/1"
   * }
   * }</pre>
   *
   * @param ex the TenantNotAllowedException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(TenantNotAllowedException.class)
  public ResponseEntity<ProblemDetail> handleTenantNotAllowedException(
      TenantNotAllowedException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/authorization/tenant-not-allowed",
        "Tenant Operation Not Allowed",
        request);
  }

  /**
   * Handles TenantInactiveException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when an operation targets a resource belonging to an inactive tenant. This
   * prevents operations on disabled tenant resources.
   *
   * <p><b>HTTP Status:</b> 403 Forbidden (Access denied due to resource state)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authorization/tenant-inactive",
   * "title": "Tenant Inactive",
   * "status": 403,
   * "detail": "Cannot operate on resources in inactive tenant",
   * "path": "/api/v1/admin/tenants/456/admins"
   * }
   * }</pre>
   *
   * @param ex the TenantInactiveException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   * @since 2025
   */
  @ExceptionHandler(TenantInactiveException.class)
  public ResponseEntity<ProblemDetail> handleTenantInactiveException(
      TenantInactiveException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/authorization/tenant-inactive",
        "Tenant Inactive",
        request);
  }

  /**
   * Handles AuthorizationDeniedException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when Spring Security's method-level security (@PreAuthorize) denies access due to
   * insufficient permissions. This catches programmatic authorization failures.
   *
   * <p><b>HTTP Status:</b> 403 Forbidden (Permission denied)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/authorization/access-denied",
   * "title": "Access Denied",
   * "status": 403,
   * "detail": "Insufficient permissions to access this resource",
   * "path": "/api/v1/admin/system/config"
   * }
   * }</pre>
   *
   * @param ex the AuthorizationDeniedException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   * @since 2025
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAuthorizationDeniedException(
      AuthorizationDeniedException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/authorization/access-denied",
        "Access Denied",
        request);
  }
}
