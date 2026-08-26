/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditIntegrityService
 * Description: Verification service for audit log entry integrity.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import org.ezkey.audit.dto.IntegrityViolationCappedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification service for audit log entry HMAC integrity.
 *
 * <p>Provides batch verification of audit log entries by recomputing HMAC-SHA256 signatures and
 * comparing them to the stored values. This is the operational verification layer for
 * tamper-evident monitoring.
 *
 * <p><b>Verification modes:</b>
 *
 * <ul>
 *   <li>Date range: verify all entries within a time window
 *   <li>Single entry: verify a specific audit log entry
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AuditIntegrityService {

  private static final Logger logger = LoggerFactory.getLogger(AuditIntegrityService.class);

  private static final int VERIFICATION_BATCH_SIZE = 500;
  private static final int DIAGNOSTIC_FAILURE_LIMIT = 5;

  private final AuditLogRepository auditLogRepository;
  private final AuditHmacService auditHmacService;
  private final EntryIntegrityViolationClassifier entryIntegrityViolationClassifier;

  public AuditIntegrityService(
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService,
      EntryIntegrityViolationClassifier entryIntegrityViolationClassifier) {
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
    this.entryIntegrityViolationClassifier = entryIntegrityViolationClassifier;
  }

  /**
   * Verifies HMAC integrity of all audit log entries within a date range.
   *
   * <p>Iterates through entries in batches, recomputing each entry's HMAC and comparing it to the
   * stored value. Returns a summary report.
   *
   * @param from start of the verification window (inclusive)
   * @param to end of the verification window (exclusive)
   * @return integrity verification report
   */
  @Transactional(readOnly = true)
  public IntegrityReport verifyRange(OffsetDateTime from, OffsetDateTime to) {
    if (!auditHmacService.isActive()) {
      return inactiveReport();
    }

    long totalEntries = 0;
    long validEntries = 0;
    long invalidEntries = 0;
    long unsignedEntries = 0;
    List<EntryIntegrityViolation> violations = new ArrayList<>();

    int page = 0;
    boolean hasMore = true;
    int diagnosticFailuresLogged = 0;

    while (hasMore) {
      Specification<AuditLog> spec = buildRangeSpec(from, to);
      PageRequest pageRequest =
          PageRequest.of(page, VERIFICATION_BATCH_SIZE, Sort.by("auditLogId").ascending());
      Page<AuditLog> batch = auditLogRepository.findAll(spec, pageRequest);

      List<AuditLog> entries = batch.getContent();
      for (AuditLog entry : entries) {
        totalEntries++;
        Optional<EntryIntegrityViolation> violation =
            entryIntegrityViolationClassifier.classifyViolation(entry);
        if (violation.isEmpty()) {
          validEntries++;
          continue;
        }
        EntryIntegrityViolation detail = violation.get();
        violations.add(detail);
        if (EntryHmacViolationCollector.REASON_MISSING_ENTRY_HMAC.equals(detail.reason())) {
          unsignedEntries++;
        } else {
          invalidEntries++;
          logger.warn(
              "HMAC verification FAILED for audit_log_id={}, event_type={}, created_at={}",
              entry.getAuditLogId(),
              entry.getEventType(),
              entry.getCreatedAt());
          if (diagnosticFailuresLogged < DIAGNOSTIC_FAILURE_LIMIT) {
            logDiagnostics(entry);
            diagnosticFailuresLogged++;
          }
        }
      }

      hasMore = batch.hasNext();
      page++;
    }

    String status = invalidEntries > 0 ? "INTEGRITY_VIOLATION_DETECTED" : "OK";
    logger.info(
        "Audit integrity check completed: total={}, valid={}, invalid={}, unsigned={}, status={}",
        totalEntries,
        validEntries,
        invalidEntries,
        unsignedEntries,
        status);

    return new IntegrityReport(
        totalEntries,
        validEntries,
        invalidEntries,
        unsignedEntries,
        invalidEntries == 0,
        status,
        IntegrityViolationCappedList.uncapped(violations));
  }

  /**
   * Verifies the HMAC integrity of a single audit log entry by its ID.
   *
   * <p>Recomputes the HMAC-SHA256 signature for the entry and compares it to the stored value. This
   * is useful for targeted forensic analysis and developer experimentation.
   *
   * @param id the audit log entry ID to verify
   * @return integrity verification report for the single entry (totalEntries=1)
   */
  @Transactional(readOnly = true)
  public IntegrityReport verifySingle(Long id) {
    if (!auditHmacService.isActive()) {
      return inactiveReport();
    }

    return auditLogRepository
        .findById(id)
        .map(
            entry -> {
              Optional<EntryIntegrityViolation> violation =
                  entryIntegrityViolationClassifier.classifyViolation(entry);
              if (violation.isEmpty()) {
                return new IntegrityReport(
                    1, 1, 0, 0, true, "OK", IntegrityViolationCappedList.uncapped(List.of()));
              }
              EntryIntegrityViolation detail = violation.get();
              boolean unsigned =
                  EntryHmacViolationCollector.REASON_MISSING_ENTRY_HMAC.equals(detail.reason());
              if (!unsigned) {
                logger.warn(
                    "HMAC verification FAILED for audit_log_id={}, event_type={}, created_at={}",
                    entry.getAuditLogId(),
                    entry.getEventType(),
                    entry.getCreatedAt());
                logDiagnostics(entry);
              } else {
                logger.info("Audit entry id={} has no HMAC signature (unsigned)", id);
              }
              String status = unsigned ? "UNSIGNED" : "INTEGRITY_VIOLATION_DETECTED";
              long invalid = unsigned ? 0L : 1L;
              long unsignedCount = unsigned ? 1L : 0L;
              boolean intact = unsigned;
              return new IntegrityReport(
                  1,
                  0,
                  invalid,
                  unsignedCount,
                  intact,
                  status,
                  IntegrityViolationCappedList.uncapped(List.of(detail)));
            })
        .orElse(
            new IntegrityReport(
                0, 0, 0, 0, true, "NOT_FOUND", IntegrityViolationCappedList.uncapped(List.of())));
  }

  private static IntegrityReport inactiveReport() {
    return new IntegrityReport(
        0,
        0,
        0,
        0,
        false,
        "HMAC signing is not active",
        IntegrityViolationCappedList.uncapped(List.of()));
  }

  private void logDiagnostics(AuditLog entry) {
    String canonical = auditHmacService.buildCanonicalForm(entry);
    String computedHmac = auditHmacService.computeHmac(entry);
    String storedHmac = entry.getEntryHmac();
    String createdAtFull =
        entry.getCreatedAt() != null
            ? entry.getCreatedAt().toString() + " (nano=" + (entry.getCreatedAt().getNano()) + ")"
            : "null";
    int len = canonical.length();
    String first = len > 80 ? canonical.substring(0, 80) + "..." : canonical;
    String last = len > 80 ? "..." + canonical.substring(len - 80) : "";
    logger.warn(
        "[HMAC DIAG] audit_log_id={} | canonical_len={} | created_at_full={} | "
            + "stored_hmac={} | computed_hmac={} | match={}",
        entry.getAuditLogId(),
        len,
        createdAtFull,
        storedHmac,
        computedHmac,
        Boolean.valueOf(storedHmac != null && storedHmac.equals(computedHmac)));
    logger.warn("[HMAC DIAG] canonical_first80={}", first);
    if (!last.isEmpty()) {
      logger.warn("[HMAC DIAG] canonical_last80={}", last);
    }
  }

  private Specification<AuditLog> buildRangeSpec(OffsetDateTime from, OffsetDateTime to) {
    return (root, _, cb) ->
        cb.and(
            cb.greaterThanOrEqualTo(root.get("createdAt"), from),
            cb.lessThan(root.get("createdAt"), to));
  }

  /**
   * Immutable report of an integrity verification run.
   *
   * @param totalEntries total number of entries checked
   * @param validEntries entries with valid HMAC
   * @param invalidEntries entries with HMAC mismatch (potential tampering)
   * @param unsignedEntries entries without HMAC (created before signing was enabled)
   * @param intact true if no HMAC mismatches were found
   * @param status human-readable status string
   * @param entryViolations structured violations (API uncapped)
   */
  public record IntegrityReport(
      long totalEntries,
      long validEntries,
      long invalidEntries,
      long unsignedEntries,
      boolean intact,
      String status,
      IntegrityViolationCappedList<EntryIntegrityViolation> entryViolations) {}
}
