/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("GlobalExceptionHandler unsupported HTTP method")
class GlobalExceptionHandlerMethodNotAllowedMvcTest {

  @Test
  @DisplayName("HttpRequestMethodNotSupportedException maps to 405 with Allow header")
  void methodNotSupported_mapsTo405WithAllow() {
    GlobalExceptionHandler handler =
        new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/enrollments/verify");

    HttpRequestMethodNotSupportedException ex =
        new HttpRequestMethodNotSupportedException("PUT", List.of("POST"));

    ResponseEntity<ProblemDetail> response =
        handler.handleHttpRequestMethodNotSupported(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    assertEquals(Set.of(HttpMethod.POST), response.getHeaders().getAllow());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(405, body.getStatus());
    assertEquals(URI.create(AuthApiProblemCatalog.TYPE_METHOD_NOT_ALLOWED), body.getType());
    assertEquals(AuthApiProblemCatalog.TITLE_METHOD_NOT_ALLOWED, body.getTitle());
    assertEquals(AuthApiProblemCatalog.DETAIL_METHOD_NOT_ALLOWED, body.getDetail());
    assertEquals("/api/v1/enrollments/verify", body.getProperties().get("path"));
  }
}
