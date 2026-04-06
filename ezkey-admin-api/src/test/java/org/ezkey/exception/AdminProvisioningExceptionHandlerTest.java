/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.ezkey.admin.exception.GlobalAdminLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("AdminProvisioningExceptionHandler")
class AdminProvisioningExceptionHandlerTest {

  @Test
  @DisplayName("Maps GlobalAdminLimitException to HTTP 400 with parameters extension")
  void mapsGlobalAdminLimitTo400WithParameters() {
    AdminProvisioningExceptionHandler handler = new AdminProvisioningExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/admins");

    GlobalAdminLimitException ex = new GlobalAdminLimitException(3);

    ResponseEntity<ProblemDetail> response = handler.handleGlobalAdminLimitException(ex, request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Global administrator limit reached", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/admin-provisioning/global-admin-limit-reached",
        body.getType().toString());
    assertEquals("/api/v1/admins", body.getProperties().get("path"));
    @SuppressWarnings("unchecked")
    Map<String, Object> parameters = (Map<String, Object>) body.getProperties().get("parameters");
    assertNotNull(parameters);
    assertEquals(3, parameters.get("maxGlobalAdmins"));
  }
}
