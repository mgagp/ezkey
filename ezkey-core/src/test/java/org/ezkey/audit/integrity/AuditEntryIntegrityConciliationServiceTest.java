/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.dto.IntegrityRuptureConciliationCategory;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditEntryIntegrityConciliationServiceTest {

  @Mock private AuditEntryIntegrityConciliationRepository conciliationRepository;
  @Mock private AuditLogService auditLogService;

  private AuditHmacService hmacService;
  private AuditEntryIntegrityConciliationService service;

  @BeforeEach
  void setUp() {
    hmacService = TestHmacServiceFactory.create();
    service =
        new AuditEntryIntegrityConciliationService(
            conciliationRepository, hmacService, auditLogService);
  }

  @Test
  void computeObservedStateFingerprint_isStableAcrossReRead() {
    AuditLog entry = signedEntry(101L, "stable payload");

    String first = service.computeObservedStateFingerprint(entry);
    String second = service.computeObservedStateFingerprint(entry);

    assertEquals(first, second);
    assertEquals(64, first.length());
  }

  @Test
  void computeObservedStateFingerprint_changesWhenEntryContentChanges() {
    AuditLog entry = signedEntry(101L, "original payload");
    String original = service.computeObservedStateFingerprint(entry);

    entry.setEventDetails("tampered payload");
    String tampered = service.computeObservedStateFingerprint(entry);

    assertNotEquals(original, tampered);
  }

  @Test
  void fingerprintMatchesActiveConciliation_trueWhenActiveFingerprintMatches() {
    AuditLog entry = signedEntry(202L, "acknowledged tamper");
    AuditEntryIntegrityConciliation active = new AuditEntryIntegrityConciliation();
    active.setObservedStateFingerprint(service.computeObservedStateFingerprint(entry));

    assertTrue(service.fingerprintMatchesActiveConciliation(entry, active));
  }

  @Test
  void createConciliation_supersedesPriorActiveRow() {
    AuditLog entry = signedEntry(303L, "entry body");
    AuditEntryIntegrityConciliation prior = new AuditEntryIntegrityConciliation();
    prior.setConciliationId(1L);
    prior.setStatus(AuditEntryIntegrityConciliationStatus.ACTIVE);
    when(conciliationRepository.findByAuditLogIdAndStatus(
            303L, AuditEntryIntegrityConciliationStatus.ACTIVE))
        .thenReturn(Optional.of(prior));
    when(conciliationRepository.save(any(AuditEntryIntegrityConciliation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.createConciliation(
        entry,
        EntryHmacViolationCollector.REASON_HMAC_MISMATCH,
        IntegrityRuptureConciliationCategory.OTHER,
        "Accepted known invalid signature after investigation.",
        null,
        9L,
        1);

    ArgumentCaptor<AuditEntryIntegrityConciliation> captor =
        ArgumentCaptor.forClass(AuditEntryIntegrityConciliation.class);
    verify(conciliationRepository, org.mockito.Mockito.times(2)).save(captor.capture());
    assertEquals(
        AuditEntryIntegrityConciliationStatus.SUPERSEDED, captor.getAllValues().get(0).getStatus());
    assertEquals(
        AuditEntryIntegrityConciliationStatus.ACTIVE, captor.getAllValues().get(1).getStatus());
    assertEquals(entry.getCreatedAt(), captor.getAllValues().get(1).getAuditLogCreatedAt());
    assertFalse(captor.getAllValues().get(1).getObservedStateFingerprint().isBlank());
  }

  private AuditLog signedEntry(long id, String details) {
    AuditLog entry =
        new AuditLog.Builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login")
            .eventStatus(org.ezkey.audit.domain.EventStatus.SUCCESS)
            .apiName(org.ezkey.audit.domain.ApiName.ADMIN_API)
            .instanceId("test-instance")
            .eventDetails(details)
            .build();
    entry.setAuditLogId(id);
    entry.setCreatedAt(OffsetDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC));
    entry.setEntryHmac(hmacService.computeHmac(entry));
    return entry;
  }
}
