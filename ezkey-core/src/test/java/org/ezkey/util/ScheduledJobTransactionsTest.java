/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ScheduledJobTransactionsTest
 * Description: a caught participant failure rolls the scheduled job back quietly (#1537).
 */

package org.ezkey.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionTemplate;

/** Unit tests for {@link ScheduledJobTransactions}. */
@DisplayName("ScheduledJobTransactions")
class ScheduledJobTransactionsTest {

  @Test
  @DisplayName("without the mark, a caught participant failure ends in UnexpectedRollback")
  void caughtFailureWithoutMark_failsCommit() {
    RecordingTransactionManager tx = new RecordingTransactionManager();
    Job job = proxy(new Job(tx, false), tx);

    assertThrows(UnexpectedRollbackException.class, job::run);
    assertEquals(0, tx.commits);
  }

  @Test
  @DisplayName("with the mark, a caught participant failure rolls back quietly")
  void caughtFailureWithMark_rollsBackQuietly() {
    RecordingTransactionManager tx = new RecordingTransactionManager();
    Job job = proxy(new Job(tx, true), tx);

    assertDoesNotThrow(job::run);
    assertEquals(0, tx.commits);
    assertEquals(1, tx.rollbacks);
  }

  @Test
  @DisplayName("conditional mark: a doomed transaction rolls back quietly")
  void conditionalMark_doomed_rollsBackQuietly() {
    RecordingTransactionManager tx = new RecordingTransactionManager();
    Job job = proxy(new Job(tx, true, true, true), tx);

    assertDoesNotThrow(job::run);
    assertEquals(0, tx.commits);
    assertEquals(1, tx.rollbacks);
  }

  @Test
  @DisplayName("conditional mark: a failure that did not doom the transaction still commits")
  void conditionalMark_notDoomed_commits() {
    RecordingTransactionManager tx = new RecordingTransactionManager();
    Job job = proxy(new Job(tx, true, true, false), tx);

    assertDoesNotThrow(job::run);
    assertEquals(1, tx.commits);
    assertEquals(0, tx.rollbacks);
  }

  @Test
  @DisplayName("outside a proxied transaction the mark does nothing")
  void noTransaction_noop() {
    assertDoesNotThrow(ScheduledJobTransactions::markCurrentTransactionRollbackOnly);
    assertDoesNotThrow(ScheduledJobTransactions::markRollbackOnlyIfAlreadyDoomed);
  }

  private static Job proxy(Job target, PlatformTransactionManager tx) {
    ProxyFactory factory = new ProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAdvice(
        new TransactionInterceptor(
            (TransactionManager) tx, new AnnotationTransactionAttributeSource()));
    return (Job) factory.getProxy();
  }

  /** A scheduled-job shape: a participating call fails, the job catches, logs and returns. */
  static class Job {
    private final PlatformTransactionManager tx;
    private final boolean mark;
    private final boolean conditional;
    private final boolean participantFails;

    Job(PlatformTransactionManager tx, boolean mark) {
      this(tx, mark, false, true);
    }

    Job(
        PlatformTransactionManager tx,
        boolean mark,
        boolean conditional,
        boolean participantFails) {
      this.tx = tx;
      this.mark = mark;
      this.conditional = conditional;
      this.participantFails = participantFails;
    }

    @Transactional
    public void run() {
      try {
        if (participantFails) {
          new TransactionTemplate(tx)
              .executeWithoutResult(_ -> {
                throw new DataAccessResourceFailureException("connection refused");
              });
        }
        throw new IllegalStateException("job step failed outside the database");
      } catch (DataAccessResourceFailureException | IllegalStateException e) {
        if (mark && conditional) {
          ScheduledJobTransactions.markRollbackOnlyIfAlreadyDoomed();
        } else if (mark) {
          ScheduledJobTransactions.markCurrentTransactionRollbackOnly();
        }
      }
    }
  }

  /** Minimal transaction manager that records outcomes and honours participant rollback-only. */
  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    private static final long serialVersionUID = 1L;

    private transient TxObject current;
    private int commits;
    private int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return current != null ? current : new TxObject();
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
      return current != null && transaction == current;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      current = (TxObject) transaction;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
      ((TxObject) status.getTransaction()).rollbackOnly = true;
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
      current = null;
    }
  }

  private static final class TxObject implements SmartTransactionObject {
    private boolean rollbackOnly;

    @Override
    public boolean isRollbackOnly() {
      return rollbackOnly;
    }

    @Override
    public void flush() {
      // nothing to flush
    }
  }
}
