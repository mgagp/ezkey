/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Class: DomainExceptionHandler
 * Description: Handles domain-level business rule exceptions in the Ezkey Admin API.
 */

package org.ezkey.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.integration.exception.IntegrationCodeAlreadyExistsException;
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see ExceptionHandlerBase
 * @see IntegrationCodeAlreadyExistsException
 */
@RestControllerAdvice
@Component
@Order(80)
public class DomainExceptionHandler extends ExceptionHandlerBase {

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
}
