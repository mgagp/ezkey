/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.integration.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.RateLimitExceededException;
import org.ezkey.exception.auth.AuthAttemptCreationCooldownException;
import org.ezkey.exception.auth.AuthAttemptPendingExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Typed 409 and 429 responses for authentication-attempt creation.
 *
 * @since 2026
 */
class IntegrationApiAuthAttemptProblemTest {

  private GlobalExceptionHandler handler;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));
    request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts");
  }

  @Test
  @DisplayName("Pending attempt is 409 auth-attempt-pending-exists with Retry-After")
  void pendingExists_typed409() {
    ResponseEntity<ProblemDetail> response =
        handler.handleAuthAttemptPendingExists(new AuthAttemptPendingExistsException(42), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getType().toString())
        .isEqualTo(AuthAttemptPendingExistsException.TYPE);
    assertThat(response.getBody().getDetail()).isEqualTo(AuthAttemptPendingExistsException.DETAIL);
    assertThat(response.getBody().getDetail()).doesNotContain("42");
  }

  @Test
  @DisplayName("Cooldown is 409 auth-attempt-creation-cooldown with Retry-After")
  void cooldown_typed409() {
    ResponseEntity<ProblemDetail> response =
        handler.handleAuthAttemptCreationCooldown(
            new AuthAttemptCreationCooldownException(300), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("300");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getType().toString())
        .isEqualTo(AuthAttemptCreationCooldownException.TYPE);
    assertThat(response.getBody().getDetail()).doesNotContain("user");
  }

  @Test
  @DisplayName("429 Retry-After is the configured window in seconds")
  void rateLimit_retryAfterFromWindowMinutes() {
    ResponseEntity<ProblemDetail> response =
        handler.handleRateLimitExceeded(
            new RateLimitExceededException("CREATE_AUTH_ATTEMPT", 7, 0, 4), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("240");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getType().toString())
        .isEqualTo(IntegrationApiProblemCatalog.TYPE_RATE_LIMIT_EXCEEDED);
    assertThat(response.getBody().getDetail()).doesNotContain("7");
  }
}
