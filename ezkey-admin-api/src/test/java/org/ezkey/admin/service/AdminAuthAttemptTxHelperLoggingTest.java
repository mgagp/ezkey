/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthAttemptTxHelperLoggingTest
 * Description: Auth-attempt challenge must not appear in AdminAuthAttemptTxHelper logs (#750).
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/**
 * Output-capture: AdminAuthAttemptTxHelper must not log challenge values (issue #750 / review e).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthAttemptTxHelperLoggingTest {

  private static final int CHALLENGE = 424242;
  private static final int ATTEMPT_ID = 99;

  @Mock private AuthAttemptService authAttemptService;

  private AdminAuthAttemptTxHelper helper;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger helperLogger;
  private Level previousLevel;

  @BeforeEach
  void setUp() {
    helper = new AdminAuthAttemptTxHelper(authAttemptService);
    helperLogger = (Logger) LoggerFactory.getLogger(AdminAuthAttemptTxHelper.class);
    previousLevel = helperLogger.getLevel();
    helperLogger.setLevel(Level.DEBUG);
    logAppender = new ListAppender<>();
    logAppender.start();
    helperLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    helperLogger.detachAppender(logAppender);
    helperLogger.setLevel(previousLevel);
  }

  @Test
  @DisplayName("createAuthAttempt logs authAttemptId and never the challenge code")
  void createAuthAttemptDoesNotLogChallenge() {
    AuthAttemptCreateResponse response = new AuthAttemptCreateResponse();
    response.setAuthAttemptId(ATTEMPT_ID);
    response.setAuthAttemptChallenge(CHALLENGE);
    when(authAttemptService.create(any())).thenReturn(response);

    helper.createAuthAttempt(new AuthAttemptCreateRequest());

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

    assertThat(joined).contains("ID: " + ATTEMPT_ID);
    assertThat(joined).doesNotContain(String.valueOf(CHALLENGE));
    assertThat(joined).doesNotContain("challenge: " + CHALLENGE);
  }
}
