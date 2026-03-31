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
import org.ezkey.exception.EnrollmentInactiveException;
import org.ezkey.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
