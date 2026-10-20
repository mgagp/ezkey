/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: GlobalExceptionHandlerAuthRespondRateLimitTest
 * Description: Maps AuthRespondRateLimitExceededException to empty 429 + Retry-After (#912 M6).
 */

package org.ezkey.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.auth.AuthRespondRateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

/**
 * Unit test for post-ownership respond 429 mapping.
 *
 * @since 2026
 */
class GlobalExceptionHandlerAuthRespondRateLimitTest {

  @Test
  @DisplayName("AuthRespondRateLimitExceededException → 429 empty body + Retry-After")
  void handleAuthRespondRateLimitExceeded_mapsTo429WithRetryAfter() {
    GlobalExceptionHandler handler =
        new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));
    AuthRespondRateLimitExceededException ex = new AuthRespondRateLimitExceededException(240L);
    WebRequest request = mock(WebRequest.class);
    when(request.getDescription(false)).thenReturn("uri=/api/v1/auth-attempts/respond");

    ResponseEntity<Void> response = handler.handleAuthRespondRateLimitExceeded(ex, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getBody()).isNull();
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("240");
  }
}
