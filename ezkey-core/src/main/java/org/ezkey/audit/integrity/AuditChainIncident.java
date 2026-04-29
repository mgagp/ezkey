/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: AuditChainIncident
 * Description: Operational incident for audit-chain heartbeat degradation (partial outage).
 */

package org.ezkey.audit.integrity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Records an operational narrative when peripheral APIs observe Admin API checkpoint heartbeat
 * loss.
 *
 * <p>This is intentionally separate from {@code GAP_DECLARATION} checkpoints (validated
 * zero-activity downtime). Rows here describe supervised-chain risk while audit entries may still
 * exist.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Entity
@Table(name = "ezkey_audit_chain_incident")
public class AuditChainIncident {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "incident_id")
  private Long incidentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 40)
  private AuditChainIncidentStatus status;

  /**
   * Nullable when degraded mode triggers before any checkpoint row exists (bootstrap edge case).
   */
  @Column(name = "anchor_checkpoint_id")
  private Long anchorCheckpointId;

  @Column(name = "stale_since", nullable = false)
  private OffsetDateTime staleSince;

  @Column(name = "degraded_since")
  private OffsetDateTime degradedSince;

  @Column(name = "recovered_at")
  private OffsetDateTime recoveredAt;

  @Column(name = "justification", columnDefinition = "TEXT")
  private String justification;

  @Enumerated(EnumType.STRING)
  @Column(name = "root_cause", length = 64)
  private AuditChainIncidentRootCause rootCause;

  @Column(name = "declared_at")
  private OffsetDateTime declaredAt;

  @Column(name = "declared_by_admin_id")
  private Integer declaredByAdminId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now(java.time.ZoneOffset.UTC);
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC);
  }

  public Long getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(Long incidentId) {
    this.incidentId = incidentId;
  }

  public AuditChainIncidentStatus getStatus() {
    return status;
  }

  public void setStatus(AuditChainIncidentStatus status) {
    this.status = status;
  }

  public Long getAnchorCheckpointId() {
    return anchorCheckpointId;
  }

  public void setAnchorCheckpointId(Long anchorCheckpointId) {
    this.anchorCheckpointId = anchorCheckpointId;
  }

  public OffsetDateTime getStaleSince() {
    return staleSince;
  }

  public void setStaleSince(OffsetDateTime staleSince) {
    this.staleSince = staleSince;
  }

  public OffsetDateTime getDegradedSince() {
    return degradedSince;
  }

  public void setDegradedSince(OffsetDateTime degradedSince) {
    this.degradedSince = degradedSince;
  }

  public OffsetDateTime getRecoveredAt() {
    return recoveredAt;
  }

  public void setRecoveredAt(OffsetDateTime recoveredAt) {
    this.recoveredAt = recoveredAt;
  }

  public String getJustification() {
    return justification;
  }

  public void setJustification(String justification) {
    this.justification = justification;
  }

  public AuditChainIncidentRootCause getRootCause() {
    return rootCause;
  }

  public void setRootCause(AuditChainIncidentRootCause rootCause) {
    this.rootCause = rootCause;
  }

  public OffsetDateTime getDeclaredAt() {
    return declaredAt;
  }

  public void setDeclaredAt(OffsetDateTime declaredAt) {
    this.declaredAt = declaredAt;
  }

  public Integer getDeclaredByAdminId() {
    return declaredByAdminId;
  }

  public void setDeclaredByAdminId(Integer declaredByAdminId) {
    this.declaredByAdminId = declaredByAdminId;
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
