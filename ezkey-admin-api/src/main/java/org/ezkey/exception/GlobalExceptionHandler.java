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

import java.util.List;
import java.util.stream.Collectors;
import org.ezkey.dto.ErrorResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
 *   <li><b>AuthorizationDeniedException:</b> Returns HTTP 403 with access denied information
 *   <li><b>ResourceNotFoundException:</b> Returns HTTP 404 with detailed error information
 *   <li><b>MethodArgumentNotValidException:</b> Returns HTTP 400 with Bean Validation error details
 *   <li><b>IllegalArgumentException:</b> Returns HTTP 400 with argument error details
 *   <li><b>IllegalStateException:</b> Returns HTTP 409 with state conflict information
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
   * Handles AuthorizationDeniedException and returns HTTP 403.
   *
   * <p>This method catches AuthorizationDeniedException instances thrown by Spring Security's
   * method-level security (@PreAuthorize) and converts them into standardized HTTP 403 Forbidden
   * responses with access denied information.
   *
   * @param ex the AuthorizationDeniedException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 403 status
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  public ResponseEntity<ErrorResponseDto> handleAuthorizationDeniedException(
      AuthorizationDeniedException ex, WebRequest request) {
    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "ACCESS_DENIED",
            "Access denied: insufficient permissions",
            request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
  }

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
   * Handles IllegalStateException and returns HTTP 409.
   *
   * <p>This method catches IllegalStateException instances and converts them into standardized HTTP
   * 409 Conflict responses, as these represent state conflicts in the authentication or enrollment
   * process.
   *
   * @param ex the IllegalStateException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 409 status
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ErrorResponseDto> handleIllegalStateException(
      IllegalStateException ex, WebRequest request) {
    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "STATE_CONFLICT", ex.getMessage(), request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
  }

  /**
   * Handles MethodArgumentNotValidException and returns HTTP 400.
   *
   * <p>This method catches MethodArgumentNotValidException instances thrown by Spring when Bean
   * Validation (@Valid) fails and converts them into standardized HTTP 400 Bad Request responses
   * with validation error details.
   *
   * <p><b>Security Note:</b> Validation error messages are safe to expose - they are client-side
   * validation errors that don't reveal server internals. These messages are already defined in
   * validation annotations (@Pattern, @NotNull, etc.) and are part of the API contract.
   *
   * @param ex the MethodArgumentNotValidException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing validation error details and HTTP 400 status
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, WebRequest request) {

    // Extract validation error messages
    // These are safe to expose - they're client validation errors, not server errors
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());

    String message = String.join("; ", errors);

    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "VALIDATION_ERROR", message, request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  /**
   * Handles DataIntegrityViolationException and returns HTTP 400.
   *
   * <p>This method catches DataIntegrityViolationException instances thrown by Spring Data JPA when
   * database constraint violations occur (e.g., NOT NULL constraints, unique constraints). These
   * represent invalid data sent by the client, so they should return HTTP 400 Bad Request instead
   * of HTTP 500 Internal Server Error.
   *
   * <p><b>Security Note:</b> The error message is sanitized to avoid exposing sensitive database
   * schema information while still providing useful feedback about what constraint was violated.
   *
   * <p><b>Constraint Parsing:</b> This handler parses PostgreSQL constraint violation messages to
   * provide specific error messages for common violations (username, email) while maintaining
   * generic fallback messages for other constraints. This provides better user experience while
   * protecting against race conditions that might bypass pre-insertion validation.
   *
   * @param ex the DataIntegrityViolationException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing constraint violation error details and HTTP 400 status
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolationException(
      DataIntegrityViolationException ex, WebRequest request) {

    // Extract a user-friendly error message from the exception
    String message = ex.getMessage();
    if (message != null && message.contains("violates not-null constraint")) {
      // Extract the column name from the error message
      // Format: "null value in column \"column_name\" violates not-null constraint"
      int columnStart = message.indexOf("\"") + 1;
      int columnEnd = message.indexOf("\"", columnStart);
      if (columnStart > 0 && columnEnd > columnStart) {
        String columnName = message.substring(columnStart, columnEnd);
        // Convert database column name to a more user-friendly field name
        String fieldName = columnName.replace("integration_i18n_", "").replace("_", " ");
        message = "Required field '" + fieldName + "' is missing or null";
      } else {
        message = "Required field is missing or null";
      }
    } else if (message != null && message.contains("violates unique constraint")) {
      // Parse unique constraint violations to identify specific constraints
      // Format examples:
      // - "duplicate key value violates unique constraint \"uq_admin_email\""
      // - "duplicate key value violates unique constraint \"ezkey_admin_username_key\""
      // - "duplicate key value violates unique constraint \"uq_admin_email\"\n  Detail: Key (email)=(test@example.com) already exists."

      String constraintName = extractConstraintName(message);
      if (constraintName != null) {
        // Identify specific constraint violations
        if (constraintName.contains("username") || constraintName.contains("_username_key")) {
          message = "Username already exists";
        } else if (constraintName.contains("email") || constraintName.equals("uq_admin_email")) {
          message = "Email already exists";
        } else {
          // Generic message for other unique constraints
          message = "Duplicate value violates unique constraint";
        }
      } else {
        // Fallback if constraint name cannot be extracted
        message = "Duplicate value violates unique constraint";
      }
    } else {
      message = "Invalid data: constraint violation";
    }

    ErrorResponseDto errorResponse =
        new ErrorResponseDto(
            "CONSTRAINT_VIOLATION", message, request.getDescription(false).replace("uri=", ""));

    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  /**
   * Extracts the constraint name from a PostgreSQL constraint violation error message.
   *
   * <p>PostgreSQL error messages for unique constraint violations typically include the constraint
   * name in quotes. This method extracts that constraint name for identification purposes.
   *
   * <p>Example formats:
   *
   * <ul>
   *   <li>"duplicate key value violates unique constraint \"uq_admin_email\""
   *   <li>"duplicate key value violates unique constraint \"ezkey_admin_username_key\""
   * </ul>
   *
   * @param errorMessage the error message from DataIntegrityViolationException
   * @return the constraint name if found, null otherwise
   */
  private String extractConstraintName(String errorMessage) {
    if (errorMessage == null) {
      return null;
    }

    // Look for constraint name in quotes after "unique constraint"
    int constraintStart = errorMessage.indexOf("unique constraint");
    if (constraintStart == -1) {
      return null;
    }

    // Find the opening quote after "unique constraint"
    int quoteStart = errorMessage.indexOf("\"", constraintStart);
    if (quoteStart == -1) {
      return null;
    }

    // Find the closing quote
    int quoteEnd = errorMessage.indexOf("\"", quoteStart + 1);
    if (quoteEnd == -1) {
      return null;
    }

    return errorMessage.substring(quoteStart + 1, quoteEnd);
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
