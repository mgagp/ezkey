/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.integrity.AuditChainVerificationService.ChainVerificationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link AuditChainVerificationService}.
 *
 * <p>Each test constructs checkpoints whose {@code entries_digest} and {@code chain_hmac} are
 * computed with the same real {@link AuditHmacService} as is injected into the service under test.
 * Integrity violations are introduced by mutating specific fields in the checkpoints after
 * creation.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuditChainVerificationServiceTest {

  @Mock private AuditChainCheckpointRepository checkpointRepository;
  @Mock private AuditLogRepository auditLogRepository;

  private AuditHmacService hmacService;
  private AuditChainVerificationService verificationService;

  private static final OffsetDateTime WIN_START =
      OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime WIN_END =
      OffsetDateTime.of(2026, 2, 19, 10, 5, 0, 0, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    hmacService = TestHmacServiceFactory.create();
    verificationService =
        new AuditChainVerificationService(checkpointRepository, auditLogRepository, hmacService);
  }

  // -----------------------------------------------------------------------
  // Guard: HMAC not active
  // -----------------------------------------------------------------------

  @Test
  void verifyChain_whenHmacNotActive_returnsError() {
    AuditHmacService inactive = TestHmacServiceFactory.createInactive();
    AuditChainVerificationService svc =
        new AuditChainVerificationService(checkpointRepository, auditLogRepository, inactive);

    ChainVerificationReport report = svc.verifyChain(null, null);

    assertFalse(report.intact());
    assertEquals("HMAC signing is not active", report.status());
  }

  // -----------------------------------------------------------------------
  // Guard: no checkpoints in range
  // -----------------------------------------------------------------------

  @Test
  void verifyChain_noCheckpoints_returnsNoCheckpoints() {
    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of());

    ChainVerificationReport report = verificationService.verifyChain(null, null);

    assertEquals(0, report.totalCheckpoints());
    assertTrue(report.intact());
    assertEquals("No checkpoints found in range", report.status());
  }

  // -----------------------------------------------------------------------
  // Happy path: single genesis checkpoint, all entries intact
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void verifyChain_singleGenesisCheckpoint_intact_returnsOk() {
    AuditLog entry = buildSignedEntry(1L);
    stubAuditLogs(List.of(entry));

    AuditChainCheckpoint checkpoint = buildGenesisCheckpoint(List.of(entry));
    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of(checkpoint));

    ChainVerificationReport report = verificationService.verifyChain(null, null);

    assertEquals(1, report.totalCheckpoints());
    assertEquals(1, report.validCheckpoints());
    assertEquals(0, report.invalidCheckpoints());
    assertTrue(report.violations().isEmpty());
    assertTrue(report.intact());
    assertEquals("OK", report.status());
  }

  // -----------------------------------------------------------------------
  // Two consecutive checkpoints, intact chain
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void verifyChain_twoLinkedCheckpoints_intact_returnsOk() {
    AuditLog e1 = buildSignedEntry(1L);
    AuditLog e2 = buildSignedEntry(2L);

    AuditChainCheckpoint cp1 = buildGenesisCheckpoint(List.of(e1));
    AuditChainCheckpoint cp2 = buildLinkedCheckpoint(List.of(e2), cp1.getChainHmac());

    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of(cp1, cp2));

    // First window returns e1, second window returns e2
    when(auditLogRepository.findAll(any(Specification.class), eq(Sort.by("auditLogId").ascending())))
        .thenReturn(List.of(e1))
        .thenReturn(List.of(e2));

    ChainVerificationReport report = verificationService.verifyChain(null, null);

    assertEquals(2, report.totalCheckpoints());
    assertEquals(2, report.validCheckpoints());
    assertTrue(report.intact());
    assertEquals("OK", report.status());
  }

  // -----------------------------------------------------------------------
  // Violation: entries_digest mismatch (entry added/removed after checkpoint)
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void verifyChain_digestMismatch_detectsViolation() {
    AuditLog originalEntry = buildSignedEntry(1L);
    AuditChainCheckpoint checkpoint = buildGenesisCheckpoint(List.of(originalEntry));

    // Attacker inserts an extra entry into the window after the checkpoint was created
    AuditLog injectedEntry = buildSignedEntry(2L);
    stubAuditLogs(List.of(originalEntry, injectedEntry));

    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of(checkpoint));

    ChainVerificationReport report = verificationService.verifyChain(null, null);

    assertEquals(1, report.invalidCheckpoints());
    assertFalse(report.intact());
    assertEquals("CHAIN_INTEGRITY_VIOLATION_DETECTED", report.status());
    assertTrue(
        report.violations().stream().anyMatch(v -> v.contains("Entries digest mismatch")),
        "Violation description must mention digest mismatch");
  }

  // -----------------------------------------------------------------------
  // Violation: chain link broken (prev_chain_hmac doesn't match predecessor)
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void verifyChain_chainLinkBroken_detectsViolation() {
    AuditLog e1 = buildSignedEntry(1L);
    AuditLog e2 = buildSignedEntry(2L);

    AuditChainCheckpoint cp1 = buildGenesisCheckpoint(List.of(e1));
    AuditChainCheckpoint cp2 = buildLinkedCheckpoint(List.of(e2), cp1.getChainHmac());

    // Attacker replaces cp2's prev_chain_hmac with garbage, breaking the link
    cp2.setPrevChainHmac("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of(cp1, cp2));
    when(auditLogRepository.findAll(any(Specification.class), eq(Sort.by("auditLogId").ascending())))
        .thenReturn(List.of(e1))
        .thenReturn(List.of(e2));

    ChainVerificationReport report = verificationService.verifyChain(null, null);

    assertFalse(report.intact());
    assertEquals("CHAIN_INTEGRITY_VIOLATION_DETECTED", report.status());
    assertTrue(
        report.violations().stream().anyMatch(v -> v.contains("Chain link broken")),
        "Violation description must mention broken chain link");
  }

  // -----------------------------------------------------------------------
  // Violation: chain_hmac of the checkpoint record itself is tampered
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void verifyChain_chainHmacTampered_detectsViolation() {
    AuditLog entry = buildSignedEntry(1L);
    AuditChainCheckpoint checkpoint = buildGenesisCheckpoint(List.of(entry));
    stubAuditLogs(List.of(entry));

    // Attacker replaces the chain_hmac on the stored checkpoint row
    checkpoint.setChainHmac("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of(checkpoint));

    ChainVerificationReport report = verificationService.verifyChain(null, null);

    assertFalse(report.intact());
    assertTrue(
        report.violations().stream().anyMatch(v -> v.contains("Chain HMAC mismatch")),
        "Violation description must mention chain HMAC mismatch");
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private void stubAuditLogs(List<AuditLog> entries) {
    when(auditLogRepository.findAll(any(Specification.class), eq(Sort.by("auditLogId").ascending())))
        .thenReturn(entries);
  }

  /**
   * Builds a genesis checkpoint (no predecessor) for the given entries, using the same HMAC
   * service as the service under test so digests match.
   */
  private AuditChainCheckpoint buildGenesisCheckpoint(List<AuditLog> entries) {
    String entriesDigest = computeEntriesDigest(entries);
    String chainInput = entriesDigest + "|GENESIS";
    String chainHmac = hmacService.computeHmac(chainInput);

    AuditChainCheckpoint cp = new AuditChainCheckpoint();
    cp.setWindowStart(WIN_START);
    cp.setWindowEnd(WIN_END);
    cp.setEntryCount(entries.size());
    cp.setEntriesDigest(entriesDigest);
    cp.setPrevChainHmac(null); // genesis
    cp.setChainHmac(chainHmac);
    return cp;
  }

  /**
   * Builds a subsequent checkpoint linked to its predecessor.
   */
  private AuditChainCheckpoint buildLinkedCheckpoint(List<AuditLog> entries, String prevChainHmac) {
    String entriesDigest = computeEntriesDigest(entries);
    String chainInput = entriesDigest + "|" + prevChainHmac;
    String chainHmac = hmacService.computeHmac(chainInput);

    AuditChainCheckpoint cp = new AuditChainCheckpoint();
    cp.setWindowStart(WIN_END);
    cp.setWindowEnd(WIN_END.plusMinutes(5));
    cp.setEntryCount(entries.size());
    cp.setEntriesDigest(entriesDigest);
    cp.setPrevChainHmac(prevChainHmac);
    cp.setChainHmac(chainHmac);
    return cp;
  }

  /** Replicates the digest logic from {@link AuditChainScheduler} / {@link AuditChainVerificationService}. */
  private String computeEntriesDigest(List<AuditLog> entries) {
    if (entries.isEmpty()) {
      return hmacService.computeHmac("EMPTY_WINDOW");
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) sb.append("|");
      String hmac = entries.get(i).getEntryHmac();
      sb.append(hmac != null ? hmac : "");
    }
    return hmacService.computeHmac(sb.toString());
  }

  private AuditLog buildSignedEntry(Long id) {
    AuditLog entry =
        new AuditLog.Builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress("10.0.0.1")
            .tenantId(1)
            .instanceId("test-instance")
            .build();
    entry.setAuditLogId(id);
    entry.setCreatedAt(OffsetDateTime.of(2026, 2, 19, 10, 1, 0, 0, ZoneOffset.UTC));
    entry.setEntryHmac(hmacService.computeHmac(entry));
    return entry;
  }
}
