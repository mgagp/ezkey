/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.exception.TenantInactiveException;
import org.ezkey.integration.exception.ApiKeyLimitExceededException;
import org.ezkey.integration.exception.IntegrationLifecycleStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("Integration API GlobalExceptionHandler Wave 1")
class GlobalExceptionHandlerWave1Test {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  @DisplayName("Maps tenant inactive exception to HTTP 403")
  void mapsTenantInactiveTo403() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts");

    ResponseEntity<ProblemDetail> response =
        handler.handleTenantInactiveException(
            new TenantInactiveException("Tenant is inactive. Contact your Ezkey administrator."),
            request);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Tenant Inactive", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/authorization/tenant-inactive", body.getType().toString());
  }

  @Test
  @DisplayName("Maps integration lifecycle state exception to HTTP 409")
  void mapsIntegrationLifecycleStateTo409() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts");

    ResponseEntity<ProblemDetail> response =
        handler.handleIntegrationLifecycleStateException(
            new IntegrationLifecycleStateException("Integration is not active."), request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Integration Lifecycle State Conflict", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/integration-lifecycle-state", body.getType().toString());
  }

  @Test
  @DisplayName("Maps API key limit exception to HTTP 409")
  void mapsApiKeyLimitExceededTo409() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts");

    ResponseEntity<ProblemDetail> response =
        handler.handleApiKeyLimitExceededException(
            new ApiKeyLimitExceededException("Maximum active keys limit reached."), request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("API Key Limit Exceeded", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/api-key-limit-exceeded", body.getType().toString());
  }
}
