/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: IntegrityNightlyTerminalService
 * Description: Nightly Integrity verdict terminal rows (OQ4 A′); tryInsert uses
 * TransactionTemplate REQUIRES_NEW so WARN catch stays outside the TX.
 */

package org.ezkey.audit.asyncjob;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists nightly terminal Integrity job rows for OQ4 A′.
 *
 * <p>Public {@code insert*} methods keep declarative {@code @Transactional(REQUIRES_NEW)} for
 * external callers through the Spring proxy. Scheduler / {@link IntegrityAsyncJobService} entry
 * points use {@code tryInsert*}, which run the insert inside a programmatic {@link
 * TransactionTemplate} with {@code PROPAGATION_REQUIRES_NEW}. That keeps the WARN-only {@code
 * catch} <em>outside</em> the transaction so commit-time failures (e.g. {@code
 * UnexpectedRollbackException}) are swallowed — annotating {@code tryInsert*} itself would put the
 * catch inside the TX boundary and let those failures escape. Self-invocation of {@code insert*}
 * from {@code tryInsert*} would bypass the proxy and would not apply {@code REQUIRES_NEW}.
 *
 * <p>Never RUNNING; never via {@link IntegrityAsyncJobStateService#markSucceeded} (that would
 * double-audit). Insert failures after a successful validation are WARN-only — the job registry
 * stays SUCCESS.
 *
 * @since 2026
 */
@Service
@ConditionalOnProperty(
    name = "ezkey.audit.integrity.async-job.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class IntegrityNightlyTerminalService {

  private static final Logger logger =
      LoggerFactory.getLogger(IntegrityNightlyTerminalService.class);

  private final IntegrityAsyncJobRepository jobRepository;
  private final TransactionTemplate requiresNew;

  /**
   * Constructs the nightly terminal writer.
   *
   * @param jobRepository job persistence
   * @param transactionManager platform transaction manager for programmatic REQUIRES_NEW
   */
  public IntegrityNightlyTerminalService(
      IntegrityAsyncJobRepository jobRepository, PlatformTransactionManager transactionManager) {
    this.jobRepository = jobRepository;
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.requiresNew = tx;
  }

  /**
   * Inserts a terminal SUCCEEDED nightly verdict row (OQ4 A′). No row when {@code result.skipped()}
   * (HMAC inactive).
   *
   * <p>Declarative {@code REQUIRES_NEW} for external callers through the Spring proxy. Prefer
   * {@link #tryInsertNightlyTerminalSucceeded} from the nightly path so insert failures stay
   * WARN-only (including commit-time exceptions).
   *
   * @param result nightly validation result
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void insertNightlyTerminalSucceeded(RetroactiveIntegrityValidationResult result) {
    if (result == null || result.skipped()) {
      return;
    }
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    IntegrityAsyncJob job = newNightlyShell(result.windowStart(), result.windowEnd(), now);
    job.setStatus(IntegrityAsyncJobStatus.SUCCEEDED);
    job.setResultIntact(result.intact());
    job.setResultAlertId(result.alertId());
    job.setResultSummary(truncate(result.outcomeSummary("Nightly validation"), 1024));
    IntegrityChainVerdictFields.fromValidation(result).applyTo(job);
    jobRepository.save(job);
  }

  /**
   * Inserts a terminal FAILED nightly row when {@code validateWindow} throws. Persists only the
   * exception simple name (#741) — never {@code getMessage()}.
   *
   * <p>Declarative {@code REQUIRES_NEW} for external callers through the Spring proxy. Prefer
   * {@link #tryInsertNightlyTerminalFailed} from the nightly path so insert failures stay
   * WARN-only (including commit-time exceptions).
   *
   * @param scopeFrom inclusive window start
   * @param scopeTo exclusive window end
   * @param errorSimpleName {@code e.getClass().getSimpleName()}
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void insertNightlyTerminalFailed(
      OffsetDateTime scopeFrom, OffsetDateTime scopeTo, String errorSimpleName) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    IntegrityAsyncJob job = newNightlyShell(scopeFrom, scopeTo, now);
    job.setStatus(IntegrityAsyncJobStatus.FAILED);
    job.setResultIntact(false);
    String safe = errorSimpleName != null ? errorSimpleName : "Exception";
    job.setErrorSummary(truncate(safe, 512));
    job.setResultSummary(truncate("Failed: " + safe, 1024));
    jobRepository.save(job);
  }

  /**
   * Attempts a SUCCEEDED nightly terminal insert in a programmatic {@code REQUIRES_NEW}
   * transaction. Any failure (including commit-time {@code RuntimeException}) logs WARN with simple
   * name and swallows — validation already committed; callers must not treat this as a validation
   * failure. The catch stays outside the TX (do not put {@code @Transactional} on this method).
   *
   * @param result nightly validation result
   */
  public void tryInsertNightlyTerminalSucceeded(RetroactiveIntegrityValidationResult result) {
    try {
      requiresNew.executeWithoutResult(_ -> insertNightlyTerminalSucceeded(result));
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn(
          "Nightly terminal SUCCEEDED row insert failed: {}", ex.getClass().getSimpleName());
    }
  }

  /**
   * Attempts a FAILED nightly terminal insert in a programmatic {@code REQUIRES_NEW} transaction.
   * Any failure (including commit-time {@code RuntimeException}) logs WARN with simple name and
   * swallows. The catch stays outside the TX (do not put {@code @Transactional} on this method).
   *
   * @param scopeFrom inclusive window start
   * @param scopeTo exclusive window end
   * @param errorSimpleName exception simple name
   */
  public void tryInsertNightlyTerminalFailed(
      OffsetDateTime scopeFrom, OffsetDateTime scopeTo, String errorSimpleName) {
    try {
      requiresNew.executeWithoutResult(
          _ -> insertNightlyTerminalFailed(scopeFrom, scopeTo, errorSimpleName));
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn("Nightly terminal FAILED row insert failed: {}", ex.getClass().getSimpleName());
    }
  }

  private static IntegrityAsyncJob newNightlyShell(
      OffsetDateTime scopeFrom, OffsetDateTime scopeTo, OffsetDateTime now) {
    IntegrityAsyncJob job = new IntegrityAsyncJob();
    job.setJobId(UUID.randomUUID());
    job.setSlotKey(IntegrityAsyncJob.GLOBAL_SLOT_KEY);
    job.setJobType(IntegrityAsyncJobType.RUN_VALIDATION);
    job.setTrigger(IntegrityAsyncJobTrigger.NIGHTLY);
    job.setStartedByAdminId(null);
    job.setStartedByUsername("system");
    job.setStartedAt(now);
    job.setHeartbeatAt(now);
    job.setFinishedAt(now);
    job.setScopeFrom(scopeFrom);
    job.setScopeTo(scopeTo);
    job.setRaiseAlert(true);
    return job;
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }
}
