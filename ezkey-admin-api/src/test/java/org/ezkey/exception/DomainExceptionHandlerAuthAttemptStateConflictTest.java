/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("DomainExceptionHandler AuthAttemptStateConflictException")
class DomainExceptionHandlerAuthAttemptStateConflictTest {

  @Test
  @DisplayName("Maps to HTTP 409 with ProblemDetail")
  void mapsTo409ProblemDetail() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts/123/cancel");

    AuthAttemptStateConflictException ex =
        new AuthAttemptStateConflictException(
            "Cannot cancel authentication attempt with status: EXPIRED");

    ResponseEntity<ProblemDetail> response =
        handler.handleAuthAttemptStateConflictException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Auth Attempt State Conflict", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/auth-attempt-state-conflict", body.getType().toString());
    assertEquals("/api/v1/auth-attempts/123/cancel", body.getProperties().get("path"));
  }
}
