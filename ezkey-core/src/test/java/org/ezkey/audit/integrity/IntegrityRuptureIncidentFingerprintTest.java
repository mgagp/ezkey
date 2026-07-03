/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.ezkey.audit.dto.ChainIntegrityViolation;
import org.ezkey.audit.dto.EntryIntegrityConciliationStatus;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import org.junit.jupiter.api.Test;

class IntegrityRuptureIncidentFingerprintTest {

  private static final OffsetDateTime FAIL =
      OffsetDateTime.of(2026, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime RESUME = FAIL.plusMinutes(30);

  @Test
  void computeFingerprint_isStableForSameIncident() {
    List<EntryIntegrityViolation> entries =
        List.of(
            new EntryIntegrityViolation(
                5L,
                "ADMIN_LOGIN",
                FAIL,
                "HMAC_MISMATCH",
                EntryIntegrityConciliationStatus.NONE,
                null));
    List<ChainIntegrityViolation> chains =
        List.of(
            new ChainIntegrityViolation(9L, FAIL, RESUME, "CHAIN_HMAC_MISMATCH", "broken link"));

    String first =
        IntegrityRuptureIncidentFingerprint.computeFingerprint(FAIL, RESUME, entries, chains);
    String second =
        IntegrityRuptureIncidentFingerprint.computeFingerprint(FAIL, RESUME, entries, chains);

    assertEquals(first, second);
    assertEquals(64, first.length());
    assertTrue(first.matches("[0-9a-f]{64}"));
  }

  @Test
  void computeDedupeKey_usesIncidentFingerprintPrefix() {
    String dedupeKey =
        IntegrityRuptureIncidentFingerprint.computeDedupeKey(null, null, List.of(), List.of());

    assertTrue(dedupeKey.startsWith(IntegrityRuptureIncidentFingerprint.DEDUPE_PREFIX));
    assertTrue(dedupeKey.length() < 256);
  }

  @Test
  void computeFingerprint_changesWhenAlertEligibleEntrySetChanges() {
    List<EntryIntegrityViolation> one =
        List.of(
            new EntryIntegrityViolation(
                1L,
                "ADMIN_LOGIN",
                FAIL,
                "HMAC_MISMATCH",
                EntryIntegrityConciliationStatus.NONE,
                null));
    List<EntryIntegrityViolation> two =
        List.of(
            new EntryIntegrityViolation(
                1L,
                "ADMIN_LOGIN",
                FAIL,
                "HMAC_MISMATCH",
                EntryIntegrityConciliationStatus.NONE,
                null),
            new EntryIntegrityViolation(
                2L,
                "ADMIN_LOGIN",
                FAIL,
                "HMAC_MISMATCH",
                EntryIntegrityConciliationStatus.NONE,
                null));

    String fingerprintOne =
        IntegrityRuptureIncidentFingerprint.computeFingerprint(null, null, one, List.of());
    String fingerprintTwo =
        IntegrityRuptureIncidentFingerprint.computeFingerprint(null, null, two, List.of());

    assertNotEquals(fingerprintOne, fingerprintTwo);
  }

  @Test
  void isChainAlertEligible_defersWhenOnlyUndeclaredGapsAndHeartbeatOpen() {
    AuditChainVerificationService.UndeclaredGap gap =
        new AuditChainVerificationService.UndeclaredGap(FAIL, RESUME, 30);
    AuditChainVerificationService.ChainVerificationReport report =
        new AuditChainVerificationService.ChainVerificationReport(
            0,
            0,
            0,
            0,
            0,
            List.of("gap"),
            List.of(),
            List.of(gap),
            null,
            null,
            FAIL,
            RESUME,
            false,
            false,
            "UNDECLARED_GAPS");

    assertEquals(false, IntegrityRuptureIncidentFingerprint.isChainAlertEligible(report, true));
    assertEquals(true, IntegrityRuptureIncidentFingerprint.isChainAlertEligible(report, false));
  }
}
