/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.domain.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * T4 — entry HMAC scan invokes the async-job heartbeat once per page batch.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class EntryIntegrityHeartbeatCadenceTest {

  private static final OffsetDateTime FROM =
      OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  @Mock private AuditLogRepository auditLogRepository;
  @Mock private AuditHmacService auditHmacService;
  @Mock private AuditEntryIntegrityConciliationService conciliationService;

  @Test
  void collectRangeViolations_heartbeatOncePerPage() {
    when(conciliationService.findActiveByAuditLogId(anyLong())).thenReturn(Optional.empty());
    when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenAnswer(
            inv -> {
              Pageable pageable = inv.getArgument(1);
              int page = pageable.getPageNumber();
              if (page == 0) {
                return new PageImpl<>(List.of(minimalLog(1L)), PageRequest.of(0, 500), 501);
              }
              if (page == 1) {
                return new PageImpl<>(List.of(minimalLog(2L)), PageRequest.of(1, 500), 501);
              }
              return new PageImpl<>(List.of(), PageRequest.of(page, 500), 501);
            });

    EntryIntegrityViolationClassifier classifier =
        new EntryIntegrityViolationClassifier(auditHmacService, conciliationService);
    AtomicInteger heartbeats = new AtomicInteger();
    classifier.collectRangeViolations(
        auditLogRepository, FROM, FROM.plusHours(1), heartbeats::incrementAndGet);

    assertEquals(2, heartbeats.get());
  }

  private static AuditLog minimalLog(long id) {
    AuditLog log = new AuditLog();
    log.setAuditLogId(id);
    log.setCreatedAt(FROM.plusMinutes(1));
    log.setEntryHmac(null);
    return log;
  }
}
