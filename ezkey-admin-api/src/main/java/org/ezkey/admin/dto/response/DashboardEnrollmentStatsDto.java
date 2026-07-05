/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardEnrollmentStatsDto
 * Description: Operational enrollment counts (by status and active flag) for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Operational enrollment counts for the dashboard overview.
 *
 * <p>Each field represents a distinct operational state. Counts are not constrained to sum to a
 * single total; they reflect independent operational questions across all enrollment states.
 *
 * <ul>
 *   <li>{@code verified} — devices ready for MFA (VERIFIED + active=true)
 *   <li>{@code inProgress} — onboarding awaiting completion (CREATED or BOUND, any active)
 *   <li>{@code suspended} — admin-disabled devices (VERIFIED + active=false)
 *   <li>{@code expired} — timed out before verification (EXPIRED, any active)
 *   <li>{@code invalid} — failed validation (INVALID, any active)
 *   <li>{@code revoked} — administrator revocations (REVOKED, any active)
 * </ul>
 *
 * @since 2025
 */
@Schema(
    description =
        "Operational enrollment counts (by status and active flag) for dashboard overview")
public class DashboardEnrollmentStatsDto {

  @Schema(description = "Devices ready for MFA: VERIFIED + active=true")
  private long verified;

  @Schema(description = "Onboarding in progress: CREATED or BOUND (any active state)")
  private long inProgress;

  @Schema(description = "Admin-disabled devices: VERIFIED + active=false")
  private long suspended;

  @Schema(description = "Timed out before verification: EXPIRED (any active state)")
  private long expired;

  @Schema(description = "Failed validation: INVALID (any active state)")
  private long invalid;

  @Schema(description = "Administrator revocations: REVOKED (any active state)")
  private long revoked;

  public DashboardEnrollmentStatsDto() {}

  public DashboardEnrollmentStatsDto(
      long verified, long inProgress, long suspended, long expired, long invalid, long revoked) {
    this.verified = verified;
    this.inProgress = inProgress;
    this.suspended = suspended;
    this.expired = expired;
    this.invalid = invalid;
    this.revoked = revoked;
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

  public long getSuspended() {
    return suspended;
  }

  public void setSuspended(long suspended) {
    this.suspended = suspended;
  }

  public long getExpired() {
    return expired;
  }

  public void setExpired(long expired) {
    this.expired = expired;
  }

  public long getInvalid() {
    return invalid;
  }

  public void setInvalid(long invalid) {
    this.invalid = invalid;
  }

  public long getRevoked() {
    return revoked;
  }

  public void setRevoked(long revoked) {
    this.revoked = revoked;
  }
}
