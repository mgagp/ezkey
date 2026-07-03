/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditEntryIntegrityConciliationService
 * Description: Fingerprint, lookup, and creation for per-entry integrity conciliation rows.
 */

package org.ezkey.audit.integrity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.IntegrityRuptureConciliationCategory;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for per-entry integrity conciliation registry rows.
 *
 * @since 2026
 */
@Service
public class AuditEntryIntegrityConciliationService {

  private final AuditEntryIntegrityConciliationRepository conciliationRepository;
  private final AuditHmacService auditHmacService;
  private final AuditLogService auditLogService;

  /**
   * Constructs the conciliation service.
   *
   * @param conciliationRepository conciliation persistence
   * @param auditHmacService canonical entry builder for fingerprint computation
   * @param auditLogService audit trail writer for conciliation meta-events
   */
  public AuditEntryIntegrityConciliationService(
      AuditEntryIntegrityConciliationRepository conciliationRepository,
      AuditHmacService auditHmacService,
      AuditLogService auditLogService) {
    this.conciliationRepository = conciliationRepository;
    this.auditHmacService = auditHmacService;
    this.auditLogService = auditLogService;
  }

  /**
   * Computes the SHA-256 hex fingerprint of the entry canonical form at the current observed state.
   *
   * @param entry audit log row
   * @return lowercase 64-character hex digest
   */
  public String computeObservedStateFingerprint(AuditLog entry) {
    String canonical = auditHmacService.buildCanonicalForm(entry);
    return sha256Hex(canonical);
  }

  /**
   * Returns the ACTIVE conciliation for an audit log entry, if any.
   *
   * @param auditLogId audit log primary key
   * @return active conciliation row
   */
  @Transactional(readOnly = true)
  public Optional<AuditEntryIntegrityConciliation> findActiveByAuditLogId(Long auditLogId) {
    return conciliationRepository.findByAuditLogIdAndStatus(
        auditLogId, AuditEntryIntegrityConciliationStatus.ACTIVE);
  }

  /**
   * Batch lookup of ACTIVE conciliations keyed by audit log id.
   *
   * @param auditLogIds audit log ids to resolve
   * @return map of audit log id to active conciliation
   */
  @Transactional(readOnly = true)
  public Map<Long, AuditEntryIntegrityConciliation> findActiveByAuditLogIds(
      Collection<Long> auditLogIds) {
    if (auditLogIds == null || auditLogIds.isEmpty()) {
      return Map.of();
    }
    List<AuditEntryIntegrityConciliation> rows =
        conciliationRepository.findByAuditLogIdInAndStatus(
            auditLogIds, AuditEntryIntegrityConciliationStatus.ACTIVE);
    Map<Long, AuditEntryIntegrityConciliation> map = new HashMap<>();
    for (AuditEntryIntegrityConciliation row : rows) {
      map.put(row.getAuditLogId(), row);
    }
    return map;
  }

  /**
   * True when an ACTIVE conciliation exists and its stored fingerprint matches the live entry
   * state.
   *
   * @param entry audit log row
   * @param activeConciliation optional pre-loaded ACTIVE conciliation
   * @return true when the entry is operationally explained (acknowledged invalid state)
   */
  public boolean fingerprintMatchesActiveConciliation(
      AuditLog entry, AuditEntryIntegrityConciliation activeConciliation) {
    if (activeConciliation == null) {
      return false;
    }
    String liveFingerprint = computeObservedStateFingerprint(entry);
    return liveFingerprint.equals(activeConciliation.getObservedStateFingerprint());
  }

  /**
   * Creates a new ACTIVE conciliation, superseding any prior ACTIVE row for the same audit log id.
   *
   * @param entry audit log row being conciliated
   * @param violationReason HMAC failure reason at conciliation time
   * @param category operator classification
   * @param justification operator narrative
   * @param externalTicketReference optional ITSM reference
   * @param sourceAlertId alert being resolved
   * @param conciliatedByAdminId reconciling Global Admin
   * @return persisted conciliation row
   */
  @Transactional
  public AuditEntryIntegrityConciliation createConciliation(
      AuditLog entry,
      String violationReason,
      IntegrityRuptureConciliationCategory category,
      String justification,
      String externalTicketReference,
      Long sourceAlertId,
      Integer conciliatedByAdminId) {
    conciliationRepository
        .findByAuditLogIdAndStatus(
            entry.getAuditLogId(), AuditEntryIntegrityConciliationStatus.ACTIVE)
        .ifPresent(
            prior -> {
              prior.setStatus(AuditEntryIntegrityConciliationStatus.SUPERSEDED);
              conciliationRepository.save(prior);
            });

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String fingerprint = computeObservedStateFingerprint(entry);

    AuditEntryIntegrityConciliation row = new AuditEntryIntegrityConciliation();
    row.setAuditLogId(entry.getAuditLogId());
    row.setAuditLogCreatedAt(entry.getCreatedAt());
    row.setViolationReason(violationReason);
    row.setObservedStateFingerprint(fingerprint);
    row.setCategory(category);
    row.setJustification(justification);
    row.setExternalTicketReference(externalTicketReference);
    row.setSourceAlertId(sourceAlertId);
    row.setConciliatedByAdminId(conciliatedByAdminId);
    row.setConciliatedAt(now);
    row.setStatus(AuditEntryIntegrityConciliationStatus.ACTIVE);
    AuditEntryIntegrityConciliation saved = conciliationRepository.save(row);

    emitConciliationAudit(saved, conciliatedByAdminId);
    return saved;
  }

  private void emitConciliationAudit(
      AuditEntryIntegrityConciliation conciliation, Integer adminId) {
    String detailsJson =
        AuditDetailsBuilder.builder()
            .custom("conciliation_id", conciliation.getConciliationId())
            .custom("audit_log_id", conciliation.getAuditLogId())
            .custom("audit_log_created_at", conciliation.getAuditLogCreatedAt().toString())
            .custom("violation_reason", conciliation.getViolationReason())
            .custom("observed_state_fingerprint", conciliation.getObservedStateFingerprint())
            .custom("category", conciliation.getCategory().name())
            .custom("source_alert_id", conciliation.getSourceAlertId())
            .custom("conciliated_at", conciliation.getConciliatedAt().toString())
            .toJson();

    AuditLog metaEntry =
        AuditLog.builder()
            .eventType(EventType.AUDIT_ENTRY_INTEGRITY_CONCILIATED)
            .eventAction("audit-entry-integrity-conciliation")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .adminId(adminId)
            .eventDetails(detailsJson)
            .reason(conciliation.getJustification())
            .build();
    auditLogService.log(metaEntry);
  }

  static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(64);
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
