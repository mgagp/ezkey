/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: EntryHmacViolationCollector
 * Description: Collects structured entry HMAC violations for a date range.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.EntryIntegrityConciliationStatus;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Shared entry HMAC violation collection for {@link AuditIntegrityService} and nightly validation.
 *
 * @since 2026
 */
final class EntryHmacViolationCollector {

  static final String REASON_MISSING_ENTRY_HMAC = "MISSING_ENTRY_HMAC";
  static final String REASON_HMAC_MISMATCH = "HMAC_MISMATCH";

  static final int VERIFICATION_BATCH_SIZE = 500;

  private EntryHmacViolationCollector() {}

  static List<EntryIntegrityViolation> collectAll(
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService,
      OffsetDateTime from,
      OffsetDateTime to) {
    List<EntryIntegrityViolation> violations = new ArrayList<>();
    int page = 0;
    boolean hasMore = true;
    while (hasMore) {
      Specification<AuditLog> spec = buildRangeSpec(from, to);
      PageRequest pageRequest =
          PageRequest.of(page, VERIFICATION_BATCH_SIZE, Sort.by("auditLogId").ascending());
      Page<AuditLog> batch = auditLogRepository.findAll(spec, pageRequest);
      for (AuditLog entry : batch.getContent()) {
        EntryIntegrityViolation violation = classify(entry, auditHmacService);
        if (violation != null) {
          violations.add(violation);
        }
      }
      hasMore = batch.hasNext();
      page++;
    }
    return violations;
  }

  private static EntryIntegrityViolation classify(
      AuditLog entry, AuditHmacService auditHmacService) {
    if (entry.getEntryHmac() == null) {
      return new EntryIntegrityViolation(
          entry.getAuditLogId(),
          entry.getEventType() != null ? entry.getEventType().name() : null,
          entry.getCreatedAt(),
          REASON_MISSING_ENTRY_HMAC,
          EntryIntegrityConciliationStatus.NONE,
          null);
    }
    if (!auditHmacService.verifyHmac(entry)) {
      return new EntryIntegrityViolation(
          entry.getAuditLogId(),
          entry.getEventType() != null ? entry.getEventType().name() : null,
          entry.getCreatedAt(),
          REASON_HMAC_MISMATCH,
          EntryIntegrityConciliationStatus.NONE,
          null);
    }
    return null;
  }

  private static Specification<AuditLog> buildRangeSpec(OffsetDateTime from, OffsetDateTime to) {
    return (root, query, cb) ->
        cb.and(
            cb.greaterThanOrEqualTo(root.get("createdAt"), from),
            cb.lessThan(root.get("createdAt"), to));
  }
}
