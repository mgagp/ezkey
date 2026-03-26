/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: AuthAttemptDashboard24hStatsTest
 * Description: Unit tests for terminal-outcome aggregation and rate rounding.
 */

package org.ezkey.authattempt.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthAttemptDashboard24hStats")
class AuthAttemptDashboard24hStatsTest {

  @Test
  @DisplayName("fromStatusCounts computes terminal total and percentages vs terminal denominator")
  void fromStatusCounts_terminalDenominator() {
    Map<AuthAttemptStatus, Long> counts = new EnumMap<>(AuthAttemptStatus.class);
    counts.put(AuthAttemptStatus.ACCEPTED, 8L);
    counts.put(AuthAttemptStatus.REJECTED, 1L);
    counts.put(AuthAttemptStatus.INVALID, 1L);
    counts.put(AuthAttemptStatus.EXPIRED, 0L);
    counts.put(AuthAttemptStatus.PENDING, 3L);
    counts.put(AuthAttemptStatus.READ, 2L);

    AuthAttemptDashboard24hStats s = AuthAttemptDashboard24hStats.fromStatusCounts(counts);

    assertThat(s.total()).isEqualTo(15L);
    assertThat(s.terminalTotal()).isEqualTo(10L);
    assertThat(s.successRatePct()).isEqualTo(80);
    assertThat(s.rejectedRatePct()).isEqualTo(10);
    assertThat(s.invalidRatePct()).isEqualTo(10);
    assertThat(s.expiredRatePct()).isEqualTo(0);
  }

  @Test
  @DisplayName("fromStatusCounts returns null rates when no terminal outcomes")
  void fromStatusCounts_noTerminal_nullRates() {
    Map<AuthAttemptStatus, Long> counts = new EnumMap<>(AuthAttemptStatus.class);
    counts.put(AuthAttemptStatus.PENDING, 5L);

    AuthAttemptDashboard24hStats s = AuthAttemptDashboard24hStats.fromStatusCounts(counts);

    assertThat(s.total()).isEqualTo(5L);
    assertThat(s.terminalTotal()).isEqualTo(0L);
    assertThat(s.successRatePct()).isNull();
    assertThat(s.invalidRatePct()).isNull();
    assertThat(s.expiredRatePct()).isNull();
    assertThat(s.rejectedRatePct()).isNull();
  }

  @Test
  @DisplayName("fromStatusCounts rounds percentages to nearest integer")
  void fromStatusCounts_rounding() {
    Map<AuthAttemptStatus, Long> counts = new EnumMap<>(AuthAttemptStatus.class);
    counts.put(AuthAttemptStatus.ACCEPTED, 1L);
    counts.put(AuthAttemptStatus.REJECTED, 2L);

    AuthAttemptDashboard24hStats s = AuthAttemptDashboard24hStats.fromStatusCounts(counts);

    assertThat(s.terminalTotal()).isEqualTo(3L);
    assertThat(s.successRatePct()).isEqualTo(33);
    assertThat(s.rejectedRatePct()).isEqualTo(67);
  }
}
