/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.dto.ErrorResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("GlobalExceptionHandler SystemTenantNotConfiguredException")
class GlobalExceptionHandlerSystemTenantTest {

  @Test
  @DisplayName("Maps to HTTP 500 with sanitized message")
  void mapsTo500() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/integrations");

    SystemTenantNotConfiguredException ex =
        new SystemTenantNotConfiguredException("System tenant not found (internal detail)");

    ResponseEntity<ErrorResponseDto> response =
        handler.handleSystemTenantNotConfiguredException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    ErrorResponseDto body = response.getBody();
    assertNotNull(body);
    assertEquals("SYSTEM_NOT_CONFIGURED", body.getCode());
    assertEquals("An unexpected error occurred", body.getMessage());
  }
}
