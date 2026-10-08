/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LoginControllerChallengeAndSessionTest
 * Description: Challenge always on, slot label, invalidate on paste, changeSessionId on ACCEPTED.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.sdk.AuthAttemptCreateResponse;
import org.ezkey.sdk.AuthAttemptWaitResponse;
import org.ezkey.sdk.EzkeyClient;
import org.ezkey.sdk.EzkeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerChallengeAndSessionTest {

  private EzkeyClientProvider ezkeyClientProvider;
  private DemoApiKeyConfigService demoApiKeyConfigService;
  private DemoRateLimitService demoRateLimitService;
  private AccessCodeService accessCodeService;
  private EzkeyClient ezkeyClient;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ezkeyClientProvider = mock(EzkeyClientProvider.class);
    demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    demoRateLimitService = mock(DemoRateLimitService.class);
    accessCodeService = mock(AccessCodeService.class);
    ezkeyClient = mock(EzkeyClient.class);
    when(demoRateLimitService.checkLogin(any()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    when(demoRateLimitService.checkApplyApiKey(any()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    when(ezkeyClientProvider.getClient(any())).thenReturn(ezkeyClient);

    LoginController controller =
        new LoginController(
            ezkeyClientProvider, demoApiKeyConfigService, demoRateLimitService, accessCodeService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void shouldForceChallengeRequestedTrueEvenWithoutFormField() throws Exception {
    when(ezkeyClient.createAuthAttemptByUserIdentifier(eq("alice"), eq(true)))
        .thenReturn(sampleCreateResponse(42, 12));

    mockMvc
        .perform(post("/login").param("username", "alice"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/challenge-wait"));

    verify(ezkeyClient).createAuthAttemptByUserIdentifier("alice", true);
    verify(ezkeyClient, never()).createAuthAttemptByUserIdentifier(anyString(), eq(false));
  }

  @Test
  void shouldShowSlotLabelOnLogin() {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn("northwind");
    when(accessCodeService.getLabel("northwind")).thenReturn("Northwind Portal");
    LoginController controller =
        new LoginController(
            ezkeyClientProvider, demoApiKeyConfigService, demoRateLimitService, accessCodeService);
    org.springframework.ui.ExtendedModelMap model = new org.springframework.ui.ExtendedModelMap();

    String view = controller.loginPage(null, null, session, model);

    assertThat(view).isEqualTo("login");
    assertThat(model.get("loginHeading")).isEqualTo("Sign in to Northwind Portal");
    assertThat(model.get("slotLabel")).isEqualTo("Northwind Portal");
  }

  @Test
  void shouldUseGenericErrorForSdkFailures() throws Exception {
    when(ezkeyClient.createAuthAttemptByUserIdentifier(anyString(), anyBoolean()))
        .thenThrow(new EzkeyException("User not found in integration XYZ"));

    mockMvc
        .perform(post("/login").param("username", "nobody"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error=authfailed"));
  }

  @Test
  void shouldInvalidateSessionWhenApplyingPastedKeys() throws Exception {
    MockHttpSession prior = new MockHttpSession();
    prior.setAttribute(DemoApiKeyConfigService.SESSION_ACCESS_CODE_SLOT_ID, "northwind");
    when(demoApiKeyConfigService.applyApiKey(any(), eq("ikey"), eq("skey"))).thenReturn(true);

    mockMvc
        .perform(
            post("/api/apply-api-key")
                .session(prior)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"integrationKey\":\"ikey\",\"secretKey\":\"skey\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    assertThat(prior.isInvalid()).isTrue();
  }

  @Test
  void shouldChangeSessionIdOnAccepted() throws Exception {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("pendingAuthAttemptId", 7);
    session.setAttribute("pendingUsername", "alice");
    session.setAttribute("pendingDisplayName", "Alice");
    session.setAttribute("pendingEnrollmentId", 3);

    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenReturn(new AuthAttemptWaitResponse("ACCEPTED", true, false));

    String beforeId = session.getId();
    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("accepted"));

    assertThat(session.getId()).isNotEqualTo(beforeId);
    assertThat(session.getAttribute("user")).isNotNull();
  }

  private static AuthAttemptCreateResponse sampleCreateResponse(int id, Integer challenge) {
    return new AuthAttemptCreateResponse(id, challenge, 120, "2026-10-08T12:00:00Z", null, null);
  }

  @Test
  void shouldNotEchoRawSdkMessageInAuthStatusError() throws Exception {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("pendingAuthAttemptId", 7);
    session.setAttribute("pendingUsername", "alice");
    when(ezkeyClient.waitForAuthAttempt(eq(7), anyInt(), anyInt()))
        .thenThrow(new EzkeyException("secret detail from SDK"));

    mockMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(DemoAuthMessages.GENERIC_SIGN_IN_FAILED));
  }
}
