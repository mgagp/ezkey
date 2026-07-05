/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentDashboardStats
 * Description: Operational enrollment counts for dashboard (grouped by status and active flag).
 */

package org.ezkey.enrollment.domain;

import java.util.Map;

/**
 * Operational enrollment statistics for the Admin UI dashboard.
 *
 * <p>Each bucket represents a distinct operational state, independent of the {@code active} flag:
 *
 * <ul>
 *   <li>{@link #verified()} — devices ready for MFA ({@link EnrollmentStatus#VERIFIED} + {@code
 *       active = true})
 *   <li>{@link #inProgress()} — onboarding in progress ({@link EnrollmentStatus#CREATED} or {@link
 *       EnrollmentStatus#BOUND}, any {@code active})
 *   <li>{@link #suspended()} — admin-disabled devices ({@link EnrollmentStatus#VERIFIED} + {@code
 *       active = false})
 *   <li>{@link #expired()} — abandoned before completion ({@link EnrollmentStatus#EXPIRED}, any
 *       {@code active})
 *   <li>{@link #invalid()} — failed validation ({@link EnrollmentStatus#INVALID}, any {@code
 *       active})
 *   <li>{@link #revoked()} — admin revocations ({@link EnrollmentStatus#REVOKED}, any {@code
 *       active})
 * </ul>
 *
 * <p>Buckets do not sum to a single total by design: each represents an independent operational
 * question.
 *
 * @param verified VERIFIED + active=true — devices currently serving authentication
 * @param inProgress CREATED or BOUND (any active) — onboarding awaiting completion
 * @param suspended VERIFIED + active=false — valid devices temporarily disabled by an admin
 * @param expired EXPIRED (any active) — enrollments that timed out before verification
 * @param invalid INVALID (any active) — enrollments that failed cryptographic validation
 * @param revoked REVOKED (any active) — enrollments revoked by an administrator
 * @since 2025
 */
public record EnrollmentDashboardStats(
    long verified, long inProgress, long suspended, long expired, long invalid, long revoked) {

  /**
   * Builds dashboard stats from per-status, per-active counts (missing entries treated as zero).
   *
   * <p>The outer map key is the enrollment status; the inner map key is the {@code active} flag
   * ({@code true} = active, {@code false} = inactive).
   *
   * @param counts map of status → (active flag → count) from a grouped query
   * @return operational bucket counts
   */
  public static EnrollmentDashboardStats fromStatusActiveCounts(
      Map<EnrollmentStatus, Map<Boolean, Long>> counts) {
    long verified = get(counts, EnrollmentStatus.VERIFIED, Boolean.TRUE);
    long suspended = get(counts, EnrollmentStatus.VERIFIED, Boolean.FALSE);
    long inProgress =
        getAll(counts, EnrollmentStatus.CREATED) + getAll(counts, EnrollmentStatus.BOUND);
    long expired = getAll(counts, EnrollmentStatus.EXPIRED);
    long invalid = getAll(counts, EnrollmentStatus.INVALID);
    long revoked = getAll(counts, EnrollmentStatus.REVOKED);
    return new EnrollmentDashboardStats(verified, inProgress, suspended, expired, invalid, revoked);
  }

  private static long get(
      Map<EnrollmentStatus, Map<Boolean, Long>> counts, EnrollmentStatus status, Boolean active) {
    Map<Boolean, Long> inner = counts.get(status);
    if (inner == null) {
      return 0L;
    }
    return inner.getOrDefault(active, 0L);
  }

  private static long getAll(
      Map<EnrollmentStatus, Map<Boolean, Long>> counts, EnrollmentStatus status) {
    Map<Boolean, Long> inner = counts.get(status);
    if (inner == null) {
      return 0L;
    }
    return inner.getOrDefault(Boolean.TRUE, 0L) + inner.getOrDefault(Boolean.FALSE, 0L);
  }
}
