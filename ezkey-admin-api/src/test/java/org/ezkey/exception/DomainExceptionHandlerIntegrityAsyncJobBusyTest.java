/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DomainExceptionHandlerIntegrityAsyncJobBusyTest
 * Description: Verifies 409 busy mapping includes Retry-After.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.audit.exception.IntegrityAsyncJobBusyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link DomainExceptionHandler} handling of {@link IntegrityAsyncJobBusyException}.
 *
 * @since 2026
 */
@DisplayName("DomainExceptionHandler IntegrityAsyncJobBusyException")
class DomainExceptionHandlerIntegrityAsyncJobBusyTest {

  @Test
  @DisplayName("busy maps to HTTP 409 with Retry-After")
  void busyMapsTo409WithRetryAfter() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/audit-logs/chain-integrity");

    IntegrityAsyncJobBusyException ex = IntegrityAsyncJobBusyException.forHeavyCryptoBusy();

    ResponseEntity<ProblemDetail> response =
        handler.handleIntegrityAsyncJobBusyException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    assertEquals(
        DomainExceptionHandler.INTEGRITY_BUSY_RETRY_AFTER_SECONDS,
        response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(
        "https://ezkey.io/problems/domain/integrity-async-job-busy", body.getType().toString());
  }
}
