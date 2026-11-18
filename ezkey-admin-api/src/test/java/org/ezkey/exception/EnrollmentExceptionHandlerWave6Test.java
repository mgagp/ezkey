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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("EnrollmentExceptionHandler Wave 6")
class EnrollmentExceptionHandlerWave6Test {

  @Test
  @DisplayName("Maps active verified duplicate to HTTP 409 ProblemDetail")
  void mapsActiveVerifiedDuplicateTo409ProblemDetail() {
    EnrollmentExceptionHandler handler = new EnrollmentExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/enrollments");

    VerifiedEnrollmentNameTakenException ex =
        new VerifiedEnrollmentNameTakenException(
            "A verified enrollment with this name is already active on this integration.", 7, true);

    ResponseEntity<ProblemDetail> response =
        handler.handleVerifiedEnrollmentNameTakenException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Active Verified Enrollment Already Exists", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/enrollment/active-verified-enrollment-exists",
        body.getType().toString());
    assertEquals("/api/v1/enrollments", body.getProperties().get("path"));
    assertEquals(Map.of("existingEnrollmentId", 7), body.getProperties().get("parameters"));
  }

  @Test
  @DisplayName("Maps a name held by a deactivated enrollment to its own 409 type (#1404)")
  void mapsDeactivatedHolderTo409WithItsOwnType() {
    EnrollmentExceptionHandler handler = new EnrollmentExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/enrollments");

    VerifiedEnrollmentNameTakenException ex =
        new VerifiedEnrollmentNameTakenException(
            "A deactivated enrollment already uses this name on this integration.", 7, false);

    ResponseEntity<ProblemDetail> response =
        handler.handleVerifiedEnrollmentNameTakenException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals("Deactivated Enrollment Holds This Name", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/enrollment/deactivated-enrollment-holds-name",
        body.getType().toString());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals(Map.of("existingEnrollmentId", 7), body.getProperties().get("parameters"));
  }

  @Test
  @DisplayName("Maps system integration create block to HTTP 403 ProblemDetail")
  void mapsSystemIntegrationCreateBlockTo403ProblemDetail() {
    EnrollmentExceptionHandler handler = new EnrollmentExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/enrollments");

    SystemIntegrationEnrollmentCreationException ex =
        new SystemIntegrationEnrollmentCreationException(
            "Cannot create enrollment for system integration.");

    ResponseEntity<ProblemDetail> response =
        handler.handleSystemIntegrationEnrollmentCreationException(ex, request);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(403, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("System Integration Enrollment Creation Not Allowed", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/enrollment/system-integration-create-not-allowed",
        body.getType().toString());
    assertEquals("/api/v1/enrollments", body.getProperties().get("path"));
  }
}
