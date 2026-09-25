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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private final NightlyIntegrityProperties nightlyProperties;
  private final AuditChainProperties chainProperties;
  private final RetroactiveIntegrityValidationService validationService;
  private final ScheduledJobLastRunService jobLastRunService;
  private final IntegrityHeavyCryptoGate heavyCryptoGate;
  private final IntegrityAsyncJobService integrityAsyncJobService;

  /**
   * Constructs the scheduler.
   *
   * @param nightlyProperties nightly batch configuration
   * @param chainProperties rolling checkpoint window size (grid alignment)
   * @param validationService retroactive validation orchestration
   * @param jobLastRunService registry updates
   * @param heavyCryptoGate process-local exclusion vs operator async jobs
   * @param integrityAsyncJobService operator slot probe (skip when RUNNING)
   */
  public NightlyIntegrityValidationScheduler(
      NightlyIntegrityProperties nightlyProperties,
      AuditChainProperties chainProperties,
      RetroactiveIntegrityValidationService validationService,
      ScheduledJobLastRunService jobLastRunService,
      IntegrityHeavyCryptoGate heavyCryptoGate,
      IntegrityAsyncJobService integrityAsyncJobService) {
    this.nightlyProperties = nightlyProperties;
    this.chainProperties = chainProperties;
    this.validationService = validationService;
    this.jobLastRunService = jobLastRunService;
    this.heavyCryptoGate = heavyCryptoGate;
    this.integrityAsyncJobService = integrityAsyncJobService;
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

    if (integrityAsyncJobService.isOperatorSlotRunning()) {
      logger.info("Skipping nightly integrity validation: operator Integrity async job is RUNNING");
      return;
    }
    if (!heavyCryptoGate.tryEnter()) {
      logger.info("Skipping nightly integrity validation: Integrity heavy crypto gate is busy");
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
}
