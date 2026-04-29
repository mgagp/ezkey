/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditChainIncidentService
 * Description: Lists and declares audit-chain heartbeat operational incidents (non-chain linkage).
 */

package org.ezkey.audit.integrity;

import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.AuditChainIncidentResponseDto;
import org.ezkey.audit.dto.DeclareAuditChainIncidentRequest;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for heartbeat incident lifecycle after peripheral degradation episodes.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AuditChainIncidentService {

  private final AuditChainIncidentRepository incidentRepository;
  private final AuditLogService auditLogService;

  /**
   * Constructs the incident service.
   *
   * @param incidentRepository incident persistence
   * @param auditLogService audit trail writer for declaration meta-events
   */
  public AuditChainIncidentService(
      AuditChainIncidentRepository incidentRepository, AuditLogService auditLogService) {
    this.incidentRepository = incidentRepository;
    this.auditLogService = auditLogService;
  }

  /**
   * Paginated incidents newest first.
   *
   * @param pageable paging request
   * @return matching incidents mapped for API boundaries
   */
  @Transactional(readOnly = true)
  public Page<AuditChainIncidentResponseDto> search(Pageable pageable) {
    return incidentRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
  }

  /**
   * Declares closure for an incident in {@link
   * AuditChainIncidentStatus#RECOVERED_PENDING_DECLARATION}.
   *
   * @param incidentId incident primary key
   * @param request justification and root cause payload
   * @param declaringAdminId declaring admin identifier or {@code null}
   * @return updated incident projection
   */
  @Transactional
  public AuditChainIncidentResponseDto declareIncident(
      long incidentId, DeclareAuditChainIncidentRequest request, Integer declaringAdminId) {
    AuditChainIncident incident =
        incidentRepository
            .findById(incidentId)
            .orElseThrow(() -> new ResourceNotFoundException("AuditChainIncident", incidentId));

    if (incident.getStatus() != AuditChainIncidentStatus.RECOVERED_PENDING_DECLARATION) {
      throw new IllegalArgumentException(
          "Incident is not awaiting declaration (current status=" + incident.getStatus() + ")");
    }

    incident.setJustification(request.justification());
    incident.setRootCause(request.rootCause());
    incident.setDeclaredAt(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
    incident.setDeclaredByAdminId(declaringAdminId);
    incident.setStatus(AuditChainIncidentStatus.CLOSED);
    AuditChainIncident saved = incidentRepository.save(incident);

    emitDeclaredAudit(saved);

    return toDto(saved);
  }

  private void emitDeclaredAudit(AuditChainIncident incident) {
    String detailsJson =
        AuditDetailsBuilder.builder()
            .custom("incident_id", incident.getIncidentId())
            .custom("anchor_checkpoint_id", incident.getAnchorCheckpointId())
            .custom("root_cause", incident.getRootCause().name())
            .toJson();

    AuditLog entry =
        AuditLog.builder()
            .eventType(EventType.AUDIT_CHAIN_INCIDENT_DECLARED)
            .eventAction("audit_chain_incident_declared")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .adminId(incident.getDeclaredByAdminId())
            .eventDetails(detailsJson)
            .build();
    auditLogService.log(entry);
  }

  private AuditChainIncidentResponseDto toDto(AuditChainIncident entity) {
    AuditChainIncidentResponseDto dto = new AuditChainIncidentResponseDto();
    dto.setIncidentId(entity.getIncidentId());
    dto.setStatus(entity.getStatus());
    dto.setAnchorCheckpointId(entity.getAnchorCheckpointId());
    dto.setStaleSince(entity.getStaleSince());
    dto.setDegradedSince(entity.getDegradedSince());
    dto.setRecoveredAt(entity.getRecoveredAt());
    dto.setJustification(entity.getJustification());
    dto.setRootCause(entity.getRootCause());
    dto.setDeclaredAt(entity.getDeclaredAt());
    dto.setDeclaredByAdminId(entity.getDeclaredByAdminId());
    dto.setCreatedAt(entity.getCreatedAt());
    dto.setUpdatedAt(entity.getUpdatedAt());
    return dto;
  }
}
