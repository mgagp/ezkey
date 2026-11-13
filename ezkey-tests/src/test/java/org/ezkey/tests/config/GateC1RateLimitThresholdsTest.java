/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: GateC1RateLimitThresholdsTest
 * Description: Unit checks for gate-c1 threshold loading (#912).
 */
package org.ezkey.tests.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Properties;
import org.ezkey.tests.tags.TestTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pure unit checks for {@link GateC1RateLimitThresholds} (no Docker stack).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Tag(TestTags.FAST)
@DisplayName("Gate C1 rate-limit thresholds")
class GateC1RateLimitThresholdsTest {

  @Test
  @DisplayName("fromProperties maps respond / fail-ip / replica keys")
  void fromProperties_mapsKeys() {
    Properties props = new Properties();
    props.setProperty("ezkey.rate-limit.respond.requests", "1");
    props.setProperty("ezkey.rate-limit.respond.window-minutes", "5");
    props.setProperty("ezkey.rate-limit.respond.fail-ip-requests", "50");
    props.setProperty("ezkey.rate-limit.respond.fail-ip-window-minutes", "1");
    props.setProperty("ezkey.gate-c1.auth-api-replicas", "2");

    GateC1RateLimitThresholds t = GateC1RateLimitThresholds.fromProperties(props);

    assertThat(t.respondRequests()).isEqualTo(1);
    assertThat(t.respondWindowMinutes()).isEqualTo(5);
    assertThat(t.failIpRequests()).isEqualTo(50);
    assertThat(t.failIpWindowMinutes()).isEqualTo(1);
    assertThat(t.authApiReplicas()).isEqualTo(2);
    assertThat(t.respondWindowSeconds()).isEqualTo(300L);
    assertThat(t.ownership429ProbeAttempts()).isEqualTo(3);
  }

  @Test
  @DisplayName("load() reads committed gate-c1 thresholds file")
  void load_readsCommittedFile() {
    GateC1RateLimitThresholds t = GateC1RateLimitThresholds.load();
    assertThat(t.respondRequests()).isEqualTo(1);
    assertThat(t.respondWindowMinutes()).isEqualTo(5);
    assertThat(t.failIpRequests()).isGreaterThanOrEqualTo(1);
    assertThat(t.authApiReplicas()).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("fromProperties rejects missing keys")
  void fromProperties_rejectsMissing() {
    assertThatThrownBy(() -> GateC1RateLimitThresholds.fromProperties(new Properties()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ezkey.rate-limit.respond.requests");
  }
}
