/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cooldown math over attempt timestamps. No stored counter.
 *
 * @since 2026
 */
class AuthAttemptCooldownTest {

  private static final OffsetDateTime T0 =
      OffsetDateTime.of(2026, 11, 9, 12, 0, 0, 0, ZoneOffset.UTC);

  @Test
  @DisplayName("Three events inside 10 minutes suspend creates for 5 minutes")
  void threeEvents_suspendForFiveMinutes() {
    List<OffsetDateTime> events =
        List.of(T0.minusMinutes(10), T0.minusMinutes(5), T0.minusMinutes(2));

    assertThat(AuthAttemptCooldown.remainingSeconds(events, T0, 3, 600, 300)).hasValue(180L);
  }

  @Test
  @DisplayName("Suspension expires on its own and never becomes a permanent lock")
  void suspensionExpires() {
    List<OffsetDateTime> events =
        List.of(T0.minusMinutes(10), T0.minusMinutes(5), T0.minusMinutes(2));
    OffsetDateTime after = T0.minusMinutes(2).plusSeconds(300);

    assertThat(AuthAttemptCooldown.remainingSeconds(events, after, 3, 600, 300)).isEmpty();
  }

  @Test
  @DisplayName("Two events inside the window do not suspend")
  void belowThreshold_doesNotSuspend() {
    List<OffsetDateTime> events = List.of(T0.minusMinutes(4), T0.minusMinutes(1));

    assertThat(AuthAttemptCooldown.remainingSeconds(events, T0, 3, 600, 300)).isEmpty();
  }

  @Test
  @DisplayName("Events older than the window do not count toward a new crossing")
  void oldEvents_doNotCount() {
    List<OffsetDateTime> events =
        List.of(T0.minusMinutes(30), T0.minusMinutes(20), T0.minusMinutes(1));

    assertThat(AuthAttemptCooldown.remainingSeconds(events, T0, 3, 600, 300)).isEmpty();
  }
}
