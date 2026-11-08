/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: InitialGlobalAdminServiceTransactionBoundaryTest
 * Description: Regression test for F-001 — transactional boundary on the public bootstrap entry.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Regression test for {@link InitialGlobalAdminService} transactional boundaries (audit F-001).
 *
 * <p>The locked task must run in a transaction that commits before the lock is released (#1259), so
 * the next lock holder sees its writes.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SpringBootTest(
    classes = InitialGlobalAdminServiceTransactionBoundaryTest.MinimalBootstrapContext.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "ezkey.admin.initial.username=john.doe",
      "ezkey.admin.initial.email=john.doe@example.com",
      "ezkey.admin.initial.first-name=John",
      "ezkey.admin.initial.last-name=Doe"
    })
class InitialGlobalAdminServiceTransactionBoundaryTest {

  @MockitoBean private EzkeyAdminRepository adminRepository;

  @Autowired private InitialGlobalAdminService initialGlobalAdminService;

  @BeforeEach
  void stubRepository() {
    when(adminRepository.findByUsername("john.doe")).thenReturn(Optional.empty());
    when(adminRepository.findByUsername("admin")).thenReturn(Optional.empty());
    when(adminRepository.save(any(EzkeyAdmin.class)))
        .thenAnswer(invocation -> {
          assertTrue(
              TransactionSynchronizationManager.isActualTransactionActive(),
              "Locked task must run inside a transaction (F-001)");
          return invocation.getArgument(0);
        });
  }

  @Test
  @DisplayName("Locked bootstrap task runs in a transaction committed before the lock is released")
  void lockedTaskCommitsBeforeLockRelease() {
    assertDoesNotThrow(() -> initialGlobalAdminService.initializeGlobalAdmin());
  }

  /**
   * Minimal Spring context: {@link InitialGlobalAdminService}, JDBC transaction manager (no JPA),
   * and a {@link LockingTaskExecutor} whose lock asserts on release that the transaction has ended.
   */
  @SpringBootConfiguration
  @EnableTransactionManagement
  @EnableConfigurationProperties(InitialGlobalAdminProperties.class)
  @Import(InitialGlobalAdminService.class)
  static class MinimalBootstrapContext {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
    }

    @Bean
    PlatformTransactionManager platformTransactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    LockProvider lockProvider() {
      return _ -> {
        SimpleLock lock = Mockito.mock(SimpleLock.class);
        Mockito.doAnswer(
                _ -> {
                  assertFalse(
                      TransactionSynchronizationManager.isActualTransactionActive(),
                      "The lock must be released after the task's commit (#1259)");
                  return null;
                })
            .when(lock)
            .unlock();
        return Optional.of(lock);
      };
    }

    @Bean
    LockingTaskExecutor lockingTaskExecutor(
        LockProvider lockProvider, PlatformTransactionManager transactionManager) {
      return new LockingTaskExecutor(lockProvider, transactionManager);
    }
  }
}
