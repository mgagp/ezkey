/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LoopbackHostSelectorTest
 * Description: Unit tests for loopback URL host rewriting
 */

package org.ezkey.tests.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LoopbackHostSelector} URL rewriting. Does not require a Docker stack.
 *
 * @since 2026
 */
@Tag(TestTags.FAST)
@DisplayName("Loopback host selector")
class LoopbackHostSelectorTest {

  @AfterEach
  void reset() {
    LoopbackHostSelector.resetForTests();
  }

  @Test
  @DisplayName("replaceHost rewrites localhost actuator URL to IPv6 loopback")
  void replaceHost_rewritesLocalhostToIpv6() {
    String rewritten =
        LoopbackHostSelector.replaceHost("http://localhost:9081/actuator/health", "::1");
    assertThat(rewritten).isEqualTo("http://[::1]:9081/actuator/health");
  }

  @Test
  @DisplayName("replaceHost leaves non-loopback hosts unchanged")
  void replaceHost_leavesRemoteHostUnchanged() {
    String url = "http://admin-api:9080/api/v1/public/instance-info";
    assertThat(LoopbackHostSelector.replaceHost(url, "::1")).isEqualTo(url);
  }

  @Test
  @DisplayName("rewriteLoopback is identity until resolveAgainst selects IPv6")
  void rewriteLoopback_isIdentityBeforeResolve() {
    assertThat(LoopbackHostSelector.rewriteLoopback("http://localhost:9080"))
        .isEqualTo("http://localhost:9080");
  }
}
