/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuditLifecycleService
 * Description: Service for audit chain lifecycle operations: archive sealing and gap declaration.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.ArchiveSealRequest;
import org.ezkey.audit.dto.ArchiveSealResult;
import org.ezkey.audit.dto.GapDeclarationRequest;
import org.ezkey.audit.dto.GapDeclarationResult;
import org.ezkey.audit.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for audit chain lifecycle operations.
 *
 * <p>Provides two admin-triggered operations that maintain chain continuity across operational
 * events that would otherwise break verification:
 *
 * <ul>
 *   <li><b>Archive Seal</b>: marks chain checkpoints as {@code ARCHIVE_SEAL} before a partition is
 *       dropped. The chain {@code chain_hmac} linkage remains verifiable; the {@code
 *       entries_digest} comparison is skipped for sealed periods (entries no longer in DB by
 *       design). Returns the seal HMAC to include in the Git archive manifest.
 *   <li><b>Gap Declaration</b>: formally documents a downtime gap (system offline longer than the
 *       scheduler lookback window) by inserting a single {@code GAP_DECLARATION} checkpoint into
 *       the chain. The gap is signed with the admin's justification.
 * </ul>
 *
 * <p>Both operations create a meta-audit entry (HMAC-signed) to record the lifecycle event in the
 * audit trail itself.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@Service
public class AuditLifecycleService {

  private static final Logger logger = LoggerFactory.getLogger(AuditLifecycleService.class);

  private static final String CHECKPOINT_TYPE_ARCHIVE_SEAL = "ARCHIVE_SEAL";
  private static final String CHECKPOINT_TYPE_GAP_DECLARATION = "GAP_DECLARATION";
  private static final String FIELD_SEPARATOR = "|";
  private static final String GENESIS_MARKER = "GENESIS";

  private final AuditChainCheckpointRepository checkpointRepository;
  private final AuditLogRepository auditLogRepository;
  private final AuditHmacService auditHmacService;
  private final AuditChainVerificationService chainVerificationService;
  private final AuditLogService auditLogService;
  private final AuditChainProperties chainProperties;

  /**
   * Constructs the lifecycle service with required dependencies.
   *
   * @param checkpointRepository chain checkpoint repository
   * @param auditLogRepository audit log entry repository
   * @param auditHmacService HMAC signing service
   * @param chainVerificationService chain integrity verification service
   * @param auditLogService audit log service for creating meta-audit entries
   * @param chainProperties chain configuration (window size for gapEnd auto-derivation)
   */
  public AuditLifecycleService(
      AuditChainCheckpointRepository checkpointRepository,
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService,
      AuditChainVerificationService chainVerificationService,
      AuditLogService auditLogService,
      AuditChainProperties chainProperties) {
    this.checkpointRepository = checkpointRepository;
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
    this.chainVerificationService = chainVerificationService;
    this.auditLogService = auditLogService;
    this.chainProperties = chainProperties;
  }

  /**
   * Seals an audit chain period prior to archiving and dropping the corresponding partition.
   *
   * <p><b>Algorithm:</b>
   *
   * <ol>
   *   <li>Pre-flight chain verification: all checkpoints in the period must be intact. Rejected if
   *       any violation is detected (you must not seal a compromised chain).
   *   <li>Mark all checkpoints in the period as {@code ARCHIVE_SEAL} and set their {@code notes}
   *       field to the provided justification.
   *   <li>Create a meta-audit entry of type {@code AUDIT_CHAIN_ARCHIVE_SEALED}.
   *   <li>Return the seal HMAC (the {@code chain_hmac} of the last checkpoint) to include in the
   *       Git archive manifest.
   * </ol>
   *
   * <p><b>Post-condition:</b> The DBA may safely {@code DROP} the audit log partition for the
   * period. Future {@code verifyChain()} calls will acknowledge sealed checkpoints and skip {@code
   * entries_digest} re-computation for those windows.
   *
   * @param request the archive seal request containing period and justification
   * @return seal result with the last chain HMAC and metadata
   * @throws IllegalStateException if HMAC signing is not active or if chain verification fails
   */
  @Transactional
  public ArchiveSealResult sealArchive(ArchiveSealRequest request) {
    if (!auditHmacService.isActive()) {
      throw new IllegalStateException(
          "HMAC signing is not active. Archive seal requires an active HMAC key.");
    }

    // Resolve the list of checkpoints and the effective period boundaries.
    // Two modes: checkpoint ID range (ergonomic) or timestamp range (default).
    boolean idMode = request.checkpointIdFrom() != null || request.checkpointIdTo() != null;
    boolean tsMode = request.periodStart() != null || request.periodEnd() != null;

    if (idMode && tsMode) {
      throw new IllegalArgumentException(
          "Provide either (checkpointIdFrom + checkpointIdTo) or (periodStart + periodEnd), "
              + "not both.");
    }

    List<AuditChainCheckpoint> checkpoints;
    OffsetDateTime periodStart;
    OffsetDateTime periodEnd;

    if (idMode) {
      if (request.checkpointIdFrom() == null || request.checkpointIdTo() == null) {
        throw new IllegalArgumentException(
            "Both checkpointIdFrom and checkpointIdTo must be provided when using ID mode.");
      }
      if (request.checkpointIdTo() < request.checkpointIdFrom()) {
        throw new IllegalArgumentException("checkpointIdTo must be >= checkpointIdFrom.");
      }
      checkpoints =
          checkpointRepository.findByIdRange(request.checkpointIdFrom(), request.checkpointIdTo());
      if (checkpoints.isEmpty()) {
        throw new IllegalArgumentException(
            "No checkpoints found with IDs in ["
                + request.checkpointIdFrom()
                + ", "
                + request.checkpointIdTo()
                + "].");
      }
      // Derive effective period from the fetched checkpoints
      periodStart = checkpoints.get(0).getWindowStart();
      periodEnd = checkpoints.get(checkpoints.size() - 1).getWindowEnd();
    } else {
      if (request.periodStart() == null || request.periodEnd() == null) {
        throw new IllegalArgumentException(
            "Either (checkpointIdFrom + checkpointIdTo) or (periodStart + periodEnd) must be "
                + "provided.");
      }
      if (!request.periodEnd().isAfter(request.periodStart())) {
        throw new IllegalArgumentException("periodEnd must be strictly after periodStart.");
      }
      periodStart = request.periodStart();
      periodEnd = request.periodEnd();
      checkpoints = checkpointRepository.findByWindowRange(periodStart, periodEnd);
    }

    // Pre-flight: verify chain integrity for the derived period before sealing
    AuditChainVerificationService.ChainVerificationReport verificationReport =
        chainVerificationService.verifyChain(periodStart, periodEnd);

    if (!verificationReport.intact()) {
      throw new IllegalStateException(
          "Chain integrity verification failed for the specified period. "
              + "Seal rejected: "
              + verificationReport.invalidCheckpoints()
              + " checkpoint(s) show violations. "
              + "Violations: "
              + verificationReport.violations()
              + ". "
              + "Resolve integrity issues before sealing.");
    }

    String sealChainHmac = null;
    for (AuditChainCheckpoint checkpoint : checkpoints) {
      checkpoint.setCheckpointType(CHECKPOINT_TYPE_ARCHIVE_SEAL);
      checkpoint.setNotes(request.justification());
      checkpointRepository.save(checkpoint);
      sealChainHmac = checkpoint.getChainHmac();
    }

    logger.info(
        "Sealed {} checkpoint(s) in period [{} to {}) as ARCHIVE_SEAL. Seal HMAC: {}",
        checkpoints.size(),
        periodStart,
        periodEnd,
        sealChainHmac != null ? sealChainHmac.substring(0, 12) + "..." : "none");

    // Create meta-audit entry
    String eventDetails =
        "{"
            + "\"periodStart\":\""
            + periodStart
            + "\","
            + "\"periodEnd\":\""
            + periodEnd
            + "\","
            + "\"checkpointsSealed\":"
            + checkpoints.size()
            + ","
            + "\"sealChainHmac\":\""
            + (sealChainHmac != null ? sealChainHmac : "")
            + "\","
            + "\"justification\":\""
            + escapeJson(request.justification())
            + "\""
            + "}";

    AuditLog metaEntry =
        AuditLog.builder()
            .eventType(EventType.AUDIT_CHAIN_ARCHIVE_SEALED)
            .eventAction("audit-chain-lifecycle")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .eventDetails(eventDetails)
            .build();
    auditLogService.log(metaEntry);

    return new ArchiveSealResult(
        periodStart,
        periodEnd,
        checkpoints.size(),
        sealChainHmac,
        metaEntry.getAuditLogId(),
        request.justification());
  }

  /**
   * Formally declares a downtime gap in the audit chain.
   *
   * <p>Creates a single {@code GAP_DECLARATION} checkpoint spanning the full gap period and signs
   * it into the chain. The next regular checkpoint created by the scheduler will then link its
   * {@code prev_chain_hmac} to the gap declaration's {@code chain_hmac}, restoring continuity.
   *
   * <p><b>Validation:</b>
   *
   * <ul>
   *   <li>No audit entries may exist in the gap period (it must be a true gap).
   *   <li>No checkpoints may already exist in the gap period (the declaration must precede
   *       scheduler catchup for the gap windows).
   * </ul>
   *
   * <p><b>Operational constraint:</b> Must be called before the scheduler creates regular
   * checkpoints covering the gap period. For the default 60-minute lookback, call this within 5
   * minutes of system restart after extended downtime.
   *
   * @param request the gap declaration request containing gap period and justification
   * @return gap declaration result with the new checkpoint ID and chain HMAC
   * @throws IllegalStateException if HMAC signing is not active, entries exist in the gap, or
   *     conflicting checkpoints already exist
   */
  @Transactional
  public GapDeclarationResult declareGap(GapDeclarationRequest request) {
    if (!auditHmacService.isActive()) {
      throw new IllegalStateException(
          "HMAC signing is not active. Gap declaration requires an active HMAC key.");
    }

    // Resolve gapStart — two modes: explicit timestamp or anchor checkpoint ID.
    boolean anchorMode = request.anchorCheckpointId() != null;
    boolean tsMode = request.gapStart() != null;

    if (anchorMode && tsMode) {
      throw new IllegalArgumentException(
          "Provide either gapStart (timestamp) or anchorCheckpointId, not both.");
    }
    if (!anchorMode && !tsMode) {
      throw new IllegalArgumentException(
          "Either gapStart (timestamp) or anchorCheckpointId must be provided.");
    }

    OffsetDateTime gapStart;
    AuditChainCheckpoint anchor = null;
    if (anchorMode) {
      anchor =
          checkpointRepository
              .findById(request.anchorCheckpointId())
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "anchorCheckpointId "
                              + request.anchorCheckpointId()
                              + " not found. Provide the checkpoint_id of the last checkpoint "
                              + "recorded before the downtime."));
      gapStart = anchor.getWindowEnd();
      logger.info(
          "Anchor checkpoint id={} resolved: gapStart derived as {}",
          anchor.getCheckpointId(),
          gapStart);
    } else {
      gapStart = request.gapStart();
    }

    // Resolve gapEnd — optional in anchor mode, required in timestamp mode.
    OffsetDateTime gapEnd;
    if (request.gapEnd() != null) {
      gapEnd = request.gapEnd();
    } else if (anchorMode) {
      gapEnd = deriveGapEnd(anchor.getWindowEnd());
      logger.info(
          "gapEnd auto-derived as {} (first checkpoint after anchor, or current window start)",
          gapEnd);
    } else {
      throw new IllegalArgumentException(
          "gapEnd is required when using timestamp mode (gapStart provided explicitly).");
    }

    if (!gapEnd.isAfter(gapStart)) {
      throw new IllegalArgumentException(
          "gapEnd must be strictly after gapStart"
              + (anchorMode
                  ? " (gapStart was derived from anchorCheckpointId "
                      + request.anchorCheckpointId()
                      + ".window_end = "
                      + gapStart
                      + (request.gapEnd() == null ? "; gapEnd was auto-derived as " + gapEnd : "")
                      + ")"
                  : "")
              + ".");
    }

    // Validate: no audit entries exist in the gap period
    long entryCount = countEntriesInRange(gapStart, gapEnd);
    if (entryCount > 0) {
      throw new IllegalStateException(
          "Gap declaration rejected: "
              + entryCount
              + " audit entry/entries found in the declared gap period ["
              + gapStart
              + ", "
              + gapEnd
              + "). A gap can only be declared for periods with no audit activity.");
    }

    // Validate: no checkpoints already exist in the gap period
    long existingCheckpoints = checkpointRepository.countByWindowRange(gapStart, gapEnd);
    if (existingCheckpoints > 0) {
      throw new IllegalStateException(
          "Gap declaration rejected: "
              + existingCheckpoints
              + " checkpoint(s) already exist in the declared gap period ["
              + gapStart
              + ", "
              + gapEnd
              + "). The gap declaration must be called before the scheduler creates checkpoints "
              + "for these windows. Either the lookback window has already covered part of the "
              + "gap, or a previous declaration already exists. "
              + "Adjust gapEnd to exclude covered windows, or increase "
              + "ezkey.audit.chain.lookback-minutes for future maintenance windows.");
    }

    // Find the chain anchor: the latest checkpoint before the gap start
    String prevChainHmac =
        checkpointRepository
            .findLatestBefore(gapStart)
            .map(AuditChainCheckpoint::getChainHmac)
            .orElse(null);

    // Compute the gap entries_digest and chain_hmac
    String gapDigestInput =
        "DECLARED_GAP:"
            + gapStart.withOffsetSameInstant(ZoneOffset.UTC)
            + FIELD_SEPARATOR
            + gapEnd.withOffsetSameInstant(ZoneOffset.UTC);
    String entriesDigest = auditHmacService.computeHmac(gapDigestInput);

    String chainInput =
        entriesDigest + FIELD_SEPARATOR + (prevChainHmac != null ? prevChainHmac : GENESIS_MARKER);
    String chainHmac = auditHmacService.computeHmac(chainInput);

    // Persist the GAP_DECLARATION checkpoint
    AuditChainCheckpoint gapCheckpoint = new AuditChainCheckpoint();
    gapCheckpoint.setWindowStart(gapStart);
    gapCheckpoint.setWindowEnd(gapEnd);
    gapCheckpoint.setEntryCount(0);
    gapCheckpoint.setEntriesDigest(entriesDigest);
    gapCheckpoint.setPrevChainHmac(prevChainHmac);
    gapCheckpoint.setChainHmac(chainHmac);
    gapCheckpoint.setCheckpointType(CHECKPOINT_TYPE_GAP_DECLARATION);
    gapCheckpoint.setNotes(request.justification());
    checkpointRepository.save(gapCheckpoint);

    logger.info(
        "Created GAP_DECLARATION checkpoint id={} for period [{} to {}). Chain HMAC: {}",
        gapCheckpoint.getCheckpointId(),
        gapStart,
        gapEnd,
        chainHmac.substring(0, 12) + "...");

    // Create meta-audit entry
    String eventDetails =
        "{"
            + "\"gapStart\":\""
            + gapStart
            + "\","
            + "\"gapEnd\":\""
            + gapEnd
            + "\","
            + "\"gapCheckpointId\":"
            + gapCheckpoint.getCheckpointId()
            + ","
            + "\"gapChainHmac\":\""
            + chainHmac
            + "\","
            + "\"justification\":\""
            + escapeJson(request.justification())
            + "\""
            + "}";

    AuditLog metaEntry =
        AuditLog.builder()
            .eventType(EventType.AUDIT_CHAIN_GAP_DECLARED)
            .eventAction("audit-chain-lifecycle")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .eventDetails(eventDetails)
            .build();
    auditLogService.log(metaEntry);

    return new GapDeclarationResult(
        gapStart,
        gapEnd,
        gapCheckpoint.getCheckpointId(),
        chainHmac,
        metaEntry.getAuditLogId(),
        request.justification());
  }

  /**
   * Derives {@code gapEnd} automatically when it is not provided in anchor checkpoint mode.
   *
   * <p><b>Algorithm:</b>
   *
   * <ol>
   *   <li>Find the first checkpoint whose {@code window_start} is strictly after the anchor's
   *       {@code window_end}. This is the earliest checkpoint the scheduler created post-restart.
   *   <li>Return its {@code window_start} — this is the exact boundary between the undeclared gap
   *       and the scheduler's catch-up checkpoints, avoiding any overlap.
   *   <li>If no such checkpoint exists (scheduler has not yet run), fall back to the start of the
   *       current 5-minute window ({@code roundDown(now, windowMinutes)}).
   * </ol>
   *
   * @param anchorWindowEnd the {@code window_end} of the anchor checkpoint (= derived gapStart)
   * @return the auto-derived gapEnd
   */
  private OffsetDateTime deriveGapEnd(OffsetDateTime anchorWindowEnd) {
    return checkpointRepository
        .findFirstAfter(anchorWindowEnd)
        .map(AuditChainCheckpoint::getWindowStart)
        .orElseGet(
            () ->
                AuditChainScheduler.roundDownToWindow(
                    OffsetDateTime.now(ZoneOffset.UTC), chainProperties.getWindowMinutes()));
  }

  /** Counts audit log entries whose createdAt falls within the given range. */
  private long countEntriesInRange(OffsetDateTime from, OffsetDateTime to) {
    Specification<AuditLog> spec =
        (root, query, cb) ->
            cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThan(root.get("createdAt"), to));
    return auditLogRepository.count(spec);
  }

  /**
   * Minimal JSON escaping for event_details strings. Escapes backslashes and double-quotes only.
   * Sufficient for justification text that may contain user-supplied content.
   */
  private static String escapeJson(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
