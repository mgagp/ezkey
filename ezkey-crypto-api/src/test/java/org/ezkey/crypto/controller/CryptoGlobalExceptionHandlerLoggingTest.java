/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CryptoGlobalExceptionHandlerLoggingTest
 * Description: Request URI CR/LF must be neutralized in Crypto API exception logs (#801).
 */

package org.ezkey.crypto.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Output-capture tests: caller-controlled request paths in crypto exception logs go through {@code
 * LogSanitizer} (CodeQL #97–#99 / issue #801).
 *
 * @since 2026
 */
class CryptoGlobalExceptionHandlerLoggingTest {

  private CryptoGlobalExceptionHandler handler;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger handlerLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    handler = new CryptoGlobalExceptionHandler();

    handlerLogger = (Logger) LoggerFactory.getLogger(CryptoGlobalExceptionHandler.class);
    previousLevel = handlerLogger.getLevel();
    handlerLogger.setLevel(Level.ERROR);
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
  @DisplayName("JSON parse ERROR does not echo CR/LF from request URI (LogSanitizer)")
  void jsonParseErrorSanitizesRequestUri() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/crypto/sign\r\ninjected");

    handler.handleHttpMessageNotReadableException(
        new HttpMessageNotReadableException("JSON parse error: bad", mock(HttpInputMessage.class)),
        request);

    String joined = joinedMessages();
    assertThat(joined).contains("JSON parsing error");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("/api/v1/crypto/sign__injected");
  }

  @Test
  @DisplayName("RuntimeException ERROR does not echo CR/LF from request URI (LogSanitizer)")
  void runtimeExceptionErrorSanitizesRequestUri() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/crypto/validate\r\ninjected");

    handler.handleRuntimeException(new RuntimeException("boom"), request);

    String joined = joinedMessages();
    assertThat(joined).contains("RuntimeException in crypto API endpoint");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("/api/v1/crypto/validate__injected");
  }

  @Test
  @DisplayName("generic Exception ERROR does not echo CR/LF from request URI (LogSanitizer)")
  void genericExceptionErrorSanitizesRequestUri() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/crypto/encrypt\r\ninjected");

    handler.handleGenericException(new Exception("boom"), request);

    String joined = joinedMessages();
    assertThat(joined).contains("Exception in crypto API endpoint");
    assertThat(joined).doesNotContain("\r");
    assertThat(joined).doesNotContain("\ninjected");
    assertThat(joined).contains("/api/v1/crypto/encrypt__injected");
  }

  private String joinedMessages() {
    return logAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .reduce("", (a, b) -> a + "\n" + b);
  }
}
