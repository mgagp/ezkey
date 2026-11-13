/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: GlobalExceptionHandlerLoggingTest
 * Description: Request URI CR/LF must be neutralized in Integration API exception logs (#801).
 */

package org.ezkey.integration.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.EnrollmentInactiveException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Output-capture tests: caller-controlled request paths in exception logs go through {@code
 * LogSanitizer} (CodeQL #69, #103–#105 / issue #801).
 *
 * @since 2026
 */
class GlobalExceptionHandlerLoggingTest {

  private GlobalExceptionHandler handler;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger handlerLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler(mock(AuditChainHeartbeatGuardService.class));

    handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    previousLevel = handlerLogger.getLevel();
    handlerLogger.setLevel(Level.DEBUG);
    logAppender = new ListAppender<>();
    logAppender.start();
    handlerLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    handlerLogger.detachAppender(logAppender);
    handlerLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("enrollment-inactive WARN does not echo CR/LF from request URI (LogSanitizer)")
  void enrollmentInactiveWarnSanitizesRequestUri() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/auth-attempts\r\ninjected");

    handler.handleEnrollmentInactiveException(
        new EnrollmentInactiveException("enrollment inactive"), request);

    String joined = joinedMessages();
    assertThat(joined).contains("enrollment inactive");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("/api/v1/auth-attempts__injected");
  }

  @Test
  @DisplayName("NoResourceFound DEBUG does not echo CR/LF from request URI (LogSanitizer)")
  void noResourceFoundDebugSanitizesRequestUri() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/scanner\r\ninjected");

    handler.handleNoResourceFoundException(
        new NoResourceFoundException(
            HttpMethod.GET, "/scanner\r\ninjected", "/scanner\r\ninjected"),
        request);

    String joined = joinedMessages();
    assertThat(joined).contains("No resource");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("/scanner__injected");
  }

  private String joinedMessages() {
    return logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }
}
