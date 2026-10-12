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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
  @DisplayName("async over-cap maps to 400 with maxWindowDays, capKind=ASYNC, no asyncAlternative")
  void asyncOverCapMapsTo400WithMaxWindowDaysAndCapKindAsync() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/audit-logs/integrity/jobs");

    IntegrityWindowOverCapException ex = new IntegrityWindowOverCapException(92);

    ResponseEntity<ProblemDetail> response =
        handler.handleIntegrityWindowOverCapException(ex, request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(IntegrityWindowOverCapException.TYPE_URI, body.getType().toString());
    assertEquals(92, body.getProperties().get("maxWindowDays"));
    assertEquals(
        IntegrityWindowOverCapException.CAP_KIND_ASYNC, body.getProperties().get("capKind"));
    assertFalse(body.getProperties().containsKey("asyncAlternative"));
    assertNull(ex.getAsyncAlternative());
  }

  @Test
  @DisplayName(
      "sync over-cap maps to 400 with maxWindowDays, capKind=SYNC, and fixed asyncAlternative")
  void syncOverCapMapsTo400WithCapKindSyncAndFixedAsyncAlternative() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/audit-logs/chain-integrity");

    IntegrityWindowOverCapException ex = IntegrityWindowOverCapException.forSyncCap(8);

    ResponseEntity<ProblemDetail> response =
        handler.handleIntegrityWindowOverCapException(ex, request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(8, body.getProperties().get("maxWindowDays"));
    assertEquals(
        IntegrityWindowOverCapException.CAP_KIND_SYNC, body.getProperties().get("capKind"));
    assertEquals(
        IntegrityWindowOverCapException.ASYNC_ALTERNATIVE,
        body.getProperties().get("asyncAlternative"));
    assertEquals(
        "POST /api/v1/audit-logs/integrity/jobs", body.getProperties().get("asyncAlternative"));
  }
}
