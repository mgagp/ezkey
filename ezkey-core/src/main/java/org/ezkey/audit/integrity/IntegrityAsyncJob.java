/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: IntegrityAsyncJob
 * Description: DB-backed Integrity async job slot / outcome row.
 */

package org.ezkey.audit.integrity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * DB-backed Integrity async job row (global slot while {@link IntegrityAsyncJobStatus#RUNNING}).
 *
 * <p>Distinct from {@code ezkey_scheduled_job_last_run}, which is last-run dashboard signal only.
 *
 * @since 2026
 */
@Entity
@Table(name = "ezkey_integrity_async_job")
public class IntegrityAsyncJob {

  public static final String GLOBAL_SLOT_KEY = "GLOBAL";

  @Id
  @Column(name = "job_id", nullable = false)
  private UUID jobId;

  @Column(name = "slot_key", nullable = false, length = 32)
  private String slotKey = GLOBAL_SLOT_KEY;

  @Enumerated(EnumType.STRING)
  @Column(name = "job_type", nullable = false, length = 64)
  private IntegrityAsyncJobType jobType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private IntegrityAsyncJobStatus status;

  @Column(name = "started_by_admin_id", nullable = false)
  private Integer startedByAdminId;

  @Column(name = "started_by_username", nullable = false, length = 255)
  private String startedByUsername;

  @Column(name = "started_at", nullable = false)
  private OffsetDateTime startedAt;

  @Column(name = "heartbeat_at", nullable = false)
  private OffsetDateTime heartbeatAt;

  @Column(name = "finished_at")
  private OffsetDateTime finishedAt;

  @Column(name = "scope_from")
  private OffsetDateTime scopeFrom;

  @Column(name = "scope_to")
  private OffsetDateTime scopeTo;

  @Column(name = "raise_alert")
  private Boolean raiseAlert;

  @Column(name = "result_summary", length = 1024)
  private String resultSummary;

  @Column(name = "error_summary", length = 512)
  private String errorSummary;

  @Column(name = "result_intact")
  private Boolean resultIntact;

  @Column(name = "result_alert_id")
  private Long resultAlertId;

  @Column(name = "abandoned_at")
  private OffsetDateTime abandonedAt;

  @Column(name = "abandoned_by_admin_id")
  private Integer abandonedByAdminId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public String getSlotKey() {
    return slotKey;
  }

  public void setSlotKey(String slotKey) {
    this.slotKey = slotKey;
  }

  public IntegrityAsyncJobType getJobType() {
    return jobType;
  }

  public void setJobType(IntegrityAsyncJobType jobType) {
    this.jobType = jobType;
  }

  public IntegrityAsyncJobStatus getStatus() {
    return status;
  }

  public void setStatus(IntegrityAsyncJobStatus status) {
    this.status = status;
  }

  public Integer getStartedByAdminId() {
    return startedByAdminId;
  }

  public void setStartedByAdminId(Integer startedByAdminId) {
    this.startedByAdminId = startedByAdminId;
  }

  public String getStartedByUsername() {
    return startedByUsername;
  }

  public void setStartedByUsername(String startedByUsername) {
    this.startedByUsername = startedByUsername;
  }

  public OffsetDateTime getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(OffsetDateTime startedAt) {
    this.startedAt = startedAt;
  }

  public OffsetDateTime getHeartbeatAt() {
    return heartbeatAt;
  }

  public void setHeartbeatAt(OffsetDateTime heartbeatAt) {
    this.heartbeatAt = heartbeatAt;
  }

  public OffsetDateTime getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(OffsetDateTime finishedAt) {
    this.finishedAt = finishedAt;
  }

  public OffsetDateTime getScopeFrom() {
    return scopeFrom;
  }

  public void setScopeFrom(OffsetDateTime scopeFrom) {
    this.scopeFrom = scopeFrom;
  }

  public OffsetDateTime getScopeTo() {
    return scopeTo;
  }

  public void setScopeTo(OffsetDateTime scopeTo) {
    this.scopeTo = scopeTo;
  }

  public Boolean getRaiseAlert() {
    return raiseAlert;
  }

  public void setRaiseAlert(Boolean raiseAlert) {
    this.raiseAlert = raiseAlert;
  }

  public String getResultSummary() {
    return resultSummary;
  }

  public void setResultSummary(String resultSummary) {
    this.resultSummary = resultSummary;
  }

  public String getErrorSummary() {
    return errorSummary;
  }

  public void setErrorSummary(String errorSummary) {
    this.errorSummary = errorSummary;
  }

  public Boolean getResultIntact() {
    return resultIntact;
  }

  public void setResultIntact(Boolean resultIntact) {
    this.resultIntact = resultIntact;
  }

  public Long getResultAlertId() {
    return resultAlertId;
  }

  public void setResultAlertId(Long resultAlertId) {
    this.resultAlertId = resultAlertId;
  }

  public OffsetDateTime getAbandonedAt() {
    return abandonedAt;
  }

  public void setAbandonedAt(OffsetDateTime abandonedAt) {
    this.abandonedAt = abandonedAt;
  }

  public Integer getAbandonedByAdminId() {
    return abandonedByAdminId;
  }

  public void setAbandonedByAdminId(Integer abandonedByAdminId) {
    this.abandonedByAdminId = abandonedByAdminId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }
}
