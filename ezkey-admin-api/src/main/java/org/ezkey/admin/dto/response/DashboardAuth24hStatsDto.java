/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardAuth24hStatsDto
 * Description: Auth attempt counts in the last 24 hours for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Authentication attempt counts in the last 24 hours for the dashboard overview.
 *
 * <p>Provides total, accepted, rejected counts and a server-computed failure rate percentage for
 * the current scope (tenant or instance).
 */
@Schema(description = "Auth attempt counts in the last 24 hours for dashboard overview")
public class DashboardAuth24hStatsDto {

  private long total;
  private long accepted;
  private long rejected;
  private int failureRatePct;

  public DashboardAuth24hStatsDto() {}

  public DashboardAuth24hStatsDto(long total, long accepted, long rejected, int failureRatePct) {
    this.total = total;
    this.accepted = accepted;
    this.rejected = rejected;
    this.failureRatePct = failureRatePct;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public long getAccepted() {
    return accepted;
  }

  public void setAccepted(long accepted) {
    this.accepted = accepted;
  }

  public long getRejected() {
    return rejected;
  }

  public void setRejected(long rejected) {
    this.rejected = rejected;
  }

  public int getFailureRatePct() {
    return failureRatePct;
  }

  public void setFailureRatePct(int failureRatePct) {
    this.failureRatePct = failureRatePct;
  }
}
