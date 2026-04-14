/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardEnrollmentStatsDto
 * Description: Active enrollment counts (grouped buckets) for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Active enrollment counts for the dashboard overview.
 *
 * <p>All counts apply to {@code active = true} enrollments in the current scope (tenant or
 * instance). Bucket fields partition that total: {@code verified}, {@code inProgress} (created or
 * bound), {@code expired}, and {@code unavailable} (invalid or revoked). The {@code total} equals
 * the sum of the four buckets.
 */
@Schema(description = "Active enrollment counts (grouped buckets) for dashboard overview")
public class DashboardEnrollmentStatsDto {

  @Schema(description = "All active enrollments in scope (sum of bucket fields)")
  private long total;

  @Schema(description = "Active enrollments with status VERIFIED")
  private long verified;

  @Schema(description = "Active enrollments in CREATED or BOUND (onboarding in progress)")
  private long inProgress;

  @Schema(description = "Active enrollments with status EXPIRED")
  private long expired;

  @Schema(description = "Active enrollments with status INVALID or REVOKED")
  private long unavailable;

  public DashboardEnrollmentStatsDto() {}

  public DashboardEnrollmentStatsDto(
      long total, long verified, long inProgress, long expired, long unavailable) {
    this.total = total;
    this.verified = verified;
    this.inProgress = inProgress;
    this.expired = expired;
    this.unavailable = unavailable;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public long getVerified() {
    return verified;
  }

  public void setVerified(long verified) {
    this.verified = verified;
  }

  public long getInProgress() {
    return inProgress;
  }

  public void setInProgress(long inProgress) {
    this.inProgress = inProgress;
  }

  public long getExpired() {
    return expired;
  }

  public void setExpired(long expired) {
    this.expired = expired;
  }

  public long getUnavailable() {
    return unavailable;
  }

  public void setUnavailable(long unavailable) {
    this.unavailable = unavailable;
  }
}
