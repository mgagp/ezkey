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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
import org.springframework.context.annotation.Primary;
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
 * <p>The locked runnable must participate in the transaction opened by the public {@link
 * InitialGlobalAdminService#initializeGlobalAdmin()} entry point (not a misleading private
 * {@code @Transactional} that Spring never applies).
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
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  @DisplayName(
      "Locked bootstrap task participates in transaction from public initializeGlobalAdmin()")
  void lockedTaskRunnableParticipatesInTransactionFromPublicEntry() {
    assertDoesNotThrow(() -> initialGlobalAdminService.initializeGlobalAdmin());
  }

  /**
   * Minimal Spring context: {@link InitialGlobalAdminService}, JDBC transaction manager (no JPA),
   * {@link LockingTaskExecutor} that asserts a transaction is active when the lock callback runs.
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
      SimpleLock lock = Mockito.mock(SimpleLock.class);
      return _ -> Optional.of(lock);
    }

    @Bean
    @Primary
    LockingTaskExecutor lockingTaskExecutor(LockProvider lockProvider) {
      LockingTaskExecutor delegate = new LockingTaskExecutor(lockProvider);
      return new LockingTaskExecutor(lockProvider) {
        @Override
        public boolean executeWithLock(String lockName, Duration lockAtMostFor, Runnable task) {
          return delegate.executeWithLock(
              lockName,
              lockAtMostFor,
              () -> {
                assertTrue(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "Runnable passed to LockingTaskExecutor must run inside the outer"
                        + " @Transactional boundary (F-001)");
                task.run();
              });
        }
      };
    }
  }
}
