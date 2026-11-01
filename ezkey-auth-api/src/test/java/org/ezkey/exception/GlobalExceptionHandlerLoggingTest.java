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
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.stream.Stream;
import org.ezkey.audit.integrity.AuditChainHeartbeatEvaluation;
import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.audit.integrity.AuditChainHeartbeatPhase;
import org.ezkey.exception.audit.AuditChainHeartbeatDegradedException;
import org.ezkey.exception.auth.AuthRespondRateLimitExceededException;
import org.ezkey.exception.auth.EnrollmentBindingFailedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Output-capture tests: caller-controlled request paths, URLs, and exception messages in Auth API
 * exception logs go through {@code LogSanitizer} (CodeQL #65, #83–#85 / issue #801; follow-up
 * #1040).
 *
 * @since 2026
 */
class GlobalExceptionHandlerLoggingTest {

  private GlobalExceptionHandler handler;
  private AuditChainHeartbeatGuardService heartbeatGuard;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger handlerLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    heartbeatGuard = mock(AuditChainHeartbeatGuardService.class);
    when(heartbeatGuard.evaluate())
        .thenReturn(
            new AuditChainHeartbeatEvaluation(
                AuditChainHeartbeatPhase.OK, null, null, null, null, false));
    handler = new GlobalExceptionHandler(heartbeatGuard);

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

  static Stream<Arguments> sanitizedLogSites() {
    return Stream.of(
        Arguments.of(
            "NoResourceFound path",
            "No resource",
            "/scanner__injected",
            (SiteInvoker)
                (handler, guard) -> {
                  MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                  httpRequest.setRequestURI("/scanner\r\ninjected");
                  ServletWebRequest request = new ServletWebRequest(httpRequest);
                  handler.handleNoResourceFoundException(
                      new NoResourceFoundException(
                          HttpMethod.GET, "/scanner\r\ninjected", "/scanner\r\ninjected"),
                      request);
                }),
        Arguments.of(
            "MethodNotAllowed path",
            "Method not allowed",
            "/api/v1/enrollments/bind__injected",
            (SiteInvoker)
                (handler, guard) -> {
                  MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                  httpRequest.setRequestURI("/api/v1/enrollments/bind\r\ninjected");
                  ServletWebRequest request = new ServletWebRequest(httpRequest);
                  handler.handleHttpRequestMethodNotSupported(
                      new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"),
                      request);
                }),
        Arguments.of(
            "EnrollmentBindingFailed message (covers other controlled-message handlers)",
            "Enrollment bind rejected",
            "bind failed__injected",
            (SiteInvoker)
                (handler, guard) -> {
                  MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                  httpRequest.setRequestURI("/api/v1/enrollments/bind");
                  ServletWebRequest request = new ServletWebRequest(httpRequest);
                  handler.handleEnrollmentBindingFailed(
                      new EnrollmentBindingFailedException("bind failed\r\ninjected"), request);
                }),
        Arguments.of(
            "NoHandlerFound method + URL",
            "No handler",
            "GE__T /missing__injected",
            (SiteInvoker)
                (handler, guard) -> {
                  MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                  httpRequest.setRequestURI("/missing\r\ninjected");
                  ServletWebRequest request = new ServletWebRequest(httpRequest);
                  handler.handleNoHandlerFoundException(
                      new NoHandlerFoundException(
                          "GE\r\nT", "/missing\r\ninjected", new HttpHeaders()),
                      request);
                }),
        Arguments.of(
            "heartbeat-degraded path",
            "heartbeat-degraded",
            "/api/v1/auth-attempts/respond__injected",
            (SiteInvoker)
                (handler, guard) -> {
                  when(guard.evaluate())
                      .thenReturn(
                          new AuditChainHeartbeatEvaluation(
                              AuditChainHeartbeatPhase.DEGRADED_SERVICE,
                              null,
                              null,
                              null,
                              null,
                              true));
                  MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                  httpRequest.setRequestURI("/api/v1/auth-attempts/respond\r\ninjected");
                  ServletWebRequest request = new ServletWebRequest(httpRequest);
                  handler.handleAuditChainHeartbeatDegraded(
                      new AuditChainHeartbeatDegradedException(), request);
                }),
        Arguments.of(
            "rate-limit path",
            "Respond rate limit",
            "/api/v1/auth-attempts/respond__injected",
            (SiteInvoker)
                (handler, guard) -> {
                  MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                  httpRequest.setRequestURI("/api/v1/auth-attempts/respond\r\ninjected");
                  ServletWebRequest request = new ServletWebRequest(httpRequest);
                  handler.handleAuthRespondRateLimitExceeded(
                      new AuthRespondRateLimitExceededException(60L), request);
                }),
        Arguments.of(
            "NoPendingAuthAttempt getDescription",
            "No pending auth attempt",
            "uri=/api/v1/auth-attempts/pending__injected",
            (SiteInvoker)
                (handler, guard) -> {
                  MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                  httpRequest.setRequestURI("/api/v1/auth-attempts/pending\r\ninjected");
                  ServletWebRequest request = new ServletWebRequest(httpRequest);
                  handler.handleNoPendingAuthAttempt(
                      new NoPendingAuthAttemptException("no pending"), request);
                }));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sanitizedLogSites")
  @DisplayName("Formatted exception logs neutralize CR/LF via LogSanitizer")
  void formattedLogNeutralizesCrLf(
      String site, String logMarker, String sanitizedFragment, SiteInvoker invoker) {
    invoker.invoke(handler, heartbeatGuard);

    String joined = joinedMessages();
    assertThat(joined).as("%s log marker", site).contains(logMarker);
    assertThat(joined).as("%s must not contain CR", site).doesNotContain("\r");
    assertThat(joined).as("%s must not contain LF injection", site).doesNotContain("\ninjected");
    assertThat(joined).as("%s sanitized fragment", site).contains(sanitizedFragment);
  }

  private String joinedMessages() {
    return logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }

  @FunctionalInterface
  private interface SiteInvoker {
    void invoke(GlobalExceptionHandler handler, AuditChainHeartbeatGuardService guard);
  }
}
