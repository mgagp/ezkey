/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: DomainExceptionHandler
 * Description: Handles domain-level business rule exceptions in the Ezkey Admin API.
 */

package org.ezkey.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.audit.dto.IntegrityAsyncJobResponse;
import org.ezkey.audit.exception.IntegrityAsyncJobAbandonNotAllowedException;
import org.ezkey.audit.exception.IntegrityAsyncJobBusyException;
import org.ezkey.audit.exception.IntegrityValidationDisabledException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.integration.exception.ApiKeyLimitExceededException;
import org.ezkey.integration.exception.IntegrationCodeAlreadyExistsException;
import org.ezkey.integration.exception.IntegrationHasEnrollmentsException;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.ezkey.integration.exception.SystemIntegrationLifecycleException;
import org.ezkey.security.exception.EncryptionLifecycleDisabledException;
import org.ezkey.security.exception.PendingEncryptionKeyExistsException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles domain-level business rule exceptions in the Ezkey Admin REST API.
 *
 * <p><b>Responsibility:</b> This component intercepts domain exceptions thrown during business
 * logic validation and converts them into RFC 9457 ProblemDetail responses with appropriate HTTP
 * status codes.
 *
 * <p><b>Exceptions Handled (1+ total):</b>
 *
 * <ul>
 *   <li><b>IntegrationCodeAlreadyExistsException (409):</b> Integration code already exists for the
 *       tenant
 *   <li><b>IntegrationHasEnrollmentsException (409):</b> Integration cannot be deleted because it
 *       has one or more enrollments
 *   <li><b>PendingEncryptionKeyExistsException (409):</b> A new encryption key cannot be introduced
 *       while a PENDING key already exists
 * </ul>
 *
 * <p><b>Response Format:</b> All responses conform to RFC 9457 (Problem Details for HTTP APIs) with
 * the following structure:
 *
 * <pre>{@code
 * {
 * "type": "https://ezkey.io/problems/domain/...",
 * "title": "Error Category",
 * "status": HTTP_STATUS_CODE,
 * "detail": "Specific error message",
 * "path": "/api/v1/admin/endpoint"
 * }
 * }</pre>
 *
 * <p><b>HTTP Status Codes:</b>
 *
 * <ul>
 *   <li>409 - Business logic violations (duplicate integration codes, uniqueness constraints)
 * </ul>
 *
 * <p><b>Integration:</b> This handler is registered as a Spring component and automatically picked
 * up by the @RestControllerAdvice scanning mechanism with @Order(80) priority.
 *
 * <p><b>Design Notes:</b>
 *
 * <ul>
 *   <li>Each handler delegates response building to {@link ExceptionHandlerBase#buildProblemDetail}
 *       for consistency
 *   <li>Problem type URIs follow the convention: https://ezkey.io/problems/domain/{error-category}
 *   <li>This allows future clients to programmatically identify error categories
 *   <li>This handler is extensible: additional domain exceptions can be added without modifying
 *       other handlers
 *   <li>Order 80 ensures this handler is invoked before the generic GlobalExceptionHandler (Order
 *       99)
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ExceptionHandlerBase
 * @see IntegrationCodeAlreadyExistsException
 * @see IntegrationHasEnrollmentsException
 * @see PendingEncryptionKeyExistsException
 */
@RestControllerAdvice
@Component
@Order(80)
public class DomainExceptionHandler extends ExceptionHandlerBase {

  /**
   * Handles PendingEncryptionKeyExistsException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when key introduction is rejected because a PENDING encryption key already exists.
   *
   * <p><b>HTTP Status:</b> 409 Conflict
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * @param ex the PendingEncryptionKeyExistsException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 409 status
   */
  @ExceptionHandler(PendingEncryptionKeyExistsException.class)
  public ResponseEntity<ProblemDetail> handlePendingEncryptionKeyExistsException(
      PendingEncryptionKeyExistsException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/pending-encryption-key-exists",
        "Pending Encryption Key Exists",
        request);
  }

  /**
   * Handles IntegrationHasEnrollmentsException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when an administrator attempts to delete an integration that has one or more
   * enrollments. Enrollments must be removed or revoked before the integration can be deleted.
   *
   * <p><b>HTTP Status:</b> 409 Conflict (Operation not allowed in current state)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * @param ex the IntegrationHasEnrollmentsException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 409 status
   * @since 2025
   */
  @ExceptionHandler(IntegrationHasEnrollmentsException.class)
  public ResponseEntity<ProblemDetail> handleIntegrationHasEnrollmentsException(
      IntegrationHasEnrollmentsException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/integration-has-enrollments",
        "Integration Has Enrollments",
        request);
  }

  /**
   * Handles IntegrationCodeAlreadyExistsException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when an administrator attempts to create an integration with a code that already
   * exists for the tenant.
   *
   * <p><b>HTTP Status:</b> 409 Conflict (Resource already exists with the specified identifier)
   *
   * <p><b>Response Format:</b> RFC 9457 ProblemDetail
   *
   * <p><b>Example Response:</b>
   *
   * <pre>{@code
   * {
   * "type": "https://ezkey.io/problems/domain/integration-code-already-exists",
   * "title": "Integration Code Already Exists",
   * "status": 409,
   * "detail": "Integration with code 'web-portal' already exists for tenant 'Acme
   * Corp'",
   * "path": "/api/v1/admin/integrations"
   * }
   * }</pre>
   *
   * @param ex the IntegrationCodeAlreadyExistsException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 409 status
   * @since 2025
   */
  @ExceptionHandler(IntegrationCodeAlreadyExistsException.class)
  public ResponseEntity<ProblemDetail> handleIntegrationCodeAlreadyExistsException(
      IntegrationCodeAlreadyExistsException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/integration-code-already-exists",
        "Integration Code Already Exists",
        request);
  }

  @ExceptionHandler(IntegrationLifecycleStateException.class)
  public ResponseEntity<ProblemDetail> handleIntegrationLifecycleStateException(
      IntegrationLifecycleStateException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/integration-lifecycle-state",
        "Integration Lifecycle State Conflict",
        request);
  }

  @ExceptionHandler(SystemIntegrationLifecycleException.class)
  public ResponseEntity<ProblemDetail> handleSystemIntegrationLifecycleException(
      SystemIntegrationLifecycleException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.FORBIDDEN,
        "https://ezkey.io/problems/domain/system-integration-lifecycle",
        "System Integration Lifecycle Protected",
        request);
  }

  @ExceptionHandler(ApiKeyLimitExceededException.class)
  public ResponseEntity<ProblemDetail> handleApiKeyLimitExceededException(
      ApiKeyLimitExceededException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/api-key-limit-exceeded",
        "API Key Limit Exceeded",
        request);
  }

  @ExceptionHandler(AuthAttemptStateConflictException.class)
  public ResponseEntity<ProblemDetail> handleAuthAttemptStateConflictException(
      AuthAttemptStateConflictException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/auth-attempt-state-conflict",
        "Auth Attempt State Conflict",
        request);
  }

  /**
   * Handles EncryptionLifecycleDisabledException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when rotation or re-encryption is requested while that lifecycle flag is off
   * (typical on the opt-in base runtime).
   *
   * @param ex the EncryptionLifecycleDisabledException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 409 status
   */
  @ExceptionHandler(EncryptionLifecycleDisabledException.class)
  public ResponseEntity<ProblemDetail> handleEncryptionLifecycleDisabledException(
      EncryptionLifecycleDisabledException ex, HttpServletRequest request) {
    boolean rotation = ex.getOperation() == EncryptionLifecycleDisabledException.Operation.ROTATION;
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        rotation
            ? "https://ezkey.io/problems/domain/encryption-rotation-disabled"
            : "https://ezkey.io/problems/domain/encryption-reencryption-disabled",
        rotation ? "Encryption rotation inactive" : "Re-encryption inactive",
        request);
  }

  /**
   * Handles IntegrityValidationDisabledException and returns HTTP 409 Conflict.
   *
   * <p>Triggered when operator (or any) retroactive validation is requested while nightly integrity
   * is off (typical on the opt-in base runtime).
   *
   * @param ex the IntegrityValidationDisabledException that was thrown
   * @param request the HTTP servlet request for path extraction
   * @return ResponseEntity containing ProblemDetail and HTTP 409 status
   */
  @ExceptionHandler(IntegrityValidationDisabledException.class)
  public ResponseEntity<ProblemDetail> handleIntegrityValidationDisabledException(
      IntegrityValidationDisabledException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        "https://ezkey.io/problems/domain/integrity-validation-disabled",
        "Integrity validation inactive",
        request);
  }

  /**
   * Handles IntegrityAsyncJobBusyException and returns HTTP 409 with current job resume summary.
   *
   * @param ex the busy exception
   * @param request the HTTP servlet request
   * @return ProblemDetail with {@code currentJob} extension
   */
  @ExceptionHandler(IntegrityAsyncJobBusyException.class)
  public ResponseEntity<ProblemDetail> handleIntegrityAsyncJobBusyException(
      IntegrityAsyncJobBusyException ex, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        buildProblemDetail(
            ex,
            HttpStatus.CONFLICT,
            "https://ezkey.io/problems/domain/integrity-async-job-busy",
            "Integrity async slot busy",
            request);
    ProblemDetail problem = response.getBody();
    if (problem != null && ex.getCurrentJob() != null) {
      IntegrityAsyncJobResponse current = IntegrityAsyncJobResponse.from(ex.getCurrentJob());
      problem.setProperty("currentJob", current);
      problem.setProperty("resumeOneLiner", current.resumeOneLiner());
    }
    return response;
  }

  /**
   * Handles IntegrityAsyncJobAbandonNotAllowedException and returns HTTP 409.
   *
   * @param ex the abandon refusal
   * @param request the HTTP servlet request
   * @return ProblemDetail
   */
  @ExceptionHandler(IntegrityAsyncJobAbandonNotAllowedException.class)
  public ResponseEntity<ProblemDetail> handleIntegrityAsyncJobAbandonNotAllowedException(
      IntegrityAsyncJobAbandonNotAllowedException ex, HttpServletRequest request) {
    ResponseEntity<ProblemDetail> response =
        buildProblemDetail(
            ex,
            HttpStatus.CONFLICT,
            "https://ezkey.io/problems/domain/integrity-async-job-abandon-not-allowed",
            "Integrity async job abandon not allowed",
            request);
    ProblemDetail problem = response.getBody();
    if (problem != null && ex.getStatus() != null) {
      problem.setProperty("status", ex.getStatus().name());
    }
    return response;
  }
}
