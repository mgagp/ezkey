/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthPendingRateLimitServiceTest
 * Description: Post-ownership pending enrollment budget and IP ceiling (#1124, #918).
 */

package org.ezkey.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthPendingRateLimitService}.
 *
 * @since 2026
 */
class AuthPendingRateLimitServiceTest {

  @Test
  @DisplayName("Ten proven pending consumes are allowed; the eleventh returns retry-after")
  void provenPending_eleventhCall_returnsRetryAfter() {
    AuthPendingRateLimitService service = service(10, 60);

    for (int i = 0; i < 10; i++) {
      assertThat(service.tryConsumeAfterOwnership(42)).isEmpty();
    }
    assertThat(service.tryConsumeAfterOwnership(42)).isPresent();
    assertThat(service.tryConsumeAfterOwnership(7)).isEmpty();
  }

  @Test
  @DisplayName("Client-ip strategy does not debit a per-enrollment budget")
  void clientIpStrategy_skipsEnrollmentBudget() {
    RateLimitProperties properties = properties(10, 60);
    properties.getPending().setKeyStrategy("client-ip");
    AuthPendingRateLimitService service = new AuthPendingRateLimitService(properties);

    assertThat(service.tryConsumeAfterOwnership(42)).isEmpty();
    assertThat(service.tryConsumeAfterOwnership(42)).isEmpty();
  }

  @Test
  @DisplayName("#918: proven pending refunds the IP token")
  void refund_doesNotCountProvenPendingAgainstIpBudget() {
    AuthPendingRateLimitService service = service(10, 1);

    assertThat(service.tryConsumeFailIp("203.0.113.20").allowed()).isTrue();
    service.refundFailIpAfterOwnership("203.0.113.20");
    assertThat(service.tryConsumeFailIp("203.0.113.20").allowed()).isTrue();
    assertThat(service.tryConsumeFailIp("203.0.113.20").allowed()).isFalse();
  }

  @Test
  @DisplayName("#918: rotating IPv6 inside a /64 does not reset the pending IP ceiling")
  void ipv6Prefix_doesNotResetPendingIpCeiling() {
    AuthPendingRateLimitService service = service(10, 1);

    assertThat(service.tryConsumeFailIp("2001:db8:9:9::1").allowed()).isTrue();
    assertThat(service.tryConsumeFailIp("2001:db8:9:9::ffff").allowed()).isFalse();
  }

  private static AuthPendingRateLimitService service(int enrollmentRequests, int failIpRequests) {
    return new AuthPendingRateLimitService(properties(enrollmentRequests, failIpRequests));
  }

  private static RateLimitProperties properties(int enrollmentRequests, int failIpRequests) {
    RateLimitProperties properties = new RateLimitProperties();
    RateLimitProperties.EndpointConfig pending = new RateLimitProperties.EndpointConfig();
    pending.setRequests(enrollmentRequests);
    pending.setWindowMinutes(1);
    pending.setKeyStrategy("enrollment-id");
    pending.setFailIpRequests(failIpRequests);
    pending.setFailIpWindowMinutes(1);
    properties.setPending(pending);
    return properties;
  }
}
