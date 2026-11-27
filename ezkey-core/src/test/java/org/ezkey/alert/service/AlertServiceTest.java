/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AlertServiceTest
 * Description: Unit tests for the minimal alert subsystem entry point.
 */

package org.ezkey.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertStatus;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.domain.entity.Alert;
import org.ezkey.alert.repository.AlertRepository;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AlertService}.
 *
 * <p>Covers the three behavioural rules that operators rely on:
 *
 * <ul>
 *   <li>First raise → INSERT + ALERT_RAISED audit entry
 *   <li>Subsequent raise (same dedupeKey, OPEN) → touch only, no extra audit entry
 *   <li>Resolve → UPDATE + ALERT_RESOLVED audit entry; missing/closed alert is a no-op
 * </ul>
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

  @Mock private AlertRepository alertRepository;
  @Mock private AuditLogService auditLogService;

  private AlertService alertService;

  @BeforeEach
  void setUp() {
    alertService = new AlertService(alertRepository, auditLogService);
  }

  @Test
  void raiseOrTouch_whenNoOpenRow_insertsAndEmitsRaisedAudit() {
    when(alertRepository.findByDedupeKeyAndStatus("AUDIT_CHAIN_GAP_PENDING:42", AlertStatus.OPEN))
        .thenReturn(Optional.empty());
    when(alertRepository.insertOpenIfAbsent(
            eq("AUDIT_CHAIN_GAP_PENDING"),
            eq("WARNING"),
            eq("AUDIT_CHAIN_GAP_PENDING:42"),
            eq("{\"anchorCheckpointId\":42}"),
            any(OffsetDateTime.class)))
        .thenReturn(Optional.of(101L));
    Alert inserted = new Alert();
    inserted.setAlertId(101L);
    inserted.setAlertType(AlertType.AUDIT_CHAIN_GAP_PENDING);
    inserted.setSeverity(AlertSeverity.WARNING);
    inserted.setStatus(AlertStatus.OPEN);
    inserted.setDedupeKey("AUDIT_CHAIN_GAP_PENDING:42");
    inserted.setOccurrenceCount(1);
    when(alertRepository.findById(101L)).thenReturn(Optional.of(inserted));

    Alert result =
        alertService.raiseOrTouch(
            AlertType.AUDIT_CHAIN_GAP_PENDING,
            AlertSeverity.WARNING,
            "AUDIT_CHAIN_GAP_PENDING:42",
            "{\"anchorCheckpointId\":42}");

    assertThat(result.getAlertId()).isEqualTo(101L);
    assertThat(result.getStatus()).isEqualTo(AlertStatus.OPEN);
    assertThat(result.getOccurrenceCount()).isEqualTo(1);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getEventType()).isEqualTo(EventType.ALERT_RAISED);
    verify(alertRepository, never()).save(any(Alert.class));
  }

  @Test
  void raiseOrTouch_whenOpenRowExists_touchesAndDoesNotEmitAudit() {
    Alert existing = new Alert();
    existing.setAlertId(7L);
    existing.setAlertType(AlertType.AUDIT_CHAIN_GAP_PENDING);
    existing.setSeverity(AlertSeverity.WARNING);
    existing.setStatus(AlertStatus.OPEN);
    existing.setDedupeKey("AUDIT_CHAIN_GAP_PENDING:7");
    existing.setOccurrenceCount(3);

    when(alertRepository.findByDedupeKeyAndStatus("AUDIT_CHAIN_GAP_PENDING:7", AlertStatus.OPEN))
        .thenReturn(Optional.of(existing));
    when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

    Alert result =
        alertService.raiseOrTouch(
            AlertType.AUDIT_CHAIN_GAP_PENDING,
            AlertSeverity.WARNING,
            "AUDIT_CHAIN_GAP_PENDING:7",
            "{\"anchorCheckpointId\":7}");

    assertThat(result.getAlertId()).isEqualTo(7L);
    assertThat(result.getOccurrenceCount()).isEqualTo(4);
    assertThat(result.getLastSeenAt()).isNotNull();
    verify(alertRepository, never())
        .insertOpenIfAbsent(any(), any(), any(), any(), any(OffsetDateTime.class));
    verify(auditLogService, never()).log(any(AuditLog.class));
  }

  @Test
  void resolveByDedupeKey_whenOpenRowExists_setsResolvedAndEmitsAudit() {
    Alert existing = new Alert();
    existing.setAlertId(11L);
    existing.setAlertType(AlertType.AUDIT_CHAIN_GAP_PENDING);
    existing.setSeverity(AlertSeverity.WARNING);
    existing.setStatus(AlertStatus.OPEN);
    existing.setDedupeKey("AUDIT_CHAIN_GAP_PENDING:11");

    when(alertRepository.findByDedupeKeyAndStatus("AUDIT_CHAIN_GAP_PENDING:11", AlertStatus.OPEN))
        .thenReturn(Optional.of(existing));
    when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

    Optional<Alert> result =
        alertService.resolveByDedupeKey(
            "AUDIT_CHAIN_GAP_PENDING:11", AlertResolutionReason.GAP_DECLARED, null);

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(AlertStatus.RESOLVED);
    assertThat(result.get().getResolutionReason()).isEqualTo(AlertResolutionReason.GAP_DECLARED);
    assertThat(result.get().getResolvedAt()).isNotNull();

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getEventType()).isEqualTo(EventType.ALERT_RESOLVED);
  }

  @Test
  void resolveByDedupeKey_whenNoOpenRow_isNoOp() {
    when(alertRepository.findByDedupeKeyAndStatus("missing", AlertStatus.OPEN))
        .thenReturn(Optional.empty());

    Optional<Alert> result =
        alertService.resolveByDedupeKey("missing", AlertResolutionReason.MANUAL, 42);

    assertThat(result).isEmpty();
    verify(alertRepository, never()).save(any(Alert.class));
    verify(auditLogService, never()).log(any(AuditLog.class));
  }

  @Test
  void resolveOpenById_whenOpenRowExists_setsResolvedAndStoresAuditReason() {
    Alert existing = new Alert();
    existing.setAlertId(41L);
    existing.setAlertType(AlertType.AUTH_ATTEMPT_REJECTED);
    existing.setSeverity(AlertSeverity.WARNING);
    existing.setStatus(AlertStatus.OPEN);
    existing.setDedupeKey("AUTH_ATTEMPT_REJECTED:41");

    when(alertRepository.findByAlertIdAndStatus(41L, AlertStatus.OPEN))
        .thenReturn(Optional.of(existing));
    when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));

    Optional<Alert> result =
        alertService.resolveOpenById(
            41L, AlertResolutionReason.MANUAL, 7, "Operator confirmed follow-up complete");

    assertThat(result).isPresent();
    assertThat(result.get().getStatus()).isEqualTo(AlertStatus.RESOLVED);
    assertThat(result.get().getResolutionReason()).isEqualTo(AlertResolutionReason.MANUAL);
    assertThat(result.get().getResolvedByAdminId()).isEqualTo(7);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(auditCaptor.capture());
    assertThat(auditCaptor.getValue().getEventType()).isEqualTo(EventType.ALERT_RESOLVED);
    assertThat(auditCaptor.getValue().getReason())
        .isEqualTo("Operator confirmed follow-up complete");
  }

  @Test
  void resolveOpenById_whenNoOpenRow_isNoOp() {
    when(alertRepository.findByAlertIdAndStatus(51L, AlertStatus.OPEN))
        .thenReturn(Optional.empty());

    Optional<Alert> result =
        alertService.resolveOpenById(51L, AlertResolutionReason.MANUAL, 3, "note");

    assertThat(result).isEmpty();
    verify(alertRepository, never()).save(any(Alert.class));
    verify(auditLogService, never()).log(any(AuditLog.class));
  }
}
