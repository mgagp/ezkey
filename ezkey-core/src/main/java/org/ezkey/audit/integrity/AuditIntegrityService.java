/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditIntegrityService
 * Description: Verification service for audit log entry integrity.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
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
 * comparing them to the stored values. This is the operational verification layer for SOC 2 tamper
 * detection (CC7.2).
 *
 * <p><b>Verification modes:</b>
 *
 * <ul>
 *   <li>Date range: verify all entries within a time window
 *   <li>Single entry: verify a specific audit log entry
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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

  public AuditIntegrityService(
      AuditLogRepository auditLogRepository, AuditHmacService auditHmacService) {
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
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
      return new IntegrityReport(0, 0, 0, 0, false, "HMAC signing is not active");
    }

    long totalEntries = 0;
    long validEntries = 0;
    long invalidEntries = 0;
    long unsignedEntries = 0;

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
        if (entry.getEntryHmac() == null) {
          unsignedEntries++;
        } else if (auditHmacService.verifyHmac(entry)) {
          validEntries++;
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
        totalEntries, validEntries, invalidEntries, unsignedEntries, invalidEntries == 0, status);
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
      return new IntegrityReport(0, 0, 0, 0, false, "HMAC signing is not active");
    }

    return auditLogRepository
        .findById(id)
        .map(
            entry -> {
              if (entry.getEntryHmac() == null) {
                logger.info("Audit entry id={} has no HMAC signature (unsigned)", id);
                return new IntegrityReport(1, 0, 0, 1, true, "UNSIGNED");
              }
              boolean valid = auditHmacService.verifyHmac(entry);
              if (!valid) {
                logger.warn(
                    "HMAC verification FAILED for audit_log_id={}, event_type={}, created_at={}",
                    entry.getAuditLogId(),
                    entry.getEventType(),
                    entry.getCreatedAt());
                logDiagnostics(entry);
              }
              String status = valid ? "OK" : "INTEGRITY_VIOLATION_DETECTED";
              long invalid = valid ? 0L : 1L;
              long validCount = valid ? 1L : 0L;
              return new IntegrityReport(1, validCount, invalid, 0, valid, status);
            })
        .orElse(new IntegrityReport(0, 0, 0, 0, true, "NOT_FOUND"));
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
    return (root, query, cb) -> {
      if (from != null && to != null) {
        return cb.and(
            cb.greaterThanOrEqualTo(root.get("createdAt"), from),
            cb.lessThan(root.get("createdAt"), to));
      } else if (from != null) {
        return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
      } else if (to != null) {
        return cb.lessThan(root.get("createdAt"), to);
      }
      return cb.conjunction();
    };
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
   */
  public record IntegrityReport(
      long totalEntries,
      long validEntries,
      long invalidEntries,
      long unsignedEntries,
      boolean intact,
      String status) {}
}
