/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.admin.exception.AdminChallengeMismatchException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("AuthenticationExceptionHandler AdminChallengeMismatchException (#1465)")
class AuthenticationExceptionHandlerChallengeMismatchTest {

  @Test
  @DisplayName("Maps challenge mismatch to HTTP 400 challenge-mismatch ProblemDetail")
  void mapsTo400ChallengeMismatch() {
    AuthenticationExceptionHandler handler = new AuthenticationExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/admin/auth/passwordless-wait");

    ResponseEntity<ProblemDetail> response =
        handler.handleAdminChallengeMismatchException(
            new AdminChallengeMismatchException(), request);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(400, body.getStatus());
    assertEquals("Challenge Mismatch", body.getTitle());
    assertEquals(AdminChallengeMismatchException.DETAIL, body.getDetail());
    assertEquals(
        "https://ezkey.io/problems/authentication/challenge-mismatch", body.getType().toString());
  }
}
