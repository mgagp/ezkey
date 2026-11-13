/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.auth.AuthAttemptRequestInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Christophe nit on #898: TTL floor (manual) and {@code @Valid} / unreadable body must return the
 * exact same HTTP 400 ProblemDetail.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@DisplayName("Integration API TTL invalid-request ProblemDetail parity (#895)")
class GlobalExceptionHandlerTtlInvalidBodyTest {

  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));

  @Test
  @DisplayName(
      "Manual TTL floor and @Valid MethodArgumentNotValid produce identical 400 ProblemDetail")
  void manualTtlFloorAndBeanValidation_identicalProblemDetail() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts");

    MethodArgumentNotValidException beanValidationEx = mock(MethodArgumentNotValidException.class);
    when(beanValidationEx.getMessage()).thenReturn("Validation failed for ttlSeconds");

    ResponseEntity<ProblemDetail> fromValid =
        handler.handleMethodArgumentNotValid(beanValidationEx, request);
    ResponseEntity<ProblemDetail> fromManual =
        handler.handleAuthAttemptRequestInvalid(
            new AuthAttemptRequestInvalidException("ttlSeconds must be at least 15"), request);

    assertIdenticalInvalidRequestBodies(fromValid, fromManual);
  }

  @Test
  @DisplayName("Unreadable JSON path matches the same invalid-request ProblemDetail")
  void unreadableBody_matchesSameProblemDetail() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts");

    MethodArgumentNotValidException beanValidationEx = mock(MethodArgumentNotValidException.class);
    when(beanValidationEx.getMessage()).thenReturn("Validation failed");

    HttpMessageNotReadableException unreadableEx = mock(HttpMessageNotReadableException.class);
    when(unreadableEx.getMessage()).thenReturn("JSON parse error");

    ResponseEntity<ProblemDetail> fromValid =
        handler.handleMethodArgumentNotValid(beanValidationEx, request);
    ResponseEntity<ProblemDetail> fromUnreadable =
        handler.handleHttpMessageNotReadable(unreadableEx, request);

    assertIdenticalInvalidRequestBodies(fromValid, fromUnreadable);
  }

  private static void assertIdenticalInvalidRequestBodies(
      ResponseEntity<ProblemDetail> left, ResponseEntity<ProblemDetail> right) {
    assertThat(left.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(right.getStatusCode()).isEqualTo(left.getStatusCode());

    ProblemDetail leftBody = left.getBody();
    ProblemDetail rightBody = right.getBody();
    assertThat(leftBody).isNotNull();
    assertThat(rightBody).isNotNull();

    assertThat(rightBody.getType()).isEqualTo(leftBody.getType());
    assertThat(rightBody.getTitle()).isEqualTo(leftBody.getTitle());
    assertThat(rightBody.getStatus()).isEqualTo(leftBody.getStatus());
    assertThat(rightBody.getDetail()).isEqualTo(leftBody.getDetail());
    assertThat(rightBody.getDetail())
        .isEqualTo(IntegrationApiProblemCatalog.DETAIL_REQUEST_INVALID);
    assertThat(rightBody.getType().toString())
        .isEqualTo(IntegrationApiProblemCatalog.TYPE_REQUEST_INVALID);
    assertThat(rightBody.getTitle()).isEqualTo(IntegrationApiProblemCatalog.TITLE_REQUEST_INVALID);
  }
}
