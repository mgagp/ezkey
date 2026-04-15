/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.integration.exception.ApiKeyCreateValidationException;
import org.ezkey.integration.exception.ApiKeyIpWhitelistValidationException;
import org.ezkey.integration.exception.ApiKeyUpdateValidationException;
import org.ezkey.integration.exception.IntegrationCreateValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("ValidationExceptionHandler Wave 7 (integration / API key)")
class ValidationExceptionHandlerWave7Test {

  @Test
  @DisplayName("IntegrationCreateValidationException maps to 400 ProblemDetail")
  void integrationCreate_mapsTo400() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/integrations");

    IntegrationCreateValidationException ex =
        new IntegrationCreateValidationException("Tenant admin must have an associated tenant");

    ResponseEntity<ProblemDetail> response =
        handler.handleIntegrationCreateValidationException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Invalid Integration Create Request", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/validation/integration-create-invalid",
        body.getType().toString());
    assertEquals("/api/v1/integrations", body.getProperties().get("path"));
  }

  @Test
  @DisplayName("ApiKeyCreateValidationException maps to 400 ProblemDetail")
  void apiKeyCreate_mapsTo400() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/api-keys");

    ApiKeyCreateValidationException ex =
        new ApiKeyCreateValidationException("Integration not found with ID: 999");

    ResponseEntity<ProblemDetail> response =
        handler.handleApiKeyCreateValidationException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Invalid API Key Create Request", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/validation/api-key-create-invalid", body.getType().toString());
  }

  @Test
  @DisplayName("ApiKeyUpdateValidationException maps to 400 ProblemDetail")
  void apiKeyUpdate_mapsTo400() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/api-keys/1");

    ApiKeyUpdateValidationException ex =
        new ApiKeyUpdateValidationException("API key cannot be updated: key has been revoked.");

    ResponseEntity<ProblemDetail> response =
        handler.handleApiKeyUpdateValidationException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Invalid API Key Update Request", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/validation/api-key-update-invalid", body.getType().toString());
  }

  @Test
  @DisplayName("ApiKeyIpWhitelistValidationException maps to 400 ProblemDetail")
  void apiKeyIpWhitelist_mapsTo400() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/api-keys/1");

    ApiKeyIpWhitelistValidationException ex =
        new ApiKeyIpWhitelistValidationException("Invalid IP address or CIDR in whitelist: x");

    ResponseEntity<ProblemDetail> response =
        handler.handleApiKeyIpWhitelistValidationException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Invalid API Key IP Whitelist", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/validation/api-key-ip-whitelist-invalid",
        body.getType().toString());
  }

  @Test
  @DisplayName("MissingServletRequestParameterException maps to 400 with param name in detail")
  void missingRequiredParam_mapsTo400() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/integrations/42");

    MissingServletRequestParameterException ex =
        new MissingServletRequestParameterException("reason", "String");

    ResponseEntity<ProblemDetail> response =
        handler.handleMissingServletRequestParameter(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals("Required parameter 'reason' is missing", body.getDetail());
    assertEquals(AdminApiProblemCatalog.TITLE_VALIDATION_FAILED, body.getTitle());
    assertEquals(AdminApiProblemCatalog.TYPE_VALIDATION_FAILED, body.getType().toString());
  }
}
