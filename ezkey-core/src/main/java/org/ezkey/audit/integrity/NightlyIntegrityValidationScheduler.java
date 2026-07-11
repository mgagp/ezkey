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
 * <p>Delegates orchestration to {@link RetroactiveIntegrityValidationService}; disabling {@code
 * ezkey.audit.integrity.nightly.enabled} idles this scheduler only — operator POST detect remains
 * available when HMAC is active.
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

  /**
   * Constructs the scheduler.
   *
   * @param nightlyProperties nightly batch configuration
   * @param chainProperties rolling checkpoint window size (grid alignment)
   * @param validationService retroactive validation orchestration
   * @param jobLastRunService registry updates
   */
  public NightlyIntegrityValidationScheduler(
      NightlyIntegrityProperties nightlyProperties,
      AuditChainProperties chainProperties,
      RetroactiveIntegrityValidationService validationService,
      ScheduledJobLastRunService jobLastRunService) {
    this.nightlyProperties = nightlyProperties;
    this.chainProperties = chainProperties;
    this.validationService = validationService;
    this.jobLastRunService = jobLastRunService;
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

    try {
      RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
          validationService.validateWindow(windowEnd);
      if (result.scope() != null) {
        scope = result.scope();
      }
      jobLastRunService.recordSuccess(ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION, scope);
    } catch (Exception e) {
      logger.error("Nightly integrity validation batch failed: {}", e.getMessage(), e);
      jobLastRunService.recordFailure(
          ScheduledJobKey.NIGHTLY_INTEGRITY_VALIDATION, scope, e.getMessage());
    }
  }
}
