/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditHmacIntegrityHealthIndicatorTest
 * Description: Unit tests for AuditHmacIntegrityHealthIndicator (SEC-008).
 */

package org.ezkey.audit.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * Unit tests for {@link AuditHmacIntegrityHealthIndicator}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuditHmacIntegrityHealthIndicatorTest {

  @Mock private AuditHmacService auditHmacService;

  @Test
  @DisplayName("health is DOWN when integrity enabled but HMAC is not active")
  void health_enabledButNotActive_isDown() {
    AuditHmacProperties properties = new AuditHmacProperties();
    properties.setEnabled(true);
    when(auditHmacService.isActive()).thenReturn(false);

    AuditHmacIntegrityHealthIndicator indicator =
        new AuditHmacIntegrityHealthIndicator(properties, auditHmacService);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertThat(health.getDetails()).containsEntry("active", false);
    assertThat(health.getDetails()).containsEntry("status", "NOT_ACTIVE");
  }

  @Test
  @DisplayName("health is UP with DISABLED_BY_CONFIG when integrity disabled")
  void health_disabledByConfig_isUp() {
    AuditHmacProperties properties = new AuditHmacProperties();
    properties.setEnabled(false);

    AuditHmacIntegrityHealthIndicator indicator =
        new AuditHmacIntegrityHealthIndicator(properties, auditHmacService);

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("status", "DISABLED_BY_CONFIG");
  }
}
