/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DomainExceptionHandlerIntegrityWindowOverCapTest
 * Description: Verifies 400 mapping for IntegrityWindowOverCapException.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.audit.exception.IntegrityWindowOverCapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link DomainExceptionHandler} handling of {@link
 * IntegrityWindowOverCapException}.
 *
 * @since 2026
 */
@DisplayName("DomainExceptionHandler IntegrityWindowOverCapException")
class DomainExceptionHandlerIntegrityWindowOverCapTest {

  @Test
  @DisplayName("over-cap maps to HTTP 400 with integrity-window-over-cap type")
  void overCapMapsTo400WithStableType() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/audit-logs/chain-integrity");

    IntegrityWindowOverCapException ex = new IntegrityWindowOverCapException(193);

    ResponseEntity<ProblemDetail> response =
        handler.handleIntegrityWindowOverCapException(ex, request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(IntegrityWindowOverCapException.TYPE_URI, body.getType().toString());
  }
}
