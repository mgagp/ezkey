/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: RateLimitIpKeyTest
 * Description: IPv6 /64 aggregation for Auth API rate-limit keys (#918).
 */

package org.ezkey.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RateLimitIpKey}.
 *
 * @since 2026
 */
class RateLimitIpKeyTest {

  @Test
  @DisplayName("IPv4 is unchanged and IPv6 collapses to /64")
  void normalize_ipv4Unchanged_ipv6CollapsedToPrefix64() {
    assertThat(RateLimitIpKey.normalize("203.0.113.10")).isEqualTo("203.0.113.10");
    assertThat(RateLimitIpKey.normalize("2001:db8:1:2::1"))
        .isEqualTo(RateLimitIpKey.normalize("2001:db8:1:2:ffff:ffff:ffff:ffff"));
    assertThat(RateLimitIpKey.normalize("2001:db8:1:2::1"))
        .isNotEqualTo(RateLimitIpKey.normalize("2001:db8:1:3::1"));
    assertThat(RateLimitIpKey.normalize("[2001:db8:1:2::5]"))
        .isEqualTo(RateLimitIpKey.normalize("2001:db8:1:2::1"));
    assertThat(RateLimitIpKey.normalize("::ffff:198.51.100.8")).isEqualTo("198.51.100.8");
    assertThat(RateLimitIpKey.normalize(null)).isEqualTo("unknown");
    assertThat(RateLimitIpKey.normalize("not-an-ip")).isEqualTo("invalid");
  }
}
