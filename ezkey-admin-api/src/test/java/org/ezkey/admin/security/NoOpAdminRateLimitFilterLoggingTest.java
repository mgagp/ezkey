/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: NoOpAdminRateLimitFilterLoggingTest
 * Description: clientId CR/LF must be neutralized in no-op rate-limit TRACE logs (#801).
 */

package org.ezkey.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Output-capture tests: caller-controlled {@code clientId} in no-op rate-limit TRACE logs go
 * through {@code LogSanitizer} (CodeQL #78, #79 / issue #801).
 *
 * @since 2026
 */
class NoOpAdminRateLimitFilterLoggingTest {

  private NoOpAdminRateLimitFilter filter;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger filterLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    filter = new NoOpAdminRateLimitFilter(new SimpleMeterRegistry());

    filterLogger = (Logger) LoggerFactory.getLogger(NoOpAdminRateLimitFilter.class);
    previousLevel = filterLogger.getLevel();
    filterLogger.setLevel(Level.TRACE);
    logAppender = new ListAppender<>();
    logAppender.start();
    filterLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    filterLogger.detachAppender(logAppender);
    filterLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("recordFailedAttempt TRACE does not echo CR/LF from clientId (LogSanitizer)")
  void recordFailedAttemptSanitizesClientId() {
    filter.recordFailedAttempt("203.0.113.9\r\ninjected");

    String joined = joinedMessages();
    assertThat(joined).contains("recordFailedAttempt");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("203.0.113.9__injected");
  }

  @Test
  @DisplayName("recordSuccessfulAttempt TRACE does not echo CR/LF from clientId (LogSanitizer)")
  void recordSuccessfulAttemptSanitizesClientId() {
    filter.recordSuccessfulAttempt("203.0.113.9\r\ninjected");

    String joined = joinedMessages();
    assertThat(joined).contains("recordSuccessfulAttempt");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("203.0.113.9__injected");
  }

  private String joinedMessages() {
    return logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }
}
