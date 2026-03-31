/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardAlertItemDto
 * Description: Single admin-console alert (e.g. AUDIT_CHAIN_GAP_PENDING) for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * A single alert intended for the admin console (e.g. undeclared audit chain gap).
 *
 * <p>Populated only for Global Admin. Currently supports {@code AUDIT_CHAIN_GAP_PENDING} with
 * structured {@code eventDetails}.
 */
@Schema(description = "Admin console alert for dashboard overview (Global Admin only)")
public class DashboardAlertItemDto {

  private Long auditLogId;
  private String eventType;
  private String eventStatus;
  private OffsetDateTime createdAt;
  private DashboardGapPendingDetailsDto eventDetails;

  public DashboardAlertItemDto() {}

  public Long getAuditLogId() {
    return auditLogId;
  }

  public void setAuditLogId(Long auditLogId) {
    this.auditLogId = auditLogId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getEventStatus() {
    return eventStatus;
  }

  public void setEventStatus(String eventStatus) {
    this.eventStatus = eventStatus;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public DashboardGapPendingDetailsDto getEventDetails() {
    return eventDetails;
  }

  public void setEventDetails(DashboardGapPendingDetailsDto eventDetails) {
    this.eventDetails = eventDetails;
  }
}
