/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.integrity.AuditChainCheckpointRepository;
import org.ezkey.audit.integrity.AuditHmacService;
import org.ezkey.audit.integrity.CheckpointLifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceRetentionGuardTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AuditChainCheckpointRepository checkpointRepository;
  @Mock private AuditHmacService auditHmacService;
  @Mock private EntityManager entityManager;
  @Mock private PlatformTransactionManager transactionManager;

  private AuditLogService auditLogService;

  @BeforeEach
  void setUp() {
    auditLogService =
        new AuditLogService(
            auditLogRepository,
            checkpointRepository,
            auditHmacService,
            entityManager,
            transactionManager);
  }

  @Test
  void purgeLifecycleEligibleLogs_whenCheckpointNotPurgeable_returnsZero() {
    OffsetDateTime purgeCutoff = OffsetDateTime.now().minusDays(90);
    when(checkpointRepository.countByWindowStartBeforeAndLifecycleStateNotIn(
            any(), any(EnumSet.class)))
        .thenReturn(1L);

    int deleted = auditLogService.purgeLifecycleEligibleLogs(purgeCutoff);

    assertEquals(0, deleted);
    verify(auditLogRepository, never()).deleteOlderThan(any(OffsetDateTime.class));
  }

  @Test
  void purgeLifecycleEligibleLogs_whenOldLogsExistButNoPurgeableCheckpoint_returnsZero() {
    OffsetDateTime purgeCutoff = OffsetDateTime.now().minusDays(90);
    when(checkpointRepository.countByWindowStartBeforeAndLifecycleStateNotIn(
            any(), any(EnumSet.class)))
        .thenReturn(0L);
    when(checkpointRepository.countByWindowStartBeforeAndLifecycleState(
            any(), any(CheckpointLifecycleState.class)))
        .thenReturn(0L);
    when(auditLogRepository.existsByCreatedAtBefore(any(OffsetDateTime.class))).thenReturn(true);

    int deleted = auditLogService.purgeLifecycleEligibleLogs(purgeCutoff);

    assertEquals(0, deleted);
    verify(auditLogRepository, never()).deleteOlderThan(any(OffsetDateTime.class));
  }

  @Test
  void purgeLifecycleEligibleLogs_whenCheckpointPurgeable_deletesRows() {
    OffsetDateTime purgeCutoff = OffsetDateTime.now().minusDays(90);
    when(checkpointRepository.countByWindowStartBeforeAndLifecycleStateNotIn(
            any(), any(EnumSet.class)))
        .thenReturn(0L);
    when(checkpointRepository.countByWindowStartBeforeAndLifecycleState(
            any(), any(CheckpointLifecycleState.class)))
        .thenReturn(2L);
    when(auditLogRepository.existsByCreatedAtBefore(any(OffsetDateTime.class))).thenReturn(true);
    when(auditLogRepository.deleteOlderThan(any(OffsetDateTime.class))).thenReturn(7);

    int deleted = auditLogService.purgeLifecycleEligibleLogs(purgeCutoff);

    assertEquals(7, deleted);
    verify(auditLogRepository).deleteOlderThan(purgeCutoff);
  }
}
