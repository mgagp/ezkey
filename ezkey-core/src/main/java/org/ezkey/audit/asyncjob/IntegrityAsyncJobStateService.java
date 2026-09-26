/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: IntegrityAsyncJobStateService
 * Description: REQUIRES_NEW state mutations for Integrity async jobs (heartbeat, terminal).
 */

package org.ezkey.audit.asyncjob;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Narrow transactional mutations for Integrity async job heartbeat and terminal states.
 *
 * <p>Separate bean so {@code REQUIRES_NEW} applies through the Spring proxy (no self-invocation).
 *
 * <p>Admin-only: see {@code ezkey.audit.integrity.async-job.enabled}.
 *
 * @since 2026
 */
@Service
@ConditionalOnProperty(
    name = "ezkey.audit.integrity.async-job.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class IntegrityAsyncJobStateService {

  private static final Logger logger = LoggerFactory.getLogger(IntegrityAsyncJobStateService.class);

  private final IntegrityAsyncJobRepository jobRepository;
  private final IntegrityAsyncJobProperties properties;
  private final AuditLogService auditLogService;

  /**
   * Constructs the state service.
   *
   * @param jobRepository job persistence
   * @param properties TTL configuration
   * @param auditLogService completion audit emission
   */
  public IntegrityAsyncJobStateService(
      IntegrityAsyncJobRepository jobRepository,
      IntegrityAsyncJobProperties properties,
      AuditLogService auditLogService) {
    this.jobRepository = jobRepository;
    this.properties = properties;
    this.auditLogService = auditLogService;
  }

  /**
   * Updates heartbeat for a RUNNING job.
   *
   * @param jobId job id
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void heartbeat(UUID jobId) {
    jobRepository
        .findById(jobId)
        .filter(j -> j.getStatus() == IntegrityAsyncJobStatus.RUNNING)
        .ifPresent(
            j -> {
              j.setHeartbeatAt(OffsetDateTime.now(ZoneOffset.UTC));
              jobRepository.save(j);
            });
  }

  /**
   * Marks a RUNNING job SUCCEEDED.
   *
   * @param jobId job id
   * @param summary safe summary
   * @param intact intact flag
   * @param alertId optional alert id
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSucceeded(UUID jobId, String summary, Boolean intact, Long alertId) {
    jobRepository
        .findById(jobId)
        .ifPresent(
            job -> {
              if (job.getStatus() != IntegrityAsyncJobStatus.RUNNING) {
                return;
              }
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              job.setStatus(IntegrityAsyncJobStatus.SUCCEEDED);
              job.setFinishedAt(now);
              job.setHeartbeatAt(now);
              job.setResultSummary(truncate(summary, 1024));
              job.setResultIntact(intact);
              job.setResultAlertId(alertId);
              jobRepository.save(job);
              emitCompleted(job, EventStatus.SUCCESS, summary);
            });
  }

  /**
   * Marks a RUNNING job FAILED.
   *
   * @param jobId job id
   * @param error safe error summary
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID jobId, String error) {
    jobRepository
        .findById(jobId)
        .ifPresent(
            job -> {
              if (job.getStatus() != IntegrityAsyncJobStatus.RUNNING) {
                return;
              }
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              job.setStatus(IntegrityAsyncJobStatus.FAILED);
              job.setFinishedAt(now);
              job.setHeartbeatAt(now);
              job.setErrorSummary(truncate(error, 512));
              job.setResultSummary(truncate("Failed: " + error, 1024));
              job.setResultIntact(false);
              jobRepository.save(job);
              emitCompleted(job, EventStatus.FAILURE, error);
            });
  }

  /**
   * Expires RUNNING jobs whose heartbeat is older than TTL.
   *
   * @return true when a job was expired
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean expireStaleRunningIfNeeded() {
    Optional<IntegrityAsyncJob> running =
        jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING);
    if (running.isEmpty()) {
      return false;
    }
    IntegrityAsyncJob job = running.get();
    OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(properties.getTtl());
    if (job.getHeartbeatAt() != null && job.getHeartbeatAt().isBefore(cutoff)) {
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      job.setStatus(IntegrityAsyncJobStatus.EXPIRED);
      job.setFinishedAt(now);
      job.setErrorSummary("No heartbeat within TTL (" + properties.getTtl() + ")");
      job.setResultSummary("Expired — no heartbeat; can restart");
      jobRepository.save(job);
      logger.warn("Integrity async job {} expired (no heartbeat)", job.getJobId());
      return true;
    }
    return false;
  }

  private void emitCompleted(IntegrityAsyncJob job, EventStatus status, String summary) {
    try {
      String details =
          AuditDetailsBuilder.builder()
              .custom("jobId", job.getJobId().toString())
              .custom("jobType", job.getJobType().name())
              .custom("jobStatus", job.getStatus().name())
              .custom("summary", truncate(summary, 400))
              .toJson();
      AuditLog log =
          AuditLog.builder()
              .apiName(ApiName.ADMIN_API)
              .eventType(EventType.INTEGRITY_ASYNC_JOB_COMPLETED)
              .eventStatus(status)
              .adminId(job.getStartedByAdminId())
              .eventDetails(details)
              .build();
      auditLogService.log(log);
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn("Failed to emit Integrity async job completion audit: {}", ex.getMessage());
    }
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
