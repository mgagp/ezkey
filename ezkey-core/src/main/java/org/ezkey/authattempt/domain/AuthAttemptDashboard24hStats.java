/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AuthAttemptDashboard24hStats
 * Description: Aggregated auth attempt counts and terminal-outcome rates for dashboard (24h window).
 */

package org.ezkey.authattempt.domain;

import java.util.Map;

/**
 * Aggregated authentication attempt statistics for a time-bounded dashboard view.
 *
 * <p>Counts are scoped by the caller (e.g. tenant vs instance). Percentages are computed over
 * <strong>terminal</strong> outcomes only: {@code ACCEPTED}, {@code REJECTED}, {@code INVALID},
 * {@code EXPIRED}. When there are no terminal outcomes, percentage fields are {@code null}.
 *
 * @param total all attempts in the window (every status)
 * @param pending count with status {@link AuthAttemptStatus#PENDING}
 * @param readCount count with status {@link AuthAttemptStatus#READ}
 * @param accepted count with status {@link AuthAttemptStatus#ACCEPTED}
 * @param rejected count with status {@link AuthAttemptStatus#REJECTED}
 * @param invalid count with status {@link AuthAttemptStatus#INVALID}
 * @param expired count with status {@link AuthAttemptStatus#EXPIRED}
 * @param terminalTotal accepted + rejected + invalid + expired
 * @param successRatePct accepted as percent of terminal total, or null if none
 * @param invalidRatePct invalid as percent of terminal total, or null if none
 * @param expiredRatePct expired as percent of terminal total, or null if none
 * @param rejectedRatePct rejected as percent of terminal total, or null if none
 */
public record AuthAttemptDashboard24hStats(
    long total,
    long pending,
    long readCount,
    long accepted,
    long rejected,
    long invalid,
    long expired,
    long terminalTotal,
    Integer successRatePct,
    Integer invalidRatePct,
    Integer expiredRatePct,
    Integer rejectedRatePct) {

  /**
   * Builds dashboard stats from per-status counts (missing statuses treated as zero).
   *
   * @param counts map of status to count from a grouped query
   * @return computed totals, terminal total, and rounded percentages vs {@code terminalTotal}
   */
  public static AuthAttemptDashboard24hStats fromStatusCounts(Map<AuthAttemptStatus, Long> counts) {
    long pending = counts.getOrDefault(AuthAttemptStatus.PENDING, 0L);
    long readCount = counts.getOrDefault(AuthAttemptStatus.READ, 0L);
    long accepted = counts.getOrDefault(AuthAttemptStatus.ACCEPTED, 0L);
    long rejected = counts.getOrDefault(AuthAttemptStatus.REJECTED, 0L);
    long invalid = counts.getOrDefault(AuthAttemptStatus.INVALID, 0L);
    long expired = counts.getOrDefault(AuthAttemptStatus.EXPIRED, 0L);
    long total = pending + readCount + accepted + rejected + invalid + expired;
    long terminalTotal = accepted + rejected + invalid + expired;
    Integer successRatePct =
        terminalTotal > 0 ? (int) Math.round(accepted * 100.0 / terminalTotal) : null;
    Integer invalidRatePct =
        terminalTotal > 0 ? (int) Math.round(invalid * 100.0 / terminalTotal) : null;
    Integer expiredRatePct =
        terminalTotal > 0 ? (int) Math.round(expired * 100.0 / terminalTotal) : null;
    Integer rejectedRatePct =
        terminalTotal > 0 ? (int) Math.round(rejected * 100.0 / terminalTotal) : null;
    return new AuthAttemptDashboard24hStats(
        total,
        pending,
        readCount,
        accepted,
        rejected,
        invalid,
        expired,
        terminalTotal,
        successRatePct,
        invalidRatePct,
        expiredRatePct,
        rejectedRatePct);
  }
}
