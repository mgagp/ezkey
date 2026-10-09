/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrityAsyncJobStateServiceTest
 * Description: TTL expiry turns stale RUNNING into EXPIRED.
 */

package org.ezkey.audit.asyncjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.ezkey.audit.domain.EventStatus;
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
 * Unit tests for {@link IntegrityAsyncJobStateService}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class IntegrityAsyncJobStateServiceTest {

  @Mock private IntegrityAsyncJobRepository jobRepository;
  @Mock private AuditLogService auditLogService;

  private IntegrityAsyncJobProperties properties;
  private IntegrityAsyncJobStateService stateService;

  @BeforeEach
  void setUp() {
    properties = new IntegrityAsyncJobProperties();
    properties.setTtl(Duration.ofMinutes(60));
    stateService = new IntegrityAsyncJobStateService(jobRepository, properties, auditLogService);
  }

  @Test
  void expireStaleRunningIfNeeded_marksExpiredWhenHeartbeatOlderThanTtl() {
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    job.setJobId(UUID.randomUUID());
    job.setStatus(IntegrityAsyncJobStatus.RUNNING);
    job.setHeartbeatAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(61));
    when(jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING))
        .thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertTrue(stateService.expireStaleRunningIfNeeded());
    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    assertEquals(IntegrityAsyncJobStatus.EXPIRED, captor.getValue().getStatus());
  }

  @Test
  void markSucceeded_emitsCompletedAuditWithEventAction() {
    IntegrityAsyncJob job = runningJob();
    when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    stateService.markSucceeded(job.getJobId(), "Chain range verified", true, null);

    ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(auditCaptor.capture());
    AuditLog audit = auditCaptor.getValue();
    assertEquals(EventType.INTEGRITY_ASYNC_JOB_COMPLETED, audit.getEventType());
    assertEquals(IntegrityAsyncJobAuditConstants.EVENT_ACTION, audit.getEventAction());
    assertEquals(EventStatus.SUCCESS, audit.getEventStatus());
    assertEquals(1, audit.getAdminId());
  }

  @Test
  void markSucceeded_whenAuditLogThrows_jobStillSucceeded() {
    IntegrityAsyncJob job = runningJob();
    when(jobRepository.findById(job.getJobId())).thenReturn(Optional.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    doThrow(new RuntimeException("audit write failed")).when(auditLogService).log(any());

    stateService.markSucceeded(job.getJobId(), "Chain range verified", true, null);

    assertEquals(IntegrityAsyncJobStatus.SUCCEEDED, job.getStatus());
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
    return job;
  }
}
