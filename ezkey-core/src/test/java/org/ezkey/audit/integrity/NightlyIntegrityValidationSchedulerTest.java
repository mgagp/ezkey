/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: NightlyIntegrityValidationSchedulerTest
 * Description: Ensures scheduled window end is aligned to the checkpoint grid (ADR-0008).
 */

package org.ezkey.audit.integrity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link NightlyIntegrityValidationScheduler}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class NightlyIntegrityValidationSchedulerTest {

  @Mock private NightlyIntegrityProperties nightlyProperties;
  @Mock private AuditChainProperties chainProperties;
  @Mock private RetroactiveIntegrityValidationService validationService;
  @Mock private ScheduledJobLastRunService jobLastRunService;

  private NightlyIntegrityValidationScheduler scheduler;

  @BeforeEach
  void setUp() {
    scheduler =
        new NightlyIntegrityValidationScheduler(
            nightlyProperties, chainProperties, validationService, jobLastRunService);
  }

  @Test
  void runNightlyValidation_passesGridAlignedWindowEnd() {
    when(chainProperties.getWindowMinutes()).thenReturn(5);
    when(nightlyProperties.getWindowHours()).thenReturn(24);
    when(validationService.validateWindow(any()))
        .thenReturn(
            RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult.skipped(
                "test", "unused", RetroactiveIntegrityValidationTriggerSource.SCHEDULED));

    scheduler.runNightlyValidation();

    verify(validationService)
        .validateWindow(
            argThat(
                windowEnd -> {
                  OffsetDateTime expected =
                      AuditChainScheduler.roundDownToWindow(OffsetDateTime.now(ZoneOffset.UTC), 5);
                  // Allow one window of clock skew between arrange and assert
                  long deltaSeconds = Math.abs(ChronoUnit.SECONDS.between(windowEnd, expected));
                  return deltaSeconds < 300
                      && windowEnd.getSecond() == 0
                      && windowEnd.getNano() == 0
                      && windowEnd.getMinute() % 5 == 0;
                }));
  }
}
