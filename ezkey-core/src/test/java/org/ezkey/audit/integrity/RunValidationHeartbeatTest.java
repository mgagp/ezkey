/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Heartbeat plumbing for RUN_VALIDATION (Integrity cut 3 F5).
 *
 * <p>Asserts real verifyChain cadence (every 10 checkpoints) and that {@link
 * RetroactiveIntegrityValidationService#runValidation} forwards the callback.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class RunValidationHeartbeatTest {

  private static final OffsetDateTime BASE =
      OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC);

  @Mock private AuditChainCheckpointRepository checkpointRepository;
  @Mock private AuditLogRepository auditLogRepository;
  @Mock private NightlyIntegrityProperties nightlyProperties;
  @Mock private AuditChainVerificationService chainVerificationServiceMock;
  @Mock private EntryIntegrityViolationClassifier entryIntegrityViolationClassifier;
  @Mock private AlertService alertService;
  @Mock private AlertRepository alertRepository;
  @Mock private AuditLogService auditLogService;

  private final IntegrityWindowCapProperties windowCapProperties =
      new IntegrityWindowCapProperties();
  private AuditHmacService hmacService;
  private AuditChainVerificationService realChainVerificationService;

  @BeforeEach
  void setUp() {
    hmacService = TestHmacServiceFactory.create();
    realChainVerificationService =
        new AuditChainVerificationService(checkpointRepository, auditLogRepository, hmacService);
  }

  @Test
  void verifyChain_heartbeatCadence_everyTenCheckpoints() {
    List<AuditChainCheckpoint> checkpoints = buildLinkedRegularCheckpoints(25);
    when(checkpointRepository.findByWindowRange(any(), any())).thenReturn(checkpoints);
    when(checkpointRepository.findEarliest()).thenReturn(Optional.of(checkpoints.get(0)));
    when(checkpointRepository.findLatest())
        .thenReturn(Optional.of(checkpoints.get(checkpoints.size() - 1)));
    when(auditLogRepository.findEntryHmacsInWindow(any(), any())).thenReturn(List.of());

    AtomicInteger heartbeats = new AtomicInteger();
    realChainVerificationService.verifyChain(
        BASE, BASE.plusMinutes(5 * 25), heartbeats::incrementAndGet);

    // i==0, (i+1)%10==0 → 9,19, and last i==24 → 4 invocations
    assertEquals(4, heartbeats.get());
  }

  @Test
  void runValidation_forwardsHeartbeat_toEntryScanAndVerifyChain() {
    when(nightlyProperties.isEnabled()).thenReturn(true);
    when(entryIntegrityViolationClassifier.collectRangeViolations(
            eq(auditLogRepository), any(), any(), any()))
        .thenAnswer(
            inv -> {
              Runnable hb = inv.getArgument(3);
              if (hb != null) {
                hb.run();
              }
              return new EntryIntegrityViolationClassifier.ViolationCollection(
                  List.of(), List.of());
            });
    when(chainVerificationServiceMock.verifyChain(any(), any(), any()))
        .thenAnswer(
            inv -> {
              Runnable hb = inv.getArgument(2);
              if (hb != null) {
                hb.run();
                hb.run();
              }
              return new AuditChainVerificationService.ChainVerificationReport(
                  0, 0, 0, 0, 0, List.of(), List.of(), List.of(), null, null, null, null, true,
                  true, "OK");
            });

    RetroactiveIntegrityValidationService validationService =
        new RetroactiveIntegrityValidationService(
            nightlyProperties,
            windowCapProperties,
            chainVerificationServiceMock,
            hmacService,
            auditLogRepository,
            alertService,
            alertRepository,
            auditLogService,
            entryIntegrityViolationClassifier);

    AtomicInteger heartbeats = new AtomicInteger();
    validationService.runValidation(
        BASE,
        BASE.plusHours(1),
        RetroactiveIntegrityValidationOptions.operator(false, 1),
        heartbeats::incrementAndGet);

    // 1 from entry scan stub + 2 from verifyChain stub
    assertEquals(3, heartbeats.get());
    verify(entryIntegrityViolationClassifier)
        .collectRangeViolations(eq(auditLogRepository), any(), any(), any());
    verify(chainVerificationServiceMock).verifyChain(any(), any(), any());
  }

  private List<AuditChainCheckpoint> buildLinkedRegularCheckpoints(int count) {
    List<AuditChainCheckpoint> list = new ArrayList<>();
    String prev = null;
    for (int i = 0; i < count; i++) {
      OffsetDateTime start = BASE.plusMinutes(5L * i);
      OffsetDateTime end = start.plusMinutes(5);
      String entriesDigest = hmacService.computeHmac("EMPTY_WINDOW");
      String chainInput = entriesDigest + "|" + (prev != null ? prev : "GENESIS");
      String chainHmac = hmacService.computeHmac(chainInput);
      AuditChainCheckpoint cp = new AuditChainCheckpoint();
      cp.setCheckpointId((long) (i + 1));
      cp.setWindowStart(start);
      cp.setWindowEnd(end);
      cp.setEntryCount(0);
      cp.setEntriesDigest(entriesDigest);
      cp.setPrevChainHmac(prev);
      cp.setChainHmac(chainHmac);
      cp.setCheckpointType("REGULAR");
      list.add(cp);
      prev = chainHmac;
    }
    return list;
  }
}
