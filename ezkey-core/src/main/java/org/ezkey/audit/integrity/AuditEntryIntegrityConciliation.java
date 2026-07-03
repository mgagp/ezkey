/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Entity: AuditEntryIntegrityConciliation
 * Description: Operator conciliation for a per-entry HMAC integrity violation.
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
import org.ezkey.audit.dto.IntegrityRuptureConciliationCategory;

/**
 * Registry row recording that a Global Admin acknowledged a known invalid per-entry HMAC state.
 *
 * <p>Distinct from mutating {@code ezkey_audit_log} — the entry remains cryptographically invalid;
 * this row is the durable operator act and skip gate for re-alerting.
 *
 * <p>{@code auditLogId} and {@code auditLogCreatedAt} snapshot the partitioned entry identity
 * {@code (audit_log_id, created_at)} at conciliation time. There is intentionally no JPA/FK link to
 * {@code ezkey_audit_log}: archive-sealed partitions are physically purged while this registry must
 * retain the operator narrative.
 *
 * @since 2026
 */
@Entity
@Table(name = "ezkey_audit_entry_integrity_conciliation")
public class AuditEntryIntegrityConciliation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "conciliation_id")
  private Long conciliationId;

  @Column(name = "audit_log_id", nullable = false)
  private Long auditLogId;

  @Column(name = "audit_log_created_at", nullable = false)
  private OffsetDateTime auditLogCreatedAt;

  @Column(name = "violation_reason", nullable = false, length = 32)
  private String violationReason;

  @Column(name = "observed_state_fingerprint", nullable = false, length = 64)
  private String observedStateFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 64)
  private IntegrityRuptureConciliationCategory category;

  @Column(name = "justification", nullable = false, length = 500)
  private String justification;

  @Column(name = "external_ticket_reference", length = 128)
  private String externalTicketReference;

  @Column(name = "source_alert_id", nullable = false)
  private Long sourceAlertId;

  @Column(name = "conciliated_by_admin_id", nullable = false)
  private Integer conciliatedByAdminId;

  @Column(name = "conciliated_at", nullable = false)
  private OffsetDateTime conciliatedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private AuditEntryIntegrityConciliationStatus status;

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

  public Long getConciliationId() {
    return conciliationId;
  }

  public void setConciliationId(Long conciliationId) {
    this.conciliationId = conciliationId;
  }

  public Long getAuditLogId() {
    return auditLogId;
  }

  public void setAuditLogId(Long auditLogId) {
    this.auditLogId = auditLogId;
  }

  public OffsetDateTime getAuditLogCreatedAt() {
    return auditLogCreatedAt;
  }

  public void setAuditLogCreatedAt(OffsetDateTime auditLogCreatedAt) {
    this.auditLogCreatedAt = auditLogCreatedAt;
  }

  public String getViolationReason() {
    return violationReason;
  }

  public void setViolationReason(String violationReason) {
    this.violationReason = violationReason;
  }

  public String getObservedStateFingerprint() {
    return observedStateFingerprint;
  }

  public void setObservedStateFingerprint(String observedStateFingerprint) {
    this.observedStateFingerprint = observedStateFingerprint;
  }

  public IntegrityRuptureConciliationCategory getCategory() {
    return category;
  }

  public void setCategory(IntegrityRuptureConciliationCategory category) {
    this.category = category;
  }

  public String getJustification() {
    return justification;
  }

  public void setJustification(String justification) {
    this.justification = justification;
  }

  public String getExternalTicketReference() {
    return externalTicketReference;
  }

  public void setExternalTicketReference(String externalTicketReference) {
    this.externalTicketReference = externalTicketReference;
  }

  public Long getSourceAlertId() {
    return sourceAlertId;
  }

  public void setSourceAlertId(Long sourceAlertId) {
    this.sourceAlertId = sourceAlertId;
  }

  public Integer getConciliatedByAdminId() {
    return conciliatedByAdminId;
  }

  public void setConciliatedByAdminId(Integer conciliatedByAdminId) {
    this.conciliatedByAdminId = conciliatedByAdminId;
  }

  public OffsetDateTime getConciliatedAt() {
    return conciliatedAt;
  }

  public void setConciliatedAt(OffsetDateTime conciliatedAt) {
    this.conciliatedAt = conciliatedAt;
  }

  public AuditEntryIntegrityConciliationStatus getStatus() {
    return status;
  }

  public void setStatus(AuditEntryIntegrityConciliationStatus status) {
    this.status = status;
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
