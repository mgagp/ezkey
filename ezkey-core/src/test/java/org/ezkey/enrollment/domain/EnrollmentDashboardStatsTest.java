/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: EnrollmentDashboardStatsTest
 * Description: Unit tests for dashboard enrollment operational bucket aggregation.
 */

package org.ezkey.enrollment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EnrollmentDashboardStats")
class EnrollmentDashboardStatsTest {

  private static Map<EnrollmentStatus, Map<Boolean, Long>> counts(Object... triplets) {
    Map<EnrollmentStatus, Map<Boolean, Long>> result = new EnumMap<>(EnrollmentStatus.class);
    for (int i = 0; i < triplets.length; i += 3) {
      EnrollmentStatus status = (EnrollmentStatus) triplets[i];
      Boolean active = (Boolean) triplets[i + 1];
      Long count = (Long) triplets[i + 2];
      result.computeIfAbsent(status, k -> new HashMap<>()).put(active, count);
    }
    return result;
  }

  @Test
  @DisplayName("fromStatusActiveCounts splits VERIFIED by active flag into verified and suspended")
  void fromStatusActiveCounts_verifiedSplitByActive() {
    Map<EnrollmentStatus, Map<Boolean, Long>> c =
        counts(
            EnrollmentStatus.VERIFIED,
            Boolean.TRUE,
            10L,
            EnrollmentStatus.VERIFIED,
            Boolean.FALSE,
            3L);

    EnrollmentDashboardStats s = EnrollmentDashboardStats.fromStatusActiveCounts(c);

    assertEquals(10L, s.verified());
    assertEquals(3L, s.suspended());
    assertEquals(0L, s.inProgress());
    assertEquals(0L, s.expired());
    assertEquals(0L, s.incidents());
  }

  @Test
  @DisplayName("fromStatusActiveCounts aggregates CREATED and BOUND (any active) into inProgress")
  void fromStatusActiveCounts_inProgressAnyActive() {
    Map<EnrollmentStatus, Map<Boolean, Long>> c =
        counts(
            EnrollmentStatus.CREATED, Boolean.FALSE, 3L, EnrollmentStatus.BOUND, Boolean.FALSE, 2L);

    EnrollmentDashboardStats s = EnrollmentDashboardStats.fromStatusActiveCounts(c);

    assertEquals(5L, s.inProgress());
    assertEquals(0L, s.verified());
    assertEquals(0L, s.suspended());
  }

  @Test
  @DisplayName("fromStatusActiveCounts aggregates INVALID and REVOKED (any active) into incidents")
  void fromStatusActiveCounts_incidentsAnyActive() {
    Map<EnrollmentStatus, Map<Boolean, Long>> c =
        counts(
            EnrollmentStatus.INVALID,
            Boolean.FALSE,
            4L,
            EnrollmentStatus.REVOKED,
            Boolean.FALSE,
            5L);

    EnrollmentDashboardStats s = EnrollmentDashboardStats.fromStatusActiveCounts(c);

    assertEquals(9L, s.incidents());
    assertEquals(0L, s.verified());
    assertEquals(0L, s.suspended());
  }

  @Test
  @DisplayName("fromStatusActiveCounts covers all buckets with mixed active states")
  void fromStatusActiveCounts_allBucketsMixed() {
    Map<EnrollmentStatus, Map<Boolean, Long>> c =
        counts(
            EnrollmentStatus.VERIFIED,
            Boolean.TRUE,
            10L,
            EnrollmentStatus.VERIFIED,
            Boolean.FALSE,
            2L,
            EnrollmentStatus.CREATED,
            Boolean.FALSE,
            3L,
            EnrollmentStatus.BOUND,
            Boolean.FALSE,
            2L,
            EnrollmentStatus.EXPIRED,
            Boolean.FALSE,
            1L,
            EnrollmentStatus.INVALID,
            Boolean.FALSE,
            4L,
            EnrollmentStatus.REVOKED,
            Boolean.FALSE,
            5L);

    EnrollmentDashboardStats s = EnrollmentDashboardStats.fromStatusActiveCounts(c);

    assertEquals(10L, s.verified());
    assertEquals(2L, s.suspended());
    assertEquals(5L, s.inProgress());
    assertEquals(1L, s.expired());
    assertEquals(9L, s.incidents());
  }

  @Test
  @DisplayName("fromStatusActiveCounts treats missing statuses as zero")
  void fromStatusActiveCounts_missingStatusesZero() {
    Map<EnrollmentStatus, Map<Boolean, Long>> c =
        counts(EnrollmentStatus.VERIFIED, Boolean.TRUE, 7L);

    EnrollmentDashboardStats s = EnrollmentDashboardStats.fromStatusActiveCounts(c);

    assertEquals(7L, s.verified());
    assertEquals(0L, s.suspended());
    assertEquals(0L, s.inProgress());
    assertEquals(0L, s.expired());
    assertEquals(0L, s.incidents());
  }
}
