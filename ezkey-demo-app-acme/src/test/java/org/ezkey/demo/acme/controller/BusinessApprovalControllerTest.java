/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: BusinessApprovalControllerTest
 * Description: Challenge forced and generic errors for business approval.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.sdk.AuthAttemptContext;
import org.ezkey.sdk.AuthAttemptCreateResponse;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BusinessApprovalControllerTest {

  private EzkeyClientProvider ezkeyClientProvider;
  private EzkeyClient ezkeyClient;
  private MockMvc mockMvc;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger businessLogger;

  @BeforeEach
  void setUp() {
    ezkeyClientProvider = mock(EzkeyClientProvider.class);
    ezkeyClient = mock(EzkeyClient.class);
    when(ezkeyClientProvider.getClient(any())).thenReturn(ezkeyClient);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new BusinessApprovalController(ezkeyClientProvider))
            .build();

    businessLogger = (Logger) LoggerFactory.getLogger(BusinessApprovalController.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    businessLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    businessLogger.detachAppender(logAppender);
  }

  @Test
  void shouldRequestChallengeForBusinessApproval() throws Exception {
    when(ezkeyClient.createAuthAttemptByUserIdentifier(
            eq("jane"), eq(true), any(AuthAttemptContext.class)))
        .thenReturn(
            new AuthAttemptCreateResponse(9, 42, 120, "2026-10-08T12:00:00Z", "Payment", "msg"));

    MockHttpSession session = new MockHttpSession();
    session.setAttribute("user", new AuthenticatedUser("john", "John", 1));

    mockMvc
        .perform(
            post("/api/business-approval")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario\":\"PAYMENT\",\"approverIdentifier\":\"jane\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authAttemptId").value(9))
        .andExpect(jsonPath("$.authAttemptChallenge").value(42));

    verify(ezkeyClient)
        .createAuthAttemptByUserIdentifier(eq("jane"), eq(true), any(AuthAttemptContext.class));
  }

  @Test
  void shouldReturnGenericErrorWithoutSdkMessage() throws Exception {
    when(ezkeyClient.createAuthAttemptByUserIdentifier(
            eq("jane"), eq(true), any(AuthAttemptContext.class)))
        .thenThrow(new EzkeyException("raw SDK leak"));

    MockHttpSession session = new MockHttpSession();
    session.setAttribute("user", new AuthenticatedUser("john", "John", 1));

    mockMvc
        .perform(
            post("/api/business-approval")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario\":\"PAYMENT\",\"approverIdentifier\":\"jane\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
  }

  @Test
  void shouldSanitizeApproverInErrorLogWithoutStackTrace() throws Exception {
    when(ezkeyClient.createAuthAttemptByUserIdentifier(
            eq("jane\ninjected"), eq(true), any(AuthAttemptContext.class)))
        .thenThrow(new EzkeyException("raw SDK leak", 503, null));

    MockHttpSession session = new MockHttpSession();
    session.setAttribute("user", new AuthenticatedUser("john", "John", 1));

    mockMvc
        .perform(
            post("/api/business-approval")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenario\":\"PAYMENT\",\"approverIdentifier\":\"jane\\ninjected\"}"))
        .andExpect(status().isBadRequest());

    String joined =
        logAppender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);
    assertThat(joined).contains("jane_injected");
    assertThat(joined).doesNotContain("jane\ninjected");
    assertThat(joined).contains("exceptionClass=EzkeyException");
    assertThat(joined).contains("httpStatus=503");
    assertThat(joined).doesNotContain("raw SDK leak");
    assertThat(logAppender.list)
        .filteredOn(e -> e.getFormattedMessage().contains("Failed to create business-approval"))
        .allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
  }
}
