/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ValidationExceptionHandlerMediaTypeTest
 * Description: Regression coverage for malformed and unsupported request content types.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartException;

/** Regression tests for request media-type failures that must not surface as HTTP 500. */
@DisplayName("ValidationExceptionHandler media type errors")
class ValidationExceptionHandlerMediaTypeTest {

  @Test
  @DisplayName("Unsupported media type maps to 415 ProblemDetail")
  void unsupportedMediaTypeMapsTo415() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/admin/auth/activate");

    HttpMediaTypeNotSupportedException ex =
        new HttpMediaTypeNotSupportedException(
            MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

    ResponseEntity<ProblemDetail> response =
        handler.handleHttpMediaTypeNotSupportedException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(415, body.getStatus());
    assertEquals(URI.create(AdminApiProblemCatalog.TYPE_UNSUPPORTED_MEDIA_TYPE), body.getType());
    assertEquals(AdminApiProblemCatalog.TITLE_UNSUPPORTED_MEDIA_TYPE, body.getTitle());
    assertEquals(AdminApiProblemCatalog.DETAIL_UNSUPPORTED_MEDIA_TYPE, body.getDetail());
    assertEquals("/api/v1/admin/auth/activate", body.getProperties().get("path"));
  }

  @Test
  @DisplayName("Malformed multipart maps to 400 ProblemDetail")
  void malformedMultipartMapsTo400() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/public/instance-info");

    ResponseEntity<ProblemDetail> response =
        handler.handleMultipartException(
            new MultipartException("missing boundary"), new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(URI.create(AdminApiProblemCatalog.TYPE_MALFORMED_REQUEST), body.getType());
    assertEquals(AdminApiProblemCatalog.TITLE_MALFORMED_REQUEST, body.getTitle());
    assertEquals("Invalid multipart request", body.getDetail());
    assertEquals("/api/v1/public/instance-info", body.getProperties().get("path"));
  }
}
