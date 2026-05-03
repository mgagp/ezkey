/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.net.URI;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@DisplayName("GlobalExceptionHandler Spring MVC not-found exceptions")
class GlobalExceptionHandlerNotFoundMvcTest {

  @Test
  @DisplayName("NoResourceFoundException maps to 404 ProblemDetail with catalog-safe detail")
  void noResourceFound_mapsTo404() {
    GlobalExceptionHandler handler =
        new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/probe");

    NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/probe", "/probe");

    ResponseEntity<ProblemDetail> response =
        handler.handleNoResourceFoundException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(404, body.getStatus());
    assertEquals(URI.create(AuthApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND), body.getType());
    assertEquals(AuthApiProblemCatalog.DETAIL_RESOURCE_NOT_FOUND, body.getDetail());
    assertEquals("/probe", body.getProperties().get("path"));
  }

  @Test
  @DisplayName("NoHandlerFoundException maps to 404 ProblemDetail")
  void noHandlerFound_mapsTo404() {
    GlobalExceptionHandler handler =
        new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/nope");

    NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/nope", HttpHeaders.EMPTY);

    ResponseEntity<ProblemDetail> response =
        handler.handleNoHandlerFoundException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(404, body.getStatus());
    assertEquals("/nope", body.getProperties().get("path"));
  }
}
