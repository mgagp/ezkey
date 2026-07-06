/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: TinkEncryptionHealthIndicatorTest
 * Description: Unit tests for TinkEncryptionHealthIndicator (SEC-002).
 */

package org.ezkey.security.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.ezkey.config.TinkProperties;
import org.ezkey.security.TinkKeyManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * Unit tests for {@link TinkEncryptionHealthIndicator}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class TinkEncryptionHealthIndicatorTest {

  @Mock private TinkKeyManager keyManager;

  @Test
  @DisplayName("health is DOWN when encryption enabled but not initialized")
  void health_enabledButNotInitialized_isDown() {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(true);
    when(keyManager.isInitialized()).thenReturn(false);

    TinkEncryptionHealthIndicator indicator =
        new TinkEncryptionHealthIndicator(properties, keyManager);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("initialized", false);
    assertThat(health.getDetails()).containsEntry("status", "NOT_INITIALIZED");
  }

  @Test
  @DisplayName("health is UP with DISABLED_BY_CONFIG when encryption disabled")
  void health_disabledByConfig_isUp() {
    TinkProperties properties = new TinkProperties();
    properties.setEnabled(false);

    TinkEncryptionHealthIndicator indicator =
        new TinkEncryptionHealthIndicator(properties, keyManager);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("status", "DISABLED_BY_CONFIG");
  }
}
