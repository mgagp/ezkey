/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("AuthorizationExceptionHandler TenantInactiveException")
class AuthorizationExceptionHandlerTenantInactiveTest {

  @Test
  @DisplayName("Maps shared tenant inactive exception to HTTP 403 with ProblemDetail")
  void mapsTo403ProblemDetail() {
    AuthorizationExceptionHandler handler = new AuthorizationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/integrations");

    TenantInactiveException ex =
        new TenantInactiveException("Cannot create integration for inactive tenant.");

    ResponseEntity<ProblemDetail> response = handler.handleTenantInactiveException(ex, request);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(403, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Tenant Inactive", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/authorization/tenant-inactive", body.getType().toString());
    assertEquals("/api/v1/integrations", body.getProperties().get("path"));
  }
}
