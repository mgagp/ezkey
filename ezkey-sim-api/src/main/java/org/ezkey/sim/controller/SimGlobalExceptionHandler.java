/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Handler: SimGlobalExceptionHandler
 * Description: Global exception handler for simulation API error responses.
 */

package org.ezkey.sim.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for simulation API error responses.
 *
 * <p>Provides consistent error handling across all simulation endpoints, converting exceptions to
 * standardized ErrorResponseDto objects.
 *
 * @since 2025
 */
@RestControllerAdvice
public class SimGlobalExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(
      IllegalArgumentException e, HttpServletRequest request) {
    var errorResponse =
        new ErrorResponseDto("INVALID_PARAMETER", e.getMessage(), request.getRequestURI());
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

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponseDto> handleRuntimeException(
      RuntimeException e, HttpServletRequest request) {
    var errorResponse =
        new ErrorResponseDto(
            "INTERNAL_ERROR",
            "An internal error occurred: " + e.getMessage(),
            request.getRequestURI());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDto> handleGenericException(
      Exception e, HttpServletRequest request) {
    var errorResponse =
        new ErrorResponseDto(
            "UNKNOWN_ERROR",
            "An unexpected error occurred: " + e.getMessage(),
            request.getRequestURI());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
  }
}
