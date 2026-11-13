/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DomainExceptionHandlerEncryptionLifecycleTest
 * Description: Verifies RFC 9457 mapping for EncryptionLifecycleDisabledException.
 */

package org.ezkey.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.ezkey.security.exception.EncryptionLifecycleDisabledException;
import org.ezkey.security.exception.EncryptionLifecycleDisabledException.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link DomainExceptionHandler} handling of {@link
 * EncryptionLifecycleDisabledException}.
 */
@DisplayName("DomainExceptionHandler EncryptionLifecycleDisabledException")
class DomainExceptionHandlerEncryptionLifecycleTest {

  @Test
  @DisplayName("Rotation disabled maps to HTTP 409")
  void rotationDisabledMapsTo409() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/encryption-keys/rotate");

    EncryptionLifecycleDisabledException ex =
        new EncryptionLifecycleDisabledException(Operation.ROTATION);

    ResponseEntity<ProblemDetail> response =
        handler.handleEncryptionLifecycleDisabledException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(409, body.getStatus());
    assertEquals(ex.getMessage(), body.getDetail());
    assertEquals("Encryption rotation inactive", body.getTitle());
    assertEquals(
        "https://ezkey.io/problems/domain/encryption-rotation-disabled", body.getType().toString());
  }

  @Test
  @DisplayName("Re-encryption disabled maps to HTTP 409")
  void reencryptionDisabledMapsTo409() {
    DomainExceptionHandler handler = new DomainExceptionHandler();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/encryption-keys/reencrypt/trigger");

    EncryptionLifecycleDisabledException ex =
        new EncryptionLifecycleDisabledException(Operation.REENCRYPTION);

    ResponseEntity<ProblemDetail> response =
        handler.handleEncryptionLifecycleDisabledException(ex, request);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    ProblemDetail body = response.getBody();
    assertNotNull(body);
    assertEquals(
        "https://ezkey.io/problems/domain/encryption-reencryption-disabled",
        body.getType().toString());
  }
}
