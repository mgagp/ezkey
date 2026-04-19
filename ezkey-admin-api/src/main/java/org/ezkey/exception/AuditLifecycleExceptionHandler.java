/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.ezkey.audit.exception.AuditLifecycleConflictException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles audit lifecycle conflicts with a dedicated RFC 9457 problem type.
 *
 * @since 2026
 */
@RestControllerAdvice
@Order(9)
public class AuditLifecycleExceptionHandler extends ExceptionHandlerBase {

  @ExceptionHandler(AuditLifecycleConflictException.class)
  public ResponseEntity<ProblemDetail> handleAuditLifecycleConflictException(
      AuditLifecycleConflictException ex, HttpServletRequest request) {
    return buildProblemDetail(
        ex,
        HttpStatus.CONFLICT,
        AdminApiProblemCatalog.BASE + "/audit-lifecycle-conflict",
        "Audit lifecycle conflict",
        request);
  }
}
