/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: NightlyIntegrityValidationServiceTest
 * Description: Unit tests for nightly retroactive integrity orchestration.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.EntryIntegrityViolation;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link NightlyIntegrityValidationService}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class NightlyIntegrityValidationServiceTest {

  private static final OffsetDateTime WINDOW_END =
      OffsetDateTime.of(2026, 6, 28, 2, 0, 0, 0, ZoneOffset.UTC);

  @Mock private NightlyIntegrityProperties nightlyProperties;
  @Mock private AuditChainVerificationService chainVerificationService;
  @Mock private AuditHmacService auditHmacService;
  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AlertService alertService;
  @Mock private AlertRepository alertRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private EntryIntegrityViolationClassifier entryIntegrityViolationClassifier;

  private NightlyIntegrityValidationService service;

  @BeforeEach
  void setUp() {
    service =
        new NightlyIntegrityValidationService(
            nightlyProperties,
            chainVerificationService,
            auditHmacService,
            auditLogRepository,
            alertService,
            alertRepository,
            auditLogService,
            entryIntegrityViolationClassifier);
    when(nightlyProperties.getWindowHours()).thenReturn(24);
  }

  @Test
  void validateWindow_skipsWhenHmacInactive() {
    when(auditHmacService.isActive()).thenReturn(false);

    NightlyIntegrityValidationService.NightlyIntegrityValidationResult result =
        service.validateWindow(WINDOW_END);

    assertTrue(result.intact());
    assertFalse(result.alertRaised());
    verify(chainVerificationService, never()).verifyChain(any(), any());
    verify(alertService, never()).raiseOrTouch(any(), any(), anyString(), any());
  }

  @Test
  void validateWindow_intactPath_doesNotRaiseAlert() {
    when(auditHmacService.isActive()).thenReturn(true);
    when(entryIntegrityViolationClassifier.collectRangeViolations(
            auditLogRepository, WINDOW_END.minusHours(24), WINDOW_END))
        .thenReturn(
            new EntryIntegrityViolationClassifier.ViolationCollection(List.of(), List.of()));
    when(chainVerificationService.verifyChain(any(), any())).thenReturn(intactReport());

    NightlyIntegrityValidationService.NightlyIntegrityValidationResult result =
        service.validateWindow(WINDOW_END);

    assertTrue(result.intact());
    assertFalse(result.alertRaised());
    verify(alertService, never()).raiseOrTouch(any(), any(), anyString(), any());
    verify(auditLogService).log(any(AuditLog.class));
  }

  @Test
  void validateWindow_entryHmacViolation_raisesIntegrityRuptureAlert() {
    when(auditHmacService.isActive()).thenReturn(true);
    EntryIntegrityViolation violation =
        new EntryIntegrityViolation(42L, "ADMIN_LOGIN", WINDOW_END.minusHours(1), "HMAC_MISMATCH");
    when(entryIntegrityViolationClassifier.collectRangeViolations(
            auditLogRepository, WINDOW_END.minusHours(24), WINDOW_END))
        .thenReturn(
            new EntryIntegrityViolationClassifier.ViolationCollection(
                List.of(violation), List.of(violation)));
    when(chainVerificationService.verifyChain(any(), any())).thenReturn(intactReport());
    Alert saved = new Alert();
    saved.setAlertId(7L);
    when(alertService.raiseOrTouch(
            eq(AlertType.AUDIT_INTEGRITY_RUPTURE),
            eq(AlertSeverity.CRITICAL),
            anyString(),
            anyString()))
        .thenReturn(saved);

    NightlyIntegrityValidationService.NightlyIntegrityValidationResult result =
        service.validateWindow(WINDOW_END);

    assertFalse(result.intact());
    assertTrue(result.alertRaised());
    verify(alertService)
        .raiseOrTouch(
            eq(AlertType.AUDIT_INTEGRITY_RUPTURE),
            eq(AlertSeverity.CRITICAL),
            anyString(),
            anyString());
  }

  @Test
  void validateWindow_c8_6_defersAlertWhenOnlyUndeclaredGapsAndHeartbeatOpen() {
    when(auditHmacService.isActive()).thenReturn(true);
    when(entryIntegrityViolationClassifier.collectRangeViolations(
            auditLogRepository, WINDOW_END.minusHours(24), WINDOW_END))
        .thenReturn(
            new EntryIntegrityViolationClassifier.ViolationCollection(List.of(), List.of()));
    AuditChainVerificationService.UndeclaredGap gap =
        new AuditChainVerificationService.UndeclaredGap(
            WINDOW_END.minusHours(2), WINDOW_END.minusHours(1), 60);
    when(chainVerificationService.verifyChain(any(), any()))
        .thenReturn(gapsOnlyReport(List.of(gap)));
    when(alertRepository.findByDedupeKeyAndStatus(
            AuditChainHeartbeatGuardService.HEARTBEAT_STALE_ALERT_DEDUPE_KEY, AlertStatus.OPEN))
        .thenReturn(Optional.of(new Alert()));

    NightlyIntegrityValidationService.NightlyIntegrityValidationResult result =
        service.validateWindow(WINDOW_END);

    assertFalse(result.intact());
    assertFalse(result.alertRaised());
    verify(alertService, never()).raiseOrTouch(any(), any(), anyString(), any());
  }

  @Test
  void validateWindow_emitsCompletionAuditWithEventType() {
    when(auditHmacService.isActive()).thenReturn(true);
    when(entryIntegrityViolationClassifier.collectRangeViolations(
            auditLogRepository, WINDOW_END.minusHours(24), WINDOW_END))
        .thenReturn(
            new EntryIntegrityViolationClassifier.ViolationCollection(List.of(), List.of()));
    when(chainVerificationService.verifyChain(any(), any())).thenReturn(intactReport());

    service.validateWindow(WINDOW_END);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(captor.capture());
    assertTrue(
        captor.getValue().getEventType() == EventType.NIGHTLY_INTEGRITY_VALIDATION_COMPLETED);
  }

  @Test
  void validateWindow_onlyExplainedEntryViolations_doesNotRaiseAlert() {
    when(auditHmacService.isActive()).thenReturn(true);
    EntryIntegrityViolation explained =
        new EntryIntegrityViolation(42L, "ADMIN_LOGIN", WINDOW_END.minusHours(1), "HMAC_MISMATCH");
    when(entryIntegrityViolationClassifier.collectRangeViolations(
            auditLogRepository, WINDOW_END.minusHours(24), WINDOW_END))
        .thenReturn(
            new EntryIntegrityViolationClassifier.ViolationCollection(
                List.of(explained), List.of()));
    when(chainVerificationService.verifyChain(any(), any())).thenReturn(intactReport());

    NightlyIntegrityValidationService.NightlyIntegrityValidationResult result =
        service.validateWindow(WINDOW_END);

    assertFalse(result.intact());
    assertFalse(result.alertRaised());
    verify(alertService, never()).raiseOrTouch(any(), any(), anyString(), any());
  }

  private static AuditChainVerificationService.ChainVerificationReport intactReport() {
    return new AuditChainVerificationService.ChainVerificationReport(
        1,
        1,
        0,
        0,
        0,
        List.of(),
        List.of(),
        List.of(),
        WINDOW_END.minusHours(24),
        WINDOW_END,
        WINDOW_END.minusHours(24),
        WINDOW_END,
        true,
        true,
        "INTACT");
  }

  private static AuditChainVerificationService.ChainVerificationReport gapsOnlyReport(
      List<AuditChainVerificationService.UndeclaredGap> gaps) {
    return new AuditChainVerificationService.ChainVerificationReport(
        0,
        0,
        0,
        0,
        0,
        List.of("Undeclared gap"),
        List.of(),
        gaps,
        null,
        null,
        WINDOW_END.minusHours(24),
        WINDOW_END,
        false,
        false,
        "UNDECLARED_GAPS");
  }
}
