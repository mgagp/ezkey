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

import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.EventTypeFamily;
import org.ezkey.audit.integrity.AuditChainCheckpointService;
import org.ezkey.audit.integrity.AuditChainIncidentService;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.mapper.AuditChainCheckpointMapper;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Unit tests for {@link AuditLogController#getAuditLogs} query parameters. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController getAuditLogs")
class AuditLogControllerGetAuditLogsTest {

  @Mock private AuditLogService auditLogService;
  @Mock private AuditLogMapper auditLogMapper;
  @Mock private AuditChainCheckpointService auditChainCheckpointService;
  @Mock private AuditChainCheckpointMapper auditChainCheckpointMapper;
  @Mock private AuditIntegrityService auditIntegrityService;
  @Mock private AuditChainVerificationService auditChainVerificationService;
  @Mock private AuditLifecycleService auditLifecycleService;
  @Mock private RetroactiveIntegrityValidationService retroactiveIntegrityValidationService;
  @Mock private AuditChainIncidentService auditChainIncidentService;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private TenantRepository tenantRepository;

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
            retroactiveIntegrityValidationService,
            auditChainIncidentService,
            adminRepository,
            enrollmentRepository,
            integrationRepository,
            tenantRepository);
  }

  @Test
  @DisplayName("getAuditLogs rejects both eventType and eventTypeFamily")
  void getAuditLogs_rejectsEventTypeAndFamilyTogether() {
    Pageable pageable = PageRequest.of(0, 20);
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () ->
                controller.getAuditLogs(
                    EventType.ENROLLMENT_CREATED,
                    EventTypeFamily.ENROLLMENT,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    pageable));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  @DisplayName("getAuditLogs passes eventTypeFamily to service")
  void getAuditLogs_passesEventTypeFamily() {
    Pageable pageable = PageRequest.of(0, 20);
    when(auditLogService.findByFilters(
            isNull(),
            eq(EventTypeFamily.ENROLLMENT),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(pageable)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    controller.getAuditLogs(
        null,
        EventTypeFamily.ENROLLMENT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        pageable);

    ArgumentCaptor<EventTypeFamily> familyCaptor = ArgumentCaptor.forClass(EventTypeFamily.class);
    verify(auditLogService)
        .findByFilters(
            isNull(),
            familyCaptor.capture(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(pageable));
    assertEquals(EventTypeFamily.ENROLLMENT, familyCaptor.getValue());
  }

  @Test
  @DisplayName("getAuditLogs passes integrationId to service")
  void getAuditLogs_passesIntegrationId() {
    Pageable pageable = PageRequest.of(0, 20);
    when(auditLogService.findByFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(42),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(pageable)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    controller.getAuditLogs(
        null, null, null, null, null, null, 42, null, null, null, null, null, pageable);

    verify(auditLogService)
        .findByFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(42),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(pageable));
  }

  @Test
  @DisplayName("getAuditLogs passes authAttemptId to service")
  void getAuditLogs_passesAuthAttemptId() {
    Pageable pageable = PageRequest.of(0, 20);
    when(auditLogService.findByFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(77),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(pageable)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    controller.getAuditLogs(
        null, null, null, null, null, 77, null, null, null, null, null, null, pageable);

    verify(auditLogService)
        .findByFilters(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(77),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(pageable));
  }
}
