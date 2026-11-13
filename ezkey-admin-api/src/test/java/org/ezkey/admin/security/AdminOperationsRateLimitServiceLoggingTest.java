/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminOperationsRateLimitServiceLoggingTest
 * Description: Recovery tokens must never appear in rate-limit DEBUG logs (CodeQL #16).
 */

package org.ezkey.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.ezkey.admin.audit.RecoveryAuditDetails;
import org.ezkey.admin.config.AdminOperationsRateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Output-capture tests: enrollment-reset recovery tokens stay out of DEBUG logs (alert #16).
 *
 * @since 2026
 */
class AdminOperationsRateLimitServiceLoggingTest {

  private static final String RAW_RECOVERY_TOKEN =
      "recovery-token-MUST-NOT-APPEAR-in-logs-9f3c2a1b";

  private AdminOperationsRateLimitService service;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger rateLimitLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    AdminOperationsRateLimitProperties properties = new AdminOperationsRateLimitProperties();
    service = new AdminOperationsRateLimitService(properties, new SimpleMeterRegistry());

    rateLimitLogger = (Logger) LoggerFactory.getLogger(AdminOperationsRateLimitService.class);
    previousLevel = rateLimitLogger.getLevel();
    rateLimitLogger.setLevel(Level.DEBUG);
    logAppender = new ListAppender<>();
    logAppender.start();
    rateLimitLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    rateLimitLogger.detachAppender(logAppender);
    rateLimitLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("recordResetEnrollment logs fingerprint only — never the raw recovery token")
  void recordResetEnrollmentDoesNotLogRawToken() {
    String expectedFingerprint = RecoveryAuditDetails.recoveryTokenFingerprint(RAW_RECOVERY_TOKEN);

    service.recordResetEnrollment(RAW_RECOVERY_TOKEN);

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).doesNotContain(RAW_RECOVERY_TOKEN);
    assertThat(joined).contains("fingerprint");
    assertThat(joined).contains(expectedFingerprint);
  }

  @Test
  @DisplayName("recordCreateApiKey does not echo CR/LF from adminId (LogSanitizer)")
  void recordCreateApiKeySanitizesAdminId() {
    service.recordCreateApiKey("admin\r\ninjected");

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("admin__injected");
  }
}
