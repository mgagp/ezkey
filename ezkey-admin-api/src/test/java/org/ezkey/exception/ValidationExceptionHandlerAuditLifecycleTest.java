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
import org.springframework.web.context.request.ServletWebRequest;

@DisplayName("ValidationExceptionHandler Audit Lifecycle")
class ValidationExceptionHandlerAuditLifecycleTest {

  @Test
  @DisplayName("IllegalArgumentException maps lifecycle invalid request to HTTP 400 ProblemDetail")
  void illegalArgument_mapsLifecycleRequestTo400ProblemDetail() {
    ValidationExceptionHandler handler = new ValidationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/audit-logs/lifecycle/confirm-archived");

    IllegalArgumentException ex =
        new IllegalArgumentException(
            "Provide either (checkpointIdFrom + checkpointIdTo) or (periodStart + periodEnd), not"
                + " both.");

    ResponseEntity<ProblemDetail> response =
        handler.handleIllegalArgumentException(ex, new ServletWebRequest(request));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals(AdminApiProblemCatalog.TITLE_INVALID_ARGUMENT, body.getTitle());
    assertEquals(AdminApiProblemCatalog.TYPE_INVALID_ARGUMENT, body.getType().toString());
    assertEquals("/api/v1/audit-logs/lifecycle/confirm-archived", body.getProperties().get("path"));
  }
}
