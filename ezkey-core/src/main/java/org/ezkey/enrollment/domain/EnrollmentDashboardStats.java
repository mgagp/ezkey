/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentDashboardStats
 * Description: Aggregated active enrollment counts for dashboard (grouped buckets).
 */

package org.ezkey.enrollment.domain;

import java.util.Map;

/**
 * Aggregated enrollment statistics for the Admin UI dashboard.
 *
 * <p>Counts are scoped by the caller (e.g. tenant vs instance) and restricted to {@code active =
 * true}. Buckets partition that universe: {@code verified}, {@code inProgress} ({@link
 * EnrollmentStatus#CREATED} + {@link EnrollmentStatus#BOUND}), {@code expired}, and {@code
 * unavailable} ({@link EnrollmentStatus#INVALID} + {@link EnrollmentStatus#REVOKED}). The {@link
 * #total()} equals the sum of per-status counts and equals the sum of the four bucket fields.
 *
 * @param total all active enrollments in scope
 * @param verified enrollments with status {@link EnrollmentStatus#VERIFIED}
 * @param inProgress enrollments in {@link EnrollmentStatus#CREATED} or {@link
 *     EnrollmentStatus#BOUND}
 * @param expired enrollments with status {@link EnrollmentStatus#EXPIRED}
 * @param unavailable enrollments with status {@link EnrollmentStatus#INVALID} or {@link
 *     EnrollmentStatus#REVOKED}
 */
public record EnrollmentDashboardStats(
    long total, long verified, long inProgress, long expired, long unavailable) {

  /**
   * Builds dashboard stats from per-status counts (missing statuses treated as zero).
   *
   * @param counts map of status to count from a grouped query
   * @return bucket totals with {@code total} equal to the sum of all status counts
   */
  public static EnrollmentDashboardStats fromStatusCounts(Map<EnrollmentStatus, Long> counts) {
    long created = counts.getOrDefault(EnrollmentStatus.CREATED, 0L);
    long bound = counts.getOrDefault(EnrollmentStatus.BOUND, 0L);
    long verified = counts.getOrDefault(EnrollmentStatus.VERIFIED, 0L);
    long expired = counts.getOrDefault(EnrollmentStatus.EXPIRED, 0L);
    long invalid = counts.getOrDefault(EnrollmentStatus.INVALID, 0L);
    long revoked = counts.getOrDefault(EnrollmentStatus.REVOKED, 0L);
    long total = created + bound + verified + expired + invalid + revoked;
    long inProgress = created + bound;
    long unavailable = invalid + revoked;
    return new EnrollmentDashboardStats(total, verified, inProgress, expired, unavailable);
  }
}
