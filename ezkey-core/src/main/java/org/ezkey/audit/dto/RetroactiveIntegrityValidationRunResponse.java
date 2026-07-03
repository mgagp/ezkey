/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: RetroactiveIntegrityValidationRunResponse
 * Description: Response for operator-initiated retroactive integrity validation.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationTriggerSource;

/**
 * Response for {@code POST /api/v1/audit-logs/integrity-validation/run}.
 *
 * @param windowStart inclusive start of validated window (null when skipped before bounds apply)
 * @param windowEnd exclusive end of validated window
 * @param scope human-readable scope summary
 * @param skipped true when validation did not run (e.g. HMAC inactive)
 * @param skipReason reason when {@code skipped} is true
 * @param intact true when no integrity failures were detected
 * @param alertRaised true when an integrity rupture alert was raised or touched
 * @param alertId alert id when {@code alertRaised} is true
 * @param chainStatus status string from chain verification
 * @param entryHmacViolationCount total per-entry HMAC failures in window (reporting set)
 * @param entryAlertEligibleCount entry violations still eligible for alerting
 * @param chainViolationCount chain-level violation message count
 * @param triggerSource {@code OPERATOR} for this endpoint
 * @since 2026
 */
public record RetroactiveIntegrityValidationRunResponse(
    OffsetDateTime windowStart,
    OffsetDateTime windowEnd,
    String scope,
    boolean skipped,
    String skipReason,
    boolean intact,
    boolean alertRaised,
    Long alertId,
    String chainStatus,
    int entryHmacViolationCount,
    int entryAlertEligibleCount,
    int chainViolationCount,
    RetroactiveIntegrityValidationTriggerSource triggerSource) {

  /**
   * Maps orchestration result to API response.
   *
   * @param result service result
   * @return API response DTO
   */
  public static RetroactiveIntegrityValidationRunResponse from(
      RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result) {
    return new RetroactiveIntegrityValidationRunResponse(
        result.windowStart(),
        result.windowEnd(),
        result.scope(),
        result.skipped(),
        result.skipReason(),
        result.intact(),
        result.alertRaised(),
        result.alertId(),
        result.chainStatus(),
        result.entryHmacViolationCount(),
        result.entryAlertEligibleCount(),
        result.chainViolationCount(),
        result.triggerSource());
  }
}
