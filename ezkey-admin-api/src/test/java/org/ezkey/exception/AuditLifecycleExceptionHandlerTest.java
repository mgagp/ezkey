/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.audit.exception.AuditLifecycleConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("AuditLifecycleExceptionHandler")
class AuditLifecycleExceptionHandlerTest {

  @Test
  @DisplayName("Maps audit lifecycle conflict to HTTP 409 ProblemDetail")
  void mapsConflictTo409ProblemDetail() {
    AuditLifecycleExceptionHandler handler = new AuditLifecycleExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/audit-logs/lifecycle/confirm-archived");

    AuditLifecycleConflictException ex =
        new AuditLifecycleConflictException(
            "Archive confirmation rejected: external archival is disabled by policy.");

    ResponseEntity<ProblemDetail> response =
        handler.handleAuditLifecycleConflictException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Audit lifecycle conflict", body.getTitle());
    assertEquals(
        AdminApiProblemCatalog.BASE + "/audit-lifecycle-conflict", body.getType().toString());
    assertEquals("/api/v1/audit-logs/lifecycle/confirm-archived", body.getProperties().get("path"));
  }
}
