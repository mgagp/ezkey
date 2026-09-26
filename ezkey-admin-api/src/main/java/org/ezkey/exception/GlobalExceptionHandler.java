/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: GlobalExceptionHandler
 * Description: Centralized exception handling for the Ezkey Admin REST API.
 */

package org.ezkey.exception;

import java.net.URI;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for the Ezkey Admin REST API.
 *
 * <p>This class provides centralized exception handling for all controllers in the Ezkey Admin API,
 * ensuring consistent error responses across the entire application. It intercepts exceptions
 * thrown by controller methods and converts them into standardized HTTP responses with appropriate
 * status codes.
 *
 * <p><b>Note:</b> Exception handlers are now organized by category:
 *
 * <ul>
 *   <li>{@link AuthenticationExceptionHandler} - Handles authentication-related exceptions
 *   <li>{@link AuthorizationExceptionHandler} - Handles authorization-related exceptions
 *   <li>{@link ValidationExceptionHandler} - Handles validation and constraint violations
 *   <li>{@link GlobalExceptionHandler} - Handles standard HTTP errors and fallbacks
 * </ul>
 *
 * <p>This handler focuses on standard HTTP error scenarios and provides fallback handling for
 * uncaught exceptions.
 *
 * <p>The handler supports the following exception types:
 *
 * <ul>
 *   <li><b>ResourceNotFoundException:</b> Returns HTTP 404 with RFC 9457 {@link ProblemDetail}
 *   <li><b>RateLimitExceededException:</b> Returns HTTP 429 with {@link ProblemDetail}
 *   <li><b>RuntimeException:</b> Returns HTTP 500 with sanitized {@link ProblemDetail}
 *   <li><b>Exception:</b> Catches all other exceptions and returns HTTP 500 with sanitized {@link
 *       ProblemDetail}
 * </ul>
 *
 * <p><b>Error response format:</b> RFC 9457 Problem Details ({@link ProblemDetail}).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthenticationExceptionHandler
 * @see AuthorizationExceptionHandler
 * @see ValidationExceptionHandler
 * @see AdminApiProblemCatalog
 * @see org.springframework.web.bind.annotation.RestControllerAdvice
 * @see org.springframework.web.bind.annotation.ExceptionHandler
 */
@RestControllerAdvice
@Order(99)
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static String pathFrom(WebRequest request) {
    return request.getDescription(false).replace("uri=", "");
  }

  private static ResponseEntity<ProblemDetail> problemResponse(
      HttpStatus status, String typeUri, String title, String detail, WebRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(typeUri));
    problem.setTitle(title);
    problem.setProperty("path", pathFrom(request));
    return ResponseEntity.status(status).body(problem);
  }

  /**
   * Handles {@link SystemTenantNotConfiguredException} and returns HTTP 500.
   *
   * <p>The response body is sanitized; operators should rely on server logs for the underlying
   * detail.
   */
  @ExceptionHandler(SystemTenantNotConfiguredException.class)
  public ResponseEntity<ProblemDetail> handleSystemTenantNotConfiguredException(
      SystemTenantNotConfiguredException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        AdminApiProblemCatalog.TYPE_SYSTEM_NOT_CONFIGURED,
        AdminApiProblemCatalog.TITLE_INTERNAL_ERROR,
        AdminApiProblemCatalog.DETAIL_UNEXPECTED,
        request);
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ProblemDetail> handleRateLimitExceededException(
      RateLimitExceededException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.TOO_MANY_REQUESTS,
        AdminApiProblemCatalog.TYPE_RATE_LIMIT_EXCEEDED,
        AdminApiProblemCatalog.TITLE_TOO_MANY_REQUESTS,
        ex.getMessage(),
        request);
  }

  @ExceptionHandler(org.ezkey.admin.exception.EvaluatorSelfRegistrationCapacityException.class)
  public ResponseEntity<ProblemDetail> handleEvaluatorSelfRegistrationCapacityException(
      org.ezkey.admin.exception.EvaluatorSelfRegistrationCapacityException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.TOO_MANY_REQUESTS,
        AdminApiProblemCatalog.TYPE_RATE_LIMIT_EXCEEDED,
        AdminApiProblemCatalog.TITLE_TOO_MANY_REQUESTS,
        ex.getMessage(),
        request);
  }

  /**
   * Handles ResourceNotFoundException and returns HTTP 404.
   *
   * @param ex the ResourceNotFoundException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing ProblemDetail and HTTP 404 status
   */
  /**
   * Unknown routes and missing static resources (e.g. internet scanners); log quietly — not ERROR
   * with stack.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResourceFoundException(
      NoResourceFoundException ex, WebRequest request) {
    LOG.debug("No resource: {}", pathFrom(request));
    return problemResponse(
        HttpStatus.NOT_FOUND,
        AdminApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND,
        AdminApiProblemCatalog.TITLE_NOT_FOUND,
        AdminApiProblemCatalog.DETAIL_NOT_FOUND,
        request);
  }

  /**
   * No Spring MVC handler for the request (when the servlet is configured to raise this); same
   * logging policy as {@link #handleNoResourceFoundException}.
   */
  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoHandlerFoundException(
      NoHandlerFoundException ex, WebRequest request) {
    LOG.debug("No handler: {} {}", ex.getHttpMethod(), ex.getRequestURL());
    return problemResponse(
        HttpStatus.NOT_FOUND,
        AdminApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND,
        AdminApiProblemCatalog.TITLE_NOT_FOUND,
        AdminApiProblemCatalog.DETAIL_NOT_FOUND,
        request);
  }

  /**
   * Unsupported verb on a mapped path. Must be handled before {@link #handleGenericException} so
   * scanners cannot flood ERROR logs or inflate 5xx metrics.
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ProblemDetail> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, WebRequest request) {
    LOG.debug("Method not allowed: {} {}", ex.getMethod(), pathFrom(request));
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.METHOD_NOT_ALLOWED, AdminApiProblemCatalog.DETAIL_METHOD_NOT_ALLOWED);
    problem.setType(URI.create(AdminApiProblemCatalog.TYPE_METHOD_NOT_ALLOWED));
    problem.setTitle(AdminApiProblemCatalog.TITLE_METHOD_NOT_ALLOWED);
    problem.setProperty("path", pathFrom(request));
    ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
    Set<HttpMethod> allowed = ex.getSupportedHttpMethods();
    if (allowed != null && !allowed.isEmpty()) {
      builder.allow(allowed.toArray(HttpMethod[]::new));
    }
    return builder.body(problem);
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.NOT_FOUND,
        AdminApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND,
        AdminApiProblemCatalog.TITLE_NOT_FOUND,
        ex.getMessage(),
        request);
  }

  /**
   * Handles RuntimeException and returns HTTP 500.
   *
   * @param ex the RuntimeException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing sanitized ProblemDetail and HTTP 500 status
   */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ProblemDetail> handleRuntimeException(
      RuntimeException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        AdminApiProblemCatalog.TYPE_INTERNAL_ERROR,
        AdminApiProblemCatalog.TITLE_INTERNAL_ERROR,
        AdminApiProblemCatalog.DETAIL_UNEXPECTED,
        request);
  }

  /**
   * Handles all other exceptions and returns HTTP 500.
   *
   * @param ex the Exception that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing sanitized ProblemDetail and HTTP 500 status
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, WebRequest request) {
    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        AdminApiProblemCatalog.TYPE_INTERNAL_ERROR,
        AdminApiProblemCatalog.TITLE_INTERNAL_ERROR,
        AdminApiProblemCatalog.DETAIL_UNEXPECTED,
        request);
  }
}
