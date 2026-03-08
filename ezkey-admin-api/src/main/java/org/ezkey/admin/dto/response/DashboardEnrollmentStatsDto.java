/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardEnrollmentStatsDto
 * Description: Enrollment counts by status for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Enrollment counts by status for the dashboard overview.
 *
 * <p>Provides total and per-status counts (verified, bound, created) for the current scope (tenant
 * or instance).
 */
@Schema(description = "Enrollment counts by status for dashboard overview")
public class DashboardEnrollmentStatsDto {

  private long total;
  private long verified;
  private long bound;
  private long created;

  public DashboardEnrollmentStatsDto() {}

  public DashboardEnrollmentStatsDto(long total, long verified, long bound, long created) {
    this.total = total;
    this.verified = verified;
    this.bound = bound;
    this.created = created;
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

  public long getBound() {
    return bound;
  }

  public void setBound(long bound) {
    this.bound = bound;
  }

  public long getCreated() {
    return created;
  }

  public void setCreated(long created) {
    this.created = created;
  }
}
