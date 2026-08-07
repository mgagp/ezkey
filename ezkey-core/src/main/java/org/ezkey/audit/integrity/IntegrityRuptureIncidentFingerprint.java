/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: IntegrityRuptureIncidentFingerprint
 * Description: Stable incident fingerprint for AUDIT_INTEGRITY_RUPTURE dedupe keys.
 */

package org.ezkey.audit.integrity;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.ezkey.audit.dto.ChainIntegrityViolation;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import tools.jackson.databind.ObjectMapper;

/**
 * Computes stable {@code AUDIT_INTEGRITY_RUPTURE} dedupe keys from alert-eligible violation sets.
 *
 * @since 2026
 */
public final class IntegrityRuptureIncidentFingerprint {

  static final String DEDUPE_PREFIX = "AUDIT_INTEGRITY_RUPTURE:";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private IntegrityRuptureIncidentFingerprint() {}

  /**
   * Builds the dedupe key for an integrity rupture incident.
   *
   * @param failBoundary chain fail boundary when chain dimension is alert-eligible; otherwise null
   * @param resumeBoundary chain resume boundary when chain dimension is alert-eligible; otherwise
   *     null
   * @param alertEligibleEntries entry violations that should raise or touch alerts
   * @param alertEligibleChains chain violations that should raise or touch alerts
   * @return dedupe key of the form {@code AUDIT_INTEGRITY_RUPTURE:<sha256hex>}
   */
  public static String computeDedupeKey(
      OffsetDateTime failBoundary,
      OffsetDateTime resumeBoundary,
      List<EntryIntegrityViolation> alertEligibleEntries,
      List<ChainIntegrityViolation> alertEligibleChains) {
    String fingerprint =
        computeFingerprint(failBoundary, resumeBoundary, alertEligibleEntries, alertEligibleChains);
    return DEDUPE_PREFIX + fingerprint;
  }

  /**
   * Computes the SHA-256 hex incident fingerprint from alert-eligible violation dimensions.
   *
   * @param failBoundary chain fail boundary when chain dimension is alert-eligible; otherwise null
   * @param resumeBoundary chain resume boundary when chain dimension is alert-eligible; otherwise
   *     null
   * @param alertEligibleEntries alert-eligible entry violations
   * @param alertEligibleChains alert-eligible chain violations
   * @return lowercase 64-character hex digest
   */
  public static String computeFingerprint(
      OffsetDateTime failBoundary,
      OffsetDateTime resumeBoundary,
      List<EntryIntegrityViolation> alertEligibleEntries,
      List<ChainIntegrityViolation> alertEligibleChains) {
    Map<String, Object> root = new TreeMap<>();
    root.put("failBoundary", boundaryToJson(failBoundary));
    root.put("resumeBoundary", boundaryToJson(resumeBoundary));
    root.put("entryViolations", toEntryViolationMaps(alertEligibleEntries));
    root.put("chainViolations", toChainViolationMaps(alertEligibleChains));
    try {
      String canonicalJson = OBJECT_MAPPER.writeValueAsString(root);
      return AuditEntryIntegrityConciliationService.sha256Hex(canonicalJson);
    } catch (RuntimeException e) { // CHECKSTYLE IGNORE IllegalCatch
      throw new IllegalStateException("Could not serialize integrity rupture fingerprint", e);
    }
  }

  /**
   * Determines whether the chain dimension should contribute to alert raise/touch (C8-6 aware).
   *
   * @param chainReport chain verification report for the window
   * @param hasOpenHeartbeatStaleAlert true when heartbeat stale alert is OPEN
   * @return true when chain findings are alert-eligible
   */
  public static boolean isChainAlertEligible(
      AuditChainVerificationService.ChainVerificationReport chainReport,
      boolean hasOpenHeartbeatStaleAlert) {
    if (chainReport.invalidCheckpoints() > 0) {
      return true;
    }
    if (chainReport.intact()) {
      return false;
    }
    if (chainReport.undeclaredGaps().isEmpty()) {
      return true;
    }
    return !hasOpenHeartbeatStaleAlert;
  }

  /**
   * Chain violations included in the incident fingerprint when chain dimension is alert-eligible.
   *
   * @param chainReport chain verification report
   * @param chainAlertEligible whether chain dimension is alert-eligible
   * @return alert-eligible chain violations (empty when chain dimension is deferred)
   */
  public static List<ChainIntegrityViolation> alertEligibleChainViolations(
      AuditChainVerificationService.ChainVerificationReport chainReport,
      boolean chainAlertEligible) {
    if (!chainAlertEligible) {
      return List.of();
    }
    return chainReport.chainViolations();
  }

  /**
   * Boundaries for fingerprint when chain dimension is alert-eligible.
   *
   * @param chainReport chain verification report
   * @param chainAlertEligible whether chain dimension is alert-eligible
   * @return fail/resume boundaries or null pair when chain dimension is not alert-eligible
   */
  public static BoundaryPair fingerprintBoundaries(
      AuditChainVerificationService.ChainVerificationReport chainReport,
      boolean chainAlertEligible) {
    if (!chainAlertEligible) {
      return new BoundaryPair(null, null);
    }
    return new BoundaryPair(chainReport.coverageStart(), chainReport.coverageEnd());
  }

  private static String boundaryToJson(OffsetDateTime boundary) {
    return boundary != null
        ? boundary.withOffsetSameInstant(java.time.ZoneOffset.UTC).toString()
        : null;
  }

  private static List<Map<String, Object>> toEntryViolationMaps(
      List<EntryIntegrityViolation> violations) {
    List<EntryIntegrityViolation> sorted = new ArrayList<>(violations);
    sorted.sort(Comparator.comparing(EntryIntegrityViolation::auditLogId));
    List<Map<String, Object>> maps = new ArrayList<>();
    for (EntryIntegrityViolation violation : sorted) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("auditLogId", violation.auditLogId());
      map.put("violationReason", violation.reason());
      maps.add(map);
    }
    return maps;
  }

  private static List<Map<String, Object>> toChainViolationMaps(
      List<ChainIntegrityViolation> violations) {
    List<ChainIntegrityViolation> sorted = new ArrayList<>(violations);
    sorted.sort(
        Comparator.comparing(
                ChainIntegrityViolation::checkpointId, Comparator.nullsLast(Long::compareTo))
            .thenComparing(
                ChainIntegrityViolation::violationType, Comparator.nullsLast(String::compareTo)));
    List<Map<String, Object>> maps = new ArrayList<>();
    for (ChainIntegrityViolation violation : sorted) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("checkpointId", violation.checkpointId());
      map.put("violationType", violation.violationType());
      maps.add(map);
    }
    return maps;
  }

  /**
   * Fail and resume boundaries for incident fingerprinting.
   *
   * @param failBoundary inclusive chain discontinuity start
   * @param resumeBoundary exclusive chain continuity resume point
   */
  public record BoundaryPair(OffsetDateTime failBoundary, OffsetDateTime resumeBoundary) {}
}
