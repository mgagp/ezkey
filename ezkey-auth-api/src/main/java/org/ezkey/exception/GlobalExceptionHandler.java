/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import java.net.URI;
import java.time.OffsetDateTime;
import org.ezkey.audit.integrity.AuditChainHeartbeatEvaluation;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.audit.AuditChainHeartbeatDegradedException;
import org.ezkey.exception.auth.AuthAttemptRequestFailedException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.exception.auth.EnrollmentAlreadyBoundException;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.ezkey.exception.auth.EnrollmentIntegrationNotFoundException;
import org.ezkey.exception.auth.EnrollmentInvitationExpiredException;
import org.ezkey.exception.auth.EnrollmentNotAvailableAfterLockException;
import org.ezkey.exception.auth.EnrollmentVerifyFailedException;
import org.ezkey.exception.auth.EnrollmentVerifyStateConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Centralized RFC 9457 exception handling for the Auth API. Raw exception messages are logged
 * server-side and never copied to {@link ProblemDetail#getDetail()} for security-sensitive flows.
 */
@RestControllerAdvice
@Order(100)
public class GlobalExceptionHandler extends AuthExceptionHandlerBase {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final AuditChainHeartbeatGuardService auditChainHeartbeatGuardService;

  /**
   * Creates the global Auth API exception handler.
   *
   * @param auditChainHeartbeatGuardService guard used for heartbeat-degraded diagnostic context on
   *     503 responses
   */
  public GlobalExceptionHandler(AuditChainHeartbeatGuardService auditChainHeartbeatGuardService) {
    this.auditChainHeartbeatGuardService = auditChainHeartbeatGuardService;
  }

  private static String pathFrom(WebRequest request) {
    return request.getDescription(false).replace("uri=", "");
  }

  @ExceptionHandler(NoPendingAuthAttemptException.class)
  public ResponseEntity<Void> handleNoPendingAuthAttempt(
      NoPendingAuthAttemptException ex, WebRequest request) {
    LOG.debug("No pending auth attempt: {}", request.getDescription(false));
    return ResponseEntity.noContent().build();
  }

  @ExceptionHandler(EnrollmentBindingFailedException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentBindingFailed(
      EnrollmentBindingFailedException ex, WebRequest request) {
    LOG.warn("Enrollment bind rejected: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_ENROLLMENT_BINDING_FAILED,
        AuthApiProblemCatalog.TITLE_BAD_REQUEST,
        AuthApiProblemCatalog.DETAIL_ENROLLMENT_BINDING_FAILED,
        pathFrom(request));
  }

  @ExceptionHandler(EnrollmentInvitationExpiredException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentInvitationExpired(
      EnrollmentInvitationExpiredException ex, WebRequest request) {
    LOG.warn("Enrollment invitation expired: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_ENROLLMENT_INVITATION_EXPIRED,
        AuthApiProblemCatalog.TITLE_BAD_REQUEST,
        AuthApiProblemCatalog.DETAIL_ENROLLMENT_INVITATION_EXPIRED,
        pathFrom(request));
  }

  @ExceptionHandler(EnrollmentNotAvailableAfterLockException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentNotAvailableAfterLock(
      EnrollmentNotAvailableAfterLockException ex, WebRequest request) {
    LOG.warn("Enrollment not available after lock: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_ENROLLMENT_NOT_AVAILABLE,
        AuthApiProblemCatalog.TITLE_BAD_REQUEST,
        AuthApiProblemCatalog.DETAIL_ENROLLMENT_NOT_AVAILABLE,
        pathFrom(request));
  }

  @ExceptionHandler(EnrollmentIntegrationNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentIntegrationNotFound(
      EnrollmentIntegrationNotFoundException ex, WebRequest request) {
    LOG.error("Enrollment integration missing: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.NOT_FOUND,
        AuthApiProblemCatalog.TYPE_ENROLLMENT_INTEGRATION_NOT_FOUND,
        AuthApiProblemCatalog.TITLE_RESOURCE_NOT_FOUND,
        AuthApiProblemCatalog.DETAIL_ENROLLMENT_INTEGRATION_NOT_FOUND,
        pathFrom(request));
  }

  @ExceptionHandler(EnrollmentAlreadyBoundException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentAlreadyBound(
      EnrollmentAlreadyBoundException ex, WebRequest request) {
    LOG.warn("Enrollment already bound: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.CONFLICT,
        AuthApiProblemCatalog.TYPE_ENROLLMENT_ALREADY_BOUND,
        AuthApiProblemCatalog.TITLE_CONFLICT,
        AuthApiProblemCatalog.DETAIL_ENROLLMENT_ALREADY_BOUND,
        pathFrom(request));
  }

  @ExceptionHandler(EnrollmentVerifyFailedException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentVerifyFailed(
      EnrollmentVerifyFailedException ex, WebRequest request) {
    LOG.warn("Enrollment verify failed: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_ENROLLMENT_VERIFY_FAILED,
        AuthApiProblemCatalog.TITLE_BAD_REQUEST,
        AuthApiProblemCatalog.DETAIL_ENROLLMENT_VERIFY_FAILED,
        pathFrom(request));
  }

  @ExceptionHandler(EnrollmentVerifyStateConflictException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentVerifyStateConflict(
      EnrollmentVerifyStateConflictException ex, WebRequest request) {
    LOG.warn("Enrollment verify conflict: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.CONFLICT,
        AuthApiProblemCatalog.TYPE_ENROLLMENT_STATE_CONFLICT,
        AuthApiProblemCatalog.TITLE_CONFLICT,
        AuthApiProblemCatalog.DETAIL_ENROLLMENT_STATE_CONFLICT,
        pathFrom(request));
  }

  @ExceptionHandler(AuthAttemptRequestFailedException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptRequestFailed(
      AuthAttemptRequestFailedException ex, WebRequest request) {
    LOG.warn("Auth attempt request failed: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_BINDING_FAILED,
        AuthApiProblemCatalog.TITLE_BAD_REQUEST,
        AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_FAILED,
        pathFrom(request));
  }

  @ExceptionHandler(AuthAttemptStateConflictException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptStateConflict(
      AuthAttemptStateConflictException ex, WebRequest request) {
    LOG.warn("Auth attempt state conflict: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.CONFLICT,
        AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_STATE_CONFLICT,
        AuthApiProblemCatalog.TITLE_CONFLICT,
        AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_STATE_CONFLICT,
        pathFrom(request));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleResourceNotFound(
      ResourceNotFoundException ex, WebRequest request) {
    LOG.warn("Resource not found (detail redacted for client)");
    return problemResponse(
        HttpStatus.NOT_FOUND,
        AuthApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND,
        AuthApiProblemCatalog.TITLE_RESOURCE_NOT_FOUND,
        AuthApiProblemCatalog.DETAIL_RESOURCE_NOT_FOUND,
        pathFrom(request));
  }

  /** Missing static resources and similar; do not log at ERROR with stack (scanner noise). */
  @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResourceFoundException(
      org.springframework.web.servlet.resource.NoResourceFoundException ex, WebRequest request) {
    LOG.debug("No resource: {}", pathFrom(request));
    return problemResponse(
        HttpStatus.NOT_FOUND,
        AuthApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND,
        AuthApiProblemCatalog.TITLE_RESOURCE_NOT_FOUND,
        AuthApiProblemCatalog.DETAIL_RESOURCE_NOT_FOUND,
        pathFrom(request));
  }

  /**
   * No handler for the request URL (when the dispatcher raises this); same policy as {@link
   * #handleNoResourceFoundException}.
   */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoHandlerFoundException(
      NoHandlerFoundException ex, WebRequest request) {
    LOG.debug("No handler: {} {}", ex.getHttpMethod(), ex.getRequestURL());
    return problemResponse(
        HttpStatus.NOT_FOUND,
        AuthApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND,
        AuthApiProblemCatalog.TITLE_RESOURCE_NOT_FOUND,
        AuthApiProblemCatalog.DETAIL_RESOURCE_NOT_FOUND,
        pathFrom(request));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, WebRequest request) {
    LOG.warn("Validation failed: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_VALIDATION_FAILED,
        AuthApiProblemCatalog.TITLE_VALIDATION_FAILED,
        AuthApiProblemCatalog.DETAIL_VALIDATION_FAILED,
        pathFrom(request));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, WebRequest request) {
    LOG.warn("Unreadable message: {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_INVALID_REQUEST_BODY,
        AuthApiProblemCatalog.TITLE_BAD_REQUEST,
        AuthApiProblemCatalog.DETAIL_INVALID_JSON,
        pathFrom(request));
  }

  /**
   * Legacy fallback for code paths still throwing generic {@link IllegalArgumentException}. More
   * specific types (e.g. {@link AuthAttemptRequestFailedException}) use dedicated handlers above.
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleLegacyIllegalArgument(
      IllegalArgumentException ex, WebRequest request) {
    LOG.warn("Illegal argument (legacy): {}", ex.getMessage());
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AuthApiProblemCatalog.TYPE_VALIDATION_FAILED,
        AuthApiProblemCatalog.TITLE_BAD_REQUEST,
        AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_FAILED,
        pathFrom(request));
  }

  /**
   * Legacy fallback for generic {@link IllegalStateException}. {@link
   * AuthAttemptStateConflictException} uses the dedicated handler above.
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ProblemDetail> handleLegacyIllegalState(
      IllegalStateException ex, WebRequest request) {
    LOG.warn("Illegal state (legacy): {}", ex.getMessage());
    return problemResponse(
        HttpStatus.CONFLICT,
        AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_STATE_CONFLICT,
        AuthApiProblemCatalog.TITLE_CONFLICT,
        AuthApiProblemCatalog.DETAIL_AUTH_ATTEMPT_STATE_CONFLICT,
        pathFrom(request));
  }

  @ExceptionHandler(AuditChainHeartbeatDegradedException.class)
  public ResponseEntity<ProblemDetail> handleAuditChainHeartbeatDegraded(
      AuditChainHeartbeatDegradedException ex, WebRequest request) {
    String path = pathFrom(request);
    AuditChainHeartbeatEvaluation ev = auditChainHeartbeatGuardService.evaluate();
    LOG.warn(
        "Auth API 503 heartbeat-degraded (path={}, phase={}, anchorCheckpointId={},"
            + " latestWindowEnd={}, stalePhaseStartsAt={}, failClosedNotBefore={},"
            + " problemType=https://ezkey.io/problems/system/audit-chain-heartbeat-degraded)",
        path,
        ev.phase(),
        ev.anchorCheckpointId(),
        ev.latestWindowEnd(),
        ev.stalePhaseStartsAt(),
        ev.failClosedNotBefore());
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            AuthApiProblemCatalog.DETAIL_AUDIT_CHAIN_HEARTBEAT_DEGRADED);
    problem.setType(URI.create(AuthApiProblemCatalog.TYPE_AUDIT_CHAIN_HEARTBEAT_DEGRADED));
    problem.setTitle(AuthApiProblemCatalog.TITLE_SERVICE_UNAVAILABLE);
    problem.setProperty("path", path);
    problem.setProperty("timestamp", OffsetDateTime.now().toString());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, "60")
        .body(problem);
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ProblemDetail> handleRuntimeException(
      RuntimeException ex, WebRequest request) {
    String path = pathFrom(request);
    if (path != null && path.startsWith("/actuator/")) {
      return null;
    }
    LOG.error("Unexpected runtime error", ex);
    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        AuthApiProblemCatalog.TYPE_INTERNAL_ERROR,
        AuthApiProblemCatalog.TITLE_INTERNAL_ERROR,
        AuthApiProblemCatalog.DETAIL_INTERNAL,
        path);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
    String path = pathFrom(request);
    if (path != null && path.startsWith("/actuator/")) {
      return null;
    }
    LOG.error("Unhandled exception", ex);
    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        AuthApiProblemCatalog.TYPE_INTERNAL_ERROR,
        AuthApiProblemCatalog.TITLE_INTERNAL_ERROR,
        AuthApiProblemCatalog.DETAIL_INTERNAL,
        path);
  }
}
