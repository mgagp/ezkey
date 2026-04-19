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
  @DisplayName("NoResourceFoundException maps to 404 ProblemDetail without domain message leakage")
  void noResourceFound_mapsTo404() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/wp-admin");

    NoResourceFoundException ex =
        new NoResourceFoundException(HttpMethod.GET, "/wp-admin", "/wp-admin");

    ResponseEntity<ProblemDetail> response =
        handler.handleNoResourceFoundException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(404, body.getStatus());
    assertEquals(URI.create(AdminApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND), body.getType());
    assertEquals(AdminApiProblemCatalog.TITLE_NOT_FOUND, body.getTitle());
    assertEquals(AdminApiProblemCatalog.DETAIL_NOT_FOUND, body.getDetail());
    assertEquals("/wp-admin", body.getProperties().get("path"));
  }

  @Test
  @DisplayName("NoHandlerFoundException maps to 404 ProblemDetail")
  void noHandlerFound_mapsTo404() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/missing-route");

    NoHandlerFoundException ex =
        new NoHandlerFoundException("GET", "/missing-route", HttpHeaders.EMPTY);

    ResponseEntity<ProblemDetail> response =
        handler.handleNoHandlerFoundException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(404, body.getStatus());
    assertEquals(URI.create(AdminApiProblemCatalog.TYPE_RESOURCE_NOT_FOUND), body.getType());
    assertEquals("/missing-route", body.getProperties().get("path"));
  }
}
