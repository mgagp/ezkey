/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeyRotationServiceTransactionBoundaryTest
 * Description: Regression test for TX-002 — transactional boundary on keyset startup sync.
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Regression test for {@link KeyRotationService} startup keyset sync (assessment TX-002).
 *
 * <p>The public {@link KeyRotationService#initializeKeysetSync()} entry must open a Spring
 * transaction so empty-table sync saves run together. {@code @PostConstruct} would not apply
 * {@code @Transactional}.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SpringBootTest(
    classes = KeyRotationServiceTransactionBoundaryTest.MinimalSyncContext.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KeyRotationServiceTransactionBoundaryTest {

  private static final long PRIMARY_KEY_ID = 1L;

  @MockitoBean private KeyManagementOperations keyManagementOperations;

  @MockitoBean private EncryptionKeyRepository keyRepository;

  @MockitoBean private TinkProperties properties;

  @MockitoBean private AuditLogService auditLogService;

  @MockitoBean private EncryptionKeyMigrationScopeService migrationScopeService;

  @MockitoBean private KeyUsageVerificationService keyUsageVerificationService;

  @Autowired private KeyRotationService keyRotationService;

  @BeforeEach
  void stubSyncPath() {
    when(properties.isEnabled()).thenReturn(true);
    when(properties.getAlgorithm()).thenReturn("AES256_GCM");
    when(keyManagementOperations.isInitialized()).thenReturn(true);
    when(keyManagementOperations.getCurrentPrimaryKeyId()).thenReturn(PRIMARY_KEY_ID);
    when(keyManagementOperations.getAllKeyIds()).thenReturn(List.of(PRIMARY_KEY_ID));
    AtomicReference<EncryptionKey> savedPrimary = new AtomicReference<>();
    when(keyRepository.count()).thenReturn(0L);
    when(keyRepository.findByKeyStatus(EncryptionKey.KeyStatus.PRIMARY))
        .thenAnswer(
            _ -> {
              EncryptionKey saved = savedPrimary.get();
              return saved == null ? List.of() : List.of(saved);
            });
    when(keyRepository.findById(PRIMARY_KEY_ID)).thenReturn(Optional.empty());
    when(keyRepository.save(any(EncryptionKey.class)))
        .thenAnswer(
            invocation -> {
              assertTrue(
                  TransactionSynchronizationManager.isActualTransactionActive(),
                  "Keyset sync save must run inside the public initializeKeysetSync()"
                      + " @Transactional boundary (TX-002)");
              EncryptionKey key = invocation.getArgument(0);
              savedPrimary.set(key);
              return key;
            });
  }

  @Test
  @DisplayName("Public initializeKeysetSync() opens a transaction for empty-table sync saves")
  void publicReadyEntryOpensTransactionForSyncSaves() {
    assertDoesNotThrow(() -> keyRotationService.initializeKeysetSync());
  }

  /**
   * Minimal Spring context: {@link KeyRotationService} plus a JDBC transaction manager (no JPA).
   * Repositories and Tink collaborators are stubs so this test only asserts the TX boundary.
   */
  @SpringBootConfiguration
  @EnableTransactionManagement
  @Import(KeyRotationService.class)
  static class MinimalSyncContext {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
    }

    @Bean
    PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }
  }
}
