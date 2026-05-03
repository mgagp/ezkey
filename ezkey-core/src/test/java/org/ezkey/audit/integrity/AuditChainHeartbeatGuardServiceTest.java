/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditChainHeartbeatGuardServiceTest
 * Description: Heartbeat guard evaluation behaviors without Spring context.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link AuditChainHeartbeatGuardService}.
 *
 * @author Ezkey contributors
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuditChainHeartbeatGuardServiceTest {

  @Mock private AuditChainHeartbeatProperties heartbeatProperties;

  @Mock private AuditChainProperties chainProperties;

  @Mock private AuditChainCheckpointRepository checkpointRepository;

  @Mock private AuditChainIncidentRepository incidentRepository;

  @Mock private AlertService alertService;

  private AuditChainHeartbeatGuardService guard;

  @BeforeEach
  void setUp() {
    guard =
        new AuditChainHeartbeatGuardService(
            heartbeatProperties,
            chainProperties,
            checkpointRepository,
            incidentRepository,
            alertService);
  }

  /**
   * Replays {@link PostConstruct}-style diagnostics after stubbing properties (mocks otherwise
   * return primitive defaults unrelated to Docker reality).
   */
  private void replayStartupDiagnosticsWithBaselineThresholds() {
    org.mockito.Mockito.lenient().when(chainProperties.getWindowMinutes()).thenReturn(5);
    org.mockito.Mockito.lenient().when(heartbeatProperties.getGraceWindows()).thenReturn(2);
    org.mockito.Mockito.lenient()
        .when(heartbeatProperties.getStopBeforeNextWindow())
        .thenReturn(Duration.ofMinutes(1));
    ReflectionTestUtils.invokeMethod(guard, "configureHeartbeatDiagnostics");
  }

  @Test
  void thresholds_defaultFiveMinuteWindow_graceWindowsTwo_doesNotCollapseUnsupervisedCushion() {
    assertFalse(
        AuditChainHeartbeatGuardService.heartbeatThresholdsCollapseUnsupervisedWindow(5, 2, 1));
  }

  @Test
  void thresholds_graceWindowsOne_collapsesUnsupervisedCushion() {
    assertTrue(
        AuditChainHeartbeatGuardService.heartbeatThresholdsCollapseUnsupervisedWindow(5, 1, 1));
  }

  @Test
  void evaluate_withCheckpointSevenMinutesBehindUtc_isUnsupervisedNotFailClosed() {
    when(heartbeatProperties.isEnabled()).thenReturn(true);
    when(chainProperties.getWindowMinutes()).thenReturn(5);
    when(heartbeatProperties.getGraceWindows()).thenReturn(2);
    when(heartbeatProperties.getStopBeforeNextWindow()).thenReturn(Duration.ofMinutes(1));
    when(heartbeatProperties.getCacheTtl()).thenReturn(Duration.ofSeconds(5));

    AuditChainCheckpoint cp = mock(AuditChainCheckpoint.class);
    when(cp.getCheckpointId()).thenReturn(40L);
    when(cp.getWindowEnd()).thenReturn(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(7));
    when(checkpointRepository.findLatest()).thenReturn(Optional.of(cp));

    replayStartupDiagnosticsWithBaselineThresholds();

    AuditChainHeartbeatEvaluation ev = guard.evaluate();
    assertEquals(AuditChainHeartbeatPhase.UNSUPERVISED_ACTIVITY, ev.phase());
    assertFalse(ev.peripheralFailClosed());

    guard.evaluate();

    verify(alertService, never()).raiseOrTouch(any(), any(), any(), any());
    verify(incidentRepository, never()).save(any());
  }

  @Test
  void evaluate_withVeryStaleCheckpoint_isDegradedServiceAndFailClosed() {
    when(heartbeatProperties.isEnabled()).thenReturn(true);
    when(chainProperties.getWindowMinutes()).thenReturn(5);
    when(heartbeatProperties.getGraceWindows()).thenReturn(2);
    when(heartbeatProperties.getStopBeforeNextWindow()).thenReturn(Duration.ofMinutes(1));
    when(heartbeatProperties.getCacheTtl()).thenReturn(Duration.ofSeconds(5));

    AuditChainCheckpoint cp = mock(AuditChainCheckpoint.class);
    when(cp.getCheckpointId()).thenReturn(42L);
    when(cp.getWindowEnd()).thenReturn(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
    when(checkpointRepository.findLatest()).thenReturn(Optional.of(cp));

    replayStartupDiagnosticsWithBaselineThresholds();

    AuditChainHeartbeatEvaluation ev = guard.evaluate();

    assertEquals(AuditChainHeartbeatPhase.DEGRADED_SERVICE, ev.phase());
    assertTrue(ev.peripheralFailClosed());

    verify(alertService)
        .raiseOrTouch(
            eq(AlertType.AUDIT_CHAIN_HEARTBEAT_STALE),
            eq(AlertSeverity.WARNING),
            eq(AuditChainHeartbeatGuardService.HEARTBEAT_STALE_ALERT_DEDUPE_KEY),
            anyString());
  }

  @Test
  void evaluate_whenHeartbeatDisabled_returnsOkPhaseWithoutHittingRepositories() {
    when(heartbeatProperties.isEnabled()).thenReturn(false);
    replayStartupDiagnosticsWithBaselineThresholds();

    AuditChainHeartbeatEvaluation ev = guard.evaluate();

    assertEquals(AuditChainHeartbeatPhase.OK, ev.phase());
    assertFalse(ev.peripheralFailClosed());
    verifyNoInteractions(checkpointRepository);
  }

  @Test
  void shouldFailClosedPeripheralWrites_whenRequiredDisabled_returnsFalseWithoutEvaluateDb() {
    when(heartbeatProperties.isEnabled()).thenReturn(true);
    when(heartbeatProperties.isRequired()).thenReturn(false);
    replayStartupDiagnosticsWithBaselineThresholds();

    assertFalse(guard.shouldFailClosedPeripheralWrites());
    verifyNoInteractions(checkpointRepository);
  }
}
