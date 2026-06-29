/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ScheduledJobLastRunServiceTest
 * Description: Unit tests for scheduled job registry updates.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ScheduledJobLastRunService}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class ScheduledJobLastRunServiceTest {

  @Mock private ScheduledJobLastRunRepository repository;

  private ScheduledJobLastRunService service;

  @BeforeEach
  void setUp() {
    service = new ScheduledJobLastRunService(repository);
  }

  @Test
  void recordSuccess_updatesRow() {
    ScheduledJobLastRun row = new ScheduledJobLastRun();
    row.setJobKey(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION);
    row.setLastStatus(ScheduledJobLastRunStatus.NEVER_RUN);
    when(repository.findById(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION))
        .thenReturn(Optional.of(row));

    service.recordSuccess(
        ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION, "Validated 24 h ending 2026-06-28T02:00Z");

    ArgumentCaptor<ScheduledJobLastRun> captor = ArgumentCaptor.forClass(ScheduledJobLastRun.class);
    verify(repository).save(captor.capture());
    ScheduledJobLastRun saved = captor.getValue();
    assertEquals(ScheduledJobLastRunStatus.SUCCESS, saved.getLastStatus());
    assertEquals("Validated 24 h ending 2026-06-28T02:00Z", saved.getLastRunScope());
    assertNull(saved.getLastErrorSummary());
  }

  @Test
  void recordFailure_truncatesErrorSummary() {
    ScheduledJobLastRun row = new ScheduledJobLastRun();
    row.setJobKey(ScheduledJobKey.AUDIT_CHAIN_CHECKPOINT);
    row.setLastStatus(ScheduledJobLastRunStatus.NEVER_RUN);
    when(repository.findById(ScheduledJobKey.AUDIT_CHAIN_CHECKPOINT)).thenReturn(Optional.of(row));

    String longError = "x".repeat(600);
    service.recordFailure(ScheduledJobKey.AUDIT_CHAIN_CHECKPOINT, "Lookback 60 min", longError);

    ArgumentCaptor<ScheduledJobLastRun> captor = ArgumentCaptor.forClass(ScheduledJobLastRun.class);
    verify(repository).save(captor.capture());
    ScheduledJobLastRun saved = captor.getValue();
    assertEquals(ScheduledJobLastRunStatus.FAILED, saved.getLastStatus());
    assertEquals(512, saved.getLastErrorSummary().length());
  }
}
