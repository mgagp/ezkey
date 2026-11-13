/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.asyncjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.ezkey.audit.integrity.AuditChainVerificationService.ChainVerificationReport;
import org.junit.jupiter.api.Test;

/**
 * Counter / chainStatus mapping (Integrity cut 3 D3 / P1).
 *
 * @since 2026
 */
class IntegrityChainVerdictFieldsTest {

  @Test
  void mapStatus_ok() {
    assertEquals(IntegrityChainStatus.OK, IntegrityChainVerdictFields.mapStatus("OK"));
  }

  @Test
  void mapStatus_undeclaredGap() {
    assertEquals(
        IntegrityChainStatus.UNDECLARED_GAP_DETECTED,
        IntegrityChainVerdictFields.mapStatus("UNDECLARED_GAP_DETECTED"));
  }

  @Test
  void mapStatus_violation() {
    assertEquals(
        IntegrityChainStatus.CHAIN_INTEGRITY_VIOLATION_DETECTED,
        IntegrityChainVerdictFields.mapStatus("CHAIN_INTEGRITY_VIOLATION_DETECTED"));
  }

  @Test
  void mapStatus_hmacInactive_notVerified() {
    assertEquals(
        IntegrityChainStatus.NOT_VERIFIED,
        IntegrityChainVerdictFields.mapStatus("HMAC signing is not active"));
  }

  @Test
  void mapStatus_noCheckpoints_notVerified() {
    assertEquals(
        IntegrityChainStatus.NOT_VERIFIED,
        IntegrityChainVerdictFields.mapStatus("No checkpoints found in range"));
  }

  @Test
  void mapStatus_unknown_failClosedNotVerified() {
    assertEquals(
        IntegrityChainStatus.NOT_VERIFIED, IntegrityChainVerdictFields.mapStatus("SOMETHING_ELSE"));
    assertEquals(IntegrityChainStatus.NOT_VERIFIED, IntegrityChainVerdictFields.mapStatus(null));
  }

  @Test
  void skippedRun_notVerified_nullCounters() {
    IntegrityChainVerdictFields fields = IntegrityChainVerdictFields.notVerified();
    assertEquals(IntegrityChainStatus.NOT_VERIFIED, fields.chainStatus());
    assertNull(fields.totalCheckpoints());
    assertNull(fields.entryHmacViolations());
  }

  @Test
  void fromChainReport_nullsEntryViolations() {
    ChainVerificationReport report =
        new ChainVerificationReport(
            5,
            4,
            1,
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
            "CHAIN_INTEGRITY_VIOLATION_DETECTED");
    IntegrityChainVerdictFields fields = IntegrityChainVerdictFields.fromChainReport(report);
    assertEquals(IntegrityChainStatus.CHAIN_INTEGRITY_VIOLATION_DETECTED, fields.chainStatus());
    assertEquals(5, fields.totalCheckpoints());
    assertNull(fields.entryHmacViolations());
  }

  @Test
  void legacyNullCounters_serialiseAsNullOnEntity() {
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    assertNull(job.getChainStatus());
    assertNull(job.getTotalCheckpoints());
    assertNull(job.getEntryHmacViolations());
  }
}
