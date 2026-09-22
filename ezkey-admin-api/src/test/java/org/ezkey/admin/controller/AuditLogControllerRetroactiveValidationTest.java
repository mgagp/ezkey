/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditLogControllerRetroactiveValidationTest
 * Description: Unit tests for POST /api/v1/audit-logs/integrity-validation/run.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.audit.dto.RetroactiveIntegrityValidationRunRequest;
import org.ezkey.audit.dto.RetroactiveIntegrityValidationRunResponse;
import org.ezkey.audit.exception.IntegrityValidationDisabledException;
import org.ezkey.audit.integrity.AuditChainCheckpointService;
import org.ezkey.audit.integrity.AuditChainIncidentService;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationOptions;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationService;
import org.ezkey.audit.integrity.RetroactiveIntegrityValidationTriggerSource;
import org.ezkey.audit.mapper.AuditChainCheckpointMapper;
import org.ezkey.audit.mapper.AuditLogMapper;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link AuditLogController#runRetroactiveIntegrityValidation}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController retroactive validation Tests")
class AuditLogControllerRetroactiveValidationTest {

  @Mock private AuditLogService auditLogService;
  @Mock private AuditLogMapper auditLogMapper;
  @Mock private AuditChainCheckpointService auditChainCheckpointService;
  @Mock private AuditChainCheckpointMapper auditChainCheckpointMapper;
  @Mock private AuditIntegrityService auditIntegrityService;
  @Mock private AuditChainVerificationService auditChainVerificationService;
  @Mock private AuditLifecycleService auditLifecycleService;
  @Mock private RetroactiveIntegrityValidationService retroactiveIntegrityValidationService;
  @Mock private AuditChainIncidentService auditChainIncidentService;
  @Mock private org.ezkey.admin.service.IntegrityBootstrapService integrityBootstrapService;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private TenantRepository tenantRepository;

  private AuditLogController controller;

  private static final OffsetDateTime FROM =
      OffsetDateTime.of(2026, 6, 29, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime TO =
      OffsetDateTime.of(2026, 6, 30, 0, 0, 0, 0, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
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
            integrityBootstrapService,
            adminRepository,
            enrollmentRepository,
            integrationRepository,
            tenantRepository);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null), null));
  }

  @Test
  void runRetroactiveIntegrityValidation_returnsMappedResponse() {
    RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult serviceResult =
        new RetroactiveIntegrityValidationService.RetroactiveIntegrityValidationResult(
            FROM,
            TO,
            "scope",
            false,
            null,
            false,
            true,
            99L,
            "CHAIN_BROKEN",
            2,
            1,
            1,
            RetroactiveIntegrityValidationTriggerSource.OPERATOR);
    when(retroactiveIntegrityValidationService.runValidation(
            eq(FROM), eq(TO), any(RetroactiveIntegrityValidationOptions.class)))
        .thenReturn(serviceResult);

    ResponseEntity<RetroactiveIntegrityValidationRunResponse> response =
        controller.runRetroactiveIntegrityValidation(
            new RetroactiveIntegrityValidationRunRequest(FROM, TO, true));

    assertEquals(200, response.getStatusCode().value());
    RetroactiveIntegrityValidationRunResponse body = response.getBody();
    assertTrue(body != null);
    assertEquals(99L, body.alertId());
    assertTrue(body.alertRaised());
    assertEquals(RetroactiveIntegrityValidationTriggerSource.OPERATOR, body.triggerSource());
    verify(retroactiveIntegrityValidationService).validateOperatorWindow(FROM, TO);
  }

  @Test
  void runRetroactiveIntegrityValidation_propagatesNightlyDisabled() {
    when(retroactiveIntegrityValidationService.runValidation(
            eq(FROM), eq(TO), any(RetroactiveIntegrityValidationOptions.class)))
        .thenThrow(new IntegrityValidationDisabledException());

    assertThrows(
        IntegrityValidationDisabledException.class,
        () ->
            controller.runRetroactiveIntegrityValidation(
                new RetroactiveIntegrityValidationRunRequest(FROM, TO, true)));
  }

  @Test
  void runRetroactiveIntegrityValidation_propagatesInvalidWindow() {
    doThrow(
            new IllegalArgumentException(
                "Invalid date range: to must be after from (exclusive end, inclusive start)."))
        .when(retroactiveIntegrityValidationService)
        .validateOperatorWindow(FROM, TO);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            controller.runRetroactiveIntegrityValidation(
                new RetroactiveIntegrityValidationRunRequest(FROM, TO, true)));
  }
}
