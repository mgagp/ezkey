/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Handler: CryptoGlobalExceptionHandler
 * Description: Global exception handler for crypto API error responses.
 */

package org.ezkey.crypto.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.ezkey.crypto.exception.CryptoApiProblemCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for crypto API error responses.
 *
 * <p>Maps exceptions to RFC 9457 {@link ProblemDetail} bodies for consistent error handling across
 * crypto endpoints.
 *
 * @since 2025
 */
@RestControllerAdvice
public class CryptoGlobalExceptionHandler {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(CryptoGlobalExceptionHandler.class);

  private static ResponseEntity<ProblemDetail> problemResponse(
      HttpStatus status, String typeUri, String title, String detail, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create(typeUri));
    problem.setTitle(title);
    problem.setProperty("path", request.getRequestURI());
    return ResponseEntity.status(status).body(problem);
  }

  // Most specific handlers first - Spring will match the most specific one
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    logger.error("JSON parsing error in crypto API endpoint: {}", request.getRequestURI(), e);

    String exceptionMessage = e.getMessage();
    if (exceptionMessage == null || exceptionMessage.isEmpty()) {
      exceptionMessage = "Invalid JSON format in request body";
    } else if (exceptionMessage.contains("JSON parse error")) {
      int jsonErrorIndex = exceptionMessage.indexOf("JSON parse error");
      if (jsonErrorIndex >= 0) {
        exceptionMessage = exceptionMessage.substring(jsonErrorIndex);
        if (exceptionMessage.length() > 200) {
          exceptionMessage = exceptionMessage.substring(0, 197) + "...";
        }
      }
    }

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        CryptoApiProblemCatalog.TYPE_MALFORMED_JSON,
        CryptoApiProblemCatalog.TITLE_INVALID_JSON,
        exceptionMessage,
        request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(
      MethodArgumentNotValidException e, HttpServletRequest request) {

    StringBuilder message = new StringBuilder("Validation failed: ");
    for (FieldError error : e.getBindingResult().getFieldErrors()) {
      message.append(error.getField()).append(" - ").append(error.getDefaultMessage()).append("; ");
    }

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        CryptoApiProblemCatalog.TYPE_VALIDATION_FAILED,
        CryptoApiProblemCatalog.TITLE_VALIDATION_FAILED,
        message.toString(),
        request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
      IllegalArgumentException e, HttpServletRequest request) {
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        CryptoApiProblemCatalog.TYPE_INVALID_ARGUMENT,
        CryptoApiProblemCatalog.TITLE_INVALID_ARGUMENT,
        e.getMessage(),
        request);
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ProblemDetail> handleRuntimeException(
      RuntimeException e, HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path != null && path.startsWith("/actuator/")) {
      return null;
    }

    if (e instanceof HttpMessageNotReadableException) {
      return null;
    }

    logger.error("RuntimeException in crypto API endpoint: {}", path, e);

    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        CryptoApiProblemCatalog.TYPE_INTERNAL_ERROR,
        CryptoApiProblemCatalog.TITLE_INTERNAL_ERROR,
        "An internal error occurred: " + e.getMessage(),
        request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(
      Exception e, HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path != null && path.startsWith("/actuator/")) {
      return null;
    }

    logger.error("Exception in crypto API endpoint: {}", path, e);

    String exceptionMessage = e.getMessage();
    if (exceptionMessage == null || exceptionMessage.isEmpty()) {
      exceptionMessage = e.getClass().getSimpleName() + " (no message)";
    }

    return problemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        CryptoApiProblemCatalog.TYPE_UNEXPECTED_ERROR,
        CryptoApiProblemCatalog.TITLE_UNEXPECTED_ERROR,
        "An unexpected error occurred: " + exceptionMessage,
        request);
  }
}
