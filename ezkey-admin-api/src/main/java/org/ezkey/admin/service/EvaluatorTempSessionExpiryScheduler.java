/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: EvaluatorTempSessionExpiryScheduler
 * Description: Scheduled expiry processing for EVALUATOR_TEMP sessions (Mode C).
 */

package org.ezkey.admin.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically processes expired {@code EVALUATOR_TEMP} tokens (revoke + identity / soft tenant
 * deactivate per predicate).
 */
@Component
public class EvaluatorTempSessionExpiryScheduler {

  private static final Logger logger =
      LoggerFactory.getLogger(EvaluatorTempSessionExpiryScheduler.class);

  private final EvaluatorTempSessionService evaluatorTempSessionService;

  /**
   * Creates the expiry scheduler.
   *
   * @param evaluatorTempSessionService Mode C session service
   */
  public EvaluatorTempSessionExpiryScheduler(
      EvaluatorTempSessionService evaluatorTempSessionService) {
    this.evaluatorTempSessionService = evaluatorTempSessionService;
  }

  /** Runs every 5 minutes; ShedLock coordinates multi-instance Admin API. */
  @Scheduled(cron = "0 */5 * * * *")
  @SchedulerLock(
      name = "EVALUATOR_TEMP_SESSION_EXPIRY",
      lockAtMostFor = "4m",
      lockAtLeastFor = "30s")
  public void expireEvaluatorTempSessions() {
    int processed = evaluatorTempSessionService.processExpiredTempSessions();
    if (processed > 0) {
      logger.info("Processed {} expired EVALUATOR_TEMP session(s)", processed);
    }
  }
}
