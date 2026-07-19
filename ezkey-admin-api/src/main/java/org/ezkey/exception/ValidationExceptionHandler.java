/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: ValidationExceptionHandler
 * Description: Handles validation and data constraint exceptions in the Ezkey Admin API.
 */

package org.ezkey.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.ezkey.integration.exception.ApiKeyCreateValidationException;
import org.ezkey.integration.exception.ApiKeyIpWhitelistValidationException;
import org.ezkey.integration.exception.ApiKeyUpdateValidationException;
import org.ezkey.integration.exception.IntegrationCreateValidationException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
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
 * <p><b>Exceptions handled (representative):</b>
 *
 * <ul>
 *   <li><b>MethodArgumentNotValidException (400):</b> Bean Validation failures on request
 *       parameters
 *   <li><b>MissingRequestHeaderException (401/400):</b> Missing required request header
 *   <li><b>ConstraintViolationException (400):</b> {@code @Validated} + {@code @Size} on
 *       {@code @RequestParam} (e.g., short {@code reason} param)
 *   <li><b>HttpMessageNotReadableException (400):</b> JSON deserialization errors (missing fields,
 *       type mismatches, malformed payload)
 *   <li><b>IllegalArgumentException (400):</b> Invalid argument values during processing
 *   <li><b>IllegalStateException (409):</b> State conflicts in business logic (e.g., invalid
 *       operation sequence)
 *   <li><b>DataIntegrityViolationException (400):</b> Database constraint violations (NOT NULL,
 *       UNIQUE, FOREIGN KEY)
 * </ul>
 *
 * <p><b>Response format:</b> RFC 9457 {@link ProblemDetail}. These are data/constraint validation
 * errors that are client-correctable.
 *
 * <p><b>HTTP Status Codes:</b>
 *
 * <ul>
 *   <li>400 - Client data validation failures, deserialization errors, or constraint violations
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
 *   <li>HttpMessageNotReadableException handles JSON parsing failures (critical for record DTOs)
 *   <li>DataIntegrityViolationException messages are sanitized to avoid exposing database schema
 *   <li>Problem {@code type} URIs identify the error category (see {@link AdminApiProblemCatalog})
 *   <li>All handlers are simple, focused, and easy to extend or maintain
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see AdminApiProblemCatalog
 * @see MethodArgumentNotValidException
 * @see HttpMessageNotReadableException
 * @see IllegalArgumentException
 * @see IllegalStateException
 * @see DataIntegrityViolationException
 */
@RestControllerAdvice
@Component
@Order(10)
public class ValidationExceptionHandler {

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
   * Response: RFC 9457 ProblemDetail with type .../validation-failed and detail listing fields
   * </pre>
   *
   * @param ex the MethodArgumentNotValidException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing validation error details and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, WebRequest request) {

    // Extract validation error messages for each failed field
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.toList());

    String message = String.join("; ", errors);

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AdminApiProblemCatalog.TYPE_VALIDATION_FAILED,
        AdminApiProblemCatalog.TITLE_VALIDATION_FAILED,
        message,
        request);
  }

  @ExceptionHandler(AuthAttemptWaitValidationException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptWaitValidationException(
      AuthAttemptWaitValidationException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create("https://ezkey.io/problems/validation/auth-attempt-wait-invalid"));
    problem.setTitle("Invalid Auth Attempt Wait Request");
    problem.setProperty("path", request.getDescription(false).replace("uri=", ""));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(AuthAttemptCreateValidationException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptCreateValidationException(
      AuthAttemptCreateValidationException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create("https://ezkey.io/problems/validation/auth-attempt-create-invalid"));
    problem.setTitle("Invalid Auth Attempt Create Request");
    problem.setProperty("path", request.getDescription(false).replace("uri=", ""));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(EnrollmentCreateValidationException.class)
  public ResponseEntity<ProblemDetail> handleEnrollmentCreateValidationException(
      EnrollmentCreateValidationException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create("https://ezkey.io/problems/validation/enrollment-create-invalid"));
    problem.setTitle("Invalid Enrollment Create Request");
    problem.setProperty("path", request.getDescription(false).replace("uri=", ""));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(IntegrationCreateValidationException.class)
  public ResponseEntity<ProblemDetail> handleIntegrationCreateValidationException(
      IntegrationCreateValidationException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create("https://ezkey.io/problems/validation/integration-create-invalid"));
    problem.setTitle("Invalid Integration Create Request");
    problem.setProperty("path", request.getDescription(false).replace("uri=", ""));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(ApiKeyCreateValidationException.class)
  public ResponseEntity<ProblemDetail> handleApiKeyCreateValidationException(
      ApiKeyCreateValidationException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create("https://ezkey.io/problems/validation/api-key-create-invalid"));
    problem.setTitle("Invalid API Key Create Request");
    problem.setProperty("path", request.getDescription(false).replace("uri=", ""));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(ApiKeyUpdateValidationException.class)
  public ResponseEntity<ProblemDetail> handleApiKeyUpdateValidationException(
      ApiKeyUpdateValidationException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create("https://ezkey.io/problems/validation/api-key-update-invalid"));
    problem.setTitle("Invalid API Key Update Request");
    problem.setProperty("path", request.getDescription(false).replace("uri=", ""));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }

  @ExceptionHandler(ApiKeyIpWhitelistValidationException.class)
  public ResponseEntity<ProblemDetail> handleApiKeyIpWhitelistValidationException(
      ApiKeyIpWhitelistValidationException ex, WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(
        URI.create("https://ezkey.io/problems/validation/api-key-ip-whitelist-invalid"));
    problem.setTitle("Invalid API Key IP Whitelist");
    problem.setProperty("path", request.getDescription(false).replace("uri=", ""));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
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
   * Response: RFC 9457 ProblemDetail with type .../invalid-argument
   * </pre>
   *
   * @param ex the IllegalArgumentException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
      IllegalArgumentException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AdminApiProblemCatalog.TYPE_INVALID_ARGUMENT,
        AdminApiProblemCatalog.TITLE_INVALID_ARGUMENT,
        ex.getMessage(),
        request);
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
   * Response: RFC 9457 ProblemDetail with type .../state-conflict (HTTP 409)
   * </pre>
   *
   * @param ex the IllegalStateException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 409 status
   * @since 2025
   */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ProblemDetail> handleIllegalStateException(
      IllegalStateException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.CONFLICT,
        AdminApiProblemCatalog.TYPE_STATE_CONFLICT,
        AdminApiProblemCatalog.TITLE_STATE_CONFLICT,
        ex.getMessage(),
        request);
  }

  /**
   * Handles ObjectOptimisticLockingFailureException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when an update request uses a stale version (optimistic locking). The resource was
   * modified by another request since the client last fetched it. The client must re-fetch the
   * resource and retry the update with the new version.
   *
   * <p><b>HTTP Status:</b> 409 Conflict
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * Input: PATCH /api/v1/tenants/1 with version=5, but current version is 6
   * Response: RFC 9457 ProblemDetail with type .../optimistic-lock-conflict (HTTP 409)
   * </pre>
   *
   * @param ex the ObjectOptimisticLockingFailureException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 409 status
   * @since 2025
   */
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(
      ObjectOptimisticLockingFailureException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.CONFLICT,
        AdminApiProblemCatalog.TYPE_OPTIMISTIC_LOCK_CONFLICT,
        AdminApiProblemCatalog.TITLE_OPTIMISTIC_LOCK,
        "Resource was modified by another request. Re-fetch and retry.",
        request);
  }

  /**
   * Handles HttpMessageNotReadableException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when Spring's JSON deserializer cannot parse the request body properly. This
   * occurs when:
   *
   * <ul>
   *   <li>Required fields are missing from the JSON payload
   *   <li>Field types do not match the expected types (e.g., string instead of integer)
   *   <li>JSON syntax is invalid
   *   <li>Required constructor parameters for records cannot be satisfied
   * </ul>
   *
   * <p><b>HTTP Status:</b> 400 Bad Request
   *
   * <p><b>Example Scenario:</b> A record DTO requires fields 'name' and 'language', but the JSON
   * request only provides 'language'. Jackson throws HttpMessageNotReadableException because it
   * cannot instantiate the record without all required constructor parameters.
   *
   * <p><b>Security Note:</b> Error messages indicate what is wrong with the request format/data,
   * which is safe to expose.
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * Input: POST /api/v1/integrations with missing required i18n.name field
   * Response: RFC 9457 ProblemDetail with type .../malformed-request
   * </pre>
   *
   * @param ex the HttpMessageNotReadableException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AdminApiProblemCatalog.TYPE_MALFORMED_REQUEST,
        AdminApiProblemCatalog.TITLE_MALFORMED_REQUEST,
        "Invalid request: missing or malformed fields in request body",
        request);
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
   * Response: RFC 9457 ProblemDetail with type .../data-constraint-violation
   * </pre>
   *
   * @param ex the DataIntegrityViolationException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing sanitized constraint violation details and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(
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
      // - "duplicate key value violates unique constraint
      // \"ezkey_admin_username_key\""
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

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AdminApiProblemCatalog.TYPE_DATA_CONSTRAINT_VIOLATION,
        AdminApiProblemCatalog.TITLE_DATA_CONSTRAINT,
        message,
        request);
  }

  /**
   * Handles ConstraintViolationException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when {@code @Validated} on a controller class intercepts an invalid
   * {@code @RequestParam} (e.g., a {@code reason} param that is shorter than 10 characters). Spring
   * AOP throws {@code jakarta.validation.ConstraintViolationException}, which is NOT handled by
   * Spring's default exception resolvers.
   *
   * <p><b>HTTP Status:</b> 400 Bad Request
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * Input: DELETE /api/v1/api-keys/42?reason=short
   * Response: RFC 9457 ProblemDetail with type .../validation-failed
   * </pre>
   *
   * @param ex the ConstraintViolationException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolationException(
      ConstraintViolationException ex, WebRequest request) {
    String message =
        ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .collect(Collectors.joining("; "));
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AdminApiProblemCatalog.TYPE_VALIDATION_FAILED,
        AdminApiProblemCatalog.TITLE_VALIDATION_FAILED,
        message,
        request);
  }

  /**
   * Handles MissingServletRequestParameterException and returns HTTP 400 Bad Request.
   *
   * <p>Triggered when a required {@code @RequestParam} (e.g., {@code reason} on DELETE endpoints)
   * is absent from the request. Without this handler the exception falls through to the generic
   * fallback and produces a misleading 500.
   *
   * @param ex the MissingServletRequestParameterException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 400 status
   * @since 2025
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ProblemDetail> handleMissingServletRequestParameter(
      MissingServletRequestParameterException ex, WebRequest request) {
    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AdminApiProblemCatalog.TYPE_VALIDATION_FAILED,
        AdminApiProblemCatalog.TITLE_VALIDATION_FAILED,
        "Required parameter '" + ex.getParameterName() + "' is missing",
        request);
  }

  /**
   * Handles MissingRequestHeaderException and returns HTTP 401 or 400.
   *
   * <p>Missing {@code Authorization} is treated as an authentication contract failure (401).
   * Missing other required headers are treated as client validation errors (400).
   *
   * @param ex the MissingRequestHeaderException that was thrown
   * @param request the web request that caused the exception
   * @return ResponseEntity containing error details and HTTP 401/400 status
   * @since 2026
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ProblemDetail> handleMissingRequestHeader(
      MissingRequestHeaderException ex, WebRequest request) {
    if ("Authorization".equalsIgnoreCase(ex.getHeaderName())) {
      return problemResponse(
          HttpStatus.UNAUTHORIZED,
          "https://ezkey.io/problems/authentication/missing-authorization-header",
          "Authentication Required",
          "Missing required Authorization header",
          request);
    }

    return problemResponse(
        HttpStatus.BAD_REQUEST,
        AdminApiProblemCatalog.TYPE_VALIDATION_FAILED,
        AdminApiProblemCatalog.TITLE_VALIDATION_FAILED,
        "Required header '" + ex.getHeaderName() + "' is missing",
        request);
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
