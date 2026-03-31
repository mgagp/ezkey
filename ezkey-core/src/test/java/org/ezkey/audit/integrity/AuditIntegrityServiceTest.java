/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.integrity.AuditIntegrityService.IntegrityReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for {@link AuditIntegrityService}.
 *
 * <p>Uses a real {@link AuditHmacService} with an in-memory key so that the full HMAC
 * compute-and-verify cycle is exercised. The {@link AuditLogRepository} is mocked to control which
 * entries the service sees without needing a database.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuditIntegrityServiceTest {

  @Mock private AuditLogRepository auditLogRepository;

  private AuditHmacService auditHmacService;
  private AuditIntegrityService integrityService;

  @BeforeEach
  void setUp() throws Exception {
    auditHmacService = TestHmacServiceFactory.create();
    integrityService = new AuditIntegrityService(auditLogRepository, auditHmacService);
  }

  // -----------------------------------------------------------------------
  // verifySingle
  // -----------------------------------------------------------------------

  @Test
  void verifySingle_entryIntact_returnsOk() {
    AuditLog entry = signedEntry(42L);
    when(auditLogRepository.findById(42L)).thenReturn(Optional.of(entry));

    IntegrityReport report = integrityService.verifySingle(42L);

    assertEquals(1, report.totalEntries());
    assertEquals(1, report.validEntries());
    assertEquals(0, report.invalidEntries());
    assertEquals(0, report.unsignedEntries());
    assertTrue(report.intact());
    assertEquals("OK", report.status());
  }

  @Test
  void verifySingle_entryTampered_returnsViolation() {
    AuditLog entry = signedEntry(42L);
    entry.setIpAddress("9.9.9.9"); // tamper after signing
    when(auditLogRepository.findById(42L)).thenReturn(Optional.of(entry));

    IntegrityReport report = integrityService.verifySingle(42L);

    assertEquals(1, report.totalEntries());
    assertEquals(0, report.validEntries());
    assertEquals(1, report.invalidEntries());
    assertFalse(report.intact());
    assertEquals("INTEGRITY_VIOLATION_DETECTED", report.status());
  }

  @Test
  void verifySingle_entryUnsigned_returnsUnsigned() {
    AuditLog entry = unsignedEntry(99L);
    when(auditLogRepository.findById(99L)).thenReturn(Optional.of(entry));

    IntegrityReport report = integrityService.verifySingle(99L);

    assertEquals(1, report.totalEntries());
    assertEquals(0, report.invalidEntries());
    assertEquals(1, report.unsignedEntries());
    assertTrue(report.intact(), "Unsigned entry is not a violation -- it pre-dates signing");
    assertEquals("UNSIGNED", report.status());
  }

  @Test
  void verifySingle_entryNotFound_returnsNotFound() {
    when(auditLogRepository.findById(404L)).thenReturn(Optional.empty());

    IntegrityReport report = integrityService.verifySingle(404L);

    assertEquals(0, report.totalEntries());
    assertTrue(report.intact());
    assertEquals("NOT_FOUND", report.status());
  }

  @Test
  void verifySingle_hmacNotActive_returnsError() {
    AuditHmacService inactive = TestHmacServiceFactory.createInactive();
    AuditIntegrityService svc = new AuditIntegrityService(auditLogRepository, inactive);

    IntegrityReport report = svc.verifySingle(1L);

    assertFalse(report.intact());
    assertEquals("HMAC signing is not active", report.status());
  }

  // -----------------------------------------------------------------------
  // verifyRange
  // -----------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void verifyRange_allValid_returnsOk() {
    AuditLog e1 = signedEntry(1L);
    AuditLog e2 = signedEntry(2L);
    Page<AuditLog> page = new PageImpl<>(List.of(e1, e2));
    when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    IntegrityReport report = integrityService.verifyRange(null, null);

    assertEquals(2, report.totalEntries());
    assertEquals(2, report.validEntries());
    assertEquals(0, report.invalidEntries());
    assertEquals(0, report.unsignedEntries());
    assertTrue(report.intact());
    assertEquals("OK", report.status());
  }

  @Test
  @SuppressWarnings("unchecked")
  void verifyRange_oneInvalidEntry_returnsViolation() {
    AuditLog intact = signedEntry(1L);
    AuditLog tampered = signedEntry(2L);
    tampered.setIpAddress("1.1.1.1"); // tamper after signing

    Page<AuditLog> page = new PageImpl<>(List.of(intact, tampered));
    when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    IntegrityReport report = integrityService.verifyRange(null, null);

    assertEquals(2, report.totalEntries());
    assertEquals(1, report.validEntries());
    assertEquals(1, report.invalidEntries());
    assertFalse(report.intact());
    assertEquals("INTEGRITY_VIOLATION_DETECTED", report.status());
  }

  @Test
  @SuppressWarnings("unchecked")
  void verifyRange_mixedSignedAndUnsigned_countsCorrectly() {
    AuditLog signed = signedEntry(1L);
    AuditLog unsigned = unsignedEntry(2L);

    Page<AuditLog> page = new PageImpl<>(List.of(signed, unsigned));
    when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    IntegrityReport report = integrityService.verifyRange(null, null);

    assertEquals(2, report.totalEntries());
    assertEquals(1, report.validEntries());
    assertEquals(0, report.invalidEntries());
    assertEquals(1, report.unsignedEntries());
    assertTrue(report.intact(), "Unsigned entries do not count as violations");
    assertEquals("OK", report.status());
  }

  @Test
  void verifyRange_hmacNotActive_returnsError() {
    AuditHmacService inactive = TestHmacServiceFactory.createInactive();
    AuditIntegrityService svc = new AuditIntegrityService(auditLogRepository, inactive);

    IntegrityReport report = svc.verifyRange(null, null);

    assertFalse(report.intact());
    assertEquals("HMAC signing is not active", report.status());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private AuditLog signedEntry(Long id) {
    AuditLog entry = buildEntry(id, "10.0.0.1");
    entry.setEntryHmac(auditHmacService.computeHmac(entry));
    return entry;
  }

  private AuditLog unsignedEntry(Long id) {
    return buildEntry(id, "10.0.0.1");
  }

  private AuditLog buildEntry(Long id, String ip) {
    AuditLog entry =
        new AuditLog.Builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .ipAddress(ip)
            .tenantId(1)
            .instanceId("test-instance")
            .build();
    entry.setAuditLogId(id);
    entry.setCreatedAt(OffsetDateTime.of(2026, 2, 19, 10, 0, 0, 0, ZoneOffset.UTC));
    return entry;
  }
}
