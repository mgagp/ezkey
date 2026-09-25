/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: IntegrityAsyncJobService
 * Description: Single global Integrity async job slot — start, status, abandon, run workers.
 */

package org.ezkey.audit.asyncjob;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.IntegrityAsyncJobAcceptedResponse;
import org.ezkey.audit.dto.IntegrityAsyncJobResponse;
import org.ezkey.audit.dto.IntegrityAsyncJobStartRequest;
import org.ezkey.audit.exception.IntegrityAsyncJobAbandonNotAllowedException;
import org.ezkey.audit.exception.IntegrityAsyncJobBusyException;
import org.ezkey.audit.exception.IntegrityValidationDisabledException;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.IntegrityHeavyCryptoGate;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationOptions;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.util.AuditDetailsBuilder;
import org.ezkey.security.ApplicationReadyStartupOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Owns the single global Integrity async job slot: start (202), status, abandon, and background
 * execution with heartbeat / TTL expiry.
 *
 * <p>Does <strong>not</strong> write {@code ezkey_scheduled_job_last_run} (dashboard last-run
 * signal only). Raise-alert happens only when a {@code RUN_VALIDATION} job completes successfully
 * through the existing retroactive path — never mid-run, never on cancel/abandon.
 *
 * <p>Admin-only: enabled via {@code ezkey.audit.integrity.async-job.enabled=true}. Auth and
 * Integration must not load this bean (least privilege on {@code ezkey_integrity_async_job}).
 *
 * @since 2026
 */
@Service
@ConditionalOnProperty(
    name = "ezkey.audit.integrity.async-job.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class IntegrityAsyncJobService {

  private static final Logger logger = LoggerFactory.getLogger(IntegrityAsyncJobService.class);

  private final IntegrityAsyncJobRepository jobRepository;
  private final IntegrityAsyncJobStateService stateService;
  private final IntegrityHeavyCryptoGate heavyCryptoGate;
  private final AuditIntegrityService auditIntegrityService;
  private final AuditChainVerificationService chainVerificationService;
  private final RetroactiveIntegrityValidationService retroactiveIntegrityValidationService;
  private final NightlyIntegrityProperties nightlyIntegrityProperties;
  private final AuditLogService auditLogService;
  private final ThreadPoolTaskExecutor integrityAsyncJobExecutor;

  /**
   * Constructs the service.
   *
   * @param jobRepository job persistence
   * @param stateService heartbeat / terminal mutations
   * @param heavyCryptoGate process-local exclusion vs nightly
   * @param auditIntegrityService entry HMAC verify
   * @param chainVerificationService chain verify
   * @param retroactiveIntegrityValidationService detective validation
   * @param nightlyIntegrityProperties nightly enable flag (fail-closed for RUN_VALIDATION)
   * @param auditLogService audit emission
   * @param integrityAsyncJobExecutor single-thread worker pool
   */
  public IntegrityAsyncJobService(
      IntegrityAsyncJobRepository jobRepository,
      IntegrityAsyncJobStateService stateService,
      IntegrityHeavyCryptoGate heavyCryptoGate,
      AuditIntegrityService auditIntegrityService,
      AuditChainVerificationService chainVerificationService,
      RetroactiveIntegrityValidationService retroactiveIntegrityValidationService,
      NightlyIntegrityProperties nightlyIntegrityProperties,
      AuditLogService auditLogService,
      @Qualifier("integrityAsyncJobExecutor") ThreadPoolTaskExecutor integrityAsyncJobExecutor) {
    this.jobRepository = jobRepository;
    this.stateService = stateService;
    this.heavyCryptoGate = heavyCryptoGate;
    this.auditIntegrityService = auditIntegrityService;
    this.chainVerificationService = chainVerificationService;
    this.retroactiveIntegrityValidationService = retroactiveIntegrityValidationService;
    this.nightlyIntegrityProperties = nightlyIntegrityProperties;
    this.auditLogService = auditLogService;
    this.integrityAsyncJobExecutor = integrityAsyncJobExecutor;
  }

  /**
   * On restart, any RUNNING row becomes INTERRUPTED and is auto-abandoned (Marc crash posture).
   *
   * <p>Crash/restart orphans are not Escape-sticky: the operator Starts again without Abandon.
   * Escape remains for TTL {@link IntegrityAsyncJobStatus#EXPIRED} and explicit {@link
   * IntegrityAsyncJobStatus#CANCELLED} only. History row is kept (not erased).
   *
   * @param event application ready
   */
  @EventListener(ApplicationReadyEvent.class)
  @Order(ApplicationReadyStartupOrder.ADMIN_MFA_BOOTSTRAP + 1)
  @Transactional
  public void markInterruptedOnStartup(ApplicationReadyEvent event) {
    Optional<IntegrityAsyncJob> running =
        jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING);
    if (running.isEmpty()) {
      return;
    }
    IntegrityAsyncJob job = running.get();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    job.setStatus(IntegrityAsyncJobStatus.INTERRUPTED);
    job.setFinishedAt(now);
    job.setAbandonedAt(now);
    job.setErrorSummary("Process restarted while job was RUNNING");
    job.setResultSummary("Interrupted by process restart — slot freed; Start again");
    jobRepository.save(job);
    logger.warn(
        "Marked Integrity async job {} as INTERRUPTED and auto-abandoned after process restart",
        job.getJobId());
  }

  /**
   * Starts a job on the global slot or refuses with 409 semantics.
   *
   * @param request type and scope
   * @param adminId starter admin id
   * @param username starter username (denormalized for banner)
   * @return accepted job id
   */
  @Transactional
  public IntegrityAsyncJobAcceptedResponse start(
      IntegrityAsyncJobStartRequest request, Integer adminId, String username) {
    stateService.expireStaleRunningIfNeeded();
    Optional<IntegrityAsyncJob> running =
        jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING);
    if (running.isPresent()) {
      throw new IntegrityAsyncJobBusyException(
          running.get(), "Integrity async slot busy: " + resumeLine(running.get()));
    }
    Optional<IntegrityAsyncJob> escapeSticky = findLatestEscapeSticky();
    if (escapeSticky.isPresent()) {
      throw new IntegrityAsyncJobBusyException(
          escapeSticky.get(),
          "Integrity async slot sticky — Abandon and restart first: "
              + resumeLine(escapeSticky.get()));
    }
    if (heavyCryptoGate.isBusy()) {
      throw busyWithoutJob("Integrity crypto path busy (scheduled or in-process heavy work)");
    }

    if (request.type() == IntegrityAsyncJobType.RUN_VALIDATION) {
      if (!nightlyIntegrityProperties.isEnabled()) {
        throw new IntegrityValidationDisabledException();
      }
      retroactiveIntegrityValidationService.validateOperatorWindow(request.from(), request.to());
    } else if (request.from() == null || request.to() == null) {
      throw new IllegalArgumentException(
          "Date range is required. Provide from (inclusive) and to (exclusive) as ISO-8601.");
    } else if (!request.to().isAfter(request.from())) {
      throw new IllegalArgumentException("Window end (to) must be after window start (from).");
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID jobId = UUID.randomUUID();
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    job.setJobId(jobId);
    job.setSlotKey(IntegrityAsyncJob.GLOBAL_SLOT_KEY);
    job.setJobType(request.type());
    job.setStatus(IntegrityAsyncJobStatus.RUNNING);
    job.setStartedByAdminId(adminId);
    job.setStartedByUsername(username != null ? username : "unknown");
    job.setStartedAt(now);
    job.setHeartbeatAt(now);
    job.setScopeFrom(request.from());
    job.setScopeTo(request.to());
    job.setRaiseAlert(
        request.type() == IntegrityAsyncJobType.RUN_VALIDATION
            ? (request.raiseAlert() == null || request.raiseAlert())
            : null);

    try {
      jobRepository.saveAndFlush(job);
    } catch (DataIntegrityViolationException ex) {
      stateService.expireStaleRunningIfNeeded();
      IntegrityAsyncJob current =
          jobRepository
              .findBySlotKeyAndStatus(
                  IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING)
              .orElseThrow(() -> ex);
      throw new IntegrityAsyncJobBusyException(
          current, "Integrity async slot busy: " + resumeLine(current));
    }

    emitStartedAudit(adminId, job);

    Runnable submit = () -> integrityAsyncJobExecutor.execute(() -> runJob(jobId));
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              submit.run();
            }
          });
    } else {
      submit.run();
    }
    return new IntegrityAsyncJobAcceptedResponse(jobId);
  }

  /**
   * Returns the job most relevant for the Integrity banner: RUNNING (after TTL), else latest
   * non-abandoned terminal, else empty.
   *
   * <p>Legacy INTERRUPTED rows without {@code abandonedAt} (pre–crash-posture) are auto-abandoned
   * here so they never Escape-sticky or block Start.
   *
   * @return current job when present
   */
  @Transactional
  public Optional<IntegrityAsyncJobResponse> getCurrent() {
    stateService.expireStaleRunningIfNeeded();
    Optional<IntegrityAsyncJob> running =
        jobRepository.findBySlotKeyAndStatus(
            IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING);
    if (running.isPresent()) {
      return Optional.of(IntegrityAsyncJobResponse.from(running.get()));
    }
    List<IntegrityAsyncJob> recent = jobRepository.findAllByOrderByStartedAtDesc();
    for (IntegrityAsyncJob job : recent) {
      if (job.getAbandonedAt() != null) {
        continue;
      }
      if (job.getStatus() == IntegrityAsyncJobStatus.INTERRUPTED) {
        healInterruptedOrphan(job);
        continue;
      }
      return Optional.of(IntegrityAsyncJobResponse.from(job));
    }
    return Optional.empty();
  }

  /**
   * Returns a job by id after applying TTL expiry when RUNNING.
   *
   * @param jobId opaque id
   * @return job when found
   */
  @Transactional
  public Optional<IntegrityAsyncJobResponse> getById(UUID jobId) {
    stateService.expireStaleRunningIfNeeded();
    return jobRepository.findById(jobId).map(IntegrityAsyncJobResponse::from);
  }

  /**
   * Frees sticky EXPIRED or CANCELLED state (UI « Abandon and restart »). Does not kill a healthy
   * RUNNING job. Crash/restart INTERRUPTED is auto-abandoned at boot — not Escape-sticky.
   *
   * @param adminId operator performing abandon
   * @return abandoned job summary
   */
  @Transactional
  public IntegrityAsyncJobResponse abandon(Integer adminId) {
    stateService.expireStaleRunningIfNeeded();
    List<IntegrityAsyncJob> recent = jobRepository.findAllByOrderByStartedAtDesc();
    if (recent.isEmpty()) {
      throw new IntegrityAsyncJobAbandonNotAllowedException(
          null, "No Integrity async job to abandon");
    }
    IntegrityAsyncJob job = recent.get(0);
    if (job.getAbandonedAt() != null) {
      return IntegrityAsyncJobResponse.from(job);
    }
    if (job.getStatus() == IntegrityAsyncJobStatus.INTERRUPTED) {
      healInterruptedOrphan(job);
      return IntegrityAsyncJobResponse.from(job);
    }
    IntegrityAsyncJobStatus status = job.getStatus();
    if (status == IntegrityAsyncJobStatus.RUNNING) {
      throw new IntegrityAsyncJobAbandonNotAllowedException(
          status, "Cannot abandon a healthy RUNNING Integrity job; wait for completion or TTL");
    }
    if (!isEscapeStickyStatus(status)) {
      throw new IntegrityAsyncJobAbandonNotAllowedException(
          status,
          "Abandon is only allowed when status is EXPIRED or CANCELLED (was " + status + ")");
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    job.setAbandonedAt(now);
    job.setAbandonedByAdminId(adminId);
    if (status == IntegrityAsyncJobStatus.EXPIRED) {
      job.setStatus(IntegrityAsyncJobStatus.CANCELLED);
      if (job.getResultSummary() == null) {
        job.setResultSummary("Abandoned by operator");
      }
    }
    jobRepository.save(job);
    emitAbandonedAudit(adminId, job);
    return IntegrityAsyncJobResponse.from(job);
  }

  /**
   * Whether the operator slot currently holds a RUNNING job (for nightly exclusion).
   *
   * @return true when a RUNNING operator job exists (after TTL scan)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean isOperatorSlotRunning() {
    stateService.expireStaleRunningIfNeeded();
    return jobRepository.existsBySlotKeyAndStatus(
        IntegrityAsyncJob.GLOBAL_SLOT_KEY, IntegrityAsyncJobStatus.RUNNING);
  }

  /**
   * Escape-sticky statuses that require Abandon before Start (TTL / explicit cancel only).
   *
   * @param status job status
   * @return true when Escape chrome applies
   */
  static boolean isEscapeStickyStatus(IntegrityAsyncJobStatus status) {
    return status == IntegrityAsyncJobStatus.EXPIRED || status == IntegrityAsyncJobStatus.CANCELLED;
  }

  private Optional<IntegrityAsyncJob> findLatestEscapeSticky() {
    List<IntegrityAsyncJob> recent = jobRepository.findAllByOrderByStartedAtDesc();
    for (IntegrityAsyncJob job : recent) {
      if (job.getAbandonedAt() != null) {
        continue;
      }
      if (job.getStatus() == IntegrityAsyncJobStatus.INTERRUPTED) {
        healInterruptedOrphan(job);
        continue;
      }
      if (isEscapeStickyStatus(job.getStatus())) {
        return Optional.of(job);
      }
      // Latest non-abandoned is SUCCEEDED/FAILED/etc. — slot free for Start.
      return Optional.empty();
    }
    return Optional.empty();
  }

  private void healInterruptedOrphan(IntegrityAsyncJob job) {
    if (job.getAbandonedAt() != null) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    job.setAbandonedAt(now);
    if (job.getFinishedAt() == null) {
      job.setFinishedAt(now);
    }
    if (job.getResultSummary() == null) {
      job.setResultSummary("Interrupted by process restart — slot freed; Start again");
    }
    jobRepository.save(job);
    logger.info(
        "Auto-abandoned legacy INTERRUPTED Integrity async job {} (crash posture)", job.getJobId());
  }

  private void runJob(UUID jobId) {
    if (!heavyCryptoGate.tryEnter()) {
      stateService.markFailed(jobId, "Integrity crypto path busy (scheduled nightly or peer work)");
      return;
    }
    try {
      IntegrityAsyncJob job =
          jobRepository
              .findById(jobId)
              .orElseThrow(() -> new IllegalStateException("Job not found: " + jobId));
      if (job.getStatus() != IntegrityAsyncJobStatus.RUNNING) {
        return;
      }
      Runnable heartbeat = () -> stateService.heartbeat(jobId);
      heartbeat.run();

      switch (job.getJobType()) {
        case VERIFY_ENTRY_HMAC_RANGE -> completeEntryVerify(job, heartbeat);
        case VERIFY_CHAIN_RANGE -> completeChainVerify(job, heartbeat);
        case RUN_VALIDATION -> completeValidation(job, heartbeat);
        default -> stateService.markFailed(jobId, "Unknown job type");
      }
    } catch (IntegrityValidationDisabledException ex) {
      stateService.markFailed(jobId, ex.getMessage());
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Integrity async job {} failed: {}", jobId, ex.getMessage(), ex);
      stateService.markFailed(jobId, truncate(ex.getMessage(), 512));
    } finally {
      heavyCryptoGate.exit();
    }
  }

  private void completeEntryVerify(IntegrityAsyncJob job, Runnable heartbeat) {
    AuditIntegrityService.IntegrityReport report =
        auditIntegrityService.verifyRange(job.getScopeFrom(), job.getScopeTo(), heartbeat);
    String summary =
        "Entry HMAC: status="
            + report.status()
            + ", total="
            + report.totalEntries()
            + ", invalid="
            + report.invalidEntries();
    stateService.markSucceeded(job.getJobId(), summary, report.intact(), null);
  }

  private void completeChainVerify(IntegrityAsyncJob job, Runnable heartbeat) {
    AuditChainVerificationService.ChainVerificationReport report =
        chainVerificationService.verifyChain(job.getScopeFrom(), job.getScopeTo(), heartbeat);
    String summary =
        "Chain: status="
            + report.status()
            + ", checkpoints="
            + report.totalCheckpoints()
            + ", invalid="
            + report.invalidCheckpoints()
            + ", gaps="
            + report.undeclaredGaps().size();
    stateService.markSucceeded(job.getJobId(), summary, report.intact(), null);
  }

  private void completeValidation(IntegrityAsyncJob job, Runnable heartbeat) {
    heartbeat.run();
    boolean raiseAlert = job.getRaiseAlert() == null || job.getRaiseAlert();
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        retroactiveIntegrityValidationService.runValidation(
            job.getScopeFrom(),
            job.getScopeTo(),
            RetroactiveIntegrityValidationOptions.operator(raiseAlert, job.getStartedByAdminId()));
    heartbeat.run();
    if (result.skipped()) {
      stateService.markSucceeded(
          job.getJobId(), "Validation skipped: " + result.skipReason(), true, null);
      return;
    }
    String summary =
        "Validation: intact="
            + result.intact()
            + ", entryViolations="
            + result.entryHmacViolationCount()
            + ", chainViolations="
            + result.chainViolationCount()
            + (result.alertRaised() ? ", alertId=" + result.alertId() : "");
    stateService.markSucceeded(job.getJobId(), summary, result.intact(), result.alertId());
  }

  private IntegrityAsyncJobBusyException busyWithoutJob(String message) {
    IntegrityAsyncJob synthetic = new IntegrityAsyncJob();
    synthetic.setJobId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    synthetic.setJobType(IntegrityAsyncJobType.RUN_VALIDATION);
    synthetic.setStatus(IntegrityAsyncJobStatus.RUNNING);
    synthetic.setStartedByAdminId(0);
    synthetic.setStartedByUsername("system");
    synthetic.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
    synthetic.setHeartbeatAt(synthetic.getStartedAt());
    synthetic.setResultSummary(message);
    return new IntegrityAsyncJobBusyException(synthetic, message);
  }

  private void emitStartedAudit(Integer adminId, IntegrityAsyncJob job) {
    try {
      String details =
          AuditDetailsBuilder.builder()
              .custom("jobId", job.getJobId().toString())
              .custom("jobType", job.getJobType().name())
              .custom("jobStatus", job.getStatus().name())
              .custom("summary", "Integrity async job started")
              .toJson();
      AuditLog log =
          AuditLog.builder()
              .apiName(ApiName.ADMIN_API)
              .eventType(EventType.INTEGRITY_ASYNC_JOB_STARTED)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(adminId)
              .eventDetails(details)
              .build();
      auditLogService.log(log);
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn("Failed to emit Integrity async job start audit: {}", ex.getMessage());
    }
  }

  private void emitAbandonedAudit(Integer adminId, IntegrityAsyncJob job) {
    try {
      String details =
          AuditDetailsBuilder.builder()
              .custom("jobId", job.getJobId().toString())
              .custom("jobType", job.getJobType().name())
              .custom("jobStatus", job.getStatus().name())
              .custom("summary", "Integrity async job slot abandoned")
              .toJson();
      AuditLog log =
          AuditLog.builder()
              .apiName(ApiName.ADMIN_API)
              .eventType(EventType.INTEGRITY_ASYNC_JOB_ABANDONED)
              .eventStatus(EventStatus.SUCCESS)
              .adminId(adminId)
              .eventDetails(details)
              .build();
      auditLogService.log(log);
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn("Failed to emit Integrity async job abandon audit: {}", ex.getMessage());
    }
  }

  private static String resumeLine(IntegrityAsyncJob job) {
    return job.getJobType()
        + " · "
        + job.getStatus()
        + " · started by "
        + job.getStartedByUsername()
        + " at "
        + job.getStartedAt();
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
