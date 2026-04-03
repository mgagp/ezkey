/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("Integration API GlobalExceptionHandler Wave 2")
class GlobalExceptionHandlerWave2Test {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("Maps auth attempt state conflict exception to HTTP 409")
  void mapsAuthAttemptStateConflictTo409() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts/123/cancel");

    ResponseEntity<ProblemDetail> response =
        handler.handleAuthAttemptStateConflictException(
            new AuthAttemptStateConflictException(
                "Cannot cancel authentication attempt with status: EXPIRED"),
            request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Auth Attempt State Conflict", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/auth-attempt-state-conflict", body.getType().toString());
  }
}
