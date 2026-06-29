/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: ScheduledJobLastRun
 * Description: Last-run metadata for a logical scheduled job.
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

/**
 * Last-run metadata for a logical scheduled job (checkpoint scheduler, nightly integrity, etc.).
 *
 * @since 2026
 */
@Entity
@Table(name = "ezkey_scheduled_job_last_run")
public class ScheduledJobLastRun {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "job_key", nullable = false, length = 64)
  private ScheduledJobKey jobKey;

  @Column(name = "last_execution_at")
  private OffsetDateTime lastExecutionAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "last_status", nullable = false, length = 16)
  private ScheduledJobLastRunStatus lastStatus;

  @Column(name = "last_run_scope", length = 512)
  private String lastRunScope;

  @Column(name = "last_error_summary", length = 512)
  private String lastErrorSummary;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public ScheduledJobKey getJobKey() {
    return jobKey;
  }

  public void setJobKey(ScheduledJobKey jobKey) {
    this.jobKey = jobKey;
  }

  public OffsetDateTime getLastExecutionAt() {
    return lastExecutionAt;
  }

  public void setLastExecutionAt(OffsetDateTime lastExecutionAt) {
    this.lastExecutionAt = lastExecutionAt;
  }

  public ScheduledJobLastRunStatus getLastStatus() {
    return lastStatus;
  }

  public void setLastStatus(ScheduledJobLastRunStatus lastStatus) {
    this.lastStatus = lastStatus;
  }

  public String getLastRunScope() {
    return lastRunScope;
  }

  public void setLastRunScope(String lastRunScope) {
    this.lastRunScope = lastRunScope;
  }

  public String getLastErrorSummary() {
    return lastErrorSummary;
  }

  public void setLastErrorSummary(String lastErrorSummary) {
    this.lastErrorSummary = lastErrorSummary;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @PrePersist
  @PreUpdate
  void touchUpdatedAt() {
    if (updatedAt == null) {
      updatedAt = OffsetDateTime.now();
    }
  }
}
