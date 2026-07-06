/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * HealthIndicator: AuditHmacIntegrityHealthIndicator
 * Description: Actuator health contributor for audit HMAC signing readiness (SEC-008).
 */

package org.ezkey.audit.integrity;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes audit log HMAC integrity signing state via Spring Boot Actuator health (SEC-008).
 *
 * @since 2026
 */
@Component
public class AuditHmacIntegrityHealthIndicator implements HealthIndicator {

  private final AuditHmacProperties properties;
  private final AuditHmacService auditHmacService;

  /**
   * Creates the health contributor.
   *
   * @param properties audit HMAC configuration
   * @param auditHmacService HMAC signing service
   */
  public AuditHmacIntegrityHealthIndicator(
      AuditHmacProperties properties, AuditHmacService auditHmacService) {
    this.properties = properties;
    this.auditHmacService = auditHmacService;
  }

  @Override
  public Health health() {
    if (!properties.isEnabled()) {
      return Health.up()
          .withDetail("enabled", false)
          .withDetail("required", properties.isRequired())
          .withDetail("active", false)
          .withDetail("status", "DISABLED_BY_CONFIG")
          .build();
    }

    boolean active = auditHmacService.isActive();
    Health.Builder builder = active ? Health.up() : Health.down();
    return builder
        .withDetail("enabled", true)
        .withDetail("required", properties.isRequired())
        .withDetail("active", active)
        .withDetail("status", active ? "READY" : "NOT_ACTIVE")
        .build();
  }
}
