/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationServiceKeysetWriterTest
 * Description: Non-writer processes must not INSERT ezkey_encryption_key on empty-table sync.
 */
package org.ezkey.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Layer 1 lock: Auth/Integration ({@code keyset.writer=false}) must not attempt empty-table sync.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class KeyRotationServiceKeysetWriterTest {

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
  @DisplayName(
      "initializeKeysetSync does not read or save keys when this process is not the writer")
  void initializeKeysetSync_whenNotWriter_doesNotTouchRepository() {
    TinkProperties.Keyset keyset = new TinkProperties.Keyset();
    keyset.setWriter(false);
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getKeyset()).thenReturn(keyset);

    keyRotationService.initializeKeysetSync();

    verify(keyRepository, never()).count();
    verify(keyRepository, never()).save(any(EncryptionKey.class));
    verify(keyManagementOperations, never()).isInitialized();
  }
}
