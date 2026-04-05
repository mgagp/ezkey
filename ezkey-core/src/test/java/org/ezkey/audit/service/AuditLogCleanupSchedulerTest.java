/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditLogCleanupSchedulerTest
 * Description: Unit tests for AuditLogCleanupScheduler dual-behavior retention strategy.
 */

package org.ezkey.audit.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.ezkey.audit.dto.ArchiveSealRequest;
import org.ezkey.audit.dto.ArchiveSealResult;
import org.ezkey.audit.integrity.AuditChainProperties;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AuditLogCleanupScheduler#cleanupOldAuditLogs()}.
 *
 * <p>Verifies the dual-behavior retention strategy:
 *
 * <ul>
 *   <li>When chain is enabled, the scheduler seals the old period instead of deleting entries.
 *   <li>When chain is disabled, the scheduler falls back to direct deletion.
 * </ul>
 *
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogCleanupScheduler")
class AuditLogCleanupSchedulerTest {

  @Mock private AuditLogService auditLogService;
  @Mock private AuditLifecycleService auditLifecycleService;
  @Mock private AuditChainProperties chainProperties;

  @InjectMocks private AuditLogCleanupScheduler scheduler;

  @Test
  @DisplayName("cleanupOldAuditLogs - seals period when chain is enabled")
  void cleanupOldAuditLogs_WhenChainEnabled_SealsPeriodInsteadOfDeleting() {
    when(chainProperties.isEnabled()).thenReturn(true);

    ArchiveSealResult result =
        new ArchiveSealResult(
            OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(90),
            3,
            "abc123hmac456",
            42L,
            "Automated retention-based archive seal.");
    when(auditLifecycleService.sealArchive(any(ArchiveSealRequest.class))).thenReturn(result);

    scheduler.cleanupOldAuditLogs();

    ArgumentCaptor<ArchiveSealRequest> captor = ArgumentCaptor.forClass(ArchiveSealRequest.class);
    verify(auditLifecycleService).sealArchive(captor.capture());
    verify(auditLogService, never()).deleteOldLogs(anyInt());

    ArchiveSealRequest request = captor.getValue();
    // Timestamp mode: no checkpoint IDs provided
    assertNull(request.checkpointIdFrom());
    assertNull(request.checkpointIdTo());
    // Period start is the epoch lower bound
    assertNotNull(request.periodStart());
    assertNotNull(request.periodEnd());
    // Period end is before now (retention cutoff)
    assertTrue(request.periodEnd().isBefore(OffsetDateTime.now(ZoneOffset.UTC)));
    // Justification is meaningful
    assertNotNull(request.justification());
    assertTrue(request.justification().length() >= 10);
  }

  @Test
  @DisplayName("cleanupOldAuditLogs - deletes entries when chain is disabled")
  void cleanupOldAuditLogs_WhenChainDisabled_DeletesEntries() {
    when(chainProperties.isEnabled()).thenReturn(false);
    when(auditLogService.deleteOldLogs(anyInt())).thenReturn(5);

    scheduler.cleanupOldAuditLogs();

    verify(auditLogService).deleteOldLogs(anyInt());
    verify(auditLifecycleService, never()).sealArchive(any());
  }

  @Test
  @DisplayName("cleanupOldAuditLogs - logs error and does not propagate when seal fails")
  void cleanupOldAuditLogs_WhenSealFails_LogsErrorWithoutThrowing() {
    when(chainProperties.isEnabled()).thenReturn(true);
    when(auditLifecycleService.sealArchive(any(ArchiveSealRequest.class)))
        .thenThrow(new IllegalStateException("Chain integrity verification failed"));

    // Should not throw; exception is swallowed and logged
    scheduler.cleanupOldAuditLogs();

    verify(auditLifecycleService).sealArchive(any());
    verify(auditLogService, never()).deleteOldLogs(anyInt());
  }

  @Test
  @DisplayName("cleanupOldAuditLogs - logs error and does not propagate when deletion fails")
  void cleanupOldAuditLogs_WhenDeletionFails_LogsErrorWithoutThrowing() {
    when(chainProperties.isEnabled()).thenReturn(false);
    when(auditLogService.deleteOldLogs(anyInt())).thenThrow(new RuntimeException("Database error"));

    // Should not throw; exception is swallowed and logged
    scheduler.cleanupOldAuditLogs();

    verify(auditLogService).deleteOldLogs(anyInt());
  }

  @Test
  @DisplayName("cleanupOldAuditLogs - seal uses timestamp mode with epoch lower bound")
  void cleanupOldAuditLogs_WhenChainEnabled_UsesTimestampModeFromEpoch() {
    when(chainProperties.isEnabled()).thenReturn(true);

    ArchiveSealResult result =
        new ArchiveSealResult(
            OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(90),
            0,
            null,
            null,
            "Automated retention-based archive seal.");
    when(auditLifecycleService.sealArchive(any(ArchiveSealRequest.class))).thenReturn(result);

    scheduler.cleanupOldAuditLogs();

    ArgumentCaptor<ArchiveSealRequest> captor = ArgumentCaptor.forClass(ArchiveSealRequest.class);
    verify(auditLifecycleService).sealArchive(captor.capture());

    ArchiveSealRequest request = captor.getValue();
    // Epoch lower bound: 2020-01-01T00:00:00Z
    OffsetDateTime epochBound = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    assertTrue(request.periodStart().isEqual(epochBound));
  }
}
