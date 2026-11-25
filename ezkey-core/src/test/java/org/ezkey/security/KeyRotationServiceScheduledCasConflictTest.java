/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationServiceScheduledCasConflictTest
 * Description: a lost keyset CAS in the scheduled rotation/promotion is a benign HA race (#1018).
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.exception.KeysetCasConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit and log-capture tests for the lost keyset CAS race in {@link
 * KeyRotationService#checkAndRotate()} and {@link KeyRotationService#checkAndPromotePendingKeys()}
 * (#1018). A lost CAS is a WARN without stack trace and no audit event; any other {@link
 * IllegalStateException}, including a persistence outage that {@code TinkKeyManager} also wraps as
 * a CAS failure, keeps the ERROR log and the {@code REENCRYPTION_FAILED} audit event. The
 * transactional case checks that the lost race rolls the job back quietly instead of failing the
 * commit with {@code UnexpectedRollbackException}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyRotationService scheduled lost CAS race")
class KeyRotationServiceScheduledCasConflictTest {

  private static final long PRIMARY_KEY_ID = 1111L;
  private static final long PENDING_KEY_ID = 2222L;

  @Mock private KeyManagementOperations keyManagementOperations;
  @Mock private EncryptionKeyRepository keyRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private EncryptionKeyMigrationScopeService migrationScopeService;
  @Mock private KeyUsageVerificationService keyUsageVerificationService;
  @Mock private PlatformTransactionManager transactionManager;

  private KeyRotationService service;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;

  @BeforeEach
  void setUp() {
    service =
        new KeyRotationService(
            keyManagementOperations,
            keyRepository,
            new TinkProperties(),
            auditLogService,
            migrationScopeService,
            keyUsageVerificationService,
            transactionManager);
    serviceLogger = (Logger) LoggerFactory.getLogger(KeyRotationService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    serviceLogger.detachAppender(logAppender);
  }

  /** Initialized keyset, an expired PRIMARY key and no PENDING key: rotation is due. */
  private void givenRotationDue() {
    EncryptionKey primary =
        new EncryptionKey(
            PRIMARY_KEY_ID,
            KeyStatus.PRIMARY,
            "AES256_GCM",
            OffsetDateTime.now().minusDays(365),
            "TEST");
    when(keyManagementOperations.isInitialized()).thenReturn(true);
    when(keyRepository.findByKeyStatus(KeyStatus.PRIMARY)).thenReturn(List.of(primary));
    when(keyRepository.existsPendingKey()).thenReturn(false);
  }

  /** What TinkKeyManager throws when a peer committed first (version pre-check or @Version). */
  private static KeysetCasConflictException lostRace() {
    return new KeysetCasConflictException(
        "Keyset mutation CAS failed (updatedBy=KEY_ADDED_PENDING); staging discarded",
        new OptimisticLockingFailureException("Keyset blob version mismatch before save"));
  }

  private List<ILoggingEvent> events(Level level) {
    return logAppender.list.stream().filter(e -> e.getLevel() == level).toList();
  }

  @Test
  @DisplayName("checkAndRotate: lost CAS logs one WARN, no stack trace, no audit, no throw")
  void checkAndRotate_lostCas_warnsWithoutAudit() throws Exception {
    givenRotationDue();
    when(keyManagementOperations.addKeyWithoutPromotion()).thenThrow(lostRace());

    assertDoesNotThrow(() -> service.checkAndRotate());

    verify(auditLogService, never()).log(any());
    assertTrue(events(Level.ERROR).isEmpty(), "a lost CAS must not log at ERROR");
    List<ILoggingEvent> warns =
        events(Level.WARN).stream()
            .filter(e -> e.getFormattedMessage().contains("lost keyset CAS race"))
            .toList();
    assertEquals(1, warns.size(), "expected exactly one lost-CAS WARN");
    assertTrue(warns.get(0).getFormattedMessage().contains("will retry on the next scheduled run"));
    assertNull(warns.get(0).getThrowableProxy(), "the WARN must not carry a stack trace");
  }

  @Test
  @DisplayName("checkAndRotate: another IllegalStateException keeps ERROR and one audit event")
  void checkAndRotate_otherIllegalState_errorAndAudit() throws Exception {
    givenRotationDue();
    when(keyManagementOperations.addKeyWithoutPromotion())
        .thenThrow(new IllegalStateException("keyset storage unavailable"));

    assertDoesNotThrow(() -> service.checkAndRotate());

    ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(audit.capture());
    assertEquals(EventType.REENCRYPTION_FAILED, audit.getValue().getEventType());
    assertEquals(EventStatus.ERROR, audit.getValue().getEventStatus());
    assertEquals(1, events(Level.ERROR).size(), "expected the ERROR log to stay");
    assertTrue(
        events(Level.WARN).stream()
            .noneMatch(e -> e.getFormattedMessage().contains("lost keyset CAS race")),
        "a non-CAS failure must not be reported as a lost race");
  }

  @Test
  @DisplayName("checkAndPromotePendingKeys: lost CAS logs WARN, no ERROR, no audit")
  void checkAndPromotePendingKeys_lostCas_warnsWithoutAudit() throws Exception {
    EncryptionKey pending =
        new EncryptionKey(
            PENDING_KEY_ID, KeyStatus.PENDING, "AES256_GCM", OffsetDateTime.now(), "TEST");
    when(keyManagementOperations.isInitialized()).thenReturn(true);
    when(keyRepository.findPendingKeysReadyForPromotion(any())).thenReturn(List.of(pending));
    when(keyManagementOperations.promoteToPrimary(PENDING_KEY_ID)).thenThrow(lostRace());

    assertDoesNotThrow(() -> service.checkAndPromotePendingKeys());

    verify(auditLogService, never()).log(any());
    assertTrue(events(Level.ERROR).isEmpty(), "a lost CAS must not log at ERROR");
    List<ILoggingEvent> warns =
        events(Level.WARN).stream()
            .filter(e -> e.getFormattedMessage().contains("lost keyset CAS race"))
            .toList();
    assertEquals(1, warns.size(), "expected exactly one lost-CAS WARN");
    assertNull(warns.get(0).getThrowableProxy(), "the WARN must not carry a stack trace");
  }

  @Test
  @DisplayName("checkAndRotate: CAS wrapper around a persistence outage keeps ERROR and audit")
  void checkAndRotate_casWrappedOutage_errorAndAudit() throws Exception {
    givenRotationDue();
    when(keyManagementOperations.addKeyWithoutPromotion())
        .thenThrow(
            new KeysetCasConflictException(
                "Keyset mutation CAS failed (updatedBy=KEY_ADDED_PENDING); staging discarded",
                new DataAccessResourceFailureException("connection refused")));

    assertDoesNotThrow(() -> service.checkAndRotate());

    ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(audit.capture());
    assertEquals(EventType.REENCRYPTION_FAILED, audit.getValue().getEventType());
    assertEquals(1, events(Level.ERROR).size(), "a real persistence failure must stay an ERROR");
  }

  @Test
  @DisplayName("checkAndRotate in a transaction: lost @Version race rolls back quietly")
  void checkAndRotate_lostVersionRaceInTransaction_rollsBackWithoutUnexpectedRollback()
      throws Exception {
    givenRotationDue();
    RecordingTransactionManager txManager = new RecordingTransactionManager();
    // Like saveAndFlush through the repository proxy: the participating call fails, which marks
    // the outer transaction rollback-only, then TinkKeyManager wraps the conflict.
    when(keyManagementOperations.addKeyWithoutPromotion())
        .thenAnswer(_ -> {
          try {
            new TransactionTemplate(txManager)
                .executeWithoutResult(_ -> {
                  throw new ObjectOptimisticLockingFailureException("KeysetBlob", Long.valueOf(1L));
                });
          } catch (ObjectOptimisticLockingFailureException e) {
            throw new KeysetCasConflictException("Keyset mutation CAS failed", e);
          }
          return 0L;
        });

    KeyRotationService proxied = transactionalProxy(txManager);

    assertDoesNotThrow(proxied::checkAndRotate, "commit must not raise UnexpectedRollback");
    assertEquals(0, txManager.commits, "the lost race must not commit");
    assertEquals(1, txManager.rollbacks, "the lost race must roll back the job transaction");
    verify(auditLogService, never()).log(any());
    assertTrue(events(Level.ERROR).isEmpty(), "a lost CAS must not log at ERROR");
  }

  private KeyRotationService transactionalProxy(PlatformTransactionManager txManager) {
    TransactionInterceptor interceptor =
        new TransactionInterceptor(
            (TransactionManager) txManager, new AnnotationTransactionAttributeSource());
    ProxyFactory factory = new ProxyFactory(service);
    factory.setProxyTargetClass(true);
    factory.addAdvice(interceptor);
    return (KeyRotationService) factory.getProxy();
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
