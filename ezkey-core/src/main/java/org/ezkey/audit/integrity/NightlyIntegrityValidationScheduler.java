/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Scheduler: NightlyIntegrityValidationScheduler
 * Description: Scheduled nightly retroactive integrity validation batch.
 */

package org.ezkey.audit.integrity;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the nightly retroactive integrity validation batch (detective layer per {@code
 * V-2026-0004}).
 *
 * <p>Delegates orchestration to {@link RetroactiveIntegrityValidationService}. Disabling {@code
 * ezkey.audit.integrity.nightly.enabled} idles this scheduler <strong>and</strong> fail-closes
 * operator {@code POST …/integrity-validation/run} (same flag, not the product profile name).
 *
 * <p>When {@link IntegrityHeavyCryptoGate} is busy, retries every {@link #GATE_BUSY_RETRY_INTERVAL}
 * for up to {@link #GATE_BUSY_RETRY_BUDGET} before recording a fail-closed registry failure (skip
 * must be observable).
 *
 * @since 2026
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.audit.integrity.nightly.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NightlyIntegrityValidationScheduler {

  private static final Logger logger =
      LoggerFactory.getLogger(NightlyIntegrityValidationScheduler.class);

  /** Delay between gate-busy retries. */
  static final Duration GATE_BUSY_RETRY_INTERVAL = Duration.ofMinutes(5);

  /** Maximum time spent waiting for the heavy-crypto gate before give-up. */
  static final Duration GATE_BUSY_RETRY_BUDGET = Duration.ofHours(1);

  /** Registry / log summary when the gate stays busy past the retry budget. */
  static final String GATE_BUSY_SKIP_SUMMARY = "skipped: heavy crypto gate busy";

  /** Interruptible wait between gate-busy retries (test seam). */
  @FunctionalInterface
  interface InterruptibleSleeper {
    /**
     * Blocks for the given duration.
     *
     * @param duration sleep length
     * @throws InterruptedException when interrupted
     */
    void sleep(Duration duration) throws InterruptedException;
  }

  private final NightlyIntegrityProperties nightlyProperties;
  private final AuditChainProperties chainProperties;
  private final RetroactiveIntegrityValidationService validationService;
  private final ScheduledJobLastRunService jobLastRunService;
  private final IntegrityHeavyCryptoGate heavyCryptoGate;
  private final ObjectProvider<IntegrityAsyncJobService> integrityAsyncJobService;

  private Duration gateBusyRetryInterval = GATE_BUSY_RETRY_INTERVAL;
  private Duration gateBusyRetryBudget = GATE_BUSY_RETRY_BUDGET;
  private InterruptibleSleeper sleeper = duration -> Thread.sleep(duration.toMillis());

  /**
   * Constructs the scheduler with production retry defaults.
   *
   * @param nightlyProperties nightly batch configuration
   * @param chainProperties rolling checkpoint window size (grid alignment)
   * @param validationService retroactive validation orchestration
   * @param jobLastRunService registry updates
   * @param heavyCryptoGate process-local exclusion vs operator async jobs
   * @param integrityAsyncJobService operator slot probe (Admin-only bean; empty on peripherals)
   */
  public NightlyIntegrityValidationScheduler(
      NightlyIntegrityProperties nightlyProperties,
      AuditChainProperties chainProperties,
      RetroactiveIntegrityValidationService validationService,
      ScheduledJobLastRunService jobLastRunService,
      IntegrityHeavyCryptoGate heavyCryptoGate,
      ObjectProvider<IntegrityAsyncJobService> integrityAsyncJobService) {
    this.nightlyProperties = nightlyProperties;
    this.chainProperties = chainProperties;
    this.validationService = validationService;
    this.jobLastRunService = jobLastRunService;
    this.heavyCryptoGate = heavyCryptoGate;
    this.integrityAsyncJobService = integrityAsyncJobService;
  }

  /**
   * Package-visible test seam for gate-busy retry timing (avoids a second Spring constructor).
   *
   * @param interval delay between retries
   * @param budget max wait before give-up
   * @param sleeper interruptible wait between retries
   */
  void configureGateBusyRetryForTests(
      Duration interval, Duration budget, InterruptibleSleeper waitBetweenRetries) {
    this.gateBusyRetryInterval = interval;
    this.gateBusyRetryBudget = budget;
    this.sleeper = waitBetweenRetries;
  }

  /**
   * Scheduled nightly retroactive integrity validation.
   *
   * <p>Batch infrastructure failures update the job registry only (C9); integrity ruptures raise
   * alerts via {@link RetroactiveIntegrityValidationService}.
   *
   * <p>{@code windowEnd} is rounded down to the checkpoint grid (ADR-0008) so sub-second wall-clock
   * precision cannot exclude the first aligned checkpoint and invent a leading undeclared gap.
   */
  @Scheduled(cron = "${ezkey.audit.integrity.nightly.cron:0 0 2 * * ?}")
  @SchedulerLock(name = "NIGHTLY_INTEGRITY_VALIDATION", lockAtMostFor = "PT2H")
  public void runNightlyValidation() {
    OffsetDateTime windowEnd =
        AuditChainScheduler.roundDownToWindow(
            OffsetDateTime.now(ZoneOffset.UTC), chainProperties.getWindowMinutes());
    String scope = "Validated " + nightlyProperties.getWindowHours() + " h ending " + windowEnd;

    IntegrityAsyncJobService asyncJobs = integrityAsyncJobService.getIfAvailable();
    if (asyncJobs != null && asyncJobs.isOperatorSlotRunning()) {
      logger.info("Skipping nightly integrity validation: operator Integrity async job is RUNNING");
      return;
    }
    if (!acquireHeavyCryptoGate(scope)) {
      return;
    }
    try {
      RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
          validationService.validateWindow(windowEnd);
      if (result.scope() != null) {
        scope = result.scope();
      }
      jobLastRunService.recordSuccess(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION, scope);
    } catch (Exception e) { // CHECKSTYLE IGNORE IllegalCatch
      logger.error("Nightly integrity validation batch failed: {}", e.getMessage(), e);
      jobLastRunService.recordFailure(
          ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION, scope, e.getMessage());
    } finally {
      heavyCryptoGate.exit();
    }
  }

  /**
   * Tries to enter the heavy-crypto gate, retrying while the budget remains.
   *
   * @param scope registry scope used if give-up records a failure
   * @return {@code true} when the gate was acquired (caller must {@code exit})
   */
  private boolean acquireHeavyCryptoGate(String scope) {
    long intervalMillis = Math.max(1L, gateBusyRetryInterval.toMillis());
    int maxAttempts = Math.max(1, (int) (gateBusyRetryBudget.toMillis() / intervalMillis));
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      if (heavyCryptoGate.tryEnter()) {
        return true;
      }
      if (attempt >= maxAttempts) {
        break;
      }
      logger.warn(
          "Integrity heavy crypto gate busy; retrying nightly validation in {} minutes"
              + " (attempt {}/{})",
          gateBusyRetryInterval.toMinutes(),
          attempt,
          maxAttempts);
      try {
        sleeper.sleep(gateBusyRetryInterval);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("Nightly integrity validation interrupted while waiting for heavy crypto gate");
        jobLastRunService.recordFailure(
            ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION, scope, GATE_BUSY_SKIP_SUMMARY);
        return false;
      }
    }
    logger.warn(
        "Skipping nightly integrity validation after {} retries: {}",
        maxAttempts,
        GATE_BUSY_SKIP_SUMMARY);
    jobLastRunService.recordFailure(
        ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION, scope, GATE_BUSY_SKIP_SUMMARY);
    return false;
  }
}
