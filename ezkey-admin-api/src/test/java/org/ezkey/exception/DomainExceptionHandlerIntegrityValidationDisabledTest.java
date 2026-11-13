/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DomainExceptionHandlerIntegrityValidationDisabledTest
 * Description: Verifies RFC 9457 mapping for IntegrityValidationDisabledException.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.audit.exception.IntegrityValidationDisabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link DomainExceptionHandler} handling of {@link
 * IntegrityValidationDisabledException}.
 */
@DisplayName("DomainExceptionHandler IntegrityValidationDisabledException")
class DomainExceptionHandlerIntegrityValidationDisabledTest {

  @Test
  @DisplayName("Nightly validation disabled maps to HTTP 409")
  void nightlyDisabledMapsTo409() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/audit-logs/integrity-validation/run");

    IntegrityValidationDisabledException ex = new IntegrityValidationDisabledException();

    ResponseEntity<ProblemDetail> response =
        handler.handleIntegrityValidationDisabledException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Integrity validation inactive", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/integrity-validation-disabled",
        body.getType().toString());
  }
}
