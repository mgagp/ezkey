/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuditChainHeartbeatPeripheralInterceptorLoggingTest
 * Description: Request path CR/LF must be neutralized in fail-closed WARN logs (#801 Lot E).
 */

package org.ezkey.audit.integrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.ezkey.exception.audit.AuditChainHeartbeatDegradedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Output-capture tests: caller-controlled request path in peripheral heartbeat fail-closed WARN
 * logs goes through {@code LogSanitizer} (CodeQL #66 / issue #801). Does not alter guard/HMAC
 * behavior — only log formatting of the path argument.
 *
 * @since 2026
 */
class AuditChainHeartbeatPeripheralInterceptorLoggingTest {

  private AuditChainHeartbeatGuardService guardService;
  private AuditChainHeartbeatPeripheralInterceptor interceptor;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger interceptorLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    guardService = mock(AuditChainHeartbeatGuardService.class);
    interceptor = new AuditChainHeartbeatPeripheralInterceptor(guardService);

    interceptorLogger =
        (Logger) LoggerFactory.getLogger(AuditChainHeartbeatPeripheralInterceptor.class);
    previousLevel = interceptorLogger.getLevel();
    interceptorLogger.setLevel(Level.WARN);
    logAppender = new ListAppender<>();
    logAppender.start();
    interceptorLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    interceptorLogger.detachAppender(logAppender);
    interceptorLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("fail-closed WARN does not echo CR/LF from request path (LogSanitizer)")
  void failClosedWarnSanitizesRequestPath() {
    when(guardService.shouldFailClosedPeripheralWrites()).thenReturn(true);
    when(guardService.evaluate())
        .thenReturn(
            new AuditChainHeartbeatEvaluation(
                AuditChainHeartbeatPhase.DEGRADED_SERVICE, 1L, null, null, null, true));

    // Trailing CR/LF still maps to the gated route (strip for match only); log uses LogSanitizer.
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth-attempts");
    request.setServletPath("/api/v1/auth-attempts\r\n");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
        .isInstanceOf(AuditChainHeartbeatDegradedException.class);

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains("Peripheral MFA request blocked");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).contains("path=/api/v1/auth-attempts__");
  }
}
