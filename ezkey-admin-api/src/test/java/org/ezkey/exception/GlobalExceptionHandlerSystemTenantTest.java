/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("GlobalExceptionHandler SystemTenantNotConfiguredException")
class GlobalExceptionHandlerSystemTenantTest {

  @Test
  @DisplayName("Maps to HTTP 500 with RFC 9457 ProblemDetail and sanitized detail")
  void mapsTo500() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/integrations");

    SystemTenantNotConfiguredException ex =
        new SystemTenantNotConfiguredException("System tenant not found (internal detail)");

    ResponseEntity<ProblemDetail> response =
        handler.handleSystemTenantNotConfiguredException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(500, body.getStatus());
    assertEquals(URI.create(AdminApiProblemCatalog.TYPE_SYSTEM_NOT_CONFIGURED), body.getType());
    assertEquals(AdminApiProblemCatalog.TITLE_INTERNAL_ERROR, body.getTitle());
    assertEquals(AdminApiProblemCatalog.DETAIL_UNEXPECTED, body.getDetail());
    assertEquals("/api/v1/integrations", body.getProperties().get("path"));
  }
}
