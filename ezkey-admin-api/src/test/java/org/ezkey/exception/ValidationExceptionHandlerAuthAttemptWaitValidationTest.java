/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("ValidationExceptionHandler AuthAttemptWaitValidationException")
class ValidationExceptionHandlerAuthAttemptWaitValidationTest {

  @Test
  @DisplayName("Maps to HTTP 400 with ProblemDetail")
  void mapsTo400ProblemDetail() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts/123/wait");

    AuthAttemptWaitValidationException ex =
        new AuthAttemptWaitValidationException("Polling must be between 1 and 60 seconds");

    ResponseEntity<ProblemDetail> response =
        handler.handleAuthAttemptWaitValidationException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Invalid Auth Attempt Wait Request", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/validation/auth-attempt-wait-invalid",
        body.getType().toString());
    assertEquals("/api/v1/auth-attempts/123/wait", body.getProperties().get("path"));
  }
}
