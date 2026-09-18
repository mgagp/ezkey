/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationServicePromotionGateTest
 * Description: KEY_PROMOTION idles when rotation is disabled while encryption stays on.
 */
package org.ezkey.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Eval / ops: encryption ON + rotation OFF must idle KEY_PROMOTION without disabling Tink.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class KeyRotationServicePromotionGateTest {

  @Mock private KeyManagementOperations keyManagementOperations;
  @Mock private EncryptionKeyRepository keyRepository;
  @Mock private TinkProperties properties;
  @Mock private AuditLogService auditLogService;
  @Mock private EncryptionKeyMigrationScopeService migrationScopeService;
  @Mock private KeyUsageVerificationService keyUsageVerificationService;

  private KeyRotationService keyRotationService;
  private final TinkProperties.Rotation rotation = new TinkProperties.Rotation();

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
  @DisplayName("checkAndPromotePendingKeys returns without DB work when rotation is disabled")
  void promote_whenRotationDisabled_idlesWithoutRepositoryAccess() {
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getRotation()).thenReturn(rotation);
    rotation.setEnabled(false);

    keyRotationService.checkAndPromotePendingKeys();

    verifyNoInteractions(keyManagementOperations);
    verify(keyRepository, never()).findPendingKeysReadyForPromotion(any(OffsetDateTime.class));
  }

  @Test
  @DisplayName("checkAndPromotePendingKeys still short-circuits when encryption is disabled")
  void promote_whenEncryptionDisabled_idlesWithoutRotationCheckSideEffects() {
    when(properties.isEnabled()).thenReturn(false);

    keyRotationService.checkAndPromotePendingKeys();

    verifyNoInteractions(keyManagementOperations);
    verify(keyRepository, never()).findPendingKeysReadyForPromotion(any(OffsetDateTime.class));
  }
}
