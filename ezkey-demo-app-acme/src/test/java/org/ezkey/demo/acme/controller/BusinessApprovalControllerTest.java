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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.ezkey.sdk.AuthAttemptContext;
import org.ezkey.sdk.AuthAttemptCreateResponse;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BusinessApprovalControllerTest {

  private EzkeyClientProvider ezkeyClientProvider;
  private EzkeyClient ezkeyClient;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ezkeyClientProvider = mock(EzkeyClientProvider.class);
    ezkeyClient = mock(EzkeyClient.class);
    when(ezkeyClientProvider.getClient(any())).thenReturn(ezkeyClient);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new BusinessApprovalController(ezkeyClientProvider))
            .build();
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
}
