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
import org.ezkey.audit.integrity.IntegrityWindowCapProperties;
import org.ezkey.audit.integrity.NightlyIntegrityProperties;
import org.ezkey.audit.integrity.NightlyIntegrityShedLockProbe;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationOptions;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult;
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
  private final IntegrityNightlyTerminalService nightlyTerminalService;
  private final IntegrityHeavyCryptoGate heavyCryptoGate;
  private final AuditIntegrityService auditIntegrityService;
  private final AuditChainVerificationService chainVerificationService;
  private final RetroactiveIntegrityValidationService retroactiveIntegrityValidationService;
  private final NightlyIntegrityProperties nightlyIntegrityProperties;
  private final IntegrityWindowCapProperties windowCapProperties;
  private final NightlyIntegrityShedLockProbe nightlyShedLockProbe;
  private final AuditLogService auditLogService;
  private final ThreadPoolTaskExecutor integrityAsyncJobExecutor;

  /**
   * Constructs the service.
   *
   * @param jobRepository job persistence
   * @param stateService heartbeat / terminal mutations
   * @param nightlyTerminalService nightly terminal row writer ({@code tryInsert*} uses
   *     programmatic {@code REQUIRES_NEW})
   * @param heavyCryptoGate process-local exclusion vs nightly
   * @param auditIntegrityService entry HMAC verify
   * @param chainVerificationService chain verify
   * @param retroactiveIntegrityValidationService detective validation
   * @param nightlyIntegrityProperties nightly enable flag (fail-closed for RUN_VALIDATION)
   * @param windowCapProperties async days-based window cap for VERIFY_* / RUN_VALIDATION starts
   *     ({@code max-window-days}; sync report GETs and POST use {@code max-sync-window-days})
   * @param nightlyShedLockProbe cross-replica nightly ShedLock probe
   * @param auditLogService audit emission
   * @param integrityAsyncJobExecutor single-thread worker pool
   */
  public IntegrityAsyncJobService(
      IntegrityAsyncJobRepository jobRepository,
      IntegrityAsyncJobStateService stateService,
      IntegrityNightlyTerminalService nightlyTerminalService,
      IntegrityHeavyCryptoGate heavyCryptoGate,
      AuditIntegrityService auditIntegrityService,
      AuditChainVerificationService chainVerificationService,
      RetroactiveIntegrityValidationService retroactiveIntegrityValidationService,
      NightlyIntegrityProperties nightlyIntegrityProperties,
      IntegrityWindowCapProperties windowCapProperties,
      NightlyIntegrityShedLockProbe nightlyShedLockProbe,
      AuditLogService auditLogService,
      @Qualifier("integrityAsyncJobExecutor") ThreadPoolTaskExecutor integrityAsyncJobExecutor) {
    this.jobRepository = jobRepository;
    this.stateService = stateService;
    this.nightlyTerminalService = nightlyTerminalService;
    this.heavyCryptoGate = heavyCryptoGate;
    this.auditIntegrityService = auditIntegrityService;
    this.chainVerificationService = chainVerificationService;
    this.retroactiveIntegrityValidationService = retroactiveIntegrityValidationService;
    this.nightlyIntegrityProperties = nightlyIntegrityProperties;
    this.windowCapProperties = windowCapProperties;
    this.nightlyShedLockProbe = nightlyShedLockProbe;
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
   * <p><b>Invariant:</b> INTERRUPTED is always written together with {@code abandonedAt} (and
   * {@code finishedAt}) here — never Escape-sticky, never blocking Start. V25 one-shot UPDATE
   * healed historical orphans; do not reintroduce a heal loop on read paths.
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
      throw IntegrityAsyncJobBusyException.forHeavyCryptoBusy(heavyCryptoGate.holder());
    }

    if (request.type() == IntegrityAsyncJobType.RUN_VALIDATION) {
      if (!nightlyIntegrityProperties.isEnabled()) {
        throw new IntegrityValidationDisabledException();
      }
      // Async RUN_VALIDATION keeps max-window-days (default 92); sync operator POST uses
      // max-sync-window-days (Christophe B1).
      retroactiveIntegrityValidationService.validateOperatorWindow(request.from(), request.to());
    } else {
      // VERIFY_CHAIN_RANGE / VERIFY_ENTRY_HMAC_RANGE — async days cap (#730 D2 / cut 3 PR 0).
      windowCapProperties.validateWindow(request.from(), request.to());
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    UUID jobId = UUID.randomUUID();
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    job.setJobId(jobId);
    job.setSlotKey(IntegrityAsyncJob.GLOBAL_SLOT_KEY);
    job.setJobType(request.type());
    job.setStatus(IntegrityAsyncJobStatus.RUNNING);
    IntegrityAsyncJobTrigger trigger =
        request.trigger() == null ? IntegrityAsyncJobTrigger.MANUAL : request.trigger();
    if (trigger == IntegrityAsyncJobTrigger.NIGHTLY) {
      throw new IllegalArgumentException("trigger NIGHTLY is not allowed on POST");
    }
    job.setTrigger(trigger);
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
   * Returns the job most relevant for the Integrity banner: RUNNING (after TTL), else synthetic
   * RUNNING when ShedLock holds nightly, else latest non-abandoned <em>operator</em> terminal, else
   * empty. Persisted nightly rows are excluded from the operator slot.
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
      // OPERATOR_JOB: always the real row (avoids end-of-job synthetic flicker).
      return Optional.of(
          IntegrityAsyncJobResponse.from(
              running.get(), IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB));
    }
    if (nightlyShedLockProbe.isNightlyLockActive()) {
      return Optional.of(syntheticNightlyBusyResponse());
    }
    IntegrityHeavyCryptoGate.Holder localHolder = heavyCryptoGate.holder();
    // REPORT_GET: no synthetic; surface via 409 on sync GETs. Still attach busyHolder when present.
    return jobRepository
        .findLatestActiveOperatorJob()
        .map(job -> IntegrityAsyncJobResponse.from(job, localHolder));
  }

  /**
   * Latest verdict-bearing job (operator or nightly) for the Integrity micro-dashboard.
   *
   * @return verdict row when present
   */
  @Transactional(readOnly = true)
  public Optional<IntegrityAsyncJobResponse> getLastVerdict() {
    return jobRepository.findLatestVerdict().map(IntegrityAsyncJobResponse::from);
  }

  private static IntegrityAsyncJobResponse syntheticNightlyBusyResponse() {
    IntegrityAsyncJob synthetic = new IntegrityAsyncJob();
    synthetic.setJobId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    synthetic.setJobType(IntegrityAsyncJobType.RUN_VALIDATION);
    synthetic.setStatus(IntegrityAsyncJobStatus.RUNNING);
    synthetic.setTrigger(IntegrityAsyncJobTrigger.NIGHTLY);
    synthetic.setStartedByAdminId(null);
    synthetic.setStartedByUsername("system");
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    synthetic.setStartedAt(now);
    synthetic.setHeartbeatAt(now);
    synthetic.setResultSummary("Nightly integrity validation in progress");
    return IntegrityAsyncJobResponse.from(synthetic, IntegrityHeavyCryptoGate.Holder.NIGHTLY);
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
    IntegrityAsyncJob job =
        jobRepository
            .findLatestOperatorJob()
            .orElseThrow(
                () ->
                    new IntegrityAsyncJobAbandonNotAllowedException(
                        null, "No Integrity async job to abandon"));
    if (job.getAbandonedAt() != null) {
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
   * Delegates to {@link IntegrityNightlyTerminalService#tryInsertNightlyTerminalSucceeded}.
   *
   * @param result nightly validation result
   */
  public void tryInsertNightlyTerminalSucceeded(RetroactiveIntegrityValidationResult result) {
    nightlyTerminalService.tryInsertNightlyTerminalSucceeded(result);
  }

  /**
   * Delegates to {@link IntegrityNightlyTerminalService#tryInsertNightlyTerminalFailed}.
   *
   * @param scopeFrom inclusive window start
   * @param scopeTo exclusive window end
   * @param errorSimpleName exception simple name
   */
  public void tryInsertNightlyTerminalFailed(
      OffsetDateTime scopeFrom, OffsetDateTime scopeTo, String errorSimpleName) {
    nightlyTerminalService.tryInsertNightlyTerminalFailed(scopeFrom, scopeTo, errorSimpleName);
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
    Optional<IntegrityAsyncJob> latest = jobRepository.findLatestActiveOperatorJob();
    if (latest.isEmpty()) {
      return Optional.empty();
    }
    IntegrityAsyncJob job = latest.get();
    if (isEscapeStickyStatus(job.getStatus())) {
      return Optional.of(job);
    }
    // Latest non-abandoned is SUCCEEDED/FAILED/etc. — slot free for Start.
    return Optional.empty();
  }

  private void runJob(UUID jobId) {
    if (!heavyCryptoGate.tryEnter(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB)) {
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
      // Heartbeat failure is fail-closed: exception escapes to markFailed below (job → FAILED).
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
      // Full exception stays in the log only — audit / job summaries must not seal SQL or values.
      // Includes heartbeat persistence failures (fail-closed → FAILED).
      logger.error("Integrity async job {} failed: {}", jobId, ex.getMessage(), ex);
      stateService.markFailed(jobId, "Job failed: " + ex.getClass().getSimpleName());
    } finally {
      heavyCryptoGate.exit(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB);
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
    stateService.markSucceeded(job.getJobId(), summary, report.intact(), null, null);
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
    stateService.markSucceeded(
        job.getJobId(),
        summary,
        report.intact(),
        null,
        IntegrityChainVerdictFields.fromChainReport(report));
  }

  private void completeValidation(IntegrityAsyncJob job, Runnable heartbeat) {
    boolean raiseAlert = job.getRaiseAlert() == null || job.getRaiseAlert();
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        retroactiveIntegrityValidationService.runValidation(
            job.getScopeFrom(),
            job.getScopeTo(),
            RetroactiveIntegrityValidationOptions.operator(raiseAlert, job.getStartedByAdminId()),
            heartbeat);
    if (result.skipped()) {
      stateService.markSucceeded(
          job.getJobId(),
          "Validation skipped: " + result.skipReason(),
          true,
          null,
          IntegrityChainVerdictFields.notVerified());
      return;
    }
    stateService.markSucceeded(
        job.getJobId(),
        result.outcomeSummary("Validation"),
        result.intact(),
        result.alertId(),
        IntegrityChainVerdictFields.fromValidation(result));
  }

  private void emitStartedAudit(Integer adminId, IntegrityAsyncJob job) {
    String details =
        AuditDetailsBuilder.builder()
            .custom("jobId", job.getJobId().toString())
            .custom("jobType", job.getJobType().name())
            .custom("jobStatus", job.getStatus().name())
            .custom("summary", "Integrity async job started")
            .toJson();
    // Builder validation (required fields) must fail loudly — do not wrap build().
    AuditLog log =
        AuditLog.builder()
            .apiName(ApiName.ADMIN_API)
            .eventType(EventType.INTEGRITY_ASYNC_JOB_STARTED)
            .eventAction(IntegrityAsyncJobAuditConstants.EVENT_ACTION)
            .eventStatus(EventStatus.SUCCESS)
            .adminId(adminId)
            .eventDetails(details)
            .build();
    try {
      auditLogService.log(log);
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn("Failed to emit Integrity async job start audit: {}", ex.getMessage());
    }
  }

  private void emitAbandonedAudit(Integer adminId, IntegrityAsyncJob job) {
    String details =
        AuditDetailsBuilder.builder()
            .custom("jobId", job.getJobId().toString())
            .custom("jobType", job.getJobType().name())
            .custom("jobStatus", job.getStatus().name())
            .custom("summary", "Integrity async job slot abandoned")
            .toJson();
    // Builder validation (required fields) must fail loudly — do not wrap build().
    AuditLog log =
        AuditLog.builder()
            .apiName(ApiName.ADMIN_API)
            .eventType(EventType.INTEGRITY_ASYNC_JOB_ABANDONED)
            .eventAction(IntegrityAsyncJobAuditConstants.EVENT_ACTION)
            .eventStatus(EventStatus.SUCCESS)
            .adminId(adminId)
            .eventDetails(details)
            .build();
    try {
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
}
