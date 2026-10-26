/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: GlobalExceptionHandlerLoggingTest
 * Description: Caller-controlled values CR/LF must be neutralized in Auth API exception logs (#801).
 */

package org.ezkey.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Output-capture tests: caller-controlled request paths and exception messages in Auth API
 * exception logs go through {@code LogSanitizer} (CodeQL #65, #83–#85 / issue #801).
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
  @DisplayName("NoResourceFound DEBUG does not echo CR/LF from request path (LogSanitizer)")
  void noResourceFoundDebugSanitizesPath() {
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    httpRequest.setRequestURI("/scanner\r\ninjected");
    ServletWebRequest request = new ServletWebRequest(httpRequest);

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

  @Test
  @DisplayName("MethodNotAllowed DEBUG does not echo CR/LF from request path (LogSanitizer)")
  void methodNotAllowedDebugSanitizesPath() {
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    httpRequest.setRequestURI("/api/v1/enrollments/bind\r\ninjected");
    ServletWebRequest request = new ServletWebRequest(httpRequest);

    handler.handleHttpRequestMethodNotSupported(
        new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"), request);

    String joined = joinedMessages();
    assertThat(joined).contains("Method not allowed");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("/api/v1/enrollments/bind__injected");
  }

  @Test
  @DisplayName("EnrollmentBindingFailed WARN does not echo CR/LF from exception message")
  void enrollmentBindingFailedWarnSanitizesMessage() {
    MockHttpServletRequest httpRequest = new MockHttpServletRequest();
    httpRequest.setRequestURI("/api/v1/enrollments/bind");
    ServletWebRequest request = new ServletWebRequest(httpRequest);

    handler.handleEnrollmentBindingFailed(
        new EnrollmentBindingFailedException("bind failed\r\ninjected"), request);

    String joined = joinedMessages();
    assertThat(joined).contains("Enrollment bind rejected");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("bind failed__injected");
  }

  private String joinedMessages() {
    return logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }
}
