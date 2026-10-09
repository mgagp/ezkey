/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: NightlyIntegrityValidationSchedulerTest
 * Description: Grid-aligned window end + heavy-crypto gate busy retry / give-up.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

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
  @Mock private IntegrityHeavyCryptoGate heavyCryptoGate;
  @Mock private IntegrityAsyncJobService integrityAsyncJobService;
  @Mock private ObjectProvider<IntegrityAsyncJobService> integrityAsyncJobServiceProvider;

  private NightlyIntegrityValidationScheduler scheduler;

  @BeforeEach
  void setUp() {
    when(integrityAsyncJobServiceProvider.getIfAvailable()).thenReturn(integrityAsyncJobService);
    scheduler =
        new NightlyIntegrityValidationScheduler(
            nightlyProperties,
            chainProperties,
            validationService,
            jobLastRunService,
            heavyCryptoGate,
            integrityAsyncJobServiceProvider);
  }

  @Test
  void runNightlyValidation_passesGridAlignedWindowEnd() {
    when(chainProperties.getWindowMinutes()).thenReturn(5);
    when(nightlyProperties.getWindowHours()).thenReturn(24);
    when(integrityAsyncJobService.isOperatorSlotRunning()).thenReturn(false);
    when(heavyCryptoGate.tryEnter()).thenReturn(true);
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
                  long deltaSeconds = Math.abs(ChronoUnit.SECONDS.between(windowEnd, expected));
                  return deltaSeconds < 300
                      && windowEnd.getSecond() == 0
                      && windowEnd.getNano() == 0
                      && windowEnd.getMinute() % 5 == 0;
                }));
    verify(heavyCryptoGate).exit();
  }

  @Test
  @DisplayName("gate busy then free: retries then succeeds without recordFailure")
  void runNightlyValidation_gateBusyThenFree_retriesThenSuccess() {
    when(chainProperties.getWindowMinutes()).thenReturn(5);
    when(nightlyProperties.getWindowHours()).thenReturn(24);
    when(integrityAsyncJobService.isOperatorSlotRunning()).thenReturn(false);
    when(heavyCryptoGate.tryEnter()).thenReturn(false, false, true);
    when(validationService.validateWindow(any()))
        .thenReturn(
            RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult.skipped(
                "ok", "unused", RetroactiveIntegrityValidationTriggerSource.SCHEDULED));

    AtomicInteger sleeps = new AtomicInteger();
    scheduler.configureGateBusyRetryForTests(
        Duration.ofMillis(1), Duration.ofMillis(10), duration -> sleeps.incrementAndGet());

    scheduler.runNightlyValidation();

    verify(heavyCryptoGate, times(3)).tryEnter();
    verify(validationService).validateWindow(any());
    verify(jobLastRunService)
        .recordSuccess(eq(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION), any());
    verify(jobLastRunService, never())
        .recordFailure(eq(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION), any(), any());
    verify(heavyCryptoGate).exit();
    assertEquals(2, sleeps.get());
  }

  @Test
  @DisplayName("gate busy for full budget: WARN path records fail-closed skip")
  void runNightlyValidation_gateBusyExhausted_recordsFailure() {
    when(chainProperties.getWindowMinutes()).thenReturn(5);
    when(nightlyProperties.getWindowHours()).thenReturn(24);
    when(integrityAsyncJobService.isOperatorSlotRunning()).thenReturn(false);
    when(heavyCryptoGate.tryEnter()).thenReturn(false);

    scheduler.configureGateBusyRetryForTests(
        Duration.ofMillis(1), Duration.ofMillis(3), duration -> {});

    scheduler.runNightlyValidation();

    verify(heavyCryptoGate, atLeast(2)).tryEnter();
    verify(validationService, never()).validateWindow(any());
    verify(jobLastRunService)
        .recordFailure(
            eq(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION),
            any(),
            eq(NightlyIntegrityValidationScheduler.GATE_BUSY_SKIP_SUMMARY));
    verify(heavyCryptoGate, never()).exit();
  }
}
