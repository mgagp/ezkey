/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: AuditLog
 * Description: JPA entity for audit log records.
 */

package org.ezkey.audit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;

/**
 * JPA entity representing an audit log entry.
 *
 * <p>Captures comprehensive information about security-relevant events across all Ezkey APIs for
 * monitoring, forensic analysis, and SOC 2 compliance requirements. Supports per-entry HMAC signing
 * for tamper-evidence in self-hosted deployments.
 *
 * <p><b>Integrity Fields:</b>
 *
 * <ul>
 *   <li>{@code instanceId} - Application instance that created this entry (HA traceability)
 *   <li>{@code entryHmac} - HMAC-SHA256 of canonical entry content (tamper-evidence)
 * </ul>
 *
 * <p><b>Database Table:</b> ezkey_audit_log (composite: RANGE created_at by month, LIST api_name
 * per month)
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Entity
@Table(name = "ezkey_audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "audit_log_id")
  private Long auditLogId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 50)
  private EventType eventType;

  @Column(name = "event_action", nullable = false, length = 100)
  private String eventAction;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_status", nullable = false, length = 20)
  private EventStatus eventStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "api_name", nullable = false, length = 50)
  private ApiName apiName;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(name = "admin_id")
  private Integer adminId;

  @Column(name = "integration_id")
  private Integer integrationId;

  @Column(name = "integration_id_hmac_snapshot")
  private Integer integrationIdHmacSnapshot;

  @Column(name = "enrollment_id")
  private Integer enrollmentId;

  @Column(name = "enrollment_id_hmac_snapshot")
  private Integer enrollmentIdHmacSnapshot;

  @Column(name = "auth_attempt_id")
  private Integer authAttemptId;

  @Column(name = "auth_attempt_created_at")
  private OffsetDateTime authAttemptCreatedAt;

  @Column(name = "tenant_id")
  private Integer tenantId;

  /**
   * Admin ID that is the subject of the event (e.g. created, deactivated, or activated). Used for
   * querying all events affecting a given administrator. Null for non-admin-lifecycle events.
   * Included in HMAC canonical form as field 16.
   */
  @Column(name = "target_admin_id")
  private Integer targetAdminId;

  @Column(name = "event_details", columnDefinition = "TEXT")
  private String eventDetails;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "instance_id", length = 50)
  private String instanceId;

  @Column(name = "entry_hmac", length = 88)
  private String entryHmac;

  /**
   * Optional justification supplied by the admin for sensitive operations (revocation, deletion,
   * deactivation, key rotation).
   *
   * <p>Supports SOC 2 CC6.3 (access deprovisioning) and CC8.1 (authorized changes). Validated to be
   * between 10 and 500 characters when provided. Included in per-entry HMAC canonical form as field
   * 15 (null → empty string).
   */
  @Column(name = "reason", length = 500)
  private String reason;

  @Column(
      name = "created_at",
      nullable = false,
      columnDefinition = "TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP")
  private OffsetDateTime createdAt;

  /** Default constructor for JPA. */
  public AuditLog() {
    this.createdAt = OffsetDateTime.now();
  }

  /**
   * Builder pattern for creating audit log entries.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for AuditLog entity. */
  public static class Builder {
    private final AuditLog auditLog;

    public Builder() {
      this.auditLog = new AuditLog();
    }

    public Builder eventType(EventType eventType) {
      auditLog.eventType = eventType;
      return this;
    }

    public Builder eventAction(String eventAction) {
      auditLog.eventAction = eventAction;
      return this;
    }

    public Builder eventStatus(EventStatus eventStatus) {
      auditLog.eventStatus = eventStatus;
      return this;
    }

    public Builder apiName(ApiName apiName) {
      auditLog.apiName = apiName;
      return this;
    }

    public Builder ipAddress(String ipAddress) {
      auditLog.ipAddress = ipAddress;
      return this;
    }

    public Builder userAgent(String userAgent) {
      auditLog.userAgent = userAgent;
      return this;
    }

    public Builder adminId(Integer adminId) {
      auditLog.adminId = adminId;
      return this;
    }

    public Builder integrationId(Integer integrationId) {
      auditLog.integrationId = integrationId;
      return this;
    }

    public Builder enrollmentId(Integer enrollmentId) {
      auditLog.enrollmentId = enrollmentId;
      return this;
    }

    public Builder authAttemptId(Integer authAttemptId) {
      auditLog.authAttemptId = authAttemptId;
      return this;
    }

    public Builder authAttemptCreatedAt(OffsetDateTime authAttemptCreatedAt) {
      auditLog.authAttemptCreatedAt = authAttemptCreatedAt;
      return this;
    }

    public Builder tenantId(Integer tenantId) {
      auditLog.tenantId = tenantId;
      return this;
    }

    public Builder targetAdminId(Integer targetAdminId) {
      auditLog.targetAdminId = targetAdminId;
      return this;
    }

    /**
     * Sets event-specific details. Must be valid JSON for event types covered by
     * JSONB indexes (KEY_*, REENCRYPTION_*). Use {@link org.ezkey.audit.util.AuditDetailsBuilder}
     * to produce compliant JSON.
     *
     * @param eventDetails JSON string (e.g. from AuditDetailsBuilder.toJson())
     * @return this builder
     */
    public Builder eventDetails(String eventDetails) {
      auditLog.eventDetails = eventDetails;
      return this;
    }

    public Builder errorMessage(String errorMessage) {
      auditLog.errorMessage = errorMessage;
      return this;
    }

    public Builder instanceId(String instanceId) {
      auditLog.instanceId = instanceId;
      return this;
    }

    public Builder entryHmac(String entryHmac) {
      auditLog.entryHmac = entryHmac;
      return this;
    }

    /**
     * Sets the optional justification for sensitive operations.
     *
     * @param reason the justification text (10–500 chars when provided; null accepted)
     * @return this builder
     */
    public Builder reason(String reason) {
      auditLog.reason = reason;
      return this;
    }

    public AuditLog build() {
      if (auditLog.eventType == null) {
        throw new IllegalStateException("eventType is required");
      }
      if (auditLog.eventAction == null) {
        throw new IllegalStateException("eventAction is required");
      }
      if (auditLog.eventStatus == null) {
        throw new IllegalStateException("eventStatus is required");
      }
      if (auditLog.apiName == null) {
        throw new IllegalStateException("apiName is required");
      }
      return auditLog;
    }
  }

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

  public Integer getIntegrationIdHmacSnapshot() {
    return integrationIdHmacSnapshot;
  }

  public void setIntegrationIdHmacSnapshot(Integer integrationIdHmacSnapshot) {
    this.integrationIdHmacSnapshot = integrationIdHmacSnapshot;
  }

  public Integer getEnrollmentIdHmacSnapshot() {
    return enrollmentIdHmacSnapshot;
  }

  public void setEnrollmentIdHmacSnapshot(Integer enrollmentIdHmacSnapshot) {
    this.enrollmentIdHmacSnapshot = enrollmentIdHmacSnapshot;
  }

  public Integer getAuthAttemptId() {
    return authAttemptId;
  }

  public void setAuthAttemptId(Integer authAttemptId) {
    this.authAttemptId = authAttemptId;
  }

  public OffsetDateTime getAuthAttemptCreatedAt() {
    return authAttemptCreatedAt;
  }

  public void setAuthAttemptCreatedAt(OffsetDateTime authAttemptCreatedAt) {
    this.authAttemptCreatedAt = authAttemptCreatedAt;
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
