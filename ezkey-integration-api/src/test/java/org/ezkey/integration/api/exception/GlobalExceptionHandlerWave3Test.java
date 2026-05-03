/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("Integration API GlobalExceptionHandler Wave 3")
class GlobalExceptionHandlerWave3Test {

  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));

  @Test
  @DisplayName("Maps auth attempt wait validation exception to HTTP 400")
  void mapsAuthAttemptWaitValidationTo400() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts/123/wait");

    ResponseEntity<ProblemDetail> response =
        handler.handleAuthAttemptWaitValidationException(
            new AuthAttemptWaitValidationException("Polling must be between 1 and 60 seconds"),
            request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Invalid Auth Attempt Wait Request", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/validation/auth-attempt-wait-invalid",
        body.getType().toString());
  }
}
