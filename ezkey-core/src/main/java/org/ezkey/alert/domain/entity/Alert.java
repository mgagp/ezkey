/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: Alert
 * Description: JPA entity for ezkey_alert rows (operator-facing alert surface).
 */

package org.ezkey.alert.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;

/**
 * JPA entity representing a single operator-facing alert row.
 *
 * <p>Backed by the {@code ezkey_alert} table created in Flyway migration V11. Lifecycle is kept
 * deliberately minimal: rows transition once from {@link AlertStatus#OPEN} to {@link
 * AlertStatus#RESOLVED}. Producers use raise-or-touch semantics keyed on {@code dedupeKey}; a
 * partial unique index in the database enforces a single open row per dedupe key.
 *
 * @since 2026
 */
@Entity
@Table(name = "ezkey_alert")
public class Alert {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "alert_id")
  private Long alertId;

  @Enumerated(EnumType.STRING)
  @Column(name = "alert_type", nullable = false, length = 64)
  private AlertType alertType;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 16)
  private AlertSeverity severity;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AlertStatus status;

  @Column(name = "dedupe_key", nullable = false, length = 256)
  private String dedupeKey;

  @Column(name = "payload", columnDefinition = "TEXT")
  private String payload;

  @Column(name = "occurrence_count", nullable = false)
  private int occurrenceCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "last_seen_at", nullable = false)
  private OffsetDateTime lastSeenAt;

  @Column(name = "resolved_at")
  private OffsetDateTime resolvedAt;

  @Column(name = "resolved_by_admin_id")
  private Integer resolvedByAdminId;

  @Enumerated(EnumType.STRING)
  @Column(name = "resolution_reason", length = 64)
  private AlertResolutionReason resolutionReason;

  public Alert() {}

  public Long getAlertId() {
    return alertId;
  }

  public void setAlertId(Long alertId) {
    this.alertId = alertId;
  }

  public AlertType getAlertType() {
    return alertType;
  }

  public void setAlertType(AlertType alertType) {
    this.alertType = alertType;
  }

  public AlertSeverity getSeverity() {
    return severity;
  }

  public void setSeverity(AlertSeverity severity) {
    this.severity = severity;
  }

  public AlertStatus getStatus() {
    return status;
  }

  public void setStatus(AlertStatus status) {
    this.status = status;
  }

  public String getDedupeKey() {
    return dedupeKey;
  }

  public void setDedupeKey(String dedupeKey) {
    this.dedupeKey = dedupeKey;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public int getOccurrenceCount() {
    return occurrenceCount;
  }

  public void setOccurrenceCount(int occurrenceCount) {
    this.occurrenceCount = occurrenceCount;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(OffsetDateTime lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public OffsetDateTime getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(OffsetDateTime resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public Integer getResolvedByAdminId() {
    return resolvedByAdminId;
  }

  public void setResolvedByAdminId(Integer resolvedByAdminId) {
    this.resolvedByAdminId = resolvedByAdminId;
  }

  public AlertResolutionReason getResolutionReason() {
    return resolutionReason;
  }

  public void setResolutionReason(AlertResolutionReason resolutionReason) {
    this.resolutionReason = resolutionReason;
  }
}
