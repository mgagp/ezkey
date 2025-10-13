/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: GlobalExceptionHandler
 * Description: Centralized exception handling for the Ezkey Admin REST API.
 */

package org.ezkey.exception;

import org.ezkey.dto.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Global exception handler for the Ezkey Admin REST API.
 *
 * <p>This class provides centralized exception handling for all controllers in the Ezkey Admin API,
 * ensuring consistent error responses across the entire application. It intercepts exceptions
 * thrown by controller methods and converts them into standardized HTTP responses with appropriate
 * status codes.
 *
 * <p>The handler supports multiple exception types:
 *
 * <ul>
 *   <li><b>ResourceNotFoundException:</b> Returns HTTP 404 with detailed error information
 *   <li><b>ValidationException:</b> Returns HTTP 400 with validation error details
 *   <li><b>IllegalArgumentException:</b> Returns HTTP 400 with argument error details
 *   <li><b>RuntimeException:</b> Returns HTTP 500 with generic error information
 *   <li><b>Exception:</b> Catches all other exceptions and returns HTTP 500
 * </ul>
 *
 * <p><b>Error Response Format:</b> All error responses follow a consistent JSON structure with:
 *
 * <ul>
 *   <li><b>timestamp:</b> When the error occurred
 *   <li><b>status:</b> HTTP status code
 *   <li><b>error:</b> Error type description
 *   <li><b>message:</b> Detailed error message
 *   <li><b>path:</b> API endpoint where the error occurred
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.web.bind.annotation.RestControllerAdvice
 * @see org.springframework.web.bind.annotation.ExceptionHandler
 * @see ErrorResponseDto
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handles ResourceNotFoundException and returns HTTP 404.
   *
   * <p>This method catches ResourceNotFoundException instances and converts them into standardized
   * HTTP 404 Not Found responses with detailed error information.
   *
   * @param ex the ResourceNotFoundException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 404 status
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponseDto> handleResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) {
    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "RESOURCE_NOT_FOUND",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
  }

  /**
   * Handles IllegalArgumentException and returns HTTP 400.
   *
   * <p>This method catches IllegalArgumentException instances and converts them into standardized
   * HTTP 400 Bad Request responses with argument error details.
   *
   * @param ex the IllegalArgumentException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 400 status
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(
      IllegalArgumentException ex, WebRequest request) {
    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "INVALID_ARGUMENT", ex.getMessage(), request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  /**
   * Handles RuntimeException and returns HTTP 500.
   *
   * <p>This method catches RuntimeException instances and converts them into standardized HTTP 500
   * Internal Server Error responses with error details.
   *
   * @param ex the RuntimeException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 500 status
   */
  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<ErrorResponseDto> handleRuntimeException(
      RuntimeException ex, WebRequest request) {
    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /**
   * Handles all other exceptions and returns HTTP 500.
   *
   * <p>This method serves as a catch-all for any exceptions not handled by more specific exception
   * handlers. It ensures that all exceptions result in a consistent error response.
   *
   * @param ex the Exception that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 500 status
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponseDto> handleGenericException(Exception ex, WebRequest request) {
    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred",
            request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
