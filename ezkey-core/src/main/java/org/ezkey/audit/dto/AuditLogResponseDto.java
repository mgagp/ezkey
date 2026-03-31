/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuditLogResponseDto
 * Description: Response DTO for audit log entries.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;

/**
 * Response DTO for audit log entries.
 *
 * <p>Provides audit log data for API responses with all relevant fields for security monitoring and
 * compliance reporting.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class AuditLogResponseDto {

  private Long auditLogId;
  private EventType eventType;
  private String eventAction;
  private EventStatus eventStatus;
  private ApiName apiName;
  private String ipAddress;
  private String userAgent;
  private Integer adminId;
  private Integer integrationId;
  private Integer enrollmentId;
  private Integer authAttemptId;
  private Integer tenantId;
  private Integer targetAdminId;
  private String eventDetails;
  private String errorMessage;
  private String instanceId;
  private String entryHmac;
  private OffsetDateTime createdAt;
  private String reason;

  // Constructors

  public AuditLogResponseDto() {}

  // Getters and setters

  public Long getAuditLogId() {
    return auditLogId;
  }

  public void setAuditLogId(Long auditLogId) {
    this.auditLogId = auditLogId;
  }

  public EventType getEventType() {
    return eventType;
  }

  public void setEventType(EventType eventType) {
    this.eventType = eventType;
  }

  public String getEventAction() {
    return eventAction;
  }

  public void setEventAction(String eventAction) {
    this.eventAction = eventAction;
  }

  public EventStatus getEventStatus() {
    return eventStatus;
  }

  public void setEventStatus(EventStatus eventStatus) {
    this.eventStatus = eventStatus;
  }

  public ApiName getApiName() {
    return apiName;
  }

  public void setApiName(ApiName apiName) {
    this.apiName = apiName;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public Integer getAdminId() {
    return adminId;
  }

  public void setAdminId(Integer adminId) {
    this.adminId = adminId;
  }

  public Integer getIntegrationId() {
    return integrationId;
  }

  public void setIntegrationId(Integer integrationId) {
    this.integrationId = integrationId;
  }

  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  public Integer getAuthAttemptId() {
    return authAttemptId;
  }

  public void setAuthAttemptId(Integer authAttemptId) {
    this.authAttemptId = authAttemptId;
  }

  public Integer getTenantId() {
    return tenantId;
  }

  public void setTenantId(Integer tenantId) {
    this.tenantId = tenantId;
  }

  public Integer getTargetAdminId() {
    return targetAdminId;
  }

  public void setTargetAdminId(Integer targetAdminId) {
    this.targetAdminId = targetAdminId;
  }

  public String getEventDetails() {
    return eventDetails;
  }

  public void setEventDetails(String eventDetails) {
    this.eventDetails = eventDetails;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = instanceId;
  }

  public String getEntryHmac() {
    return entryHmac;
  }

  public void setEntryHmac(String entryHmac) {
    this.entryHmac = entryHmac;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}
