/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Handler: CryptoGlobalExceptionHandler
 * Description: Global exception handler for crypto API error responses.
 */

package org.ezkey.crypto.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for crypto API error responses.
 *
 * <p>Provides consistent error handling across all crypto endpoints, converting exceptions to
 * standardized ErrorResponseDto objects.
 *
 * @since 2025
 */
@RestControllerAdvice
public class CryptoGlobalExceptionHandler {

  // Most specific handlers first - Spring will match the most specific one
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    // Log the full exception for debugging
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CryptoGlobalExceptionHandler.class);
    logger.error("JSON parsing error in crypto API endpoint: {}", request.getRequestURI(), e);

    String exceptionMessage = e.getMessage();
    if (exceptionMessage == null || exceptionMessage.isEmpty()) {
      exceptionMessage = "Invalid JSON format in request body";
    } else if (exceptionMessage.contains("JSON parse error")) {
      // Extract the relevant part of the error message
      int jsonErrorIndex = exceptionMessage.indexOf("JSON parse error");
      if (jsonErrorIndex >= 0) {
        exceptionMessage = exceptionMessage.substring(jsonErrorIndex);
        // Truncate if too long
        if (exceptionMessage.length() > 200) {
          exceptionMessage = exceptionMessage.substring(0, 197) + "...";
        }
      }
    }

    var errorResponse =
        new ErrorResponseDto("INVALID_JSON", exceptionMessage, request.getRequestURI());
    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleValidationException(
      MethodArgumentNotValidException e, HttpServletRequest request) {

    StringBuilder message = new StringBuilder("Validation failed: ");
    for (FieldError error : e.getBindingResult().getFieldErrors()) {
      message.append(error.getField()).append(" - ").append(error.getDefaultMessage()).append("; ");
    }

    var errorResponse =
        new ErrorResponseDto("VALIDATION_ERROR", message.toString(), request.getRequestURI());
    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(
      IllegalArgumentException e, HttpServletRequest request) {
    var errorResponse =
        new ErrorResponseDto("INVALID_PARAMETER", e.getMessage(), request.getRequestURI());
    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponseDto> handleRuntimeException(
      RuntimeException e, HttpServletRequest request) {
    // Exclude actuator endpoints from global exception handling
    String path = request.getRequestURI();
    if (path != null && path.startsWith("/actuator/")) {
      // Return null to let Spring Boot handle actuator exceptions with its default handler
      return null;
    }

    // Exclude HttpMessageNotReadableException - handled by specific handler above
    if (e instanceof HttpMessageNotReadableException) {
      return null;
    }

    // Log the full exception for debugging
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CryptoGlobalExceptionHandler.class);
    logger.error("RuntimeException in crypto API endpoint: {}", path, e);

    var errorResponse =
        new ErrorResponseDto(
            "INTERNAL_ERROR", "An internal error occurred: " + e.getMessage(), path);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDto> handleGenericException(
      Exception e, HttpServletRequest request) {
    // Exclude actuator endpoints from global exception handling
    String path = request.getRequestURI();
    if (path != null && path.startsWith("/actuator/")) {
      // Return null to let Spring Boot handle actuator exceptions with its default handler
      return null;
    }

    // Log the full exception for debugging
    org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CryptoGlobalExceptionHandler.class);
    logger.error("Exception in crypto API endpoint: {}", path, e);

    String exceptionMessage = e.getMessage();
    if (exceptionMessage == null || exceptionMessage.isEmpty()) {
      exceptionMessage = e.getClass().getSimpleName() + " (no message)";
    }

    var errorResponse =
        new ErrorResponseDto(
            "UNKNOWN_ERROR", "An unexpected error occurred: " + exceptionMessage, path);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
  }
}
