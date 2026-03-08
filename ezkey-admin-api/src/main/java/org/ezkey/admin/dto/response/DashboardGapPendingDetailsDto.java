/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardGapPendingDetailsDto
 * Description: Structured details for AUDIT_CHAIN_GAP_PENDING alert items.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Structured details for an {@code AUDIT_CHAIN_GAP_PENDING} alert emitted by the audit chain
 * scheduler.
 *
 * <p>When the scheduler detects an undeclared gap (last checkpoint before the lookback window), it
 * emits an audit log with these details. The Global Admin must call {@code POST
 * /lifecycle/declare-gap} with {@code anchorCheckpointId} to resolve the gap.
 */
@Schema(description = "Details for audit chain gap pending alert")
public class DashboardGapPendingDetailsDto {

  private String gapStart;
  private String estimatedGapEnd;
  private Long estimatedGapMinutes;
  private Long anchorCheckpointId;
  private String message;

  public DashboardGapPendingDetailsDto() {}

  public String getGapStart() {
    return gapStart;
  }

  public void setGapStart(String gapStart) {
    this.gapStart = gapStart;
  }

  public String getEstimatedGapEnd() {
    return estimatedGapEnd;
  }

  public void setEstimatedGapEnd(String estimatedGapEnd) {
    this.estimatedGapEnd = estimatedGapEnd;
  }

  public Long getEstimatedGapMinutes() {
    return estimatedGapMinutes;
  }

  public void setEstimatedGapMinutes(Long estimatedGapMinutes) {
    this.estimatedGapMinutes = estimatedGapMinutes;
  }

  public Long getAnchorCheckpointId() {
    return anchorCheckpointId;
  }

  public void setAnchorCheckpointId(Long anchorCheckpointId) {
    this.anchorCheckpointId = anchorCheckpointId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
