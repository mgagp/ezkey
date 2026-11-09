/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: AuthAttemptEnrollmentAlert
 * Description: Raises the enrollment attempt alert after the status transaction commits.
 */

package org.ezkey.authattempt.service;

import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Raises the shared enrollment alert after the status change has committed.
 *
 * <p>The alert must not run inside the transaction that writes {@code INVALID} or {@code REJECTED}.
 * A concurrent insert can hit {@code uq_alert_dedupe_key_open} and abort that Postgres transaction;
 * the status change would roll back, the attempt would stay {@code READ}, and respond would return
 * 500. The raise is fail-open: a failure is logged and does not propagate to the caller.
 *
 * @since 2026
 */
@Component
public class AuthAttemptEnrollmentAlert {

  private static final Logger logger = LoggerFactory.getLogger(AuthAttemptEnrollmentAlert.class);

  private final AlertService alertService;

  /**
   * Creates the scheduler.
   *
   * @param alertService operator alerts
   */
  public AuthAttemptEnrollmentAlert(AlertService alertService) {
    this.alertService = alertService;
  }

  /**
   * Schedules the enrollment alert. When a transaction is active the raise runs in {@code
   * afterCommit}; otherwise it runs immediately. The payload is the enrollment id only.
   *
   * @param enrollmentId enrollment the attempt belongs to; ignored when null
   */
  public void raiseAfterCommit(Integer enrollmentId) {
    if (enrollmentId == null) {
      return;
    }
    Runnable raise = () -> raiseFailOpen(enrollmentId);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              raise.run();
            }
          });
      return;
    }
    raise.run();
  }

  /**
   * Raises the alert and swallows failures so a dedupe-index race cannot undo the status change.
   *
   * @param enrollmentId enrollment id stored in the payload
   */
  private void raiseFailOpen(Integer enrollmentId) {
    try {
      alertService.raiseOrTouch(
          AlertType.AUTH_ATTEMPT_REJECTED,
          AlertSeverity.WARNING,
          "AUTH_ATTEMPT_REJECTED:" + enrollmentId,
          "{\"enrollmentId\":" + enrollmentId + "}");
    } catch (RuntimeException ex) { // CHECKSTYLE IGNORE IllegalCatch
      logger.warn(
          "Enrollment attempt alert was not recorded for enrollment {}: {}",
          enrollmentId,
          ex.toString());
    }
  }
}
