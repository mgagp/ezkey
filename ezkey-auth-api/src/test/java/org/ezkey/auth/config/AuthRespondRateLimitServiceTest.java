/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthRespondRateLimitServiceTest
 * Description: Post-ownership per-id budget beyond threshold (#912 Patrick M6).
 */

package org.ezkey.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthRespondRateLimitService} ownership budget (1 / window by default).
 *
 * @since 2026
 */
class AuthRespondRateLimitServiceTest {

  private AuthRespondRateLimitService service;

  @BeforeEach
  void setUp() {
    RateLimitProperties properties = new RateLimitProperties();
    RateLimitProperties.EndpointConfig respond = new RateLimitProperties.EndpointConfig();
    respond.setRequests(1);
    respond.setWindowMinutes(5);
    respond.setKeyStrategy("auth-attempt-id");
    respond.setFailIpRequests(30);
    respond.setFailIpWindowMinutes(1);
    properties.setRespond(respond);
    service = new AuthRespondRateLimitService(properties);
  }

  @Test
  @DisplayName("First post-ownership consume for an id is allowed; second returns retry-after")
  void tryConsumeAfterOwnership_secondCallBeyondThreshold_returnsRetryAfter() {
    OptionalLong first = service.tryConsumeAfterOwnership(99);
    OptionalLong second = service.tryConsumeAfterOwnership(99);

    assertThat(first).isEmpty();
    assertThat(second).isPresent();
    assertThat(second.getAsLong()).isBetween(1L, 5L * 60L);
  }

  @Test
  @DisplayName("Distinct authAttemptIds have independent post-ownership budgets")
  void tryConsumeAfterOwnership_distinctIdsIndependent() {
    assertThat(service.tryConsumeAfterOwnership(1)).isEmpty();
    assertThat(service.tryConsumeAfterOwnership(2)).isEmpty();
  }
}
