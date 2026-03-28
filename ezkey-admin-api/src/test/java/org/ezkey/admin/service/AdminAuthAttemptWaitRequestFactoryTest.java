/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthAttemptWaitRequestFactoryTest
 * Description: Unit tests for {@link AdminAuthAttemptWaitRequestFactory}.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AdminAuthAttemptWaitRequestFactory}.
 *
 * @since 2025
 */
@DisplayName("AdminAuthAttemptWaitRequestFactory")
class AdminAuthAttemptWaitRequestFactoryTest {

  @Test
  @DisplayName("forNewAttempt adds slack and caps at 300")
  void forNewAttempt_addsSlackAndCapsAt300() {
    var req = AdminAuthAttemptWaitRequestFactory.forNewAttempt(120);
    assertThat(req.getTimeout()).isEqualTo(135);
    assertThat(req.getPolling()).isEqualTo(AdminAuthAttemptWaitRequestFactory.POLLING_SECONDS);
  }

  @Test
  @DisplayName("forNewAttempt caps large TTL at 300")
  void forNewAttempt_capsLargeTtlAt300() {
    var req = AdminAuthAttemptWaitRequestFactory.forNewAttempt(600);
    assertThat(req.getTimeout()).isEqualTo(300);
  }

  @Test
  @DisplayName("forNewAttempt uses max when ttl null")
  void forNewAttempt_usesMaxWhenTtlNull() {
    var req = AdminAuthAttemptWaitRequestFactory.forNewAttempt(null);
    assertThat(req.getTimeout()).isEqualTo(300);
  }

  @Test
  @DisplayName("forLoadedAttempt uses remaining time plus slack")
  void forLoadedAttempt_usesRemainingPlusSlack() {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setExpiresAt(OffsetDateTime.now().plusSeconds(200));
    var req = AdminAuthAttemptWaitRequestFactory.forLoadedAttempt(attempt);
    assertThat(req.getTimeout()).isBetween(200, 215);
    assertThat(req.getPolling()).isEqualTo(AdminAuthAttemptWaitRequestFactory.POLLING_SECONDS);
  }

  @Test
  @DisplayName("forLoadedAttempt uses 300 when expiresAt null")
  void forLoadedAttempt_usesMaxWhenExpiresAtNull() {
    AuthAttempt attempt = new AuthAttempt();
    var req = AdminAuthAttemptWaitRequestFactory.forLoadedAttempt(attempt);
    assertThat(req.getTimeout()).isEqualTo(300);
  }
}
