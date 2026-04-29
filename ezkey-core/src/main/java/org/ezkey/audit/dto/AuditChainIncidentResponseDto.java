/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuditChainIncidentResponseDto
 * Description: API response shape for audit-chain heartbeat operational incidents.
 */

package org.ezkey.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.ezkey.audit.integrity.AuditChainIncidentRootCause;
import org.ezkey.audit.integrity.AuditChainIncidentStatus;

/**
 * Response payload for {@code ezkey_audit_chain_incident} rows.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Schema(description = "Operational audit-chain heartbeat incident summary")
public class AuditChainIncidentResponseDto {

  private Long incidentId;

  private AuditChainIncidentStatus status;

  private Long anchorCheckpointId;

  private OffsetDateTime staleSince;

  private OffsetDateTime degradedSince;

  private OffsetDateTime recoveredAt;

  private String justification;

  private AuditChainIncidentRootCause rootCause;

  private OffsetDateTime declaredAt;

  private Integer declaredByAdminId;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;

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
