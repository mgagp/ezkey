/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.ezkey.audit.dto.AuditLogContextResponseDto;
import org.ezkey.audit.integrity.AuditChainCheckpointService;
import org.ezkey.audit.integrity.AuditChainIncidentService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link AuditLogController#getAuditLogContext(Long, Integer, Integer, Integer)}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController getAuditLogContext")
class AuditLogControllerContextTest {

  @Mock private AuditLogService auditLogService;
  @Mock private AuditLogMapper auditLogMapper;
  @Mock private AuditChainCheckpointService auditChainCheckpointService;
  @Mock private AuditChainCheckpointMapper auditChainCheckpointMapper;
  @Mock private AuditIntegrityService auditIntegrityService;
  @Mock private AuditChainVerificationService auditChainVerificationService;
  @Mock private AuditLifecycleService auditLifecycleService;
  @Mock private AuditChainIncidentService auditChainIncidentService;

  private AuditLogController controller;

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
            auditLifecycleService,
            auditChainIncidentService);
  }

  @Test
  @DisplayName("getAuditLogContext uses default counts when omitted")
  void getAuditLogContext_usesDefaultCountsWhenOmitted() {
    when(auditLogService.findContextAround(eq(42L), eq(10), eq(10), isNull(), isNull()))
        .thenReturn(new AuditLogService.AuditLogContextSlice(List.of(), 42L, false, false));

    AuditLogContextResponseDto response =
        controller.getAuditLogContext(42L, null, null, null).getBody();

    verify(auditLogService).findContextAround(42L, 10, 10, null, null);
    assertEquals(42L, response.getAnchorAuditLogId());
  }

  @Test
  @DisplayName("getAuditLogContext rejects invalid counts")
  void getAuditLogContext_rejectsInvalidCounts() {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class, () -> controller.getAuditLogContext(42L, 51, 10, null));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }
}
