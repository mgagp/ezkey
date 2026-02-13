/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: ValidationExceptionHandler
 * Description: Handles validation and data constraint exceptions in the Ezkey Admin API.
 */

package org.ezkey.exception;

import java.util.List;
import java.util.stream.Collectors;
import org.ezkey.dto.ErrorResponseDto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * Handles validation and data constraint exceptions in the Ezkey Admin REST API.
 *
 * <p><b>Responsibility:</b> This component intercepts validation exceptions thrown during request
 * processing and data persistence, converting them into standardized HTTP responses with
 * appropriate status codes.
 *
 * <p><b>Exceptions Handled (4 total):</b>
 *
 * <ul>
 *   <li><b>MethodArgumentNotValidException (400):</b> Bean Validation failures on request
 *       parameters
 *   <li><b>IllegalArgumentException (400):</b> Invalid argument values during processing
 *   <li><b>IllegalStateException (409):</b> State conflicts in business logic (e.g., invalid
 *       operation sequence)
 *   <li><b>DataIntegrityViolationException (400):</b> Database constraint violations (NOT NULL,
 *       UNIQUE, FOREIGN KEY)
 * </ul>
 *
 * <p><b>Response Format:</b> All responses use ErrorResponseDto for consistency with legacy error
 * handling. These are data/constraint validation errors that are client-correctable.
 *
 * <p><b>HTTP Status Codes:</b>
 *
 * <ul>
 *   <li>400 - Client data validation failures or constraint violations
 *   <li>409 - State conflicts (business logic violations)
 * </ul>
 *
 * <p><b>Integration:</b> This handler is registered as a Spring component and automatically picked
 * up by the @RestControllerAdvice scanning mechanism.
 *
 * <p><b>Design Notes:</b>
 *
 * <ul>
 *   <li>MethodArgumentNotValidException includes detailed field-level error messages
 *   <li>DataIntegrityViolationException messages are sanitized to avoid exposing database schema
 *   <li>Error codes identify validation type (VALIDATION_ERROR, CONSTRAINT_VIOLATION, etc.)
 *   <li>All handlers are simple, focused, and easy to extend or maintain
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ErrorResponseDto
 * @see MethodArgumentNotValidException
 * @see IllegalArgumentException
 * @see IllegalStateException
 * @see DataIntegrityViolationException
 */
@RestControllerAdvice
@Component
public class ValidationExceptionHandler {

  /**
   * Handles MethodArgumentNotValidException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when Spring's Bean Validation (@Valid) fails on request parameters. This includes
   * field-level validation errors from validation annotations (@Pattern, @NotNull, @Email, etc.)
   * documented in the API contract.
   *
   * <p><b>HTTP Status:</b> 400 Bad Request
   *
   * <p><b>Error Messages:</b> Includes all validation errors as a "; "-separated list identifying
   * which field failed and why.
   *
   * <p><b>Security Note:</b> Validation error messages are safe to expose - they are client-side
   * validation errors from the API contract, not server internals.
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * Input: POST /api/v1/admin/admins with invalid email format
   * Response: {
   *   "code": "VALIDATION_ERROR",
   *   "message": "email: must be a valid email address",
   *   "path": "/api/v1/admin/admins"
   * }
   * </pre>
   *
   * @param ex the MethodArgumentNotValidException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing validation error details and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, WebRequest request) {

    // Extract validation error messages for each failed field
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
   * Handles IllegalArgumentException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when invalid argument values are passed to service methods or constructors. These
   * represent client-correctable input errors.
   *
   * <p><b>HTTP Status:</b> 400 Bad Request
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * Input: Invalid enum value for admin type
   * Response: {
   *   "code": "INVALID_ARGUMENT",
   *   "message": "Invalid admin type: SUPERUSER",
   *   "path": "/api/v1/admin/admins"
   * }
   * </pre>
   *
   * @param ex the IllegalArgumentException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 400 status
   * @since 2025
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
   * Handles IllegalStateException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when an operation would violate the expected state (e.g., attempting to perform an
   * operation in the wrong order, state conflicts in authentication/enrollment flows).
   *
   * <p><b>HTTP Status:</b> 409 Conflict (State conflict - client must fix state before retrying)
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * Input: POST /password-reset with user who doesn't have valid recovery code
   * Response: {
   *   "code": "STATE_CONFLICT",
   *   "message": "Password reset not available for this account",
   *   "path": "/api/v1/admin/accounts/456/password-reset"
   * }
   * </pre>
   *
   * @param ex the IllegalStateException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 409 status
   * @since 2025
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
   * Handles DataIntegrityViolationException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered by Spring Data JPA when database constraint violations occur (NOT NULL, UNIQUE,
   * FOREIGN KEY constraints). These represent invalid data sent by the client that bypassed
   * pre-insertion validation (e.g., race conditions, concurrent updates).
   *
   * <p><b>HTTP Status:</b> 400 Bad Request (Client data is invalid)
   *
   * <p><b>Error Message Sanitization:</b> Error messages are parsed to provide specific feedback
   * for common violations:
   *
   * <ul>
   *   <li>NOT NULL violations: "Required field 'fieldName' is missing or null"
   *   <li>UNIQUE violations: "Username already exists" or "Email already exists"
   *   <li>Other constraints: "Duplicate value violates unique constraint"
   * </ul>
   *
   * <p><b>Security Note:</b> Messages are sanitized to avoid exposing sensitive database schema
   * information while still providing useful feedback.
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * Input: POST /api/v1/admin/admins with duplicate email
   * Response: {
   *   "code": "CONSTRAINT_VIOLATION",
   *   "message": "Email already exists",
   *   "path": "/api/v1/admin/admins"
   * }
   * </pre>
   *
   * @param ex the DataIntegrityViolationException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing sanitized constraint violation details and HTTP 400 status
   * @since 2025
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
}
