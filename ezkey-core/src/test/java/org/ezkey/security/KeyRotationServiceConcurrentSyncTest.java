/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Two Admin API instances run the startup keyset sync together on an empty table.
 */
package org.ezkey.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.entity.EncryptionKey;
import org.ezkey.security.domain.repository.EncryptionKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Two Admin API instances run the startup keyset sync at the same time on an empty table (#1259).
 *
 * <p>A barrier holds both writers once each has read an empty table, just before they write. The
 * losing writer must not fail startup; the winner's rows must stay committed.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SpringBootTest(classes = KeyRotationServiceConcurrentSyncTest.TestConfiguration.class)
@TestPropertySource(
    properties = {
      "ezkey.encryption.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:concurrent-sync;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    })
class KeyRotationServiceConcurrentSyncTest {

  private static final long PRIMARY_KEY_ID = 1234567890L;

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @EntityScan(basePackageClasses = EncryptionKey.class)
  @EnableJpaRepositories(basePackageClasses = EncryptionKeyRepository.class)
  @Import(KeyRotationService.class)
  static class TestConfiguration {

    @Bean
    TinkProperties tinkProperties() {
      return new TinkProperties();
    }
  }

  @MockitoBean private KeyManagementOperations keyManagementOperations;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private EncryptionKeyMigrationScopeService migrationScopeService;
  @MockitoBean private KeyUsageVerificationService keyUsageVerificationService;
  @Autowired private EncryptionKeyRepository keyRepository;
  @Autowired private KeyRotationService keyRotationService;
  @Autowired private TinkProperties tinkProperties;

  @BeforeEach
  void setUp() {
    tinkProperties.setEnabled(true);
    tinkProperties.getKeyset().setWriter(true);
    when(keyManagementOperations.isInitialized()).thenReturn(true);
    when(keyManagementOperations.getCurrentPrimaryKeyId()).thenReturn(PRIMARY_KEY_ID);
    keyRepository.deleteAll();
  }

  @RepeatedTest(10)
  @DisplayName("Two writers on an empty table: both start, one PRIMARY row remains")
  void twoWritersOnEmptyTable_bothStartAndOneRowRemains() throws Exception {
    CyclicBarrier afterEmptyTableRead = new CyclicBarrier(2);
    when(keyManagementOperations.getAllKeyIds())
        .thenAnswer(_ -> {
          afterEmptyTableRead.await(10, TimeUnit.SECONDS);
          return List.of(PRIMARY_KEY_ID);
        });

    Callable<Void> writer =
        () -> {
          keyRotationService.initializeKeysetSync();
          return null;
        };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    List<Future<Void>> runs = new ArrayList<>();
    try {
      runs.add(pool.submit(writer));
      runs.add(pool.submit(writer));
      for (Future<Void> run : runs) {
        run.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(keyRepository.count()).isEqualTo(1);
    assertThat(keyRepository.findById(PRIMARY_KEY_ID))
        .get()
        .extracting(EncryptionKey::getKeyStatus)
        .isEqualTo(EncryptionKey.KeyStatus.PRIMARY);
  }
}
