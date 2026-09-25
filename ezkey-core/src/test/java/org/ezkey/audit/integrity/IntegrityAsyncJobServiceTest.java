/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityAsyncJobServiceTest
 * Description: Unit tests for Integrity async job slot, 409, TTL, abandon rules.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.audit.dto.IntegrityAsyncJobStartRequest;
import org.ezkey.audit.exception.IntegrityAsyncJobAbandonNotAllowedException;
import org.ezkey.audit.exception.IntegrityAsyncJobBusyException;
import org.ezkey.audit.exception.IntegrityValidationDisabledException;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
  @Mock private AuditLogService auditLogService;

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
  void start_runValidationWhenNightlyDisabled_throws() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of());
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
  void start_uniqueViolation_mapsToBusy() {
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(runningJob()));
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of());
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
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(running));

    assertThrows(IntegrityAsyncJobAbandonNotAllowedException.class, () -> service.abandon(1));
  }

  @Test
  void abandon_expired_marksCancelledAndAbandoned() {
    IntegrityAsyncJob expired = runningJob();
    expired.setStatus(IntegrityAsyncJobStatus.EXPIRED);
    expired.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(expired));
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
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(expired));

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
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(interrupted));
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
  void getCurrent_healsLegacyInterruptedWithoutAbandonedAt() {
    IntegrityAsyncJob interrupted = runningJob();
    interrupted.setStatus(IntegrityAsyncJobStatus.INTERRUPTED);
    interrupted.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.empty());
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(interrupted));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertTrue(service.getCurrent().isEmpty());
    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    assertTrue(captor.getValue().getAbandonedAt() != null);
  }

  @Test
  void abandon_interrupted_healsWithoutEscapeCeremony() {
    IntegrityAsyncJob interrupted = runningJob();
    interrupted.setStatus(IntegrityAsyncJobStatus.INTERRUPTED);
    when(jobRepository.findAllByOrderByStartedAtDesc()).thenReturn(List.of(interrupted));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    var response = service.abandon(1);
    assertEquals(IntegrityAsyncJobStatus.INTERRUPTED, response.status());
    assertTrue(response.abandonedAt() != null);
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

    assertTrue(service.getCurrent().isPresent());
    verify(stateService).expireStaleRunningIfNeeded();
  }

  @Test
  void start_persistsRunningJob() {
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
