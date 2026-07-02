/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditChainVerificationService
 * Description: Verification service for audit chain checkpoint integrity.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.ChainIntegrityViolation;
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
 *   <li>Undeclared temporal gaps (missing checkpoints between consecutive windows)
 * </ul>
 *
 * <p><b>Lifecycle-aware verification:</b> Checkpoints of type {@code ARCHIVE_SEAL}, {@code
 * GAP_DECLARATION}, and {@code MANIPULATION_CONCILIATION} are handled specially:
 *
 * <ul>
 *   <li>{@code ARCHIVE_SEAL}: entries_digest re-computation is skipped (entries no longer in DB by
 *       design). Chain HMAC linkage is still fully verified.
 *   <li>{@code GAP_DECLARATION}: entries_digest re-computation is skipped (no entries expected).
 *       Chain HMAC linkage is still fully verified.
 *   <li>{@code MANIPULATION_CONCILIATION}: entries_digest re-computation is skipped (integrity
 *       rupture bridge). Chain HMAC linkage is still fully verified.
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
public class AuditChainVerificationService {

  private static final Logger logger = LoggerFactory.getLogger(AuditChainVerificationService.class);

  private static final String EMPTY_WINDOW_MARKER = "EMPTY_WINDOW";
  private static final String GENESIS_MARKER = "GENESIS";
  private static final String FIELD_SEPARATOR = "|";

  static final String VIOLATION_ENTRIES_DIGEST_MISMATCH = "ENTRIES_DIGEST_MISMATCH";
  static final String VIOLATION_CHAIN_LINK_BROKEN = "CHAIN_LINK_BROKEN";
  static final String VIOLATION_CHAIN_HMAC_MISMATCH = "CHAIN_HMAC_MISMATCH";

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
          0,
          0,
          0,
          0,
          0,
          List.of(),
          List.of(),
          List.of(),
          null,
          null,
          null,
          null,
          true,
          false,
          "HMAC signing is not active");
    }

    List<AuditChainCheckpoint> checkpoints = checkpointRepository.findByWindowRange(from, to);

    if (checkpoints.isEmpty()) {
      return new ChainVerificationReport(
          0,
          0,
          0,
          0,
          0,
          List.of(),
          List.of(),
          List.of(),
          null,
          null,
          null,
          null,
          true,
          true,
          "No checkpoints found in range");
    }

    int validCheckpoints = 0;
    int invalidCheckpoints = 0;
    int archivedCheckpoints = 0;
    int gapDeclaredCheckpoints = 0;
    List<String> violations = new ArrayList<>();
    List<ChainIntegrityViolation> chainViolations = new ArrayList<>();
    List<UndeclaredGap> undeclaredGaps = new ArrayList<>();

    for (int i = 0; i < checkpoints.size(); i++) {
      AuditChainCheckpoint checkpoint = checkpoints.get(i);
      boolean valid = true;
      String type = checkpoint.getCheckpointType();
      boolean isArchiveSeal = "ARCHIVE_SEAL".equals(type);
      boolean isGapDeclaration = "GAP_DECLARATION".equals(type);
      boolean isManipulationConciliation = "MANIPULATION_CONCILIATION".equals(type);

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
      } else if (isManipulationConciliation) {
        gapDeclaredCheckpoints++;
        logger.debug(
            "Skipping entries_digest re-computation for MANIPULATION_CONCILIATION checkpoint at"
                + " window {} (integrity rupture conciliation: {})",
            checkpoint.getWindowStart(),
            checkpoint.getNotes());
      } else {
        // 1. Recompute entries_digest from actual audit log entries (REGULAR checkpoints only)
        String recomputedDigest =
            computeEntriesDigest(checkpoint.getWindowStart(), checkpoint.getWindowEnd());

        if (!checkpoint.getEntriesDigest().equals(recomputedDigest)) {
          valid = false;
          String detail =
              "Entries digest mismatch at window "
                  + checkpoint.getWindowStart()
                  + " (possible entry modification, insertion, or deletion)";
          addChainViolation(
              violations, chainViolations, checkpoint, VIOLATION_ENTRIES_DIGEST_MISMATCH, detail);
        }
      }

      // 2. Verify chain linkage (all checkpoint types)
      if (i > 0) {
        AuditChainCheckpoint prev = checkpoints.get(i - 1);
        String expectedPrevHmac = prev.getChainHmac();
        if (!java.util.Objects.equals(checkpoint.getPrevChainHmac(), expectedPrevHmac)) {
          valid = false;
          String suffix = isArchiveSeal ? " [ARCHIVE_SEAL]" : isGapDeclaration ? " [GAP]" : "";
          String detail =
              "Chain link broken at window "
                  + checkpoint.getWindowStart()
                  + " (prev_chain_hmac does not match previous checkpoint's chain_hmac)"
                  + suffix;
          addChainViolation(
              violations, chainViolations, checkpoint, VIOLATION_CHAIN_LINK_BROKEN, detail);
        }
        // 2b. Temporal continuity: detect undeclared gap between consecutive checkpoints
        OffsetDateTime prevEnd = prev.getWindowEnd();
        OffsetDateTime currStart = checkpoint.getWindowStart();
        if (prevEnd.isBefore(currStart)) {
          long gapMinutes = ChronoUnit.MINUTES.between(prevEnd, currStart);
          undeclaredGaps.add(new UndeclaredGap(prevEnd, currStart, gapMinutes));
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
        String suffix = isArchiveSeal ? " [ARCHIVE_SEAL]" : isGapDeclaration ? " [GAP]" : "";
        String detail =
            "Chain HMAC mismatch at window "
                + checkpoint.getWindowStart()
                + " (checkpoint record may have been tampered with)"
                + suffix;
        addChainViolation(
            violations, chainViolations, checkpoint, VIOLATION_CHAIN_HMAC_MISMATCH, detail);
      }

      if (valid) {
        validCheckpoints++;
      } else {
        invalidCheckpoints++;
      }
    }

    OffsetDateTime coverageStart = checkpoints.get(0).getWindowStart();
    OffsetDateTime coverageEnd = checkpoints.get(checkpoints.size() - 1).getWindowEnd();

    // Boundary coverage: report leading/trailing gaps only when the requested range extends
    // beyond the system's checkpoint extent. When from is before the first checkpoint in the DB
    // (or to is after the last), that period is "before/after EZKey time", not a real gap.
    Optional<AuditChainCheckpoint> earliestOpt = checkpointRepository.findEarliest();
    Optional<AuditChainCheckpoint> latestOpt = checkpointRepository.findLatest();
    boolean firstInRangeIsEarliest =
        earliestOpt.isPresent()
            && checkpoints.get(0).getWindowStart().equals(earliestOpt.get().getWindowStart());
    boolean lastInRangeIsLatest =
        latestOpt.isPresent()
            && checkpoints
                .get(checkpoints.size() - 1)
                .getWindowStart()
                .equals(latestOpt.get().getWindowStart());

    boolean addLeadingGap = from != null && from.isBefore(coverageStart) && !firstInRangeIsEarliest;
    boolean addTrailingGap = to != null && coverageEnd.isBefore(to) && !lastInRangeIsLatest;

    if (addLeadingGap) {
      long leadingGapMinutes = ChronoUnit.MINUTES.between(from, coverageStart);
      undeclaredGaps.add(new UndeclaredGap(from, coverageStart, leadingGapMinutes));
    }
    if (addTrailingGap) {
      long trailingGapMinutes = ChronoUnit.MINUTES.between(coverageEnd, to);
      undeclaredGaps.add(new UndeclaredGap(coverageEnd, to, trailingGapMinutes));
    }

    OffsetDateTime effectiveFrom = addLeadingGap ? from : coverageStart;
    OffsetDateTime effectiveTo = addTrailingGap ? to : coverageEnd;

    boolean continuousCoverage = undeclaredGaps.isEmpty();
    boolean cryptographicallyIntact = invalidCheckpoints == 0;
    boolean intact = cryptographicallyIntact && continuousCoverage;
    String status;
    if (!cryptographicallyIntact) {
      status = "CHAIN_INTEGRITY_VIOLATION_DETECTED";
    } else if (!continuousCoverage) {
      status = "UNDECLARED_GAP_DETECTED";
    } else {
      status = "OK";
    }

    logger.info(
        "Chain verification completed: total={}, valid={}, invalid={}, archived={}, gaps={}, "
            + "undeclaredGaps={}, violations={}, status={}",
        checkpoints.size(),
        validCheckpoints,
        invalidCheckpoints,
        archivedCheckpoints,
        gapDeclaredCheckpoints,
        undeclaredGaps.size(),
        violations.size(),
        status);

    return new ChainVerificationReport(
        checkpoints.size(),
        validCheckpoints,
        invalidCheckpoints,
        archivedCheckpoints,
        gapDeclaredCheckpoints,
        violations,
        chainViolations,
        undeclaredGaps,
        coverageStart,
        coverageEnd,
        effectiveFrom,
        effectiveTo,
        continuousCoverage,
        intact,
        status);
  }

  private static void addChainViolation(
      List<String> violations,
      List<ChainIntegrityViolation> chainViolations,
      AuditChainCheckpoint checkpoint,
      String violationType,
      String detail) {
    violations.add(detail);
    chainViolations.add(
        new ChainIntegrityViolation(
            checkpoint.getCheckpointId(),
            checkpoint.getWindowStart(),
            checkpoint.getWindowEnd(),
            violationType,
            detail));
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
   * Represents an undeclared temporal gap between two consecutive checkpoints (missing windows).
   *
   * @param gapStart end of the previous checkpoint's window (exclusive)
   * @param gapEnd start of the next checkpoint's window (exclusive)
   * @param gapMinutes approximate duration of the gap in minutes
   */
  public record UndeclaredGap(OffsetDateTime gapStart, OffsetDateTime gapEnd, long gapMinutes) {}

  /**
   * Immutable report of a chain verification run.
   *
   * @param totalCheckpoints total number of checkpoints verified
   * @param validCheckpoints checkpoints with valid chain and digest
   * @param invalidCheckpoints checkpoints with integrity violations
   * @param archivedCheckpoints ARCHIVE_SEAL checkpoints (entries_digest skipped, chain verified)
   * @param gapDeclaredCheckpoints GAP_DECLARATION checkpoints (downtime gaps, chain verified)
   * @param violations list of human-readable violation descriptions
   * @param chainViolations structured checkpoint violations for operator investigation
   * @param undeclaredGaps temporal gaps between consecutive checkpoints (no checkpoint coverage)
   * @param coverageStart window start of the first checkpoint in range (null if none)
   * @param coverageEnd window end of the last checkpoint in range (null if none)
   * @param effectiveFrom start of the range used for boundary gap reporting (null if no range or
   *     early return). When the requested from was before the first checkpoint in the DB, this is
   *     clamped to coverageStart; otherwise equals the requested from.
   * @param effectiveTo end of the range used for boundary gap reporting (null if no range or early
   *     return). When the requested to was after the last checkpoint in the DB, this is clamped to
   *     coverageEnd; otherwise equals the requested to.
   * @param continuousCoverage true if no undeclared gaps exist between checkpoints
   * @param intact true if chain is cryptographically valid and no undeclared gaps
   * @param status human-readable status string (OK, UNDECLARED_GAP_DETECTED,
   *     CHAIN_INTEGRITY_VIOLATION_DETECTED)
   */
  public record ChainVerificationReport(
      int totalCheckpoints,
      int validCheckpoints,
      int invalidCheckpoints,
      int archivedCheckpoints,
      int gapDeclaredCheckpoints,
      List<String> violations,
      List<ChainIntegrityViolation> chainViolations,
      List<UndeclaredGap> undeclaredGaps,
      OffsetDateTime coverageStart,
      OffsetDateTime coverageEnd,
      OffsetDateTime effectiveFrom,
      OffsetDateTime effectiveTo,
      boolean continuousCoverage,
      boolean intact,
      String status) {}
}
