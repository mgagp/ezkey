/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityAsyncJobServiceTest
 * Description: Unit tests for Integrity async job slot, 409, TTL, abandon rules.
 */

package org.ezkey.audit.asyncjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.IntegrityAsyncJobStartRequest;
import org.ezkey.audit.exception.IntegrityAsyncJobAbandonNotAllowedException;
import org.ezkey.audit.exception.IntegrityAsyncJobBusyException;
import org.ezkey.audit.exception.IntegrityValidationDisabledException;
import org.ezkey.audit.exception.IntegrityWindowOverCapException;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.IntegrityHeavyCryptoGate;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.ezkey.audit.integrity.NightlyIntegrityShedLockProbe;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Unit tests for {@link IntegrityAsyncJobService}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class IntegrityAsyncJobServiceTest {

  @Mock private IntegrityAsyncJobRepository jobRepository;
  @Mock private IntegrityAsyncJobStateService stateService;
  @Mock private IntegrityHeavyCryptoGate heavyCryptoGate;
  @Mock private AuditIntegrityService auditIntegrityService;
  @Mock private AuditChainVerificationService chainVerificationService;
  @Mock private RetroactiveIntegrityValidationService retroactiveIntegrityValidationService;
  @Mock private NightlyIntegrityProperties nightlyIntegrityProperties;
  @Mock private NightlyIntegrityShedLockProbe nightlyShedLockProbe;
  @Mock private AuditLogService auditLogService;

  private final org.ezkey.audit.integrity.IntegrityWindowCapProperties windowCapProperties =
      new org.ezkey.audit.integrity.IntegrityWindowCapProperties();

  private ThreadPoolTaskExecutor executor;
  private IntegrityAsyncJobService service;

  @BeforeEach
  void setUp() {
    executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(1);
    executor.setThreadNamePrefix("test-integrity-async-");
    executor.initialize();
    service =
        new IntegrityAsyncJobService(
            jobRepository,
            stateService,
            heavyCryptoGate,
            auditIntegrityService,
            chainVerificationService,
            retroactiveIntegrityValidationService,
            nightlyIntegrityProperties,
            windowCapProperties,
            nightlyShedLockProbe,
            auditLogService,
            executor);
  }

  @Test
  void start_whenSlotOccupied_throwsBusy() {
    IntegrityAsyncJob running = runningJob();
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.of(running));

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            null);

    IntegrityAsyncJobBusyException ex =
        assertThrows(
            IntegrityAsyncJobBusyException.class, () -> service.start(request, 1, "admin.docker"));
    assertEquals(running.getJobId(), ex.getCurrentJob().getJobId());
    verify(jobRepository, never()).saveAndFlush(any());
  }

  @Test
  void start_verifyChainAtExactCap_accepted() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    AtomicReference<IntegrityAsyncJob> saved = new AtomicReference<>();
    when(jobRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              IntegrityAsyncJob job = inv.getArgument(0);
              saved.set(job);
              return job;
            });

    OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            from,
            from.plus(windowCapProperties.maxWindow()),
            null);

    var accepted = service.start(request, 1, "admin.docker");
    assertEquals(saved.get().getJobId(), accepted.jobId());
    assertEquals(IntegrityAsyncJobStatus.RUNNING, saved.get().getStatus());
  }

  @Test
  void start_verifyChainOverCap_throwsIntegrityWindowOverCapException() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
    when(heavyCryptoGate.isBusy()).thenReturn(false);

    OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            from,
            from.plus(windowCapProperties.maxWindow()).plusSeconds(1),
            null);

    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class, () -> service.start(request, 1, "admin.docker"));
    assertEquals(92, ex.getMaxWindowDays());
    verify(jobRepository, never()).saveAndFlush(any());
  }

  @Test
  void start_runValidationWhenNightlyDisabled_throws() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    when(nightlyIntegrityProperties.isEnabled()).thenReturn(false);

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.RUN_VALIDATION,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            true);

    assertThrows(
        IntegrityValidationDisabledException.class,
        () -> service.start(request, 1, "admin.docker"));
  }

  @Test
  @DisplayName("async RUN_VALIDATION keeps max-window-days: exact 92×24h+1h accepted")
  void start_runValidationAtExactAsyncCap_accepted() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    when(nightlyIntegrityProperties.isEnabled()).thenReturn(true);
    doAnswer(
            inv -> {
              windowCapProperties.validateWindow(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(retroactiveIntegrityValidationService)
        .validateOperatorWindow(any(), any());
    AtomicReference<IntegrityAsyncJob> saved = new AtomicReference<>();
    when(jobRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              IntegrityAsyncJob job = inv.getArgument(0);
              saved.set(job);
              return job;
            });

    OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.RUN_VALIDATION,
            from,
            from.plus(windowCapProperties.maxWindow()),
            true);

    var accepted = service.start(request, 1, "admin.docker");
    assertEquals(saved.get().getJobId(), accepted.jobId());
    assertEquals(IntegrityAsyncJobType.RUN_VALIDATION, saved.get().getJobType());
  }

  @Test
  @DisplayName("async RUN_VALIDATION refuses beyond 92 d with capKind=ASYNC (no asyncAlternative)")
  void start_runValidationOverAsyncCap_throwsIntegrityWindowOverCapException() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    when(nightlyIntegrityProperties.isEnabled()).thenReturn(true);
    doAnswer(
            inv -> {
              windowCapProperties.validateWindow(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(retroactiveIntegrityValidationService)
        .validateOperatorWindow(any(), any());

    OffsetDateTime from = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.RUN_VALIDATION,
            from,
            from.plus(windowCapProperties.maxWindow()).plusSeconds(1),
            true);

    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class, () -> service.start(request, 1, "admin.docker"));
    assertEquals(92, ex.getMaxWindowDays());
    assertEquals(IntegrityWindowOverCapException.CAP_KIND_ASYNC, ex.getCapKind());
    assertEquals(null, ex.getAsyncAlternative());
    verify(jobRepository, never()).saveAndFlush(any());
  }

  @Test
  void start_uniqueViolation_mapsToBusy() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(runningJob()));
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    when(jobRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq"));

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_ENTRY_HMAC_RANGE,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            null);

    assertThrows(
        IntegrityAsyncJobBusyException.class, () -> service.start(request, 1, "admin.docker"));
  }

  @Test
  void abandon_running_refused() {
    IntegrityAsyncJob running = runningJob();
    when(jobRepository.findLatestOperatorJob()).thenReturn(Optional.of(running));

    assertThrows(IntegrityAsyncJobAbandonNotAllowedException.class, () -> service.abandon(1));
  }

  @Test
  void abandon_expired_marksCancelledAndAbandoned() {
    IntegrityAsyncJob expired = runningJob();
    expired.setStatus(IntegrityAsyncJobStatus.EXPIRED);
    expired.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    when(jobRepository.findLatestOperatorJob()).thenReturn(Optional.of(expired));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service.abandon(7);
    assertEquals(IntegrityAsyncJobStatus.CANCELLED, response.status());
    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    assertEquals(7, captor.getValue().getAbandonedByAdminId());
    assertTrue(captor.getValue().getAbandonedAt() != null);
  }

  @Test
  void markInterruptedOnStartup_autoAbandonsSoStartIsFree() {
    IntegrityAsyncJob running = runningJob();
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.of(running));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.markInterruptedOnStartup(null);

    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    IntegrityAsyncJob saved = captor.getValue();
    assertEquals(IntegrityAsyncJobStatus.INTERRUPTED, saved.getStatus());
    assertTrue(saved.getAbandonedAt() != null);
    assertFalse(IntegrityAsyncJobService.isEscapeStickyStatus(saved.getStatus()));
  }

  @Test
  void start_whenExpiredSticky_throwsBusy() {
    IntegrityAsyncJob expired = runningJob();
    expired.setStatus(IntegrityAsyncJobStatus.EXPIRED);
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.of(expired));

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            null);

    IntegrityAsyncJobBusyException ex =
        assertThrows(
            IntegrityAsyncJobBusyException.class, () -> service.start(request, 1, "admin.docker"));
    assertEquals(expired.getJobId(), ex.getCurrentJob().getJobId());
    verify(jobRepository, never()).saveAndFlush(any());
  }

  @Test
  void start_afterInterruptedAbandoned_allowed() {
    IntegrityAsyncJob interrupted = runningJob();
    interrupted.setStatus(IntegrityAsyncJobStatus.INTERRUPTED);
    interrupted.setAbandonedAt(OffsetDateTime.now(ZoneOffset.UTC));
    interrupted.setFinishedAt(interrupted.getAbandonedAt());
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    AtomicReference<IntegrityAsyncJob> saved = new AtomicReference<>();
    when(jobRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              IntegrityAsyncJob job = inv.getArgument(0);
              saved.set(job);
              return job;
            });

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            null);

    var accepted = service.start(request, 3, "ga.one");
    assertEquals(saved.get().getJobId(), accepted.jobId());
    assertEquals(IntegrityAsyncJobStatus.RUNNING, saved.get().getStatus());
  }

  @Test
  void getCurrent_skipsAbandonedInterrupted_returnsEmpty() {
    // V25 + boot hook: INTERRUPTED always has abandonedAt; active finder excludes it.
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());

    assertTrue(service.getCurrent().isEmpty());
    verify(jobRepository, never()).save(any());
  }

  @Test
  void abandon_alreadyAbandonedInterrupted_idempotent() {
    IntegrityAsyncJob interrupted = runningJob();
    interrupted.setStatus(IntegrityAsyncJobStatus.INTERRUPTED);
    interrupted.setAbandonedAt(OffsetDateTime.now(ZoneOffset.UTC));
    when(jobRepository.findLatestOperatorJob()).thenReturn(Optional.of(interrupted));

    var response = service.abandon(1);
    assertEquals(IntegrityAsyncJobStatus.INTERRUPTED, response.status());
    assertTrue(response.abandonedAt() != null);
    verify(jobRepository, never()).save(any());
  }

  @Test
  void escapeStickyStatus_excludesInterrupted() {
    assertTrue(IntegrityAsyncJobService.isEscapeStickyStatus(IntegrityAsyncJobStatus.EXPIRED));
    assertTrue(IntegrityAsyncJobService.isEscapeStickyStatus(IntegrityAsyncJobStatus.CANCELLED));
    assertFalse(IntegrityAsyncJobService.isEscapeStickyStatus(IntegrityAsyncJobStatus.INTERRUPTED));
  }

  @Test
  void getCurrent_expiresStaleThenReturnsRunning() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.of(runningJob()));

    var current = service.getCurrent();
    assertTrue(current.isPresent());
    assertEquals(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB, current.get().busyHolder());
    verify(stateService).expireStaleRunningIfNeeded();
  }

  @Test
  void getCurrent_nightlyShedLock_returnsSyntheticWithBusyHolder() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(nightlyShedLockProbe.isNightlyLockActive()).thenReturn(true);

    var current = service.getCurrent();
    assertTrue(current.isPresent());
    assertEquals(IntegrityAsyncJobStatus.RUNNING, current.get().status());
    assertEquals(IntegrityAsyncJobTrigger.NIGHTLY, current.get().trigger());
    assertEquals(IntegrityHeavyCryptoGate.Holder.NIGHTLY, current.get().busyHolder());
    assertNull(current.get().startedByAdminId());
    verify(jobRepository, never()).findLatestActiveOperatorJob();
  }

  @Test
  void getCurrent_operatorJobHolderWithTerminalRow_returnsRealRowNotSynthetic() {
    // Job-end race: markSucceeded committed, gate still held as OPERATOR_JOB.
    IntegrityAsyncJob succeeded = runningJob();
    succeeded.setStatus(IntegrityAsyncJobStatus.SUCCEEDED);
    succeeded.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    succeeded.setChainStatus(IntegrityChainStatus.OK);
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(nightlyShedLockProbe.isNightlyLockActive()).thenReturn(false);
    when(heavyCryptoGate.holder()).thenReturn(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB);
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.of(succeeded));

    var current = service.getCurrent();
    assertTrue(current.isPresent());
    assertEquals(succeeded.getJobId(), current.get().jobId());
    assertEquals(IntegrityAsyncJobStatus.SUCCEEDED, current.get().status());
    assertEquals(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB, current.get().busyHolder());
  }

  @Test
  void getLastVerdict_returnsVerdictBearingRow() {
    IntegrityAsyncJob verdict = runningJob();
    verdict.setStatus(IntegrityAsyncJobStatus.SUCCEEDED);
    verdict.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    verdict.setChainStatus(IntegrityChainStatus.OK);
    verdict.setTotalCheckpoints(10);
    when(jobRepository.findLatestVerdict()).thenReturn(Optional.of(verdict));

    var last = service.getLastVerdict();
    assertTrue(last.isPresent());
    assertEquals(verdict.getJobId(), last.get().jobId());
    assertEquals(IntegrityChainStatus.OK, last.get().chainStatus());
  }

  @Test
  void getLastVerdict_emptyWhenNone() {
    when(jobRepository.findLatestVerdict()).thenReturn(Optional.empty());
    assertTrue(service.getLastVerdict().isEmpty());
  }

  @Test
  void start_persistsRunningJob() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    AtomicReference<IntegrityAsyncJob> saved = new AtomicReference<>();
    when(jobRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              IntegrityAsyncJob job = inv.getArgument(0);
              saved.set(job);
              return job;
            });

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            null);

    var accepted = service.start(request, 3, "ga.one");
    assertEquals(saved.get().getJobId(), accepted.jobId());
    assertEquals(IntegrityAsyncJobStatus.RUNNING, saved.get().getStatus());
    assertEquals("ga.one", saved.get().getStartedByUsername());
  }

  @Test
  void startThenComplete_emitsExactlyTwoAuditsWithEventAction() {
    // Discard background work so runJob cannot race markSucceeded / emit extra audits.
    ThreadPoolTaskExecutor discarding =
        new ThreadPoolTaskExecutor() {
          @Override
          public void execute(Runnable task) {
            // Intentionally do not run Integrity async workers in this audit-emission test.
          }
        };
    discarding.initialize();
    try {
      IntegrityAsyncJobProperties properties = new IntegrityAsyncJobProperties();
      properties.setTtl(Duration.ofMinutes(60));
      IntegrityAsyncJobStateService realStateService =
          new IntegrityAsyncJobStateService(jobRepository, properties, auditLogService);
      IntegrityAsyncJobService auditScopedService =
          new IntegrityAsyncJobService(
              jobRepository,
              realStateService,
              heavyCryptoGate,
              auditIntegrityService,
              chainVerificationService,
              retroactiveIntegrityValidationService,
              nightlyIntegrityProperties,
              windowCapProperties,
              nightlyShedLockProbe,
              auditLogService,
              discarding);

      when(jobRepository.findBySlotKeyAndStatus(
              IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
          .thenReturn(Optional.empty());
      when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
      when(heavyCryptoGate.isBusy()).thenReturn(false);
      AtomicReference<IntegrityAsyncJob> saved = new AtomicReference<>();
      when(jobRepository.saveAndFlush(any()))
          .thenAnswer(
              inv -> {
                IntegrityAsyncJob job = inv.getArgument(0);
                saved.set(job);
                return job;
              });
      when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(jobRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));

      IntegrityAsyncJobStartRequest request =
          new IntegrityAsyncJobStartRequest(
              IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
              OffsetDateTime.parse("2026-01-01T00:00:00Z"),
              OffsetDateTime.parse("2026-01-02T00:00:00Z"),
              null);

      auditScopedService.start(request, 3, "ga.one");
      realStateService.markSucceeded(saved.get().getJobId(), "Chain range verified", true, null);

      ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
      verify(auditLogService, times(2)).log(auditCaptor.capture());
      List<AuditLog> audits = auditCaptor.getAllValues();
      assertEquals(EventType.INTEGRITY_ASYNC_JOB_STARTED, audits.get(0).getEventType());
      assertEquals(EventType.INTEGRITY_ASYNC_JOB_COMPLETED, audits.get(1).getEventType());
      assertEquals(IntegrityAsyncJobAuditConstants.EVENT_ACTION, audits.get(0).getEventAction());
      assertEquals(IntegrityAsyncJobAuditConstants.EVENT_ACTION, audits.get(1).getEventAction());
      assertEquals(EventStatus.SUCCESS, audits.get(0).getEventStatus());
      assertEquals(EventStatus.SUCCESS, audits.get(1).getEventStatus());
      assertEquals(3, audits.get(0).getAdminId());
      assertEquals(3, audits.get(1).getAdminId());
    } finally {
      discarding.shutdown();
    }
  }

  @Test
  void runJob_onRuntimeFailure_emitsGenericFailureAuditAndMarksFailed() {
    // Run workers inline so start() → runJob() is deterministic for this failure path.
    ThreadPoolTaskExecutor sync =
        new ThreadPoolTaskExecutor() {
          @Override
          public void execute(Runnable task) {
            task.run();
          }
        };
    sync.initialize();
    try {
      IntegrityAsyncJobProperties properties = new IntegrityAsyncJobProperties();
      properties.setTtl(Duration.ofMinutes(60));
      IntegrityAsyncJobStateService realStateService =
          new IntegrityAsyncJobStateService(jobRepository, properties, auditLogService);
      IntegrityAsyncJobService failureScopedService =
          new IntegrityAsyncJobService(
              jobRepository,
              realStateService,
              heavyCryptoGate,
              auditIntegrityService,
              chainVerificationService,
              retroactiveIntegrityValidationService,
              nightlyIntegrityProperties,
              windowCapProperties,
              nightlyShedLockProbe,
              auditLogService,
              sync);

      when(jobRepository.findBySlotKeyAndStatus(
              IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
          .thenReturn(Optional.empty());
      when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
      when(heavyCryptoGate.isBusy()).thenReturn(false);
      when(heavyCryptoGate.tryEnter(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB)).thenReturn(true);
      AtomicReference<IntegrityAsyncJob> saved = new AtomicReference<>();
      when(jobRepository.saveAndFlush(any()))
          .thenAnswer(
              inv -> {
                IntegrityAsyncJob job = inv.getArgument(0);
                saved.set(job);
                return job;
              });
      when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      when(jobRepository.findById(any())).thenAnswer(inv -> Optional.ofNullable(saved.get()));

      String leakySql =
          "PreparedStatementCallback; SQL [SELECT * FROM secrets WHERE token='abc123']";
      when(chainVerificationService.verifyChain(any(), any(), any()))
          .thenThrow(new DataAccessResourceFailureException(leakySql));

      IntegrityAsyncJobStartRequest request =
          new IntegrityAsyncJobStartRequest(
              IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
              OffsetDateTime.parse("2026-01-01T00:00:00Z"),
              OffsetDateTime.parse("2026-01-02T00:00:00Z"),
              null);

      failureScopedService.start(request, 3, "ga.one");

      assertEquals(IntegrityAsyncJobStatus.FAILED, saved.get().getStatus());
      assertEquals("Job failed: DataAccessResourceFailureException", saved.get().getErrorSummary());
      assertFalse(saved.get().getErrorSummary().contains("SELECT"));
      assertFalse(saved.get().getErrorSummary().contains("abc123"));

      ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
      verify(auditLogService, times(2)).log(auditCaptor.capture());
      AuditLog completed = auditCaptor.getAllValues().get(1);
      assertEquals(EventType.INTEGRITY_ASYNC_JOB_COMPLETED, completed.getEventType());
      assertEquals(EventStatus.FAILURE, completed.getEventStatus());
      assertEquals(IntegrityAsyncJobAuditConstants.EVENT_ACTION, completed.getEventAction());
      String details = completed.getEventDetails();
      assertTrue(details.contains("Job failed: DataAccessResourceFailureException"));
      assertFalse(details.contains(leakySql));
      assertFalse(details.contains("SELECT"));
      assertFalse(details.contains("abc123"));
    } finally {
      sync.shutdown();
    }
  }

  @Test
  void insertNightlyTerminalSucceeded_skipped_noPersist() {
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult skipped =
        new RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult(
            null,
            null,
            "scope",
            true,
            "HMAC signing is not active",
            true,
            false,
            null,
            "HMAC signing is not active",
            0,
            0,
            0,
            org.ezkey.audit.integrity.RetroactiveIntegrityValidationTriggerSource.SCHEDULED,
            0,
            0,
            0,
            0,
            0);

    service.insertNightlyTerminalSucceeded(skipped);

    verify(jobRepository, never()).save(any());
  }

  @Test
  void insertNightlyTerminalSucceeded_persistsNightlyTerminalWithCounters() {
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        new RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult(
            from,
            to,
            "scope",
            false,
            null,
            true,
            false,
            null,
            "OK",
            0,
            0,
            0,
            org.ezkey.audit.integrity.RetroactiveIntegrityValidationTriggerSource.SCHEDULED,
            288,
            288,
            0,
            0,
            0);
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.insertNightlyTerminalSucceeded(result);

    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    IntegrityAsyncJob saved = captor.getValue();
    assertEquals(IntegrityAsyncJobTrigger.NIGHTLY, saved.getTrigger());
    assertEquals(IntegrityAsyncJobType.RUN_VALIDATION, saved.getJobType());
    assertEquals(IntegrityAsyncJobStatus.SUCCEEDED, saved.getStatus());
    assertNull(saved.getStartedByAdminId());
    assertEquals("system", saved.getStartedByUsername());
    assertEquals(IntegrityChainStatus.OK, saved.getChainStatus());
    assertEquals(288, saved.getTotalCheckpoints());
    assertEquals(0, saved.getEntryHmacViolations());
    assertTrue(saved.getFinishedAt() != null);
  }

  @Test
  void insertNightlyTerminalFailed_persistsSimpleNameOnly() {
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");

    service.insertNightlyTerminalFailed(from, to, "DataAccessResourceFailureException");

    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    IntegrityAsyncJob saved = captor.getValue();
    assertEquals(IntegrityAsyncJobStatus.FAILED, saved.getStatus());
    assertEquals(IntegrityAsyncJobTrigger.NIGHTLY, saved.getTrigger());
    assertEquals("DataAccessResourceFailureException", saved.getErrorSummary());
    assertFalse(saved.getErrorSummary().contains("SELECT"));
  }

  @Test
  void tryInsertNightlyTerminalSucceeded_dataAccess_swallows() {
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        new RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult(
            from,
            to,
            "scope",
            false,
            null,
            true,
            false,
            null,
            "OK",
            0,
            0,
            0,
            org.ezkey.audit.integrity.RetroactiveIntegrityValidationTriggerSource.SCHEDULED,
            10,
            10,
            0,
            0,
            0);
    when(jobRepository.save(any())).thenThrow(new DataAccessResourceFailureException("disk"));

    service.tryInsertNightlyTerminalSucceeded(result);
    // no throw
  }

  @Test
  void start_rejectsNightlyTrigger() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
    when(heavyCryptoGate.isBusy()).thenReturn(false);

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            null,
            IntegrityAsyncJobTrigger.NIGHTLY);

    assertThrows(IllegalArgumentException.class, () -> service.start(request, 1, "admin.docker"));
    verify(jobRepository, never()).saveAndFlush(any());
  }

  @Test
  void start_persistsAutoTrigger() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findLatestActiveOperatorJob()).thenReturn(Optional.empty());
    when(heavyCryptoGate.isBusy()).thenReturn(false);
    AtomicReference<IntegrityAsyncJob> saved = new AtomicReference<>();
    when(jobRepository.saveAndFlush(any()))
        .thenAnswer(
            inv -> {
              IntegrityAsyncJob job = inv.getArgument(0);
              saved.set(job);
              return job;
            });

    IntegrityAsyncJobStartRequest request =
        new IntegrityAsyncJobStartRequest(
            IntegrityAsyncJobType.VERIFY_CHAIN_RANGE,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            OffsetDateTime.parse("2026-01-02T00:00:00Z"),
            null,
            IntegrityAsyncJobTrigger.AUTO);

    service.start(request, 3, "ga.one");
    assertEquals(IntegrityAsyncJobTrigger.AUTO, saved.get().getTrigger());
  }

  @Test
  void abandon_expired_emitsAbandonedAuditWithEventAction() {
    IntegrityAsyncJob expired = runningJob();
    expired.setStatus(IntegrityAsyncJobStatus.EXPIRED);
    expired.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    when(jobRepository.findLatestOperatorJob()).thenReturn(Optional.of(expired));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.abandon(7);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(auditCaptor.capture());
    AuditLog audit = auditCaptor.getValue();
    assertEquals(EventType.INTEGRITY_ASYNC_JOB_ABANDONED, audit.getEventType());
    assertEquals(IntegrityAsyncJobAuditConstants.EVENT_ACTION, audit.getEventAction());
    assertEquals(EventStatus.SUCCESS, audit.getEventStatus());
    assertEquals(7, audit.getAdminId());
  }

  private static IntegrityAsyncJob runningJob() {
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    job.setJobId(UUID.randomUUID());
    job.setSlotKey(IntegrityAsyncJob.GLOBAL_SLOT_KEY);
    job.setJobType(IntegrityAsyncJobType.VERIFY_CHAIN_RANGE);
    job.setStatus(IntegrityAsyncJobStatus.RUNNING);
    job.setStartedByAdminId(1);
    job.setStartedByUsername("admin.docker");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    job.setStartedAt(now);
    job.setHeartbeatAt(now);
    job.setScopeFrom(now.minus(Duration.ofDays(1)));
    job.setScopeTo(now);
    return job;
  }
}
