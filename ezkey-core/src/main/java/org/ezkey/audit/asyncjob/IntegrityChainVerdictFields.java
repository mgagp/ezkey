/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrityChainVerdictFields
 * Description: Persisted chain counters + status for Integrity async job rows.
 */

package org.ezkey.audit.asyncjob;

import org.ezkey.audit.integrity.AuditChainVerificationService.ChainVerificationReport;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult;

/**
 * Structured chain verdict fields persisted on Integrity job rows (Integrity cut 3 D3 / P1).
 *
 * @param chainStatus mapped status (never null when constructed from a report)
 * @param totalCheckpoints total checkpoints in range
 * @param validCheckpoints valid checkpoints
 * @param invalidCheckpoints invalid checkpoints
 * @param archivedCheckpoints archived checkpoints
 * @param undeclaredGaps undeclared gap count
 * @param entryHmacViolations entry HMAC violations (null for VERIFY_CHAIN_RANGE)
 * @since 2026
 */
public record IntegrityChainVerdictFields(
    IntegrityChainStatus chainStatus,
    Integer totalCheckpoints,
    Integer validCheckpoints,
    Integer invalidCheckpoints,
    Integer archivedCheckpoints,
    Integer undeclaredGaps,
    Integer entryHmacViolations) {

  /**
   * Maps a chain verification report to persisted fields (VERIFY_CHAIN path; no entry counters).
   *
   * @param report chain report
   * @return verdict fields
   */
  public static IntegrityChainVerdictFields fromChainReport(ChainVerificationReport report) {
    return new IntegrityChainVerdictFields(
        mapStatus(report.status()),
        report.totalCheckpoints(),
        report.validCheckpoints(),
        report.invalidCheckpoints(),
        report.archivedCheckpoints(),
        report.undeclaredGaps().size(),
        null);
  }

  /**
   * Maps a chain report plus entry-HMAC violation count (RUN_VALIDATION / nightly).
   *
   * @param report chain report
   * @param entryHmacViolations entry violation count
   * @return verdict fields
   */
  public static IntegrityChainVerdictFields fromValidation(
      ChainVerificationReport report, int entryHmacViolations) {
    return new IntegrityChainVerdictFields(
        mapStatus(report.status()),
        report.totalCheckpoints(),
        report.validCheckpoints(),
        report.invalidCheckpoints(),
        report.archivedCheckpoints(),
        report.undeclaredGaps().size(),
        entryHmacViolations);
  }

  /**
   * Maps a retroactive validation result (operator RUN_VALIDATION / nightly terminal).
   *
   * @param result completed validation result (not skipped)
   * @return verdict fields including {@code entryHmacViolations}
   */
  public static IntegrityChainVerdictFields fromValidation(
      RetroactiveIntegrityValidationResult result) {
    return new IntegrityChainVerdictFields(
        mapStatus(result.chainStatus()),
        result.totalCheckpoints(),
        result.validCheckpoints(),
        result.invalidCheckpoints(),
        result.archivedCheckpoints(),
        result.undeclaredGaps(),
        result.entryHmacViolationCount());
  }

  /**
   * Skipped / not-run validation → {@link IntegrityChainStatus#NOT_VERIFIED} (fail closed).
   *
   * @return not-verified fields with null counters
   */
  public static IntegrityChainVerdictFields notVerified() {
    return new IntegrityChainVerdictFields(
        IntegrityChainStatus.NOT_VERIFIED, null, null, null, null, null, null);
  }

  /**
   * Maps {@link ChainVerificationReport#status()} (and early-return messages) to the closed enum.
   *
   * @param status report status string
   * @return mapped status; unknown → {@link IntegrityChainStatus#NOT_VERIFIED}
   */
  public static IntegrityChainStatus mapStatus(String status) {
    if (status == null) {
      return IntegrityChainStatus.NOT_VERIFIED;
    }
    return switch (status) {
      case "OK" -> IntegrityChainStatus.OK;
      case "UNDECLARED_GAP_DETECTED" -> IntegrityChainStatus.UNDECLARED_GAP_DETECTED;
      case "CHAIN_INTEGRITY_VIOLATION_DETECTED" ->
          IntegrityChainStatus.CHAIN_INTEGRITY_VIOLATION_DETECTED;
      case "HMAC signing is not active", "No checkpoints found in range" ->
          IntegrityChainStatus.NOT_VERIFIED;
      default -> IntegrityChainStatus.NOT_VERIFIED;
    };
  }

  /**
   * Applies these fields onto a job entity.
   *
   * @param job target job
   */
  public void applyTo(IntegrityAsyncJob job) {
    job.setChainStatus(chainStatus);
    job.setTotalCheckpoints(totalCheckpoints);
    job.setValidCheckpoints(validCheckpoints);
    job.setInvalidCheckpoints(invalidCheckpoints);
    job.setArchivedCheckpoints(archivedCheckpoints);
    job.setUndeclaredGaps(undeclaredGaps);
    job.setEntryHmacViolations(entryHmacViolations);
  }
}
