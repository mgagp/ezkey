/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: PartitionSchedulerServiceTest
 * Description: Horizon, failure registry, alert, and ShedLock startup behaviour.
 */

package org.ezkey.database.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Pattern;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.ezkey.alert.domain.AlertResolutionReason;
import org.ezkey.alert.domain.AlertSeverity;
import org.ezkey.alert.domain.AlertType;
import org.ezkey.alert.service.AlertService;
import org.ezkey.audit.integrity.ScheduledJobKey;
import org.ezkey.audit.integrity.ScheduledJobLastRunService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * Unit tests for {@link PartitionSchedulerService}.
 *
 * <p>The PostgreSQL proof of a missing partition and of a grants-style function failure lives in
 * {@link PartitionSchedulerPostgresTest}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Partition scheduler service")
class PartitionSchedulerServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-13T15:00:00Z"), ZoneOffset.UTC);

  private static final String SUCCESS_SCOPE = "UTC 2026-11..2027-02 monthsAhead=3";

  private static final String FAILURE_PAYLOAD =
      "{\"monthsAhead\":3,\"from\":\"2026-11\",\"to\":\"2027-02\",\"failedTargets\":8,"
          + "\"error\":\"PersistenceException\"}";

  @Mock private EntityManager entityManager;
  @Mock private jakarta.persistence.Query nativeQuery;
  @Mock private ScheduledJobLastRunService jobLastRunService;
  @Mock private AlertService alertService;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private ObjectProvider<LockProvider> lockProvider;

  private PartitionSchedulerService service;

  @BeforeEach
  void setUp() {
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(mock(TransactionStatus.class));
    service = newService(PartitionSchedulerService.DEFAULT_MONTHS_AHEAD, true);
  }

  @Test
  @DisplayName(
      "horizon calls create_monthly_partition for current month through three months ahead")
  void createNextMonthPartitions_coversCurrentMonthAndThreeAhead() {
    stubFunctionResult(Boolean.TRUE);

    service.createNextMonthPartitions();

    verify(entityManager, times(8)).createNativeQuery(anyString());
    ArgumentCaptor<String> tableCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> partitionCaptor = ArgumentCaptor.forClass(String.class);
    verify(nativeQuery, times(8)).setParameter(eq("tableName"), tableCaptor.capture());
    verify(nativeQuery, times(8)).setParameter(eq("partitionName"), partitionCaptor.capture());

    assertTrue(tableCaptor.getAllValues().contains("ezkey_auth_attempt"));
    assertTrue(tableCaptor.getAllValues().contains("ezkey_audit_log"));
    assertTrue(partitionCaptor.getAllValues().contains("ezkey_auth_attempt_2026_11"));
    assertTrue(partitionCaptor.getAllValues().contains("ezkey_auth_attempt_2027_02"));
    assertTrue(partitionCaptor.getAllValues().contains("ezkey_audit_log_2026_11"));
    assertTrue(partitionCaptor.getAllValues().contains("ezkey_audit_log_2027_02"));
    for (String partition : partitionCaptor.getAllValues()) {
      assertTrue(
          Pattern.matches("ezkey_(auth_attempt|audit_log)_\\d{4}_\\d{2}", partition), partition);
    }
    verify(jobLastRunService).recordSuccess(ScheduledJobKey.PARTITION_CREATION, SUCCESS_SCOPE);
    verify(alertService)
        .resolveByDedupeKey(
            PartitionSchedulerService.ALERT_DEDUPE_KEY,
            AlertResolutionReason.PARTITION_HORIZON_RESTORED,
            null);
    verify(alertService, never()).raiseOrTouch(any(), any(), any(), any());
  }

  @Test
  @DisplayName("an existing partition is still a successful horizon")
  void createNextMonthPartitions_whenAlreadyExists_recordsSuccess() {
    stubFunctionResult(Boolean.FALSE);

    service.createNextMonthPartitions();

    verify(jobLastRunService)
        .recordSuccess(eq(ScheduledJobKey.PARTITION_CREATION), eq(SUCCESS_SCOPE));
  }

  @Test
  @DisplayName("function failure is recorded and alerted without the driver message")
  void createNextMonthPartitions_whenFunctionFails_recordsFailureAndRaisesAlert() {
    stubQuery();
    when(nativeQuery.getSingleResult())
        .thenThrow(new PersistenceException("SELECT secret FROM ezkey_auth_attempt"));

    assertDoesNotThrow(() -> service.createNextMonthPartitions());

    verify(entityManager, times(8)).createNativeQuery(anyString());
    verify(jobLastRunService)
        .recordFailure(
            ScheduledJobKey.PARTITION_CREATION,
            "UTC 2026-11..2027-02 monthsAhead=3 failed=8/8",
            "PersistenceException");
    verify(jobLastRunService, never())
        .recordFailure(any(), any(), org.mockito.ArgumentMatchers.contains("SELECT"));
    verify(alertService)
        .raiseOrTouch(
            AlertType.PARTITION_CREATION_FAILED,
            AlertSeverity.CRITICAL,
            PartitionSchedulerService.ALERT_DEDUPE_KEY,
            FAILURE_PAYLOAD);
    verify(jobLastRunService, never()).recordSuccess(any(), any());
  }

  @Test
  @DisplayName("one failed target still attempts the rest of the horizon")
  void createNextMonthPartitions_partialFailure_continues() {
    stubQuery();
    when(nativeQuery.getSingleResult())
        .thenThrow(new PersistenceException("permission denied"))
        .thenReturn(Boolean.TRUE);

    service.createNextMonthPartitions();

    verify(nativeQuery, times(8)).getSingleResult();
    verify(jobLastRunService)
        .recordFailure(
            eq(ScheduledJobKey.PARTITION_CREATION),
            eq("UTC 2026-11..2027-02 monthsAhead=3 failed=1/8"),
            eq("PersistenceException"));
  }

  @Test
  @DisplayName("months-ahead below 1 still covers the next month")
  void monthsAhead_belowMinimum_clampsToNextMonth() {
    service = newService(0, true);
    stubFunctionResult(Boolean.TRUE);

    service.createNextMonthPartitions();

    verify(entityManager, times(4)).createNativeQuery(anyString());
    verify(jobLastRunService)
        .recordSuccess(ScheduledJobKey.PARTITION_CREATION, "UTC 2026-11..2026-12 monthsAhead=1");
  }

  @Test
  @DisplayName("months-ahead above 12 is capped")
  void monthsAhead_aboveMaximum_capsAtTwelve() {
    service = newService(100, true);
    stubFunctionResult(Boolean.TRUE);

    service.createNextMonthPartitions();

    verify(entityManager, times(26)).createNativeQuery(anyString());
  }

  @Test
  @DisplayName("startup skips when another instance holds DB_PARTITION_CREATION")
  void startup_whenLockHeld_doesNotCreateOrRecordFailure() {
    LockProvider locks = mock(LockProvider.class);
    when(locks.lock(any())).thenReturn(Optional.empty());
    when(lockProvider.getIfAvailable()).thenReturn(locks);

    service.ensurePartitionsOnStartup();

    verify(entityManager, never()).createNativeQuery(anyString());
    verify(jobLastRunService, never()).recordFailure(any(), any(), any());
    verify(jobLastRunService, never()).recordSuccess(any(), any());
  }

  @Test
  @DisplayName("startup creates the horizon under the same lock and releases it")
  void startup_whenLockAcquired_createsHorizonAndUnlocks() {
    SimpleLock held = mock(SimpleLock.class);
    LockProvider locks = mock(LockProvider.class);
    when(locks.lock(any())).thenReturn(Optional.of(held));
    when(lockProvider.getIfAvailable()).thenReturn(locks);
    stubFunctionResult(Boolean.TRUE);

    service.ensurePartitionsOnStartup();

    verify(jobLastRunService).recordSuccess(ScheduledJobKey.PARTITION_CREATION, SUCCESS_SCOPE);
    verify(held).unlock();
  }

  @Test
  @DisplayName("startup still creates partitions when the lock store errors")
  void startup_whenLockStoreFails_runsBecauseFunctionIsIdempotent() {
    LockProvider locks = mock(LockProvider.class);
    when(locks.lock(any())).thenThrow(new IllegalStateException("shedlock down"));
    when(lockProvider.getIfAvailable()).thenReturn(locks);
    stubFunctionResult(Boolean.TRUE);

    service.ensurePartitionsOnStartup();

    verify(jobLastRunService).recordSuccess(ScheduledJobKey.PARTITION_CREATION, SUCCESS_SCOPE);
  }

  @Test
  @DisplayName("startup can be disabled without touching the database")
  void startup_whenDisabled_doesNothing() {
    service = newService(PartitionSchedulerService.DEFAULT_MONTHS_AHEAD, false);

    service.ensurePartitionsOnStartup();

    verify(entityManager, never()).createNativeQuery(anyString());
    verify(lockProvider, never()).getIfAvailable();
  }

  @Test
  @DisplayName("daily job keeps the ShedLock name used by startup")
  void scheduledMethod_keepsDistributedLock() throws NoSuchMethodException {
    SchedulerLock lock =
        PartitionSchedulerService.class
            .getDeclaredMethod("createNextMonthPartitions")
            .getAnnotation(SchedulerLock.class);
    Scheduled scheduled =
        PartitionSchedulerService.class
            .getDeclaredMethod("createNextMonthPartitions")
            .getAnnotation(Scheduled.class);

    assertNotNull(lock);
    assertEquals(PartitionSchedulerService.LOCK_NAME, lock.name());
    assertEquals("PT10M", lock.lockAtMostFor());
    assertNotNull(scheduled);
    assertEquals("${ezkey.database.partition.scheduler.cron:0 0 1 * * ?}", scheduled.cron());
  }

  private PartitionSchedulerService newService(int monthsAhead, boolean startupEnabled) {
    return new PartitionSchedulerService(
        entityManager,
        jobLastRunService,
        alertService,
        transactionManager,
        lockProvider,
        CLOCK,
        monthsAhead,
        startupEnabled);
  }

  private void stubFunctionResult(Boolean result) {
    stubQuery();
    when(nativeQuery.getSingleResult()).thenReturn(result);
  }

  private void stubQuery() {
    when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
    when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
  }
}
