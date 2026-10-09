/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: InitialGlobalAdminServiceLoggingTest
 * Description: Bootstrap admin email must not appear in InitialGlobalAdminService logs (#750).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.Optional;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Output-capture tests: bootstrap admin email stays out of logs (issue #750).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class InitialGlobalAdminServiceLoggingTest {

  private static final String USERNAME = "john.doe";
  private static final String EMAIL = "john.doe@secret-email.example";

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private LockingTaskExecutor lockingTaskExecutor;

  private InitialGlobalAdminService service;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger adminLogger;

  @BeforeEach
  void setUp() {
    InitialGlobalAdminProperties properties = new InitialGlobalAdminProperties();
    properties.setUsername(USERNAME);
    properties.setEmail(EMAIL);
    properties.setFirstName("John");
    properties.setLastName("Doe");
    service = new InitialGlobalAdminService(adminRepository, properties, lockingTaskExecutor);

    when(lockingTaskExecutor.executeWithLock(anyString(), any(Duration.class), any(Runnable.class)))
        .thenAnswer(
            invocation -> {
              invocation.getArgument(2, Runnable.class).run();
              return true;
            });

    adminLogger = (Logger) LoggerFactory.getLogger(InitialGlobalAdminService.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    adminLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    adminLogger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("initializeGlobalAdmin logs username and never the configured email")
  void initializeDoesNotLogEmail() {
    when(adminRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
    when(adminRepository.findByUsername("admin")).thenReturn(Optional.empty());
    when(adminRepository.save(any(EzkeyAdmin.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.initializeGlobalAdmin();

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains(USERNAME);
    assertThat(joined).doesNotContain(EMAIL);
    assertThat(joined).doesNotContain("@secret-email.example");
  }
}
