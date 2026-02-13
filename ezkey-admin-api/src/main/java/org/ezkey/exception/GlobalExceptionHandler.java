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
import org.springframework.core.annotation.Order;
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
 *   <li><b>ResourceNotFoundException:</b> Returns HTTP 404 with ErrorResponseDto
 *   <li><b>RateLimitExceededException:</b> Returns HTTP 429 with ErrorResponseDto
 *   <li><b>RuntimeException:</b> Returns HTTP 500 with ErrorResponseDto
 *   <li><b>Exception:</b> Catches all other exceptions and returns HTTP 500 with ErrorResponseDto
 * </ul>
 *
 * <p><b>Error Response Format:</b> Uses ErrorResponseDto for consistency with legacy error handling
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AuthenticationExceptionHandler
 * @see AuthorizationExceptionHandler
 * @see ValidationExceptionHandler
 * @see org.springframework.web.bind.annotation.RestControllerAdvice
 * @see org.springframework.web.bind.annotation.ExceptionHandler
 * @see ErrorResponseDto
 */
@RestControllerAdvice
@Order(99)
public class GlobalExceptionHandler {

  /**
   * Handles RateLimitExceededException and returns HTTP 429.
   *
   * <p>This method catches RateLimitExceededException instances thrown by the RateLimitService when
   * API keys exceed their rate limits and converts them into standardized HTTP 429 Too Many
   * Requests responses with rate limit information.
   *
   * @param ex the RateLimitExceededException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 429 status
   */
  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ErrorResponseDto> handleRateLimitExceededException(
      RateLimitExceededException ex, WebRequest request) {
    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "RATE_LIMIT_EXCEEDED",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.TOO_MANY_REQUESTS);
  }

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
