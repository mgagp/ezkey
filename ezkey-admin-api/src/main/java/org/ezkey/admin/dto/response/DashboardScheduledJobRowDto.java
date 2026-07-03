/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardScheduledJobRowDto
 * Description: Last-run row for a scheduled job on the dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Last-run metadata for a logical scheduled job, projected for the dashboard (Global Admin only).
 *
 * @since 2026
 */
@Schema(description = "Scheduled job last-run row for dashboard batch health widgets")
public class DashboardScheduledJobRowDto {

  private String jobKey;
  private OffsetDateTime lastExecutionAt;
  private String lastStatus;
  private String lastRunScope;
  private String lastErrorSummary;

  public DashboardScheduledJobRowDto() {}

  @Schema(
      description = "Stable job identifier",
      example = "AUDIT_CHAIN_CHECKPOINT",
      allowableValues = {"AUDIT_CHAIN_CHECKPOINT", "NIGHTLY_INTEGRITY_VALIDATION", "REENCRYPTION"})
  public String getJobKey() {
    return jobKey;
  }

  public void setJobKey(String jobKey) {
    this.jobKey = jobKey;
  }

  @Schema(description = "Timestamp of the most recent execution (null when never run)")
  public OffsetDateTime getLastExecutionAt() {
    return lastExecutionAt;
  }

  public void setLastExecutionAt(OffsetDateTime lastExecutionAt) {
    this.lastExecutionAt = lastExecutionAt;
  }

  @Schema(
      description = "Outcome of the most recent execution",
      allowableValues = {"SUCCESS", "FAILED", "NEVER_RUN"})
  public String getLastStatus() {
    return lastStatus;
  }

  public void setLastStatus(String lastStatus) {
    this.lastStatus = lastStatus;
  }

  @Schema(description = "Human-readable scope of the last run (e.g. validated window)")
  public String getLastRunScope() {
    return lastRunScope;
  }

  public void setLastRunScope(String lastRunScope) {
    this.lastRunScope = lastRunScope;
  }

  @Schema(description = "Safe operator-facing error summary when lastStatus is FAILED")
  public String getLastErrorSummary() {
    return lastErrorSummary;
  }

  public void setLastErrorSummary(String lastErrorSummary) {
    this.lastErrorSummary = lastErrorSummary;
  }
}
