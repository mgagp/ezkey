/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Helper: ScheduledJobTransactions
 * Description: Quiet rollback for a scheduled @Transactional job that catches a failure and returns.
 */

package org.ezkey.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/**
 * Helper for scheduled {@code @Transactional} jobs that catch a failure, log it and return.
 *
 * <p>A repository call that fails through its proxy has already marked the job's transaction
 * rollback-only. Returning normally then fails the commit with {@code UnexpectedRollbackException},
 * a second ERROR with stack trace from the scheduler for one incident (stabilization lesson 7,
 * #1537). Marking the current transaction rollback-only locally before returning turns that commit
 * into a silent rollback.
 *
 * @since 2026
 */
public final class ScheduledJobTransactions {

  private static final Logger logger = LoggerFactory.getLogger(ScheduledJobTransactions.class);

  private ScheduledJobTransactions() {}

  /**
   * Marks the current Spring-managed transaction rollback-only. Does nothing outside a proxied
   * transaction (direct call, unit test).
   */
  public static void markCurrentTransactionRollbackOnly() {
    try {
      TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
    } catch (NoTransactionException e) {
      logger.debug("No proxied transaction to mark rollback-only");
    }
  }

  /**
   * Marks the current transaction rollback-only only when a participating call has already doomed
   * it, so work that would have committed still commits. Use it in a catch-all where some failures
   * do not come from the database: it removes the misleading {@code UnexpectedRollbackException}
   * without changing which runs commit. Does nothing outside a proxied transaction.
   */
  public static void markRollbackOnlyIfAlreadyDoomed() {
    try {
      TransactionStatus status = TransactionAspectSupport.currentTransactionStatus();
      if (status.isRollbackOnly()) {
        status.setRollbackOnly();
      }
    } catch (NoTransactionException e) {
      logger.debug("No proxied transaction to check for rollback-only");
    }
  }
}
