/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationServicePromotionConflictTest
 * Description: Typed KEY_PROMOTION_CONFLICT audits for CAS / kid-absent HA conflicts (#914).
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.entity.EncryptionKey.KeyStatus;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.exception.KeysetConcurrentModificationException;
import org.ezkey.security.exception.KeysetKeyNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Brief §4.6 — expected HA promotion conflicts use typed audits, not generic ERROR promotion.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class KeyRotationServicePromotionConflictTest {

  @Mock private KeyManagementOperations keyManagementOperations;
  @Mock private EncryptionKeyRepository keyRepository;
  @Mock private TinkProperties properties;
  @Mock private AuditLogService auditLogService;
  @Mock private EncryptionKeyMigrationScopeService migrationScopeService;
  @Mock private KeyUsageVerificationService keyUsageVerificationService;

  private KeyRotationService keyRotationService;

  @BeforeEach
  void setUp() {
    keyRotationService =
        new KeyRotationService(
            keyManagementOperations,
            keyRepository,
            properties,
            auditLogService,
            migrationScopeService,
            keyUsageVerificationService);
  }

  @Test
  @DisplayName("CAS lost: WARN path audits KEY_PROMOTION_CONFLICT FAILURE (not KEY_PROMOTED ERROR)")
  void promote_whenCasLost_emitsPromotionConflictFailure() throws Exception {
    EncryptionKey pending =
        new EncryptionKey(42L, KeyStatus.PENDING, "AES256_GCM", OffsetDateTime.now(), "TEST");

    when(keyManagementOperations.promoteToPrimary(42L))
        .thenThrow(new KeysetConcurrentModificationException(3L, "CAS failed"));

    keyRotationService.promotePendingToPrimary(pending);

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(captor.capture());
    AuditLog audit = captor.getValue();
    assertEquals(EventType.KEY_PROMOTION_CONFLICT, audit.getEventType());
    assertEquals(EventStatus.FAILURE, audit.getEventStatus());
    assertEquals(1L, keyRotationService.getPromotionConflictCount());
    verify(keyRepository, never()).save(any());
  }

  @Test
  @DisplayName("kid absent after fresh reload: ERROR conflict audit + escalate")
  void promote_whenKidAbsentAfterFreshReload_emitsErrorConflict() throws Exception {
    EncryptionKey pending =
        new EncryptionKey(99L, KeyStatus.PENDING, "AES256_GCM", OffsetDateTime.now(), "TEST");

    when(keyManagementOperations.promoteToPrimary(99L))
        .thenThrow(new KeysetKeyNotFoundException(99L, true, "not found after fresh reload"));

    try {
      keyRotationService.promotePendingToPrimary(pending);
    } catch (RuntimeException expected) {
      // true inconsistency rethrows after audit
    }

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());
    AuditLog audit = captor.getValue();
    assertEquals(EventType.KEY_PROMOTION_CONFLICT, audit.getEventType());
    assertEquals(EventStatus.ERROR, audit.getEventStatus());
  }
}
