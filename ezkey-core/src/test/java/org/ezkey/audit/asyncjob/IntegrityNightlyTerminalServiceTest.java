/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.asyncjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationTriggerSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * Unit tests for {@link IntegrityNightlyTerminalService} (programmatic REQUIRES_NEW tryInsert +
 * WARN catch outside the TX).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class IntegrityNightlyTerminalServiceTest {

  @Mock private IntegrityAsyncJobRepository jobRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private TransactionStatus transactionStatus;

  private IntegrityNightlyTerminalService service;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger serviceLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    // tryInsert* uses TransactionTemplate; direct insert* tests do not.
    lenient()
        .when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(transactionStatus);
    service = new IntegrityNightlyTerminalService(jobRepository, transactionManager);
    serviceLogger = (Logger) LoggerFactory.getLogger(IntegrityNightlyTerminalService.class);
    previousLevel = serviceLogger.getLevel();
    serviceLogger.setLevel(Level.WARN);
    logAppender = new ListAppender<>();
    logAppender.start();
    serviceLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    serviceLogger.detachAppender(logAppender);
    serviceLogger.setLevel(previousLevel);
  }

  @Test
  void insertNightlyTerminalSucceeded_skipped_noPersist() {
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult skipped =
        RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult.skipped(
            "scope",
            "HMAC signing is not active",
            RetroactiveIntegrityValidationTriggerSource.SCHEDULED);

    service.insertNightlyTerminalSucceeded(skipped);

    verify(jobRepository, never()).save(any());
  }

  @Test
  void insertNightlyTerminalSucceeded_persistsNightlyTerminalWithCounters() {
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        new RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult(
            from,
            to,
            "scope",
            false,
            null,
            true,
            false,
            null,
            "OK",
            0,
            0,
            0,
            RetroactiveIntegrityValidationTriggerSource.SCHEDULED,
            288,
            288,
            0,
            0,
            0);
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.insertNightlyTerminalSucceeded(result);

    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    IntegrityAsyncJob saved = captor.getValue();
    assertEquals(IntegrityAsyncJobTrigger.NIGHTLY, saved.getTrigger());
    assertEquals(IntegrityAsyncJobType.RUN_VALIDATION, saved.getJobType());
    assertEquals(IntegrityAsyncJobStatus.SUCCEEDED, saved.getStatus());
    assertNull(saved.getStartedByAdminId());
    assertEquals("system", saved.getStartedByUsername());
    assertEquals(IntegrityChainStatus.OK, saved.getChainStatus());
    assertEquals(288, saved.getTotalCheckpoints());
    assertEquals(0, saved.getEntryHmacViolations());
    assertTrue(saved.getFinishedAt() != null);
    assertTrue(saved.getResultSummary().startsWith("Nightly validation:"));
  }

  @Test
  void insertNightlyTerminalFailed_persistsSimpleNameOnly() {
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");

    service.insertNightlyTerminalFailed(from, to, "DataAccessResourceFailureException");

    ArgumentCaptor<IntegrityAsyncJob> captor = ArgumentCaptor.forClass(IntegrityAsyncJob.class);
    verify(jobRepository).save(captor.capture());
    IntegrityAsyncJob saved = captor.getValue();
    assertEquals(IntegrityAsyncJobStatus.FAILED, saved.getStatus());
    assertEquals(IntegrityAsyncJobTrigger.NIGHTLY, saved.getTrigger());
    assertEquals("DataAccessResourceFailureException", saved.getErrorSummary());
    assertFalse(saved.getErrorSummary().contains("SELECT"));
  }

  @Test
  void tryInsertNightlyTerminalSucceeded_dataAccess_swallows() {
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        sampleSucceededResult(from, to);
    when(jobRepository.save(any())).thenThrow(new DataAccessResourceFailureException("disk"));

    service.tryInsertNightlyTerminalSucceeded(result);
  }

  @Test
  void tryInsertNightlyTerminalSucceeded_nonDataAccess_swallowsWithoutRethrow() {
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        sampleSucceededResult(from, to);
    when(jobRepository.save(any())).thenThrow(new IllegalStateException("proxy / mapping boom"));

    service.tryInsertNightlyTerminalSucceeded(result);
  }

  @Test
  void tryInsertNightlyTerminalSucceeded_commitFailure_swallowsAndWarnsSimpleName() {
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult result =
        sampleSucceededResult(from, to);
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    doThrow(new UnexpectedRollbackException("commit boom"))
        .when(transactionManager)
        .commit(transactionStatus);

    service.tryInsertNightlyTerminalSucceeded(result);

    List<ILoggingEvent> warns =
        logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertEquals(1, warns.size());
    assertTrue(
        warns.get(0).getFormattedMessage().contains("UnexpectedRollbackException"),
        "WARN must log exception simple name, got: " + warns.get(0).getFormattedMessage());
    assertFalse(
        warns.get(0).getFormattedMessage().contains("commit boom"),
        "WARN must not leak exception message");
  }

  @Test
  void tryInsertNightlyTerminalFailed_commitFailure_swallowsAndWarnsSimpleName() {
    OffsetDateTime from = OffsetDateTime.parse("2026-06-27T02:00:00Z");
    OffsetDateTime to = OffsetDateTime.parse("2026-06-28T02:00:00Z");
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    doThrow(new UnexpectedRollbackException("commit boom"))
        .when(transactionManager)
        .commit(transactionStatus);

    service.tryInsertNightlyTerminalFailed(from, to, "DataAccessResourceFailureException");

    List<ILoggingEvent> warns =
        logAppender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    assertEquals(1, warns.size());
    assertTrue(warns.get(0).getFormattedMessage().contains("UnexpectedRollbackException"));
  }

  private static RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult
      sampleSucceededResult(OffsetDateTime from, OffsetDateTime to) {
    return new RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult(
        from,
        to,
        "scope",
        false,
        null,
        true,
        false,
        null,
        "OK",
        0,
        0,
        0,
        RetroactiveIntegrityValidationTriggerSource.SCHEDULED,
        10,
        10,
        0,
        0,
        0);
  }
}
