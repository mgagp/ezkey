/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.integration.exception.ApiKeyLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("DomainExceptionHandler ApiKeyLimitExceededException")
class DomainExceptionHandlerApiKeyLimitTest {

  @Test
  @DisplayName("Maps to HTTP 409 with ProblemDetail")
  void mapsTo409ProblemDetail() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/api-keys");

    ApiKeyLimitExceededException ex =
        new ApiKeyLimitExceededException(
            "Maximum active keys limit (5) reached for integration: 123");

    ResponseEntity<ProblemDetail> response =
        handler.handleApiKeyLimitExceededException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("API Key Limit Exceeded", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/api-key-limit-exceeded", body.getType().toString());
    assertEquals("/api/v1/api-keys", body.getProperties().get("path"));
  }
}
