/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditChainVerificationService
 * Description: Verification service for audit chain checkpoint integrity.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification service for audit chain checkpoint integrity.
 *
 * <p>Validates the chain of checkpoints by recomputing entries_digest for each window and verifying
 * the chain linkage between consecutive checkpoints. Detects:
 *
 * <ul>
 *   <li>Missing entries (deleted after checkpoint creation)
 *   <li>Inserted entries (added after checkpoint creation)
 *   <li>Reordered entries
 *   <li>Modified entries (entry_hmac changed)
 *   <li>Broken chain links (checkpoint tampering)
 * </ul>
 *
 * <p><b>Lifecycle-aware verification:</b> Checkpoints of type {@code ARCHIVE_SEAL} and {@code
 * GAP_DECLARATION} are handled specially:
 *
 * <ul>
 *   <li>{@code ARCHIVE_SEAL}: entries_digest re-computation is skipped (entries no longer in DB by
 *       design). Chain HMAC linkage is still fully verified.
 *   <li>{@code GAP_DECLARATION}: entries_digest re-computation is skipped (no entries expected).
 *       Chain HMAC linkage is still fully verified.
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
public class AuditChainVerificationService {

  private static final Logger logger = LoggerFactory.getLogger(AuditChainVerificationService.class);

  private static final String EMPTY_WINDOW_MARKER = "EMPTY_WINDOW";
  private static final String GENESIS_MARKER = "GENESIS";
  private static final String FIELD_SEPARATOR = "|";

  private final AuditChainCheckpointRepository checkpointRepository;
  private final AuditLogRepository auditLogRepository;
  private final AuditHmacService auditHmacService;

  public AuditChainVerificationService(
      AuditChainCheckpointRepository checkpointRepository,
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService) {
    this.checkpointRepository = checkpointRepository;
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
  }

  /**
   * Verifies the chain integrity for all checkpoints within a date range.
   *
   * <p>For each checkpoint in the range:
   *
   * <ol>
   *   <li>Recomputes entries_digest from current audit log entries in the window (skipped for
   *       {@code ARCHIVE_SEAL} and {@code GAP_DECLARATION} checkpoints — see class Javadoc)
   *   <li>Compares recomputed digest to stored digest (detects entry modification)
   *   <li>Verifies chain_hmac linkage to previous checkpoint (detects chain tampering, always
   *       executed for all checkpoint types including lifecycle ones)
   * </ol>
   *
   * @param from start of the verification range (inclusive)
   * @param to end of the verification range (exclusive)
   * @return chain verification report
   */
  @Transactional(readOnly = true)
  public ChainVerificationReport verifyChain(OffsetDateTime from, OffsetDateTime to) {
    if (!auditHmacService.isActive()) {
      return new ChainVerificationReport(
          0, 0, 0, 0, 0, List.of(), false, "HMAC signing is not active");
    }

    List<AuditChainCheckpoint> checkpoints = checkpointRepository.findByWindowRange(from, to);

    if (checkpoints.isEmpty()) {
      return new ChainVerificationReport(
          0, 0, 0, 0, 0, List.of(), true, "No checkpoints found in range");
    }

    int validCheckpoints = 0;
    int invalidCheckpoints = 0;
    int archivedCheckpoints = 0;
    int gapDeclaredCheckpoints = 0;
    List<String> violations = new ArrayList<>();

    for (int i = 0; i < checkpoints.size(); i++) {
      AuditChainCheckpoint checkpoint = checkpoints.get(i);
      boolean valid = true;
      String type = checkpoint.getCheckpointType();
      boolean isArchiveSeal = "ARCHIVE_SEAL".equals(type);
      boolean isGapDeclaration = "GAP_DECLARATION".equals(type);

      if (isArchiveSeal) {
        archivedCheckpoints++;
        logger.debug(
            "Skipping entries_digest re-computation for ARCHIVE_SEAL checkpoint at window {} "
                + "(entries archived to external storage)",
            checkpoint.getWindowStart());
      } else if (isGapDeclaration) {
        gapDeclaredCheckpoints++;
        logger.debug(
            "Skipping entries_digest re-computation for GAP_DECLARATION checkpoint at window {} "
                + "(declared downtime gap: {})",
            checkpoint.getWindowStart(),
            checkpoint.getNotes());
      } else {
        // 1. Recompute entries_digest from actual audit log entries (REGULAR checkpoints only)
        String recomputedDigest =
            computeEntriesDigest(checkpoint.getWindowStart(), checkpoint.getWindowEnd());

        if (!checkpoint.getEntriesDigest().equals(recomputedDigest)) {
          valid = false;
          violations.add(
              "Entries digest mismatch at window "
                  + checkpoint.getWindowStart()
                  + " (possible entry modification, insertion, or deletion)");
        }
      }

      // 2. Verify chain linkage (all checkpoint types)
      if (i > 0) {
        String expectedPrevHmac = checkpoints.get(i - 1).getChainHmac();
        if (!java.util.Objects.equals(checkpoint.getPrevChainHmac(), expectedPrevHmac)) {
          valid = false;
          violations.add(
              "Chain link broken at window "
                  + checkpoint.getWindowStart()
                  + " (prev_chain_hmac does not match previous checkpoint's chain_hmac)"
                  + (isArchiveSeal ? " [ARCHIVE_SEAL]" : isGapDeclaration ? " [GAP]" : ""));
        }
      }

      // 3. Verify chain_hmac computation (all checkpoint types)
      String chainInput =
          checkpoint.getEntriesDigest()
              + FIELD_SEPARATOR
              + (checkpoint.getPrevChainHmac() != null
                  ? checkpoint.getPrevChainHmac()
                  : GENESIS_MARKER);
      String recomputedChainHmac = auditHmacService.computeHmac(chainInput);
      if (!checkpoint.getChainHmac().equals(recomputedChainHmac)) {
        valid = false;
        violations.add(
            "Chain HMAC mismatch at window "
                + checkpoint.getWindowStart()
                + " (checkpoint record may have been tampered with)"
                + (isArchiveSeal ? " [ARCHIVE_SEAL]" : isGapDeclaration ? " [GAP]" : ""));
      }

      if (valid) {
        validCheckpoints++;
      } else {
        invalidCheckpoints++;
      }
    }

    boolean intact = invalidCheckpoints == 0;
    String status = intact ? "OK" : "CHAIN_INTEGRITY_VIOLATION_DETECTED";

    logger.info(
        "Chain verification completed: total={}, valid={}, invalid={}, archived={}, gaps={}, "
            + "violations={}, status={}",
        checkpoints.size(),
        validCheckpoints,
        invalidCheckpoints,
        archivedCheckpoints,
        gapDeclaredCheckpoints,
        violations.size(),
        status);

    return new ChainVerificationReport(
        checkpoints.size(),
        validCheckpoints,
        invalidCheckpoints,
        archivedCheckpoints,
        gapDeclaredCheckpoints,
        violations,
        intact,
        status);
  }

  /** Recomputes the entries_digest for a given time window from current audit log data. */
  private String computeEntriesDigest(OffsetDateTime windowStart, OffsetDateTime windowEnd) {
    Specification<org.ezkey.audit.domain.entity.AuditLog> spec =
        (root, query, cb) ->
            cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), windowStart),
                cb.lessThan(root.get("createdAt"), windowEnd));

    List<org.ezkey.audit.domain.entity.AuditLog> entries =
        auditLogRepository.findAll(spec, Sort.by("auditLogId").ascending());

    if (entries.isEmpty()) {
      return auditHmacService.computeHmac(EMPTY_WINDOW_MARKER);
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        sb.append(FIELD_SEPARATOR);
      }
      String hmac = entries.get(i).getEntryHmac();
      sb.append(hmac != null ? hmac : "");
    }
    return auditHmacService.computeHmac(sb.toString());
  }

  /**
   * Immutable report of a chain verification run.
   *
   * @param totalCheckpoints total number of checkpoints verified
   * @param validCheckpoints checkpoints with valid chain and digest
   * @param invalidCheckpoints checkpoints with integrity violations
   * @param archivedCheckpoints ARCHIVE_SEAL checkpoints (entries_digest skipped, chain verified)
   * @param gapDeclaredCheckpoints GAP_DECLARATION checkpoints (downtime gaps, chain verified)
   * @param violations list of human-readable violation descriptions
   * @param intact true if no violations were found
   * @param status human-readable status string
   */
  public record ChainVerificationReport(
      int totalCheckpoints,
      int validCheckpoints,
      int invalidCheckpoints,
      int archivedCheckpoints,
      int gapDeclaredCheckpoints,
      List<String> violations,
      boolean intact,
      String status) {}
}
