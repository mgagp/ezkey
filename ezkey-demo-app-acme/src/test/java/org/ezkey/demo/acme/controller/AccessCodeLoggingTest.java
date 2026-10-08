/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AccessCodeLoggingTest
 * Description: Access codes and challenge values must not appear in INFO logs.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Optional;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.sdk.AuthAttemptCreateResponse;
import org.ezkey.sdk.EzkeyClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccessCodeLoggingTest {

  private static final String CODE = "0123456789abcdef0123456789abcdef";

  private ListAppender<ILoggingEvent> accessCodeAppender;
  private ListAppender<ILoggingEvent> loginAppender;
  private Logger accessCodeLogger;
  private Logger loginLogger;

  @BeforeEach
  void setUpAppenders() {
    accessCodeLogger = (Logger) LoggerFactory.getLogger(AccessCodeController.class);
    loginLogger = (Logger) LoggerFactory.getLogger(LoginController.class);
    accessCodeAppender = new ListAppender<>();
    loginAppender = new ListAppender<>();
    accessCodeAppender.start();
    loginAppender.start();
    accessCodeLogger.addAppender(accessCodeAppender);
    loginLogger.addAppender(loginAppender);
  }

  @AfterEach
  void tearDownAppenders() {
    accessCodeLogger.detachAppender(accessCodeAppender);
    loginLogger.detachAppender(loginAppender);
  }

  @Test
  void shouldNotLogAccessCodeOnSuccessfulActivation() throws Exception {
    AccessCodeService accessCodeService = mock(AccessCodeService.class);
    DemoApiKeyConfigService demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    DemoRateLimitService demoRateLimitService = mock(DemoRateLimitService.class);
    when(demoRateLimitService.checkLogin(any()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    when(accessCodeService.findSlotIdByCode(CODE)).thenReturn(Optional.of("northwind"));
    when(accessCodeService.getLabel("northwind")).thenReturn("Northwind Portal");

    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AccessCodeController(
                    accessCodeService, demoApiKeyConfigService, demoRateLimitService))
            .build();

    mockMvc.perform(get("/t/{code}", CODE));

    String joined =
        accessCodeAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);
    assertThat(joined).doesNotContain(CODE);
    assertThat(joined).contains("Northwind Portal");
  }

  @Test
  void shouldNotLogChallengeValueOnLogin() throws Exception {
    EzkeyClientProvider ezkeyClientProvider = mock(EzkeyClientProvider.class);
    DemoApiKeyConfigService demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    DemoRateLimitService demoRateLimitService = mock(DemoRateLimitService.class);
    AccessCodeService accessCodeService = mock(AccessCodeService.class);
    EzkeyClient client = mock(EzkeyClient.class);
    when(demoRateLimitService.checkLogin(any()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    when(ezkeyClientProvider.getClient(any())).thenReturn(client);
    when(client.createAuthAttemptByUserIdentifier("alice", true))
        .thenReturn(new AuthAttemptCreateResponse(99, 42, 120, "2026-10-08T12:00:00Z", null, null));

    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(
                new LoginController(
                    ezkeyClientProvider,
                    demoApiKeyConfigService,
                    demoRateLimitService,
                    accessCodeService))
            .build();

    mockMvc.perform(post("/login").param("username", "alice"));

    String joined =
        loginAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);
    assertThat(joined).doesNotContain("42");
    assertThat(joined).doesNotContain("challenge=");
    assertThat(joined).contains("authAttemptId=99");
  }
}
