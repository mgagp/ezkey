/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.ezkey.audit.domain.ApiName;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.ezkey.audit.integrity.AuditChainCheckpointRepository;
import org.ezkey.audit.integrity.AuditHmacService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * Write-path invariant for the single-INSERT HMAC seal: exactly one {@code save()}, id and
 * micros-truncated {@code created_at} are set before {@code computeHmac}.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceSingleInsertSealTest {

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AuditChainCheckpointRepository checkpointRepository;
  @Mock private AuditHmacService auditHmacService;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private TransactionStatus transactionStatus;

  private AuditLogService auditLogService;

  @BeforeEach
  void setUp() {
    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(transactionStatus);

    auditLogService =
        new AuditLogService(
            auditLogRepository, checkpointRepository, auditHmacService, transactionManager);
  }

  @Test
  void log_singleInsertSeal_setsIdAndMicrosCreatedAtBeforeHmac_andSavesOnce() {
    when(auditHmacService.isActive()).thenReturn(true);
    when(auditHmacService.getInstanceId()).thenReturn("test-instance");
    when(auditLogRepository.nextAuditLogId()).thenReturn(42L);
    when(auditLogRepository.save(any(AuditLog.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(auditHmacService.computeHmac(any(AuditLog.class)))
        .thenAnswer(
            invocation -> {
              AuditLog entity = invocation.getArgument(0);
              assertNotNull(entity.getAuditLogId(), "audit_log_id must be set before HMAC");
              assertEquals(42L, entity.getAuditLogId());
              assertNotNull(entity.getCreatedAt(), "created_at must be set before HMAC");
              assertEquals(
                  entity.getCreatedAt().truncatedTo(ChronoUnit.MICROS),
                  entity.getCreatedAt(),
                  "created_at must already be truncated to microseconds");
              return "test-hmac";
            });

    // Sub-microsecond nanos to prove the service truncates before signing.
    OffsetDateTime subMicroNow = OffsetDateTime.parse("2026-07-18T12:34:56.789123456Z");

    AuditLog auditLog =
        AuditLog.builder()
            .eventType(EventType.ADMIN_LOGIN)
            .eventAction("login_attempt")
            .eventStatus(EventStatus.SUCCESS)
            .apiName(ApiName.ADMIN_API)
            .build();
    auditLog.setCreatedAt(subMicroNow);

    auditLogService.log(auditLog);

    InOrder order = inOrder(auditLogRepository, auditHmacService);
    order.verify(auditLogRepository).nextAuditLogId();
    order.verify(auditHmacService).computeHmac(any(AuditLog.class));
    order.verify(auditLogRepository).save(any(AuditLog.class));

    verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    verify(auditLogRepository, times(1)).nextAuditLogId();

    ArgumentCaptor<AuditLog> savedCaptor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogRepository).save(savedCaptor.capture());
    AuditLog saved = savedCaptor.getValue();
    assertEquals(42L, saved.getAuditLogId());
    assertEquals(subMicroNow.truncatedTo(ChronoUnit.MICROS), saved.getCreatedAt());
    assertEquals("test-hmac", saved.getEntryHmac());
    assertTrue(
        saved.getCreatedAt().getNano() % 1_000 == 0,
        "created_at nanos must be micros-aligned (no sub-micro residue)");
  }
}
