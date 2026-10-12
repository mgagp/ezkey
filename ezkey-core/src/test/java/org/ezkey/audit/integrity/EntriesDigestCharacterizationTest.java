/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
 * RED-first characterization of {@code computeEntriesDigest} (Integrity cut 3 D5 / F7).
 *
 * <p>Oracle = writer algorithm in {@link AuditChainScheduler#createCheckpoint} (identical
 * concatenation of {@code entry_hmac} values, null → {@code ""}, empty window → {@code
 * EMPTY_WINDOW}). Verifier must stay equivalent after the {@code entryHmac}-only projection lands.
 * Archived / GAP / MANIPULATION_CONCILIATION checkpoints never call {@code computeEntriesDigest}
 * and are intentionally out of scope here.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EntriesDigestCharacterizationTest {

  private static final String EMPTY_WINDOW_MARKER = "EMPTY_WINDOW";
  private static final String FIELD_SEPARATOR = "|";
  private static final String GENESIS_MARKER = "GENESIS";

  private static final OffsetDateTime WIN_START =
      OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime WIN_END =
      OffsetDateTime.of(2026, 2, 19, 10, 5, 0, 0, ZoneOffset.UTC);

  @Mock private AuditChainCheckpointRepository checkpointRepository;
  @Mock private AuditLogRepository auditLogRepository;

  private AuditHmacService hmacService;
  private AuditChainVerificationService verificationService;

  @BeforeEach
  void setUp() {
    hmacService = TestHmacServiceFactory.create();
    verificationService =
        new AuditChainVerificationService(checkpointRepository, auditLogRepository, hmacService);
  }

  @Test
  void emptyWindow_digestMatchesWriter_verifyOk() {
    List<AuditLog> inWindow = List.of();
    AuditChainCheckpoint cp = buildGenesisFromWriter(inWindow);
    stubWindow(cp, inWindow);

    ChainVerificationReport report = verificationService.verifyChain(WIN_START, WIN_END);

    assertEquals("OK", report.status());
    assertEquals(0, report.invalidCheckpoints());
  }

  @Test
  void singleEntry_digestMatchesWriter_verifyOk() {
    AuditLog entry = signedEntry(1L, WIN_START.plusMinutes(1), "hmac-a");
    List<AuditLog> inWindow = List.of(entry);
    AuditChainCheckpoint cp = buildGenesisFromWriter(inWindow);
    stubWindow(cp, inWindow);

    ChainVerificationReport report = verificationService.verifyChain(WIN_START, WIN_END);

    assertEquals("OK", report.status());
  }

  @Test
  void multiEntry_digestMatchesWriter_orderedByAuditLogId() {
    AuditLog e1 = signedEntry(10L, WIN_START.plusMinutes(1), "hmac-10");
    AuditLog e2 = signedEntry(20L, WIN_START.plusMinutes(2), "hmac-20");
    AuditLog e3 = signedEntry(30L, WIN_START.plusMinutes(3), "hmac-30");
    List<AuditLog> inWindow = List.of(e1, e2, e3);
    AuditChainCheckpoint cp = buildGenesisFromWriter(inWindow);
    stubWindow(cp, inWindow);

    ChainVerificationReport report = verificationService.verifyChain(WIN_START, WIN_END);

    assertEquals("OK", report.status());
  }

  @Test
  void createdAtTies_digestUsesAuditLogIdAscending() {
    OffsetDateTime sameInstant = WIN_START.plusMinutes(2);
    // Same createdAt; writer sorts by auditLogId ascending — digest must follow that order.
    AuditLog laterId = signedEntry(200L, sameInstant, "hmac-200");
    AuditLog earlierId = signedEntry(100L, sameInstant, "hmac-100");
    List<AuditLog> unsorted = List.of(laterId, earlierId);
    List<AuditLog> ordered =
        unsorted.stream().sorted(Comparator.comparing(AuditLog::getAuditLogId)).toList();
    AuditChainCheckpoint cp = buildGenesisFromWriter(ordered);
    stubWindow(cp, ordered);

    ChainVerificationReport report = verificationService.verifyChain(WIN_START, WIN_END);

    assertEquals("OK", report.status());
    assertEquals(writerEntriesDigest(List.of(earlierId, laterId)), writerEntriesDigest(ordered));
  }

  @Test
  void nullEntryHmac_treatedAsEmptyString_matchesWriter() {
    AuditLog withNull = signedEntry(1L, WIN_START.plusMinutes(1), null);
    AuditLog withValue = signedEntry(2L, WIN_START.plusMinutes(2), "hmac-b");
    List<AuditLog> inWindow = List.of(withNull, withValue);
    AuditChainCheckpoint cp = buildGenesisFromWriter(inWindow);
    stubWindow(cp, inWindow);

    ChainVerificationReport report = verificationService.verifyChain(WIN_START, WIN_END);

    assertEquals("OK", report.status());
    assertEquals(
        hmacService.computeHmac(FIELD_SEPARATOR + "hmac-b"), writerEntriesDigest(inWindow));
  }

  @Test
  void halfOpenBoundary_entryAtWindowEndExcludedFromDigest() {
    AuditLog inside = signedEntry(1L, WIN_START, "hmac-inside");
    AuditLog atEnd = signedEntry(2L, WIN_END, "hmac-at-end");
    // Half-open [windowStart, windowEnd): repository returns only entries with createdAt < end.
    List<AuditLog> inWindow = List.of(inside);
    AuditChainCheckpoint cp = buildGenesisFromWriter(inWindow);
    stubWindow(cp, inWindow);

    ChainVerificationReport report = verificationService.verifyChain(WIN_START, WIN_END);

    assertEquals("OK", report.status());
    // Including the boundary entry would change the digest — proves exclusion contract.
    assertFalse(writerEntriesDigest(List.of(inside, atEnd)).equals(writerEntriesDigest(inWindow)));
  }

  @Test
  void tamperedEntryHmac_yieldsChainIntegrityViolationDetected() {
    AuditLog original = signedEntry(1L, WIN_START.plusMinutes(1), "hmac-original");
    AuditChainCheckpoint cp = buildGenesisFromWriter(List.of(original));

    AuditLog tampered = signedEntry(1L, WIN_START.plusMinutes(1), "hmac-TAMPERED");
    stubWindow(cp, List.of(tampered));

    ChainVerificationReport report = verificationService.verifyChain(WIN_START, WIN_END);

    assertEquals("CHAIN_INTEGRITY_VIOLATION_DETECTED", report.status());
    assertEquals(1, report.invalidCheckpoints());
    assertFalse(report.intact());
  }

  // -----------------------------------------------------------------------
  // Writer oracle (AuditChainScheduler.createCheckpoint digest loop)
  // -----------------------------------------------------------------------

  /**
   * Replicates {@link AuditChainScheduler} entries_digest: empty → HMAC({@code EMPTY_WINDOW}); else
   * HMAC of {@code entry_hmac} values joined by {@code |}, null hmac → {@code ""}.
   */
  private String writerEntriesDigest(List<AuditLog> entries) {
    if (entries.isEmpty()) {
      return hmacService.computeHmac(EMPTY_WINDOW_MARKER);
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < entries.size(); i++) {
      if (i > 0) {
        sb.append(FIELD_SEPARATOR);
      }
      String hmac = entries.get(i).getEntryHmac();
      sb.append(hmac != null ? hmac : "");
    }
    return hmacService.computeHmac(sb.toString());
  }

  private AuditChainCheckpoint buildGenesisFromWriter(List<AuditLog> entries) {
    String entriesDigest = writerEntriesDigest(entries);
    String chainInput = entriesDigest + FIELD_SEPARATOR + GENESIS_MARKER;
    String chainHmac = hmacService.computeHmac(chainInput);

    AuditChainCheckpoint cp = new AuditChainCheckpoint();
    cp.setWindowStart(WIN_START);
    cp.setWindowEnd(WIN_END);
    cp.setEntryCount(entries.size());
    cp.setEntriesDigest(entriesDigest);
    cp.setPrevChainHmac(null);
    cp.setChainHmac(chainHmac);
    cp.setCheckpointType("REGULAR");
    return cp;
  }

  @SuppressWarnings("unchecked")
  private void stubWindow(AuditChainCheckpoint cp, List<AuditLog> inWindow) {
    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(List.of(cp));
    when(checkpointRepository.findEarliest()).thenReturn(Optional.of(cp));
    when(checkpointRepository.findLatest()).thenReturn(Optional.of(cp));
    when(auditLogRepository.findAll(
            any(Specification.class), eq(Sort.by("auditLogId").ascending())))
        .thenReturn(new ArrayList<>(inWindow));
  }

  private AuditLog signedEntry(Long id, OffsetDateTime createdAt, String entryHmac) {
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
    entry.setCreatedAt(createdAt);
    entry.setEntryHmac(entryHmac);
    return entry;
  }
}
