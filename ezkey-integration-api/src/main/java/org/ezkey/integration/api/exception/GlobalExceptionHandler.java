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
import java.util.Set;
import org.ezkey.audit.integrity.AuditChainHeartbeatEvaluation;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.TenantInactiveException;
import org.ezkey.exception.audit.AuditChainHeartbeatDegradedException;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.exception.auth.AuthAttemptExpiredException;
import org.ezkey.exception.auth.AuthAttemptRequestInvalidException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.ezkey.integration.api.controller.IntegrationApiAuthAttemptController;
import org.ezkey.integration.exception.ApiKeyLimitExceededException;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.ezkey.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
 * <p><b>Scope (#895 Patrick M4):</b> Limited to {@link IntegrationApiAuthAttemptController}. The
 * Integration API module exposes a single REST controller; the unified invalid-request 400 handlers
 * ({@code MethodArgumentNotValid}, {@code AuthAttemptRequestInvalid}, {@code
 * HttpMessageNotReadable}) therefore cannot leak onto unrelated controllers. If a second
 * Integration controller is added later, either broaden {@code assignableTypes} deliberately or
 * extract a dedicated advice for that surface.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@RestControllerAdvice(assignableTypes = IntegrationApiAuthAttemptController.class)
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
        LogSanitizer.sanitizeForLog(request.getRequestURI()));
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

  @ExceptionHandler(AuthAttemptExpiredException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptExpiredException(
      AuthAttemptExpiredException ex, HttpServletRequest request) {
    return buildProblemDetail(
        "This authentication request has expired.",
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/auth-attempt-expired",
        "Auth Attempt Expired",
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
   * Bean Validation failures on request bodies (e.g. {@code ttlSeconds < 15}). Same ProblemDetail
   * as {@link #handleAuthAttemptRequestInvalid} and {@link #handleHttpMessageNotReadable} (#895).
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    logger.debug(
        "Validation failed on {}: {}",
        LogSanitizer.sanitizeForLog(request.getRequestURI()),
        LogSanitizer.sanitizeForLog(ex.getMessage()));
    return invalidRequestProblem(request);
  }

  /**
   * Explicit create-field gate (TTL floor before enrollment lookup). Must return the exact same 400
   * body as {@link #handleMethodArgumentNotValid} (#895 Christophe nit).
   */
  @ExceptionHandler(AuthAttemptRequestInvalidException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptRequestInvalid(
      AuthAttemptRequestInvalidException ex, HttpServletRequest request) {
    logger.debug(
        "Request invalid on {}: {}",
        LogSanitizer.sanitizeForLog(request.getRequestURI()),
        LogSanitizer.sanitizeForLog(ex.getMessage()));
    return invalidRequestProblem(request);
  }

  /**
   * JSON parse / type coercion failures (e.g. non-integer {@code ttlSeconds}). Same generic 400 as
   * Bean Validation (#895).
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    logger.debug(
        "Unreadable body on {}: {}",
        LogSanitizer.sanitizeForLog(request.getRequestURI()),
        LogSanitizer.sanitizeForLog(ex.getMessage()));
    return invalidRequestProblem(request);
  }

  /**
   * Shared non-enumerating invalid-request ProblemDetail for create validation paths.
   *
   * @param request HTTP servlet request for path extraction
   * @return HTTP 400 with stable catalog type/title/detail
   */
  private ResponseEntity<ProblemDetail> invalidRequestProblem(HttpServletRequest request) {
    return buildProblemDetail(
        IntegrationApiProblemCatalog.DETAIL_REQUEST_INVALID,
        HttpStatus.BAD_REQUEST,
        IntegrationApiProblemCatalog.TYPE_REQUEST_INVALID,
        IntegrationApiProblemCatalog.TITLE_REQUEST_INVALID,
        request);
  }

  /**
   * Unknown routes / missing resources (e.g. scanners). Log at DEBUG only — not ERROR with stack.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpServletRequest request) {
    logger.debug("No resource: {}", LogSanitizer.sanitizeForLog(request.getRequestURI()));
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
    logger.debug(
        "No handler: {} {}",
        LogSanitizer.sanitizeForLog(ex.getHttpMethod()),
        LogSanitizer.sanitizeForLog(ex.getRequestURL()));
    return buildProblemDetail(
        "The requested resource could not be found.",
        HttpStatus.NOT_FOUND,
        "https://ezkey.io/problems/resource/not-found",
        "Resource Not Found",
        request);
  }

  /**
   * Unsupported verb on a mapped path. Must be handled before {@link #handleGenericException} so
   * scanners cannot flood ERROR logs or inflate 5xx metrics. Integration API-key filter may still
   * return 401 first on unauthenticated traffic.
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    logger.debug(
        "Method not allowed: {} {}",
        LogSanitizer.sanitizeForLog(ex.getMethod()),
        LogSanitizer.sanitizeForLog(request.getRequestURI()));
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.METHOD_NOT_ALLOWED, IntegrationApiProblemCatalog.DETAIL_METHOD_NOT_ALLOWED);
    problem.setType(URI.create(IntegrationApiProblemCatalog.TYPE_METHOD_NOT_ALLOWED));
    problem.setTitle(IntegrationApiProblemCatalog.TITLE_METHOD_NOT_ALLOWED);
    problem.setProperty("path", request.getRequestURI());
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
    Set<HttpMethod> allowed = ex.getSupportedHttpMethods();
    if (allowed != null && !allowed.isEmpty()) {
      builder.allow(allowed.toArray(HttpMethod[]::new));
    }
    return builder.body(problem);
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
        LogSanitizer.sanitizeForLog(request.getRequestURI()),
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
    logger.error(
        "Unhandled exception in Integration API: {}",
        LogSanitizer.sanitizeForLog(ex.getMessage()),
        ex);
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
