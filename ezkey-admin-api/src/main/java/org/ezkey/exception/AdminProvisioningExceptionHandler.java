/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.ezkey.admin.exception.GlobalAdminLimitException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handles admin provisioning business exceptions with RFC 9457 Problem Details.
 *
 * <p>Order is between {@link AuthorizationExceptionHandler} (30) and {@link
 * EnrollmentExceptionHandler} (35).
 */
@RestControllerAdvice
@Component
@Order(32)
public class AdminProvisioningExceptionHandler {

  private static final String TYPE_GLOBAL_ADMIN_LIMIT_REACHED =
      "https://ezkey.io/problems/admin-provisioning/global-admin-limit-reached";

  /**
   * Handles {@link GlobalAdminLimitException} — active global admin count is at the configured
   * maximum.
   *
   * <p>Includes RFC 9457 extension property {@code parameters} with {@code maxGlobalAdmins} for
   * client-side localization.
   */
  @ExceptionHandler(GlobalAdminLimitException.class)
  public ResponseEntity<ProblemDetail> handleGlobalAdminLimitException(
      GlobalAdminLimitException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create(TYPE_GLOBAL_ADMIN_LIMIT_REACHED));
    problem.setTitle("Global administrator limit reached");
    problem.setProperty("path", request.getRequestURI());
    problem.setProperty("parameters", Map.of("maxGlobalAdmins", ex.getMaxGlobalAdmins()));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
  }
}
