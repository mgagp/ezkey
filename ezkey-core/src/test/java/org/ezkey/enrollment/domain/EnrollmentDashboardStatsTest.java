/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: EnrollmentDashboardStatsTest
 * Description: Unit tests for dashboard enrollment bucket aggregation.
 */

package org.ezkey.enrollment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnrollmentDashboardStats")
class EnrollmentDashboardStatsTest {

  @Test
  @DisplayName("fromStatusCounts partitions statuses into buckets and total matches sum")
  void fromStatusCounts_partitionsAndTotals() {
    Map<EnrollmentStatus, Long> counts = new EnumMap<>(EnrollmentStatus.class);
    counts.put(EnrollmentStatus.CREATED, 3L);
    counts.put(EnrollmentStatus.BOUND, 2L);
    counts.put(EnrollmentStatus.VERIFIED, 10L);
    counts.put(EnrollmentStatus.EXPIRED, 1L);
    counts.put(EnrollmentStatus.INVALID, 4L);
    counts.put(EnrollmentStatus.REVOKED, 5L);

    EnrollmentDashboardStats s = EnrollmentDashboardStats.fromStatusCounts(counts);

    assertEquals(25L, s.total());
    assertEquals(10L, s.verified());
    assertEquals(5L, s.inProgress());
    assertEquals(1L, s.expired());
    assertEquals(9L, s.unavailable());
    assertEquals(s.total(), s.verified() + s.inProgress() + s.expired() + s.unavailable());
  }

  @Test
  @DisplayName("fromStatusCounts treats missing statuses as zero")
  void fromStatusCounts_missingStatusesZero() {
    Map<EnrollmentStatus, Long> counts = new EnumMap<>(EnrollmentStatus.class);
    counts.put(EnrollmentStatus.VERIFIED, 7L);

    EnrollmentDashboardStats s = EnrollmentDashboardStats.fromStatusCounts(counts);

    assertEquals(7L, s.total());
    assertEquals(7L, s.verified());
    assertEquals(0L, s.inProgress());
    assertEquals(0L, s.expired());
    assertEquals(0L, s.unavailable());
  }
}
