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

  @Test
  @DisplayName("#918: proven respond refunds the IP token so successes do not fill the ceiling")
  void refundAfterOwnership_doesNotCountProvenSuccessesAgainstIpBudget() {
    RateLimitProperties properties = properties();
    properties.getRespond().setFailIpRequests(1);
    AuthRespondRateLimitService limited = new AuthRespondRateLimitService(properties);

    assertThat(limited.tryConsumeFailIp("203.0.113.10").allowed()).isTrue();
    limited.refundFailIpAfterOwnership("203.0.113.10");
    assertThat(limited.tryConsumeFailIp("203.0.113.10").allowed())
        .as("a proven call returns the token, so the next respond is not 429")
        .isTrue();

    assertThat(limited.tryConsumeFailIp("203.0.113.10").allowed())
        .as("an unproven call keeps the token")
        .isFalse();
  }

  @Test
  @DisplayName("#918: many proven successes stay inside a one-token IP ceiling")
  void repeatedProvenSuccesses_doNotExhaustIpCeiling() {
    RateLimitProperties properties = properties();
    properties.getRespond().setFailIpRequests(1);
    AuthRespondRateLimitService limited = new AuthRespondRateLimitService(properties);

    for (int i = 0; i < 30; i++) {
      assertThat(limited.tryConsumeFailIp("198.51.100.4").allowed()).isTrue();
      limited.refundFailIpAfterOwnership("198.51.100.4");
    }
    assertThat(limited.tryConsumeFailIp("198.51.100.4").allowed()).isTrue();
  }

  @Test
  @DisplayName("#918: IPv6 addresses in one /64 share a bucket; a different /64 does not")
  void ipv6Prefix64_sharesFailIpBucket() {
    RateLimitProperties properties = properties();
    properties.getRespond().setFailIpRequests(1);
    AuthRespondRateLimitService limited = new AuthRespondRateLimitService(properties);

    assertThat(limited.tryConsumeFailIp("2001:db8:1:2::1").allowed()).isTrue();
    assertThat(limited.tryConsumeFailIp("2001:DB8:1:2::abcd").allowed())
        .as("privacy-extension rotation inside the /64 must not reset the ceiling")
        .isFalse();
    assertThat(limited.tryConsumeFailIp("2001:db8:1:3::1").allowed())
        .as("a different /64 is a different subscriber prefix")
        .isTrue();
    assertThat(limited.tryConsumeFailIp("::ffff:203.0.113.9").allowed()).isTrue();
    assertThat(limited.tryConsumeFailIp("203.0.113.9").allowed())
        .as("IPv4-mapped IPv6 and the dotted address are one key")
        .isFalse();
  }

  @Test
  @DisplayName("#918: filling the IP cache does not evict an exhausted IP or an id budget")
  void ipRotation_doesNotResetExhaustedIpOrIdBudget() {
    RateLimitProperties properties = properties();
    properties.getRespond().setFailIpRequests(1);
    properties.getRespond().setRequests(1);
    AuthRespondRateLimitService limited = new AuthRespondRateLimitService(properties, 2, 1);

    assertThat(limited.tryConsumeFailIp("203.0.113.1").allowed()).isTrue();
    assertThat(limited.tryConsumeFailIp("203.0.113.1").allowed()).isFalse();
    assertThat(limited.tryConsumeAfterOwnership(99)).isEmpty();
    assertThat(limited.tryConsumeAfterOwnership(99)).isPresent();

    assertThat(limited.tryConsumeFailIp("203.0.113.2").allowed()).isTrue();
    assertThat(limited.tryConsumeFailIp("203.0.113.3").allowed())
        .as("the first key past capacity shares the overflow bucket")
        .isTrue();
    assertThat(limited.tryConsumeFailIp("203.0.113.4").allowed())
        .as("a further rotated address must not receive a fresh full budget")
        .isFalse();

    assertThat(limited.tryConsumeFailIp("203.0.113.1").allowed())
        .as("admitting new prefixes must not resurrect the exhausted address")
        .isFalse();
    assertThat(limited.tryConsumeAfterOwnership(99))
        .as("an IP flood must not evict the post-ownership id bucket")
        .isPresent();
  }

  private static RateLimitProperties properties() {
    RateLimitProperties properties = new RateLimitProperties();
    RateLimitProperties.EndpointConfig respond = new RateLimitProperties.EndpointConfig();
    respond.setRequests(1);
    respond.setWindowMinutes(5);
    respond.setKeyStrategy("auth-attempt-id");
    respond.setFailIpRequests(30);
    respond.setFailIpWindowMinutes(1);
    properties.setRespond(respond);
    return properties;
  }
}
