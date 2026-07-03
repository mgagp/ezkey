/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: EntryIntegrityViolationClassifier
 * Description: Splits reporting vs alert-eligible per-entry HMAC violations.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Classifies per-entry HMAC integrity findings for reporting and alert aggregation.
 *
 * @since 2026
 */
@Component
public class EntryIntegrityViolationClassifier {

  private final AuditHmacService auditHmacService;
  private final AuditEntryIntegrityConciliationService conciliationService;

  /**
   * Constructs the classifier.
   *
   * @param auditHmacService per-entry HMAC verification
   * @param conciliationService conciliation lookup and fingerprint helpers
   */
  public EntryIntegrityViolationClassifier(
      AuditHmacService auditHmacService,
      AuditEntryIntegrityConciliationService conciliationService) {
    this.auditHmacService = auditHmacService;
    this.conciliationService = conciliationService;
  }

  /**
   * Returns a violation detail when HMAC verification fails, otherwise empty.
   *
   * @param entry audit log row
   * @return violation when HMAC is not intact
   */
  public Optional<EntryIntegrityViolation> classifyViolation(AuditLog entry) {
    if (entry.getEntryHmac() == null) {
      return Optional.of(
          new EntryIntegrityViolation(
              entry.getAuditLogId(),
              entry.getEventType() != null ? entry.getEventType().name() : null,
              entry.getCreatedAt(),
              EntryHmacViolationCollector.REASON_MISSING_ENTRY_HMAC));
    }
    if (!auditHmacService.verifyHmac(entry)) {
      return Optional.of(
          new EntryIntegrityViolation(
              entry.getAuditLogId(),
              entry.getEventType() != null ? entry.getEventType().name() : null,
              entry.getCreatedAt(),
              EntryHmacViolationCollector.REASON_HMAC_MISMATCH));
    }
    return Optional.empty();
  }

  /**
   * True when the entry should contribute to alert aggregation and incident fingerprinting.
   *
   * <p>Alert-eligible when HMAC is not intact and the entry is not explained (ACTIVE conciliation
   * with matching fingerprint).
   *
   * @param entry audit log row
   * @return true when the violation should raise or keep open an integrity rupture alert
   */
  public boolean isAlertEligible(AuditLog entry) {
    Optional<EntryIntegrityViolation> violation = classifyViolation(entry);
    if (violation.isEmpty()) {
      return false;
    }
    return !isExplained(entry);
  }

  /**
   * True when HMAC is not intact but an ACTIVE conciliation fingerprint matches the live entry.
   *
   * @param entry audit log row
   * @return true when the violation is operationally acknowledged
   */
  public boolean isExplained(AuditLog entry) {
    Optional<EntryIntegrityViolation> violation = classifyViolation(entry);
    if (violation.isEmpty()) {
      return false;
    }
    return conciliationService
        .findActiveByAuditLogId(entry.getAuditLogId())
        .map(active -> conciliationService.fingerprintMatchesActiveConciliation(entry, active))
        .orElse(false);
  }

  /**
   * Classifies a violation with alert eligibility for orchestration paths.
   *
   * @param entry audit log row
   * @return classification when HMAC is not intact
   */
  public Optional<EntryClassification> classify(AuditLog entry) {
    return classifyViolation(entry)
        .map(
            violation ->
                new EntryClassification(violation, isAlertEligible(entry), isExplained(entry)));
  }

  /**
   * Per-entry classification for reporting and alert aggregation.
   *
   * @param violation structured violation detail
   * @param alertEligible true when the violation should contribute to alert raise/touch
   * @param explained true when an ACTIVE conciliation fingerprint matches the live entry
   */
  public record EntryClassification(
      EntryIntegrityViolation violation, boolean alertEligible, boolean explained) {}

  /**
   * Scans a date range and splits HMAC violations into reporting vs alert-eligible lists.
   *
   * @param auditLogRepository audit log access
   * @param from inclusive window start
   * @param to exclusive window end
   * @return reporting violations (all HMAC KO) and alert-eligible subset
   */
  public ViolationCollection collectRangeViolations(
      AuditLogRepository auditLogRepository, OffsetDateTime from, OffsetDateTime to) {
    List<EntryIntegrityViolation> reporting = new ArrayList<>();
    List<EntryIntegrityViolation> alertEligible = new ArrayList<>();
    int page = 0;
    boolean hasMore = true;
    while (hasMore) {
      Specification<AuditLog> spec = buildRangeSpec(from, to);
      PageRequest pageRequest =
          PageRequest.of(
              page,
              EntryHmacViolationCollector.VERIFICATION_BATCH_SIZE,
              Sort.by("auditLogId").ascending());
      Page<AuditLog> batch = auditLogRepository.findAll(spec, pageRequest);
      for (AuditLog entry : batch.getContent()) {
        Optional<EntryClassification> classification = classify(entry);
        if (classification.isPresent()) {
          reporting.add(classification.get().violation());
          if (classification.get().alertEligible()) {
            alertEligible.add(classification.get().violation());
          }
        }
      }
      hasMore = batch.hasNext();
      page++;
    }
    return new ViolationCollection(reporting, alertEligible);
  }

  private static Specification<AuditLog> buildRangeSpec(OffsetDateTime from, OffsetDateTime to) {
    return (root, query, cb) ->
        cb.and(
            cb.greaterThanOrEqualTo(root.get("createdAt"), from),
            cb.lessThan(root.get("createdAt"), to));
  }

  /**
   * Reporting vs alert-eligible entry violation lists for a scanned range.
   *
   * @param reportingViolations all HMAC KO rows in range
   * @param alertEligibleViolations subset that should raise or touch integrity rupture alerts
   */
  public record ViolationCollection(
      List<EntryIntegrityViolation> reportingViolations,
      List<EntryIntegrityViolation> alertEligibleViolations) {}
}
