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
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("ValidationExceptionHandler MissingRequestHeaderException")
class ValidationExceptionHandlerMissingRequestHeaderTest {

  @Test
  @DisplayName("Missing Authorization header maps to 401 authentication problem")
  void missingAuthorizationHeaderMapsTo401() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/admin/enrollments/reset");

    MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
    when(ex.getHeaderName()).thenReturn("Authorization");

    ResponseEntity<ProblemDetail> response =
        handler.handleMissingRequestHeader(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(401, body.getStatus());
    assertEquals("Authentication Required", body.getTitle());
    assertEquals("Missing required Authorization header", body.getDetail());
    assertEquals(
        "https://ezkey.io/problems/authentication/missing-authorization-header",
        body.getType().toString());
    assertEquals("/api/v1/admin/enrollments/reset", body.getProperties().get("path"));
  }

  @Test
  @DisplayName("Missing non-auth header maps to 400 validation problem")
  void missingNonAuthHeaderMapsTo400() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/some-endpoint");

    MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
    when(ex.getHeaderName()).thenReturn("X-Custom-Header");

    ResponseEntity<ProblemDetail> response =
        handler.handleMissingRequestHeader(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(AdminApiProblemCatalog.TITLE_VALIDATION_FAILED, body.getTitle());
    assertEquals("Required header 'X-Custom-Header' is missing", body.getDetail());
    assertEquals(AdminApiProblemCatalog.TYPE_VALIDATION_FAILED, body.getType().toString());
    assertEquals("/api/v1/some-endpoint", body.getProperties().get("path"));
  }
}
