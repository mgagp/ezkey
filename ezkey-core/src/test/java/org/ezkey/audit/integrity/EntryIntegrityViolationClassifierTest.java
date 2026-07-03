/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntryIntegrityViolationClassifierTest {

  @Mock private AuditEntryIntegrityConciliationRepository conciliationRepository;
  @Mock private AuditLogService auditLogService;

  private AuditHmacService hmacService;
  private AuditEntryIntegrityConciliationService conciliationService;
  private EntryIntegrityViolationClassifier classifier;

  @BeforeEach
  void setUp() {
    hmacService = TestHmacServiceFactory.create();
    conciliationService =
        new AuditEntryIntegrityConciliationService(
            conciliationRepository, hmacService, auditLogService);
    classifier = new EntryIntegrityViolationClassifier(hmacService, conciliationService);
  }

  @Test
  void isAlertEligible_falseForExplainedViolation() {
    AuditLog entry = tamperedEntry(77L, "explained tamper");
    AuditEntryIntegrityConciliation active = new AuditEntryIntegrityConciliation();
    active.setObservedStateFingerprint(conciliationService.computeObservedStateFingerprint(entry));
    when(conciliationRepository.findByAuditLogIdAndStatus(
            77L, AuditEntryIntegrityConciliationStatus.ACTIVE))
        .thenReturn(Optional.of(active));

    assertTrue(classifier.isExplained(entry));
    assertFalse(classifier.isAlertEligible(entry));
  }

  @Test
  void isAlertEligible_trueForOpenViolation() {
    AuditLog entry = tamperedEntry(88L, "open tamper");
    when(conciliationRepository.findByAuditLogIdAndStatus(
            88L, AuditEntryIntegrityConciliationStatus.ACTIVE))
        .thenReturn(Optional.empty());

    assertFalse(classifier.isExplained(entry));
    assertTrue(classifier.isAlertEligible(entry));
  }

  @Test
  void isAlertEligible_trueWhenActiveConciliationFingerprintMismatch() {
    AuditLog entry = tamperedEntry(99L, "re-tampered");
    AuditEntryIntegrityConciliation active = new AuditEntryIntegrityConciliation();
    active.setObservedStateFingerprint("deadbeef".repeat(8));
    when(conciliationRepository.findByAuditLogIdAndStatus(
            99L, AuditEntryIntegrityConciliationStatus.ACTIVE))
        .thenReturn(Optional.of(active));

    assertFalse(classifier.isExplained(entry));
    assertTrue(classifier.isAlertEligible(entry));
  }

  private AuditLog tamperedEntry(long id, String details) {
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
    entry.setEntryHmac("invalid-hmac");
    return entry;
  }
}
