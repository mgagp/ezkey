/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AuthAttemptExpiryScheduler
 * Description: Scheduled job to persist EXPIRED status for auth attempts past their TTL.
 */

package org.ezkey.authattempt.service;

import java.time.OffsetDateTime;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks {@link AuthAttemptStatus#PENDING} and {@link AuthAttemptStatus#READ} rows as {@link
 * AuthAttemptStatus#EXPIRED} when {@code expires_at} is in the past.
 *
 * <p>Without this job, rows could remain {@code PENDING} or {@code READ} indefinitely after TTL
 * while {@link org.ezkey.authattempt.service.AuthAttemptWaitService} only computes {@code EXPIRED}
 * at read time. This scheduler aligns persisted state with real-world expiry.
 *
 * <p>Runs in processes that enable scheduling and ShedLock (e.g. Admin API). Default: every 60
 * seconds, configurable via {@code ezkey.auth-attempt.expiry-scheduler.fixed-delay-ms}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Component
@ConditionalOnProperty(
    name = "ezkey.auth-attempt.expiry-scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class AuthAttemptExpiryScheduler {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptExpiryScheduler.class);

  private static final List<AuthAttemptStatus> EXPIRABLE_STATUSES =
      List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ);

  private final AuthAttemptRepository authAttemptRepository;

  public AuthAttemptExpiryScheduler(AuthAttemptRepository authAttemptRepository) {
    this.authAttemptRepository = authAttemptRepository;
  }

  /**
   * Bulk-updates auth attempts that are past {@code expires_at} to {@link
   * AuthAttemptStatus#EXPIRED}.
   */
  @Scheduled(
      fixedDelayString = "${ezkey.auth-attempt.expiry-scheduler.fixed-delay-ms:60000}",
      initialDelayString = "${ezkey.auth-attempt.expiry-scheduler.initial-delay-ms:60000}")
  @SchedulerLock(name = "AUTH_ATTEMPT_EXPIRY", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  @Transactional
  public void expireStalePendingAndReadAttempts() {
    OffsetDateTime now = OffsetDateTime.now();
    int updated =
        authAttemptRepository.expireAttemptsPastDeadline(
            EXPIRABLE_STATUSES, now, AuthAttemptStatus.EXPIRED);
    if (updated > 0) {
      logger.info(
          "Auth attempt expiry: marked {} attempt(s) as EXPIRED (past expires_at)", updated);
    } else {
      logger.debug("Auth attempt expiry: no attempts past expires_at to update");
    }
  }
}
