/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditLogControllerVerifyReportGateTest
 * Description: Gate busy + range cap + happy path for Integrity report GETs (#692).
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.ezkey.audit.dto.IntegrityViolationCappedList;
import org.ezkey.audit.exception.IntegrityAsyncJobBusyException;
import org.ezkey.audit.exception.IntegrityWindowOverCapException;
import org.ezkey.audit.integrity.AuditChainCheckpointService;
import org.ezkey.audit.integrity.AuditChainIncidentService;
import org.ezkey.audit.integrity.AuditChainVerificationService;
import org.ezkey.audit.integrity.AuditIntegrityService;
import org.ezkey.audit.integrity.AuditLifecycleService;
import org.ezkey.audit.integrity.IntegrityHeavyCryptoGate;
import org.ezkey.audit.integrity.IntegrityWindowCapProperties;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for gated Integrity report GETs ({@code integrity-check}, {@code chain-integrity}).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogController verify-report gate + cap")
class AuditLogControllerVerifyReportGateTest {

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

  private IntegrityHeavyCryptoGate heavyCryptoGate;
  private IntegrityWindowCapProperties windowCapProperties;
  private AuditLogController controller;

  private static final OffsetDateTime FROM =
      OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);
  private static final OffsetDateTime TO =
      OffsetDateTime.of(2026, 10, 8, 0, 0, 0, 0, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    heavyCryptoGate = new IntegrityHeavyCryptoGate();
    windowCapProperties = new IntegrityWindowCapProperties();
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
            heavyCryptoGate,
            windowCapProperties,
            adminRepository,
            enrollmentRepository,
            integrationRepository,
            tenantRepository);
  }

  @Test
  @DisplayName("checkChainIntegrity returns undeclared gaps on happy path and releases the gate")
  void checkChainIntegrity_happyPath_returnsGapsAndReleasesGate() {
    AuditChainVerificationService.UndeclaredGap gap =
        new AuditChainVerificationService.UndeclaredGap(FROM.plusHours(1), FROM.plusHours(2), 60);
    AuditChainVerificationService.ChainVerificationReport report =
        new AuditChainVerificationService.ChainVerificationReport(
            2,
            2,
            0,
            0,
            0,
            List.of("Undeclared gap"),
            List.of(),
            List.of(gap),
            FROM,
            TO,
            FROM,
            TO,
            false,
            false,
            "UNDECLARED_GAP_DETECTED");
    when(auditChainVerificationService.verifyChain(eq(FROM), eq(TO))).thenReturn(report);

    ResponseEntity<AuditChainVerificationService.ChainVerificationReport> response =
        controller.checkChainIntegrity(FROM, TO);

    assertEquals(200, response.getStatusCode().value());
    assertEquals(1, response.getBody().undeclaredGaps().size());
    assertFalse(heavyCryptoGate.isBusy());
  }

  @Test
  @DisplayName("checkIntegrity happy path returns report and releases the gate")
  void checkIntegrity_happyPath_releasesGate() {
    AuditIntegrityService.IntegrityReport report =
        new AuditIntegrityService.IntegrityReport(
            10,
            10,
            0,
            0,
            true,
            "OK",
            IntegrityViolationCappedList.of(
                List.of(), IntegrityViolationCappedList.ALERT_ENTRY_CAP));
    when(auditIntegrityService.verifyRange(eq(FROM), eq(TO))).thenReturn(report);

    ResponseEntity<AuditIntegrityService.IntegrityReport> response =
        controller.checkIntegrity(FROM, TO);

    assertEquals(200, response.getStatusCode().value());
    assertTrue(response.getBody().intact());
    assertFalse(heavyCryptoGate.isBusy());
  }

  @Test
  @DisplayName("checkChainIntegrity throws IntegrityAsyncJobBusyException when gate is held")
  void checkChainIntegrity_gateBusy_throwsBusyException() {
    assertTrue(heavyCryptoGate.tryEnter(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB));
    try {
      IntegrityAsyncJobBusyException ex =
          assertThrows(
              IntegrityAsyncJobBusyException.class, () -> controller.checkChainIntegrity(FROM, TO));
      assertEquals(IntegrityAsyncJobBusyException.HEAVY_CRYPTO_BUSY_MESSAGE, ex.getMessage());
      verify(auditChainVerificationService, never()).verifyChain(eq(FROM), eq(TO));
    } finally {
      heavyCryptoGate.exit(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB);
    }
  }

  @Test
  @DisplayName("checkIntegrity throws IntegrityAsyncJobBusyException when gate is held")
  void checkIntegrity_gateBusy_throwsBusyException() {
    assertTrue(heavyCryptoGate.tryEnter(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB));
    try {
      IntegrityAsyncJobBusyException ex =
          assertThrows(
              IntegrityAsyncJobBusyException.class, () -> controller.checkIntegrity(FROM, TO));
      assertEquals(IntegrityAsyncJobBusyException.HEAVY_CRYPTO_BUSY_MESSAGE, ex.getMessage());
      verify(auditIntegrityService, never()).verifyRange(eq(FROM), eq(TO));
    } finally {
      heavyCryptoGate.exit(IntegrityHeavyCryptoGate.Holder.OPERATOR_JOB);
    }
  }

  @Test
  @DisplayName("checkChainIntegrity accepts exactly 8×24h+1h (sync cap)")
  void checkChainIntegrity_exactSyncCapBoundary_accepted() {
    OffsetDateTime toExclusive = FROM.plus(windowCapProperties.maxSyncWindow());
    AuditChainVerificationService.ChainVerificationReport report =
        new AuditChainVerificationService.ChainVerificationReport(
            0,
            0,
            0,
            0,
            0,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            FROM,
            toExclusive,
            true,
            true,
            "OK");
    when(auditChainVerificationService.verifyChain(eq(FROM), eq(toExclusive))).thenReturn(report);

    ResponseEntity<AuditChainVerificationService.ChainVerificationReport> response =
        controller.checkChainIntegrity(FROM, toExclusive);

    assertEquals(200, response.getStatusCode().value());
    verify(auditChainVerificationService).verifyChain(eq(FROM), eq(toExclusive));
    assertFalse(heavyCryptoGate.isBusy());
  }

  @Test
  @DisplayName("checkIntegrity accepts exactly 8×24h+1h (sync cap)")
  void checkIntegrity_exactSyncCapBoundary_accepted() {
    OffsetDateTime toExclusive = FROM.plus(windowCapProperties.maxSyncWindow());
    AuditIntegrityService.IntegrityReport report =
        new AuditIntegrityService.IntegrityReport(
            1,
            1,
            0,
            0,
            true,
            "OK",
            IntegrityViolationCappedList.of(
                List.of(), IntegrityViolationCappedList.ALERT_ENTRY_CAP));
    when(auditIntegrityService.verifyRange(eq(FROM), eq(toExclusive))).thenReturn(report);

    ResponseEntity<AuditIntegrityService.IntegrityReport> response =
        controller.checkIntegrity(FROM, toExclusive);

    assertEquals(200, response.getStatusCode().value());
    verify(auditIntegrityService).verifyRange(eq(FROM), eq(toExclusive));
    assertFalse(heavyCryptoGate.isBusy());
  }

  @Test
  @DisplayName("checkChainIntegrity rejects windows above the 8-day sync cap")
  void checkChainIntegrity_rangeOverCap_throwsIntegrityWindowOverCapException() {
    OffsetDateTime overCapTo = FROM.plus(windowCapProperties.maxSyncWindow()).plusSeconds(1);
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () -> controller.checkChainIntegrity(FROM, overCapTo));
    assertEquals(8, ex.getMaxWindowDays());
    assertEquals(IntegrityWindowOverCapException.CAP_KIND_SYNC, ex.getCapKind());
    assertEquals(IntegrityWindowOverCapException.ASYNC_ALTERNATIVE, ex.getAsyncAlternative());
    verify(auditChainVerificationService, never()).verifyChain(eq(FROM), eq(overCapTo));
    assertFalse(heavyCryptoGate.isBusy());
  }

  @Test
  @DisplayName("checkIntegrity rejects windows above the 8-day sync cap")
  void checkIntegrity_rangeOverCap_throwsIntegrityWindowOverCapException() {
    OffsetDateTime overCapTo = FROM.plus(windowCapProperties.maxSyncWindow()).plusSeconds(1);
    IntegrityWindowOverCapException ex =
        assertThrows(
            IntegrityWindowOverCapException.class,
            () -> controller.checkIntegrity(FROM, overCapTo));
    assertEquals(8, ex.getMaxWindowDays());
    assertEquals(IntegrityWindowOverCapException.CAP_KIND_SYNC, ex.getCapKind());
    assertEquals(IntegrityWindowOverCapException.ASYNC_ALTERNATIVE, ex.getAsyncAlternative());
    verify(auditIntegrityService, never()).verifyRange(eq(FROM), eq(overCapTo));
  }

  @Test
  @DisplayName("default UI 7-day lookback Instant span including DST (+1h) is accepted")
  void checkChainIntegrity_defaultSevenDayLookbackSpan_accepted() {
    // Exactly the sync Instant cap (8×24h+1h = 193h); async default remains 92 days.
    OffsetDateTime toExclusive = FROM.plusHours(193);
    AuditChainVerificationService.ChainVerificationReport report =
        new AuditChainVerificationService.ChainVerificationReport(
            0,
            0,
            0,
            0,
            0,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            FROM,
            toExclusive,
            true,
            true,
            "OK");
    when(auditChainVerificationService.verifyChain(eq(FROM), eq(toExclusive))).thenReturn(report);

    ResponseEntity<AuditChainVerificationService.ChainVerificationReport> response =
        controller.checkChainIntegrity(FROM, toExclusive);

    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  @DisplayName("gate is released when verifyChain throws")
  void checkChainIntegrity_serviceThrows_releasesGate() {
    when(auditChainVerificationService.verifyChain(eq(FROM), eq(TO)))
        .thenThrow(new IllegalStateException("boom"));

    assertThrows(IllegalStateException.class, () -> controller.checkChainIntegrity(FROM, TO));
    assertFalse(heavyCryptoGate.isBusy());
  }
}
