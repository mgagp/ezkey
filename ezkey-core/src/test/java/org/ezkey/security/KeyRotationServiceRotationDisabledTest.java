/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationServiceRotationDisabledTest
 * Description: introduceNewKey must fail closed when rotation.enabled is false.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.ezkey.security.exception.EncryptionLifecycleDisabledException;
import org.ezkey.security.exception.EncryptionLifecycleDisabledException.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the rotation.enabled fail-closed gate on {@link
 * KeyRotationService#introduceNewKey(String)}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyRotationService rotation disabled")
class KeyRotationServiceRotationDisabledTest {

  @Mock private KeyManagementOperations keyManagementOperations;
  @Mock private EncryptionKeyRepository keyRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private EncryptionKeyMigrationScopeService migrationScopeService;
  @Mock private KeyUsageVerificationService keyUsageVerificationService;

  @Test
  @DisplayName("introduceNewKey throws when rotation.enabled is false")
  void introduceNewKey_whenRotationDisabled_throws() {
    TinkProperties properties = new TinkProperties();
    properties.getRotation().setEnabled(false);
    KeyRotationService service =
        new KeyRotationService(
            keyManagementOperations,
            keyRepository,
            properties,
            auditLogService,
            migrationScopeService,
            keyUsageVerificationService);

    EncryptionLifecycleDisabledException ex =
        assertThrows(
            EncryptionLifecycleDisabledException.class,
            () -> service.introduceNewKey("ADMIN_MANUAL"));
    assertEquals(Operation.ROTATION, ex.getOperation());
  }
}
