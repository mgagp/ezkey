/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.dto.ArchiveConfirmArchivedRequest;
import org.ezkey.audit.dto.ArchiveEligibilityResult;
import org.ezkey.audit.dto.ArchiveSealRequest;
import org.ezkey.audit.exception.AuditLifecycleConflictException;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLifecycleServiceTest {

  @Mock private AuditChainCheckpointRepository checkpointRepository;
  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AuditChainVerificationService chainVerificationService;
  @Mock private AuditLogService auditLogService;

  private AuditLifecycleService lifecycleService;
  private AuditHmacService hmacService;
  private AuditArchiveProperties archiveProperties;

  @BeforeEach
  void setUp() {
    hmacService = TestHmacServiceFactory.create();
    AuditChainProperties chainProperties = new AuditChainProperties();
    archiveProperties = new AuditArchiveProperties();
    lifecycleService =
        new AuditLifecycleService(
            checkpointRepository,
            auditLogRepository,
            hmacService,
            chainVerificationService,
            auditLogService,
            chainProperties,
            archiveProperties);
  }

  @Test
  void sealArchive_whenCheckpointAlreadySealed_throwsConflict() {
    OffsetDateTime start = OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime end = start.plusMinutes(5);
    AuditChainCheckpoint checkpoint = new AuditChainCheckpoint();
    checkpoint.setCheckpointId(10L);
    checkpoint.setWindowStart(start);
    checkpoint.setWindowEnd(end);
    checkpoint.setCheckpointType("ARCHIVE_SEAL");
    checkpoint.setLifecycleState(CheckpointLifecycleState.SEALED);

    when(checkpointRepository.findByIdRange(10L, 10L)).thenReturn(List.of(checkpoint));

    ArchiveSealRequest request = new ArchiveSealRequest(null, null, 10L, 10L, "retention");

    assertThrows(
        AuditLifecycleConflictException.class, () -> lifecycleService.sealArchive(request));
    verify(chainVerificationService, never()).verifyChain(any(), any());
  }

  @Test
  void progressLifecyclePolicy_whenExternalArchivalDisabled_sealsAndPromotesEligibleCheckpoints() {
    OffsetDateTime now = OffsetDateTime.of(2026, 4, 19, 12, 0, 0, 0, ZoneOffset.UTC);
    archiveProperties.setExternalArchivalEnabled(false);
    archiveProperties.setRetentionPeriod(Period.ofDays(90));
    archiveProperties.setSealDelay(Period.ZERO);
    archiveProperties.setPurgeDelay(Period.ofDays(30));

    AuditChainCheckpoint activeCheckpoint = new AuditChainCheckpoint();
    activeCheckpoint.setCheckpointId(10L);
    activeCheckpoint.setCheckpointType("REGULAR");
    activeCheckpoint.setLifecycleState(CheckpointLifecycleState.ACTIVE);

    AuditChainCheckpoint sealedCheckpoint = new AuditChainCheckpoint();
    sealedCheckpoint.setCheckpointId(11L);
    sealedCheckpoint.setCheckpointType("ARCHIVE_SEAL");
    sealedCheckpoint.setLifecycleState(CheckpointLifecycleState.SEALED);

    when(checkpointRepository
            .findByLifecycleStateAndCheckpointTypeAndWindowStartBeforeOrderByWindowStartAsc(
                CheckpointLifecycleState.ACTIVE, "REGULAR", now.minusDays(90)))
        .thenReturn(List.of(activeCheckpoint));
    when(checkpointRepository
            .findByLifecycleStateAndCheckpointTypeAndWindowStartBeforeOrderByWindowStartAsc(
                CheckpointLifecycleState.SEALED, "ARCHIVE_SEAL", now.minusDays(120)))
        .thenReturn(List.of(sealedCheckpoint));

    AuditLifecycleService.LifecycleAutomationResult result =
        lifecycleService.progressLifecyclePolicy(now);

    assertEquals(1, result.sealedCount());
    assertEquals(1, result.purgeableCount());
    assertEquals(CheckpointLifecycleState.SEALED, activeCheckpoint.getLifecycleState());
    assertEquals("ARCHIVE_SEAL", activeCheckpoint.getCheckpointType());
    assertEquals(CheckpointLifecycleState.PURGEABLE, sealedCheckpoint.getLifecycleState());
    verify(checkpointRepository, times(2)).saveAll(any());
  }

  @Test
  void progressLifecyclePolicy_whenExternalArchivalEnabled_onlySealsEligibleCheckpoints() {
    OffsetDateTime now = OffsetDateTime.of(2026, 4, 19, 12, 0, 0, 0, ZoneOffset.UTC);
    archiveProperties.setExternalArchivalEnabled(true);

    AuditChainCheckpoint activeCheckpoint = new AuditChainCheckpoint();
    activeCheckpoint.setCheckpointId(20L);
    activeCheckpoint.setCheckpointType("REGULAR");
    activeCheckpoint.setLifecycleState(CheckpointLifecycleState.ACTIVE);

    when(checkpointRepository
            .findByLifecycleStateAndCheckpointTypeAndWindowStartBeforeOrderByWindowStartAsc(
                CheckpointLifecycleState.ACTIVE,
                "REGULAR",
                now.minus(archiveProperties.getRetentionPeriod())
                    .minus(archiveProperties.getSealDelay())))
        .thenReturn(List.of(activeCheckpoint));

    AuditLifecycleService.LifecycleAutomationResult result =
        lifecycleService.progressLifecyclePolicy(now);

    assertEquals(1, result.sealedCount());
    assertEquals(0, result.purgeableCount());
    verify(checkpointRepository, never())
        .findByLifecycleStateAndCheckpointTypeAndWindowStartBeforeOrderByWindowStartAsc(
            eq(CheckpointLifecycleState.SEALED), eq("ARCHIVE_SEAL"), any());
  }

  @Test
  void getArchiveEligibility_whenNoSealedCheckpoints_doesNotRequireConfirmation() {
    archiveProperties.setExternalArchivalEnabled(true);

    when(checkpointRepository.findByLifecycleStateAndCheckpointTypeOrderByWindowStartAsc(
            CheckpointLifecycleState.SEALED, "ARCHIVE_SEAL"))
        .thenReturn(List.of());

    ArchiveEligibilityResult result = lifecycleService.getArchiveEligibility();

    assertEquals(true, result.externalArchivalEnabled());
    assertEquals(false, result.confirmationRequired());
    assertEquals(0, result.sealedCheckpointCount());
    assertEquals(null, result.oldestSealedWindowStart());
    assertEquals(null, result.newestSealedWindowEnd());
    assertEquals(null, result.checkpointIdFrom());
    assertEquals(null, result.checkpointIdTo());
  }

  @Test
  void getArchiveEligibility_whenSealedCheckpointsExist_returnsCurrentTrancheSummary() {
    archiveProperties.setExternalArchivalEnabled(true);

    AuditChainCheckpoint first = new AuditChainCheckpoint();
    first.setCheckpointId(30L);
    first.setWindowStart(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    first.setWindowEnd(OffsetDateTime.of(2026, 1, 1, 0, 5, 0, 0, ZoneOffset.UTC));

    AuditChainCheckpoint last = new AuditChainCheckpoint();
    last.setCheckpointId(40L);
    last.setWindowStart(OffsetDateTime.of(2026, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC));
    last.setWindowEnd(OffsetDateTime.of(2026, 1, 2, 0, 5, 0, 0, ZoneOffset.UTC));

    when(checkpointRepository.findByLifecycleStateAndCheckpointTypeOrderByWindowStartAsc(
            CheckpointLifecycleState.SEALED, "ARCHIVE_SEAL"))
        .thenReturn(List.of(first, last));

    ArchiveEligibilityResult result = lifecycleService.getArchiveEligibility();

    assertEquals(true, result.externalArchivalEnabled());
    assertEquals(true, result.confirmationRequired());
    assertEquals(2, result.sealedCheckpointCount());
    assertEquals(30L, result.checkpointIdFrom());
    assertEquals(40L, result.checkpointIdTo());
  }

  @Test
  void confirmArchived_whenSealedRangeValid_marksCheckpointsExported() {
    archiveProperties.setExternalArchivalEnabled(true);
    OffsetDateTime exportedAt = OffsetDateTime.of(2026, 4, 20, 8, 0, 0, 0, ZoneOffset.UTC);

    AuditChainCheckpoint checkpoint = new AuditChainCheckpoint();
    checkpoint.setCheckpointId(50L);
    checkpoint.setWindowStart(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    checkpoint.setWindowEnd(OffsetDateTime.of(2026, 1, 1, 0, 5, 0, 0, ZoneOffset.UTC));
    checkpoint.setCheckpointType("ARCHIVE_SEAL");
    checkpoint.setLifecycleState(CheckpointLifecycleState.SEALED);

    when(checkpointRepository.findByIdRange(50L, 50L)).thenReturn(List.of(checkpoint));

    var result =
        lifecycleService.confirmArchived(
            new ArchiveConfirmArchivedRequest(
                null, null, 50L, 50L, "digest-0123456789abcdef", exportedAt),
            12);

    assertEquals(CheckpointLifecycleState.EXPORTED, checkpoint.getLifecycleState());
    assertEquals(exportedAt, checkpoint.getExportedAt());
    assertEquals(12, checkpoint.getExportedByAdminId());
    assertEquals("digest-0123456789abcdef", checkpoint.getExportBundleDigest());
    assertEquals(1, result.checkpointsExported());
    verify(checkpointRepository).saveAll(any());
  }
}
