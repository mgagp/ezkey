/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("Integration API GlobalExceptionHandler Wave 5")
class GlobalExceptionHandlerWave5Test {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("Maps auth attempt create validation exception to HTTP 400")
  void mapsAuthAttemptCreateValidationTo400() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts");

    ResponseEntity<ProblemDetail> response =
        handler.handleAuthAttemptCreateValidationException(
            new AuthAttemptCreateValidationException(
                "Either enrollmentId or userIdentifier is required."),
            request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Invalid Auth Attempt Create Request", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/validation/auth-attempt-create-invalid",
        body.getType().toString());
  }
}
