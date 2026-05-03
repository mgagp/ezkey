/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: GlobalExceptionHandler
 * Description: Global exception handler for the Integration API, providing RFC 9457 ProblemDetail
 *              responses for all handled exceptions.
 */

package org.ezkey.integration.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import org.ezkey.audit.integrity.AuditChainHeartbeatEvaluation;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.exception.audit.AuditChainHeartbeatDegradedException;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.ezkey.integration.exception.ApiKeyLimitExceededException;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for the Ezkey Integration REST API.
 *
 * <p>Provides RFC 9457 ProblemDetail responses for all exceptions thrown in the Integration API
 * layer. This handler ensures consistent, structured error responses for API key consumers
 * (machine-to- machine integrations).
 *
 * <p><b>Exceptions Handled:</b>
 *
 * <ul>
 *   <li><b>EnrollmentInactiveException (403):</b> Auth attempt rejected — enrollment is not active
 *       or not in VERIFIED status. Security-relevant event, logged at WARN level.
 *   <li><b>ResourceNotFoundException (404):</b> Requested resource does not exist.
 *   <li><b>IllegalArgumentException (400):</b> Invalid or inconsistent request parameters.
 *   <li><b>Exception (500):</b> Unhandled server-side error (catch-all).
 * </ul>
 *
 * <p><b>Response Format:</b> All responses conform to RFC 9457 (Problem Details for HTTP APIs):
 *
 * <pre>{@code
 * {
 *   "type": "https://ezkey.io/problems/...",
 *   "title": "...",
 *   "status": HTTP_STATUS_CODE,
 *   "detail": "...",
 *   "path": "/api/v1/..."
 * }
 * }</pre>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final AuditChainHeartbeatGuardService auditChainHeartbeatGuardService;

  /**
   * Creates the global Integration API exception handler.
   *
   * @param heartbeatGuardService guard used for heartbeat-degraded diagnostic context on 503
   *     responses
   */
  public GlobalExceptionHandler(AuditChainHeartbeatGuardService heartbeatGuardService) {
    this.auditChainHeartbeatGuardService = heartbeatGuardService;
  }

  /**
   * Handles EnrollmentInactiveException and returns HTTP 403 Forbidden.
   *
   * <p>Triggered when an Integration API client attempts to create an auth attempt for an
   * enrollment that is no longer active (deactivated or revoked). This is a security-relevant event
   * and is logged at WARN level. The caller possesses a valid API key and a known enrollmentId, but
   * the enrollment has been administratively disabled — this may indicate a stale integration or a
   * compromised API key attempting to access a revoked enrollment.
   *
   * @param ex the EnrollmentInactiveException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 403 status
   */
  @ExceptionHandler(EnrollmentInactiveException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentInactiveException(
      EnrollmentInactiveException ex, HttpServletRequest request) {
    logger.warn(
        "Integration API auth attempt blocked: enrollment inactive. Path: {}",
        request.getRequestURI());
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/enrollment/enrollment-inactive",
        "Enrollment Inactive",
        request);
  }

  /**
   * Handles ResourceNotFoundException and returns HTTP 404 Not Found.
   *
   * @param ex the ResourceNotFoundException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 404 status
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleResourceNotFoundException(
      ResourceNotFoundException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.NOT_FOUND,
        "https://ezkey.io/problems/resource/not-found",
        "Resource Not Found",
        request);
  }

  /**
   * Handles IllegalArgumentException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered by invalid or inconsistent request parameters such as mismatched enrollmentId and
   * userIdentifier, missing required fields, or enrollment not found.
   *
   * @param ex the IllegalArgumentException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 400 status
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
      IllegalArgumentException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/validation/invalid-argument",
        "Invalid Argument",
        request);
  }

  @ExceptionHandler(AuthAttemptCreateValidationException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptCreateValidationException(
      AuthAttemptCreateValidationException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/validation/auth-attempt-create-invalid",
        "Invalid Auth Attempt Create Request",
        request);
  }

  @ExceptionHandler(TenantInactiveException.class)
  public ResponseEntity<ProblemDetail> handleTenantInactiveException(
      TenantInactiveException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/authorization/tenant-inactive",
        "Tenant Inactive",
        request);
  }

  @ExceptionHandler(IntegrationLifecycleStateException.class)
  public ResponseEntity<ProblemDetail> handleIntegrationLifecycleStateException(
      IntegrationLifecycleStateException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/integration-lifecycle-state",
        "Integration Lifecycle State Conflict",
        request);
  }

  @ExceptionHandler(ApiKeyLimitExceededException.class)
  public ResponseEntity<ProblemDetail> handleApiKeyLimitExceededException(
      ApiKeyLimitExceededException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/api-key-limit-exceeded",
        "API Key Limit Exceeded",
        request);
  }

  @ExceptionHandler(AuthAttemptStateConflictException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptStateConflictException(
      AuthAttemptStateConflictException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/auth-attempt-state-conflict",
        "Auth Attempt State Conflict",
        request);
  }

  @ExceptionHandler(AuthAttemptWaitValidationException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptWaitValidationException(
      AuthAttemptWaitValidationException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex.getMessage(),
        HttpStatus.BAD_REQUEST,
        "https://ezkey.io/problems/validation/auth-attempt-wait-invalid",
        "Invalid Auth Attempt Wait Request",
        request);
  }

  /**
   * Unknown routes / missing resources (e.g. scanners). Log at DEBUG only — not ERROR with stack.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpServletRequest request) {
    logger.debug("No resource: {}", request.getRequestURI());
    return buildProblemDetail(
        "The requested resource could not be found.",
        HttpStatus.NOT_FOUND,
        "https://ezkey.io/problems/resource/not-found",
        "Resource Not Found",
        request);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoHandlerFoundException(
      NoHandlerFoundException ex, HttpServletRequest request) {
    logger.debug("No handler: {} {}", ex.getHttpMethod(), ex.getRequestURL());
    return buildProblemDetail(
        "The requested resource could not be found.",
        HttpStatus.NOT_FOUND,
        "https://ezkey.io/problems/resource/not-found",
        "Resource Not Found",
        request);
  }

  /**
   * Fail-closed Integration auth-attempt creation when audit-chain checkpoints appear stalled.
   *
   * @param ex degraded heartbeat guard signal
   * @param request HTTP servlet request for path extraction
   * @return HTTP 503 with stable problem type and Retry-After
   */
  @ExceptionHandler(AuditChainHeartbeatDegradedException.class)
  public ResponseEntity<ProblemDetail> handleAuditChainHeartbeatDegraded(
      AuditChainHeartbeatDegradedException ex, HttpServletRequest request) {
    AuditChainHeartbeatEvaluation ev = auditChainHeartbeatGuardService.evaluate();
    logger.warn(
        "Integration API 503 heartbeat-degraded (path={}, phase={}, anchorCheckpointId={}, "
            + "latestWindowEnd={}, stalePhaseStartsAt={}, failClosedNotBefore={}, "
            + "problemType=https://ezkey.io/problems/system/audit-chain-heartbeat-degraded)",
        request.getRequestURI(),
        ev.phase(),
        ev.anchorCheckpointId(),
        ev.latestWindowEnd(),
        ev.stalePhaseStartsAt(),
        ev.failClosedNotBefore());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            IntegrationApiProblemCatalog.DETAIL_AUDIT_CHAIN_HEARTBEAT_DEGRADED);
    problem.setType(URI.create(IntegrationApiProblemCatalog.TYPE_AUDIT_CHAIN_HEARTBEAT_DEGRADED));
    problem.setTitle(IntegrationApiProblemCatalog.TITLE_SERVICE_UNAVAILABLE);
    problem.setProperty("path", request.getRequestURI());
    problem.setProperty("timestamp", OffsetDateTime.now().toString());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "60")
        .body(problem);
  }

  /**
   * Catch-all handler for unhandled exceptions, returns HTTP 500 Internal Server Error.
   *
   * @param ex the unhandled exception
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 500 status
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(
      Exception ex, HttpServletRequest request) {
    logger.error("Unhandled exception in Integration API: {}", ex.getMessage(), ex);
    return buildProblemDetail(
        "An unexpected error occurred.",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "https://ezkey.io/problems/system/internal-error",
        "Internal Server Error",
        request);
  }

  /**
   * Builds an RFC 9457 ProblemDetail response.
   *
   * @param detail specific error message
   * @param status HTTP status
   * @param typeUri problem type URI
   * @param title human-readable error title
   * @param request HTTP servlet request for path extraction
   * @return ResponseEntity with RFC 9457 ProblemDetail
   */
  private ResponseEntity<ProblemDetail> buildProblemDetail(
      String detail, HttpStatus status, String typeUri, String title, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(typeUri));
    problem.setTitle(title);
    problem.setProperty("path", request.getRequestURI());
    return ResponseEntity.status(status).body(problem);
  }
}
