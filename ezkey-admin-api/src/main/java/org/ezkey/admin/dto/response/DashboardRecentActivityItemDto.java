/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DashboardRecentActivityItemDto
 * Description: Single recent audit log entry for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** A single recent audit log entry for the dashboard recent activity widget. */
@Schema(description = "Recent audit log entry for dashboard overview")
public class DashboardRecentActivityItemDto {

  private Long auditLogId;
  private String eventType;
  private String eventStatus;
  private String eventAction;
  private String apiName;
  private Integer adminId;
  private OffsetDateTime createdAt;

  public DashboardRecentActivityItemDto() {}

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

  public String getEventAction() {
    return eventAction;
  }

  public void setEventAction(String eventAction) {
    this.eventAction = eventAction;
  }

  public String getApiName() {
    return apiName;
  }

  public void setApiName(String apiName) {
    this.apiName = apiName;
  }

  public Integer getAdminId() {
    return adminId;
  }

  public void setAdminId(Integer adminId) {
    this.adminId = adminId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
