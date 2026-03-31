/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditLogControllerChainCheckpointsTest
 * Description: Unit tests for GET /api/v1/audit-logs/chain-checkpoints.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.ezkey.audit.dto.AuditChainCheckpointResponseDto;
import org.ezkey.audit.dto.CheckpointType;
import org.ezkey.audit.integrity.AuditChainCheckpoint;
import org.ezkey.audit.integrity.AuditChainCheckpointService;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.ezkey.audit.mapper.AuditChainCheckpointMapper;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link AuditLogController#getChainCheckpoints}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController chain-checkpoints Tests")
class AuditLogControllerChainCheckpointsTest {

  @Mock private AuditLogService auditLogService;
  @Mock private AuditLogMapper auditLogMapper;
  @Mock private AuditChainCheckpointService auditChainCheckpointService;
  @Mock private AuditChainCheckpointMapper auditChainCheckpointMapper;
  @Mock private AuditIntegrityService auditIntegrityService;
  @Mock private AuditChainVerificationService auditChainVerificationService;
  @Mock private AuditLifecycleService auditLifecycleService;

  private AuditLogController controller;

  private static final OffsetDateTime WINDOW_START =
      OffsetDateTime.of(2026, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime WINDOW_END =
      OffsetDateTime.of(2026, 3, 1, 10, 5, 0, 0, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    controller =
        new AuditLogController(
            auditLogService,
            auditLogMapper,
            auditChainCheckpointService,
            auditChainCheckpointMapper,
            auditIntegrityService,
            auditChainVerificationService,
            auditLifecycleService);
  }

  @Test
  @DisplayName("getChainCheckpoints returns 200 and mapped page")
  void getChainCheckpoints_returnsMappedPage() {
    Pageable pageable = PageRequest.of(0, 20);
    AuditChainCheckpoint checkpoint = createCheckpoint(1L);
    Page<AuditChainCheckpoint> servicePage = new PageImpl<>(List.of(checkpoint), pageable, 1);
    AuditChainCheckpointResponseDto dto =
        new AuditChainCheckpointResponseDto(
            1L,
            WINDOW_START,
            WINDOW_END,
            2,
            100L,
            101L,
            "digest",
            "prev",
            "chain",
            OffsetDateTime.now(ZoneOffset.UTC),
            CheckpointType.REGULAR,
            null);

    when(auditChainCheckpointService.findCheckpoints(
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            any(Pageable.class)))
        .thenReturn(servicePage);
    when(auditChainCheckpointMapper.toResponseDto(checkpoint)).thenReturn(dto);

    ResponseEntity<Page<AuditChainCheckpointResponseDto>> response =
        controller.getChainCheckpoints(null, null, null, null, null, null, null, pageable);

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().getTotalElements());
    assertEquals(1, response.getBody().getContent().size());
    assertEquals(1L, response.getBody().getContent().get(0).checkpointId());
    assertEquals(CheckpointType.REGULAR, response.getBody().getContent().get(0).checkpointType());
    verify(auditChainCheckpointService)
        .findCheckpoints(null, null, null, null, null, null, null, pageable);
  }

  @Test
  @DisplayName("getChainCheckpoints with filters passes params to service")
  void getChainCheckpoints_withFilters_passesToService() {
    Pageable pageable = PageRequest.of(0, 10);
    when(auditChainCheckpointService.findCheckpoints(
            eq(WINDOW_START),
            eq(WINDOW_END),
            eq(1),
            eq(100),
            eq("REGULAR"),
            eq(WINDOW_START.minusDays(1)),
            eq(WINDOW_END.plusDays(1)),
            eq(pageable)))
        .thenReturn(Page.empty(pageable));

    controller.getChainCheckpoints(
        WINDOW_START,
        WINDOW_END,
        1,
        100,
        CheckpointType.REGULAR,
        WINDOW_START.minusDays(1),
        WINDOW_END.plusDays(1),
        pageable);

    verify(auditChainCheckpointService)
        .findCheckpoints(
            WINDOW_START,
            WINDOW_END,
            1,
            100,
            "REGULAR",
            WINDOW_START.minusDays(1),
            WINDOW_END.plusDays(1),
            pageable);
  }

  @Test
  @DisplayName("checkIntegrity without from and to throws IllegalArgumentException (400)")
  void checkIntegrity_withoutDateRange_throwsIllegalArgumentException() {
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> controller.checkIntegrity(null, null));
    assertNotNull(ex.getMessage());
    assertEquals(
        "Date range is required for verification. Provide from (inclusive) and to (exclusive) as"
            + " ISO-8601.",
        ex.getMessage());
  }

  @Test
  @DisplayName("checkChainIntegrity without from and to throws IllegalArgumentException (400)")
  void checkChainIntegrity_withoutDateRange_throwsIllegalArgumentException() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> controller.checkChainIntegrity(null, null));
    assertNotNull(ex.getMessage());
    assertEquals(
        "Date range is required for verification. Provide from (inclusive) and to (exclusive) as"
            + " ISO-8601.",
        ex.getMessage());
  }

  private static AuditChainCheckpoint createCheckpoint(long id) {
    AuditChainCheckpoint c = new AuditChainCheckpoint();
    c.setCheckpointId(id);
    c.setWindowStart(WINDOW_START);
    c.setWindowEnd(WINDOW_END);
    c.setEntryCount(2);
    c.setFirstEntryId(100L);
    c.setLastEntryId(101L);
    c.setEntriesDigest("digest");
    c.setPrevChainHmac("prev");
    c.setChainHmac("chain");
    c.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    c.setCheckpointType("REGULAR");
    c.setNotes(null);
    return c;
  }
}
