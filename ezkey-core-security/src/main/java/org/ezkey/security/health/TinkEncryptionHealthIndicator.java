/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * HealthIndicator: TinkEncryptionHealthIndicator
 * Description: Actuator health contributor for Tink at-rest encryption readiness (SEC-002).
 */

package org.ezkey.security.health;

import org.ezkey.config.TinkProperties;
import org.ezkey.security.TinkKeyManager;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes Tink encryption initialization state via Spring Boot Actuator health (SEC-002).
 *
 * @since 2026
 */
@Component
public class TinkEncryptionHealthIndicator implements HealthIndicator {

  private final TinkProperties properties;
  private final TinkKeyManager keyManager;

  /**
   * Creates the health contributor.
   *
   * @param properties Tink encryption configuration
   * @param keyManager Tink key manager
   */
  public TinkEncryptionHealthIndicator(TinkProperties properties, TinkKeyManager keyManager) {
    this.properties = properties;
    this.keyManager = keyManager;
  }

  @Override
  public Health health() {
    if (!properties.isEnabled()) {
      return Health.up()
          .withDetail("enabled", false)
          .withDetail("required", properties.isRequired())
          .withDetail("initialized", false)
          .withDetail("status", "DISABLED_BY_CONFIG")
          .build();
    }

    boolean initialized = keyManager.isInitialized();
    Health.Builder builder = initialized ? Health.up() : Health.down();
    return builder
        .withDetail("enabled", true)
        .withDetail("required", properties.isRequired())
        .withDetail("initialized", initialized)
        .withDetail("status", initialized ? "READY" : "NOT_INITIALIZED")
        .build();
  }
}
