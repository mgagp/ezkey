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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.ezkey.alert.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

  @Test
  void evaluate_whenHeartbeatDisabled_returnsOkPhaseWithoutHittingRepositories() {
    when(heartbeatProperties.isEnabled()).thenReturn(false);

    AuditChainHeartbeatEvaluation ev = guard.evaluate();

    assertEquals(AuditChainHeartbeatPhase.OK, ev.phase());
    assertFalse(ev.peripheralFailClosed());
    verifyNoInteractions(checkpointRepository);
  }

  @Test
  void shouldFailClosedPeripheralWrites_whenRequiredDisabled_returnsFalseWithoutEvaluateDb() {
    when(heartbeatProperties.isEnabled()).thenReturn(true);
    when(heartbeatProperties.isRequired()).thenReturn(false);

    assertFalse(guard.shouldFailClosedPeripheralWrites());
    verifyNoInteractions(checkpointRepository);
  }
}
