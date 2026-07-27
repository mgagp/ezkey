/*
 * Ezkey - Open Source Cryptographic MFA Platform
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.ArchiveConfirmArchivedRequest;
import org.ezkey.audit.dto.ArchiveConfirmArchivedResult;
import org.ezkey.audit.dto.ArchiveEligibilityResult;
import org.ezkey.audit.dto.ArchiveSealRequest;
import org.ezkey.audit.dto.ArchiveSealResult;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import org.ezkey.audit.dto.GapDeclarationRequest;
import org.ezkey.audit.dto.GapDeclarationResult;
import org.ezkey.audit.dto.IntegrityRuptureReconciliationRequest;
import org.ezkey.audit.dto.IntegrityRuptureReconciliationResult;
import org.ezkey.audit.exception.AuditLifecycleConflictException;
import org.ezkey.audit.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
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
  private static final String CHECKPOINT_TYPE_MANIPULATION_CONCILIATION =
      "MANIPULATION_CONCILIATION";
  private static final String CHECKPOINT_TYPE_REGULAR = "REGULAR";
  private static final String FIELD_SEPARATOR = "|";
  private static final String GENESIS_MARKER = "GENESIS";
  private static final String AUTO_SEAL_NOTES = "Auto-sealed by audit lifecycle policy.";
  private static final ObjectMapper PAYLOAD_OBJECT_MAPPER = new ObjectMapper();

  private final AuditChainCheckpointRepository checkpointRepository;
  private final AuditLogRepository auditLogRepository;
  private final AuditHmacService auditHmacService;
  private final AuditChainVerificationService chainVerificationService;
  private final AuditLogService auditLogService;
  private final AuditChainProperties chainProperties;
  private final AuditArchiveProperties archiveProperties;
  private final AlertService alertService;
  private final EntryIntegrityViolationClassifier entryIntegrityViolationClassifier;
  private final AuditEntryIntegrityConciliationService entryIntegrityConciliationService;

  /**
   * Constructs the lifecycle service with required dependencies.
   *
   * @param checkpointRepository chain checkpoint repository
   * @param auditLogRepository audit log entry repository
   * @param auditHmacService HMAC signing service
   * @param chainVerificationService chain integrity verification service
   * @param auditLogService audit log service for creating meta-audit entries
   * @param chainProperties chain configuration (window size for gapEnd auto-derivation)
   * @param archiveProperties archive lifecycle policy configuration
   * @param alertService alert subsystem entry point for auto-resolving matching gap alerts
   * @param entryIntegrityViolationClassifier per-entry HMAC violation classifier
   * @param entryIntegrityConciliationService per-entry conciliation registry
   */
  public AuditLifecycleService(
      AuditChainCheckpointRepository checkpointRepository,
      AuditLogRepository auditLogRepository,
      AuditHmacService auditHmacService,
      AuditChainVerificationService chainVerificationService,
      AuditLogService auditLogService,
      AuditChainProperties chainProperties,
      AuditArchiveProperties archiveProperties,
      AlertService alertService,
      EntryIntegrityViolationClassifier entryIntegrityViolationClassifier,
      AuditEntryIntegrityConciliationService entryIntegrityConciliationService) {
    this.checkpointRepository = checkpointRepository;
    this.auditLogRepository = auditLogRepository;
    this.auditHmacService = auditHmacService;
    this.chainVerificationService = chainVerificationService;
    this.auditLogService = auditLogService;
    this.chainProperties = chainProperties;
    this.archiveProperties = archiveProperties;
    this.alertService = alertService;
    this.entryIntegrityViolationClassifier = entryIntegrityViolationClassifier;
    this.entryIntegrityConciliationService = entryIntegrityConciliationService;
  }

  /**
   * Progresses checkpoint lifecycle states according to the system-owned archive policy.
   *
   * <p>This automation covers the backend-only path needed for the current implementation slice:
   * eligible regular checkpoints are sealed automatically, and when external archival is disabled
   * the same FSM short-circuits from {@code SEALED} to {@code PURGEABLE} after the configured
   * lifecycle horizon.
   *
   * @param now reference time for lifecycle cutoff evaluation
   * @return summary of transitions applied during this execution
   */
  @Transactional
  public LifecycleAutomationResult progressLifecyclePolicy(OffsetDateTime now) {
    OffsetDateTime sealCutoff =
        now.minus(archiveProperties.getRetentionPeriod()).minus(archiveProperties.getSealDelay());
    int sealedCount = autoSealEligibleCheckpoints(sealCutoff);

    int purgeableCount = 0;
    if (!archiveProperties.isExternalArchivalEnabled()) {
      OffsetDateTime purgeableCutoff = sealCutoff.minus(archiveProperties.getPurgeDelay());
      purgeableCount = promoteSealedCheckpointsToPurgeable(purgeableCutoff);
    }

    return new LifecycleAutomationResult(sealedCount, purgeableCount);
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

    validateSealableCheckpoints(checkpoints);

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
      checkpoint.setLifecycleState(CheckpointLifecycleState.SEALED);
      checkpoint.setSealedAt(OffsetDateTime.now(ZoneOffset.UTC));
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
            .reason(request.justification())
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
   * Returns the current export-facing lifecycle eligibility summary.
   *
   * <p>This read-only contract is intended for future archival automation clients. It does not
   * materialize an export bundle; it only exposes whether sealed checkpoints currently await
   * confirmation and which tranche forms the next eligible archive unit.
   *
   * @return summary of the current archive eligibility window
   */
  @Transactional(readOnly = true)
  public ArchiveEligibilityResult getArchiveEligibility() {
    List<AuditChainCheckpoint> sealedCheckpoints =
        checkpointRepository.findByLifecycleStateAndCheckpointTypeOrderByWindowStartAsc(
            CheckpointLifecycleState.SEALED, CHECKPOINT_TYPE_ARCHIVE_SEAL);
    boolean confirmationRequired =
        archiveProperties.isExternalArchivalEnabled() && !sealedCheckpoints.isEmpty();

    if (sealedCheckpoints.isEmpty()) {
      return new ArchiveEligibilityResult(
          archiveProperties.isExternalArchivalEnabled(),
          confirmationRequired,
          0,
          null,
          null,
          null,
          null);
    }

    AuditChainCheckpoint oldest = sealedCheckpoints.get(0);
    AuditChainCheckpoint newest = sealedCheckpoints.get(sealedCheckpoints.size() - 1);
    return new ArchiveEligibilityResult(
        archiveProperties.isExternalArchivalEnabled(),
        confirmationRequired,
        sealedCheckpoints.size(),
        oldest.getWindowStart(),
        newest.getWindowEnd(),
        oldest.getCheckpointId(),
        newest.getCheckpointId());
  }

  /**
   * Confirms that a sealed audit range has been archived externally.
   *
   * <p>This is the backend contract meant to be called by a future archival workflow once it has
   * exported and persisted a sealed range. It intentionally records only the lifecycle transition
   * and archival digest binding; bundle generation remains out of scope for this session.
   *
   * @param request confirmation request identifying the sealed range and exported bundle digest
   * @param adminId identifier of the admin actor confirming the archive, or {@code null} when not
   *     available
   * @return confirmation result summarizing the exported tranche
   */
  @Transactional
  public ArchiveConfirmArchivedResult confirmArchived(
      ArchiveConfirmArchivedRequest request, Integer adminId) {
    if (!archiveProperties.isExternalArchivalEnabled()) {
      throw new AuditLifecycleConflictException(
          "Archive confirmation rejected: external archival is disabled by policy.");
    }

    ResolvedCheckpointRange resolvedRange =
        resolveCheckpointRange(
            request.periodStart(),
            request.periodEnd(),
            request.checkpointIdFrom(),
            request.checkpointIdTo());
    validateExportableCheckpoints(resolvedRange.checkpoints());

    OffsetDateTime exportedAt =
        request.archivedAt() != null ? request.archivedAt() : OffsetDateTime.now(ZoneOffset.UTC);
    for (AuditChainCheckpoint checkpoint : resolvedRange.checkpoints()) {
      checkpoint.setLifecycleState(CheckpointLifecycleState.EXPORTED);
      checkpoint.setExportedAt(exportedAt);
      checkpoint.setExportedByAdminId(adminId);
      checkpoint.setExportBundleDigest(request.exportBundleDigest());
    }
    checkpointRepository.saveAll(resolvedRange.checkpoints());

    String eventDetails =
        "{"
            + "\"periodStart\":\""
            + resolvedRange.periodStart()
            + "\","
            + "\"periodEnd\":\""
            + resolvedRange.periodEnd()
            + "\","
            + "\"checkpointsExported\":"
            + resolvedRange.checkpoints().size()
            + ","
            + "\"exportBundleDigest\":\""
            + escapeJson(request.exportBundleDigest())
            + "\""
            + "}";

    AuditLog metaEntry =
        AuditLog.builder()
            .eventType(EventType.AUDIT_CHAIN_ARCHIVE_EXPORTED)
            .eventAction("audit-chain-lifecycle")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .adminId(adminId)
            .eventDetails(eventDetails)
            .build();
    auditLogService.log(metaEntry);

    return new ArchiveConfirmArchivedResult(
        resolvedRange.periodStart(),
        resolvedRange.periodEnd(),
        resolvedRange.checkpoints().size(),
        request.exportBundleDigest(),
        exportedAt,
        metaEntry.getAuditLogId());
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

    // Re-chain any checkpoints that already exist after the gap (scheduler created them before
    // declaration). Their prev_chain_hmac currently points to the anchor; update to the gap's
    // chain_hmac and recompute each chain_hmac so verification passes.
    List<AuditChainCheckpoint> afterGap =
        checkpointRepository.findAllWithWindowStartAtOrAfter(gapEnd);
    String previousChainHmac = gapCheckpoint.getChainHmac();
    for (AuditChainCheckpoint cp : afterGap) {
      cp.setPrevChainHmac(previousChainHmac);
      String rechainInput =
          cp.getEntriesDigest()
              + FIELD_SEPARATOR
              + (previousChainHmac != null ? previousChainHmac : GENESIS_MARKER);
      String newChainHmac = auditHmacService.computeHmac(rechainInput);
      cp.setChainHmac(newChainHmac);
      checkpointRepository.save(cp);
      previousChainHmac = newChainHmac;
    }
    if (!afterGap.isEmpty()) {
      logger.info(
          "Re-chained {} checkpoint(s) after gap [{} to {}) to link from GAP_DECLARATION id={}",
          afterGap.size(),
          gapStart,
          gapEnd,
          gapCheckpoint.getCheckpointId());
    }

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
            .reason(request.justification())
            .build();
    auditLogService.log(metaEntry);

    // Auto-resolve the matching open AUDIT_CHAIN_GAP_PENDING alert (if any). The scheduler keys
    // its alert dedupe on the checkpoint id that was the latest at detection time, which is
    // exactly the chain anchor preceding the declared gap.
    Long alertAnchorId =
        anchorMode
            ? anchor.getCheckpointId()
            : checkpointRepository
                .findLatestBefore(gapStart)
                .map(AuditChainCheckpoint::getCheckpointId)
                .orElse(null);
    if (alertAnchorId != null) {
      alertService.resolveByDedupeKey(
          "AUDIT_CHAIN_GAP_PENDING:" + alertAnchorId, AlertResolutionReason.GAP_DECLARED, null);
    }

    return new GapDeclarationResult(
        gapStart,
        gapEnd,
        gapCheckpoint.getCheckpointId(),
        chainHmac,
        metaEntry.getAuditLogId(),
        request.justification());
  }

  /**
   * Reconciles an open {@code AUDIT_INTEGRITY_RUPTURE} alert with a signed {@code
   * MANIPULATION_CONCILIATION} checkpoint.
   *
   * <p>Removes {@code REGULAR} checkpoints in the rupture window, inserts a conciliation checkpoint
   * spanning {@code failBoundary} to {@code resumeBoundary}, re-chains downstream checkpoints,
   * emits {@link EventType#AUDIT_INTEGRITY_RUPTURE_CONCILIATED}, and resolves the alert.
   *
   * @param request reconciliation boundaries, justification, and alert reference
   * @param adminId Global Admin performing the reconciliation
   * @return conciliation result with checkpoint id and chain HMAC
   * @throws IllegalArgumentException when the alert or boundaries are invalid
   * @throws IllegalStateException when HMAC is inactive or heartbeat stale blocks reconciliation
   * @throws AuditLifecycleConflictException when non-regular checkpoints occupy the rupture window
   */
  @Transactional
  public IntegrityRuptureReconciliationResult reconcileIntegrityRupture(
      IntegrityRuptureReconciliationRequest request, Integer adminId) {
    if (!auditHmacService.isActive()) {
      throw new IllegalStateException(
          "HMAC signing is not active. Integrity rupture reconciliation requires an active HMAC"
              + " key.");
    }

    if (request.alertId() == null
        && (request.dedupeKey() == null || request.dedupeKey().isBlank())) {
      throw new IllegalArgumentException("Either alertId or dedupeKey must be provided.");
    }
    if (request.alertId() != null
        && request.dedupeKey() != null
        && !request.dedupeKey().isBlank()) {
      throw new IllegalArgumentException("Provide either alertId or dedupeKey, not both.");
    }

    OffsetDateTime failBoundary = normalizeUtc(request.failBoundary());
    OffsetDateTime resumeBoundary = normalizeUtc(request.resumeBoundary());
    if (!resumeBoundary.isAfter(failBoundary)) {
      throw new IllegalArgumentException("resumeBoundary must be strictly after failBoundary.");
    }

    if (alertService.hasOpenHeartbeatStaleAlert()) {
      throw new IllegalStateException(
          "Integrity rupture reconciliation rejected: AUDIT_CHAIN_HEARTBEAT_STALE alert is OPEN. "
              + "Resolve heartbeat supervision first.");
    }

    Alert alert = resolveIntegrityRuptureAlert(request);
    validateIntegrityRuptureAlert(alert, failBoundary, resumeBoundary);

    EntryIntegrityViolationClassifier.ViolationCollection liveEntryViolations =
        entryIntegrityViolationClassifier.collectRangeViolations(
            auditLogRepository, failBoundary, resumeBoundary);
    List<Long> requiredAcknowledgements =
        liveEntryViolations.alertEligibleViolations().stream()
            .map(EntryIntegrityViolation::auditLogId)
            .sorted()
            .toList();
    List<Long> providedAcknowledgements = normalizeAcknowledgedAuditLogIds(request);
    if (!requiredAcknowledgements.equals(providedAcknowledgements)) {
      throw new IllegalArgumentException(
          "acknowledgedAuditLogIds must exactly match live unacknowledged entry violations in the"
              + " rupture window. Expected "
              + requiredAcknowledgements
              + " but received "
              + providedAcknowledgements
              + ".");
    }

    List<AuditChainCheckpoint> inRuptureWindow =
        checkpointRepository.findByWindowRange(failBoundary, resumeBoundary);
    for (AuditChainCheckpoint checkpoint : inRuptureWindow) {
      String type = checkpoint.getCheckpointType();
      if (CHECKPOINT_TYPE_MANIPULATION_CONCILIATION.equals(type)) {
        throw new AuditLifecycleConflictException(
            "Integrity rupture reconciliation rejected: MANIPULATION_CONCILIATION checkpoint "
                + checkpoint.getCheckpointId()
                + " already exists in the rupture window.");
      }
      if (!CHECKPOINT_TYPE_REGULAR.equals(type)) {
        throw new AuditLifecycleConflictException(
            "Integrity rupture reconciliation rejected: checkpoint "
                + checkpoint.getCheckpointId()
                + " in the rupture window is not REGULAR (type="
                + type
                + "). Resolve archive or gap checkpoints separately.");
      }
    }

    if (!inRuptureWindow.isEmpty()) {
      checkpointRepository.deleteAll(inRuptureWindow);
      logger.info(
          "Removed {} REGULAR checkpoint(s) in rupture window [{} to {})",
          inRuptureWindow.size(),
          failBoundary,
          resumeBoundary);
    }

    String prevChainHmac =
        checkpointRepository
            .findLatestBefore(failBoundary)
            .map(AuditChainCheckpoint::getChainHmac)
            .orElse(null);

    String conciliationDigestInput =
        "MANIPULATION_CONCILIATION:" + failBoundary + FIELD_SEPARATOR + resumeBoundary;
    String entriesDigest = auditHmacService.computeHmac(conciliationDigestInput);

    String chainInput =
        entriesDigest + FIELD_SEPARATOR + (prevChainHmac != null ? prevChainHmac : GENESIS_MARKER);
    String chainHmac = auditHmacService.computeHmac(chainInput);

    AuditChainCheckpoint conciliationCheckpoint = new AuditChainCheckpoint();
    conciliationCheckpoint.setWindowStart(failBoundary);
    conciliationCheckpoint.setWindowEnd(resumeBoundary);
    conciliationCheckpoint.setEntryCount(0);
    conciliationCheckpoint.setEntriesDigest(entriesDigest);
    conciliationCheckpoint.setPrevChainHmac(prevChainHmac);
    conciliationCheckpoint.setChainHmac(chainHmac);
    conciliationCheckpoint.setCheckpointType(CHECKPOINT_TYPE_MANIPULATION_CONCILIATION);
    conciliationCheckpoint.setNotes(request.justification());
    checkpointRepository.save(conciliationCheckpoint);

    List<AuditChainCheckpoint> afterRupture =
        checkpointRepository.findAllWithWindowStartAtOrAfter(resumeBoundary);
    String previousChainHmac = conciliationCheckpoint.getChainHmac();
    for (AuditChainCheckpoint cp : afterRupture) {
      cp.setPrevChainHmac(previousChainHmac);
      String rechainInput =
          cp.getEntriesDigest()
              + FIELD_SEPARATOR
              + (previousChainHmac != null ? previousChainHmac : GENESIS_MARKER);
      String newChainHmac = auditHmacService.computeHmac(rechainInput);
      cp.setChainHmac(newChainHmac);
      checkpointRepository.save(cp);
      previousChainHmac = newChainHmac;
    }
    if (!afterRupture.isEmpty()) {
      logger.info(
          "Re-chained {} checkpoint(s) after integrity rupture [{} to {}) from"
              + " MANIPULATION_CONCILIATION id={}",
          afterRupture.size(),
          failBoundary,
          resumeBoundary,
          conciliationCheckpoint.getCheckpointId());
    }

    String eventDetails =
        "{"
            + "\"alertId\":"
            + alert.getAlertId()
            + ","
            + "\"failBoundary\":\""
            + failBoundary
            + "\","
            + "\"resumeBoundary\":\""
            + resumeBoundary
            + "\","
            + "\"conciliationCheckpointId\":"
            + conciliationCheckpoint.getCheckpointId()
            + ","
            + "\"conciliationChainHmac\":\""
            + chainHmac
            + "\","
            + "\"category\":\""
            + request.category().name()
            + "\","
            + "\"externalTicketReference\":\""
            + escapeJson(request.externalTicketReference())
            + "\","
            + "\"justification\":\""
            + escapeJson(request.justification())
            + "\""
            + "}";

    AuditLog metaEntry =
        AuditLog.builder()
            .eventType(EventType.AUDIT_INTEGRITY_RUPTURE_CONCILIATED)
            .eventAction("audit-chain-lifecycle")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .adminId(adminId)
            .eventDetails(eventDetails)
            .reason(request.justification())
            .build();
    auditLogService.log(metaEntry);

    List<Long> conciliatedAuditLogIds = new ArrayList<>();
    for (Long auditLogId : providedAcknowledgements) {
      AuditLog entry =
          auditLogRepository
              .findById(auditLogId)
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Audit log id " + auditLogId + " not found for entry conciliation."));
      String violationReason =
          entry.getEntryHmac() == null
              ? EntryHmacViolationCollector.REASON_MISSING_ENTRY_HMAC
              : EntryHmacViolationCollector.REASON_HMAC_MISMATCH;
      entryIntegrityConciliationService.createConciliation(
          entry,
          violationReason,
          request.category(),
          request.justification(),
          request.externalTicketReference(),
          alert.getAlertId(),
          adminId);
      conciliatedAuditLogIds.add(auditLogId);
    }

    alertService.resolveByDedupeKey(
        alert.getDedupeKey(), AlertResolutionReason.INTEGRITY_RUPTURE_CONCILIATED, adminId);

    logger.info(
        "Reconciled AUDIT_INTEGRITY_RUPTURE alert id={} with MANIPULATION_CONCILIATION checkpoint"
            + " id={} for [{} to {})",
        alert.getAlertId(),
        conciliationCheckpoint.getCheckpointId(),
        failBoundary,
        resumeBoundary);

    return new IntegrityRuptureReconciliationResult(
        failBoundary,
        resumeBoundary,
        conciliationCheckpoint.getCheckpointId(),
        chainHmac,
        metaEntry.getAuditLogId(),
        alert.getAlertId(),
        request.justification(),
        request.category(),
        List.copyOf(conciliatedAuditLogIds),
        conciliatedAuditLogIds.size());
  }

  private static List<Long> normalizeAcknowledgedAuditLogIds(
      IntegrityRuptureReconciliationRequest request) {
    if (request.acknowledgedAuditLogIds() == null || request.acknowledgedAuditLogIds().isEmpty()) {
      return List.of();
    }
    return request.acknowledgedAuditLogIds().stream().sorted().distinct().toList();
  }

  private Alert resolveIntegrityRuptureAlert(IntegrityRuptureReconciliationRequest request) {
    if (request.alertId() != null) {
      Alert alert =
          alertService
              .findById(request.alertId())
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Alert id " + request.alertId() + " not found."));
      if (alert.getStatus() != AlertStatus.OPEN) {
        throw new IllegalArgumentException(
            "Alert id " + request.alertId() + " is not OPEN (status=" + alert.getStatus() + ").");
      }
      return alert;
    }
    return alertService
        .findOpenByDedupeKey(request.dedupeKey())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No OPEN alert found for dedupeKey " + request.dedupeKey() + "."));
  }

  private void validateIntegrityRuptureAlert(
      Alert alert, OffsetDateTime failBoundary, OffsetDateTime resumeBoundary) {
    if (alert.getAlertType() != AlertType.AUDIT_INTEGRITY_RUPTURE) {
      throw new IllegalArgumentException(
          "Alert id "
              + alert.getAlertId()
              + " is not AUDIT_INTEGRITY_RUPTURE (type="
              + alert.getAlertType()
              + ").");
    }

    OffsetDateTime payloadFail = parsePayloadBoundary(alert.getPayload(), "failBoundary");
    if (payloadFail == null) {
      payloadFail = parsePayloadBoundary(alert.getPayload(), "windowStart");
    }
    OffsetDateTime payloadResume = parsePayloadBoundary(alert.getPayload(), "resumeBoundary");
    if (payloadResume == null) {
      payloadResume = parsePayloadBoundary(alert.getPayload(), "windowEnd");
    }

    if (payloadFail != null && !payloadFail.equals(failBoundary)) {
      throw new IllegalArgumentException(
          "failBoundary does not match alert payload (expected "
              + payloadFail
              + ", got "
              + failBoundary
              + ").");
    }
    if (payloadResume != null && !payloadResume.equals(resumeBoundary)) {
      throw new IllegalArgumentException(
          "resumeBoundary does not match alert payload (expected "
              + payloadResume
              + ", got "
              + resumeBoundary
              + ").");
    }
  }

  private static OffsetDateTime parsePayloadBoundary(String payload, String field) {
    if (payload == null || payload.isBlank()) {
      return null;
    }
    try {
      JsonNode root = PAYLOAD_OBJECT_MAPPER.readTree(payload);
      JsonNode node = root.get(field);
      if (node == null || node.isNull()) {
        return null;
      }
      return normalizeUtc(OffsetDateTime.parse(node.asString()));
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Unable to parse " + field + " from alert payload: " + e.getMessage());
    }
  }

  private static OffsetDateTime normalizeUtc(OffsetDateTime value) {
    return value.withOffsetSameInstant(ZoneOffset.UTC);
  }

  private void validateSealableCheckpoints(List<AuditChainCheckpoint> checkpoints) {
    if (checkpoints.isEmpty()) {
      return;
    }

    EnumSet<CheckpointLifecycleState> sealableStates = EnumSet.of(CheckpointLifecycleState.ACTIVE);

    for (AuditChainCheckpoint checkpoint : checkpoints) {
      if (!sealableStates.contains(checkpoint.getLifecycleState())
          || !CHECKPOINT_TYPE_REGULAR.equals(checkpoint.getCheckpointType())) {
        throw new AuditLifecycleConflictException(
            "Archive seal rejected: checkpoint "
                + checkpoint.getCheckpointId()
                + " is not sealable (lifecycleState="
                + checkpoint.getLifecycleState()
                + ", checkpointType="
                + checkpoint.getCheckpointType()
                + ").");
      }
    }
  }

  private void validateExportableCheckpoints(List<AuditChainCheckpoint> checkpoints) {
    if (checkpoints.isEmpty()) {
      throw new IllegalArgumentException("No checkpoints found for archive confirmation.");
    }

    for (AuditChainCheckpoint checkpoint : checkpoints) {
      if (checkpoint.getLifecycleState() != CheckpointLifecycleState.SEALED
          || !CHECKPOINT_TYPE_ARCHIVE_SEAL.equals(checkpoint.getCheckpointType())) {
        throw new AuditLifecycleConflictException(
            "Archive confirmation rejected: checkpoint "
                + checkpoint.getCheckpointId()
                + " is not exportable (lifecycleState="
                + checkpoint.getLifecycleState()
                + ", checkpointType="
                + checkpoint.getCheckpointType()
                + ").");
      }
    }
  }

  private ResolvedCheckpointRange resolveCheckpointRange(
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd,
      Long checkpointIdFrom,
      Long checkpointIdTo) {
    boolean idMode = checkpointIdFrom != null || checkpointIdTo != null;
    boolean tsMode = periodStart != null || periodEnd != null;

    if (idMode && tsMode) {
      throw new IllegalArgumentException(
          "Provide either (checkpointIdFrom + checkpointIdTo) or (periodStart + periodEnd), not"
              + " both.");
    }

    List<AuditChainCheckpoint> checkpoints;
    OffsetDateTime resolvedPeriodStart;
    OffsetDateTime resolvedPeriodEnd;

    if (idMode) {
      if (checkpointIdFrom == null || checkpointIdTo == null) {
        throw new IllegalArgumentException(
            "Both checkpointIdFrom and checkpointIdTo must be provided when using ID mode.");
      }
      if (checkpointIdTo < checkpointIdFrom) {
        throw new IllegalArgumentException("checkpointIdTo must be >= checkpointIdFrom.");
      }
      checkpoints = checkpointRepository.findByIdRange(checkpointIdFrom, checkpointIdTo);
      if (checkpoints.isEmpty()) {
        throw new IllegalArgumentException(
            "No checkpoints found with IDs in [" + checkpointIdFrom + ", " + checkpointIdTo + "].");
      }
      resolvedPeriodStart = checkpoints.get(0).getWindowStart();
      resolvedPeriodEnd = checkpoints.get(checkpoints.size() - 1).getWindowEnd();
    } else {
      if (periodStart == null || periodEnd == null) {
        throw new IllegalArgumentException(
            "Either (checkpointIdFrom + checkpointIdTo) or (periodStart + periodEnd) must be"
                + " provided.");
      }
      if (!periodEnd.isAfter(periodStart)) {
        throw new IllegalArgumentException("periodEnd must be strictly after periodStart.");
      }
      checkpoints = checkpointRepository.findByWindowRange(periodStart, periodEnd);
      resolvedPeriodStart = periodStart;
      resolvedPeriodEnd = periodEnd;
    }

    return new ResolvedCheckpointRange(checkpoints, resolvedPeriodStart, resolvedPeriodEnd);
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
        (root, _, cb) ->
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

  private int autoSealEligibleCheckpoints(OffsetDateTime sealCutoff) {
    List<AuditChainCheckpoint> eligibleCheckpoints =
        checkpointRepository
            .findByLifecycleStateAndCheckpointTypeAndWindowStartBeforeOrderByWindowStartAsc(
                CheckpointLifecycleState.ACTIVE, CHECKPOINT_TYPE_REGULAR, sealCutoff);

    if (eligibleCheckpoints.isEmpty()) {
      return 0;
    }

    OffsetDateTime sealedAt = OffsetDateTime.now(ZoneOffset.UTC);
    for (AuditChainCheckpoint checkpoint : eligibleCheckpoints) {
      checkpoint.setLifecycleState(CheckpointLifecycleState.SEALED);
      checkpoint.setCheckpointType(CHECKPOINT_TYPE_ARCHIVE_SEAL);
      checkpoint.setSealedAt(sealedAt);
      checkpoint.setNotes(AUTO_SEAL_NOTES);
    }
    checkpointRepository.saveAll(eligibleCheckpoints);
    logger.info(
        "Lifecycle automation sealed {} checkpoint(s) before {}",
        eligibleCheckpoints.size(),
        sealCutoff);
    return eligibleCheckpoints.size();
  }

  private int promoteSealedCheckpointsToPurgeable(OffsetDateTime purgeableCutoff) {
    List<AuditChainCheckpoint> eligibleCheckpoints =
        checkpointRepository
            .findByLifecycleStateAndCheckpointTypeAndWindowStartBeforeOrderByWindowStartAsc(
                CheckpointLifecycleState.SEALED, CHECKPOINT_TYPE_ARCHIVE_SEAL, purgeableCutoff);

    if (eligibleCheckpoints.isEmpty()) {
      return 0;
    }

    for (AuditChainCheckpoint checkpoint : eligibleCheckpoints) {
      checkpoint.setLifecycleState(CheckpointLifecycleState.PURGEABLE);
    }
    checkpointRepository.saveAll(eligibleCheckpoints);
    logger.info(
        "Lifecycle automation promoted {} checkpoint(s) to PURGEABLE before {}",
        eligibleCheckpoints.size(),
        purgeableCutoff);
    return eligibleCheckpoints.size();
  }

  /**
   * Summary of automatic lifecycle transitions applied by policy execution.
   *
   * @param sealedCount number of checkpoints promoted from {@code ACTIVE} to {@code SEALED}
   * @param purgeableCount number of checkpoints promoted to {@code PURGEABLE}
   */
  public record LifecycleAutomationResult(int sealedCount, int purgeableCount) {}

  private record ResolvedCheckpointRange(
      List<AuditChainCheckpoint> checkpoints,
      OffsetDateTime periodStart,
      OffsetDateTime periodEnd) {}
}
