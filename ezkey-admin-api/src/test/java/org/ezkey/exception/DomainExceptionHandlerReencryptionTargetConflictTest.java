/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DomainExceptionHandlerReencryptionTargetConflictTest
 * Description: HTTP 409 mapping for ReencryptionTargetConflictException (#921).
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.security.exception.ReencryptionTargetConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Proves #921 conflict is mapped to HTTP 409 (not swallowed as a generic IllegalStateException).
 *
 * @since 2026
 */
@DisplayName("DomainExceptionHandler ReencryptionTargetConflictException")
class DomainExceptionHandlerReencryptionTargetConflictTest {

  @Test
  @DisplayName("old==new re-encryption conflict maps to HTTP 409")
  void reencryptionTargetConflictMapsTo409() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/encryption-keys/42/reencrypt");

    ReencryptionTargetConflictException ex =
        new ReencryptionTargetConflictException(
            42L, 42L, "Cannot re-encrypt: old key equals primary; refusing silent no-op");

    ResponseEntity<ProblemDetail> response =
        handler.handleReencryptionTargetConflictException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Re-encryption Target Conflict", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/reencryption-target-conflict", body.getType().toString());
  }
}
