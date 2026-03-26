/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardAuth24hStatsDto
 * Description: Auth attempt health stats in the last 24 hours for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Authentication attempt statistics in the last rolling 24 hours for the dashboard overview.
 *
 * <p>Counts include every attempt created in the window. Percentage fields are computed over
 * <strong>terminal</strong> outcomes only ({@code ACCEPTED}, {@code REJECTED}, {@code INVALID},
 * {@code EXPIRED}) and are {@code null} when there are no terminal outcomes in the window.
 */
@Schema(description = "Auth attempt counts and terminal-outcome rates in the last 24h (dashboard)")
public class DashboardAuth24hStatsDto {

  @Schema(description = "All attempts created in the rolling 24h window")
  private long total;

  @Schema(description = "Attempts still pending on the device")
  private long pending;

  @Schema(description = "Attempts claimed by the device (read) but not yet completed")
  private long readCount;

  @Schema(description = "User approved")
  private long accepted;

  @Schema(description = "User explicitly denied")
  private long rejected;

  @Schema(description = "Cryptographic validation failed")
  private long invalid;

  @Schema(description = "Timed out or superseded")
  private long expired;

  @Schema(
      description =
          "Terminal outcomes: accepted + rejected + invalid + expired (denominator for rate"
              + " fields)")
  private long terminalTotal;

  @Schema(
      description = "Accepted as % of terminal outcomes; null if terminalTotal is 0",
      nullable = true)
  private Integer successRatePct;

  @Schema(
      description = "Invalid as % of terminal outcomes; null if terminalTotal is 0",
      nullable = true)
  private Integer invalidRatePct;

  @Schema(
      description = "Expired as % of terminal outcomes; null if terminalTotal is 0",
      nullable = true)
  private Integer expiredRatePct;

  @Schema(
      description = "Rejected as % of terminal outcomes; null if terminalTotal is 0",
      nullable = true)
  private Integer rejectedRatePct;

  public DashboardAuth24hStatsDto() {}

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public long getPending() {
    return pending;
  }

  public void setPending(long pending) {
    this.pending = pending;
  }

  public long getReadCount() {
    return readCount;
  }

  public void setReadCount(long readCount) {
    this.readCount = readCount;
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

  public long getInvalid() {
    return invalid;
  }

  public void setInvalid(long invalid) {
    this.invalid = invalid;
  }

  public long getExpired() {
    return expired;
  }

  public void setExpired(long expired) {
    this.expired = expired;
  }

  public long getTerminalTotal() {
    return terminalTotal;
  }

  public void setTerminalTotal(long terminalTotal) {
    this.terminalTotal = terminalTotal;
  }

  public Integer getSuccessRatePct() {
    return successRatePct;
  }

  public void setSuccessRatePct(Integer successRatePct) {
    this.successRatePct = successRatePct;
  }

  public Integer getInvalidRatePct() {
    return invalidRatePct;
  }

  public void setInvalidRatePct(Integer invalidRatePct) {
    this.invalidRatePct = invalidRatePct;
  }

  public Integer getExpiredRatePct() {
    return expiredRatePct;
  }

  public void setExpiredRatePct(Integer expiredRatePct) {
    this.expiredRatePct = expiredRatePct;
  }

  public Integer getRejectedRatePct() {
    return rejectedRatePct;
  }

  public void setRejectedRatePct(Integer rejectedRatePct) {
    this.rejectedRatePct = rejectedRatePct;
  }
}
