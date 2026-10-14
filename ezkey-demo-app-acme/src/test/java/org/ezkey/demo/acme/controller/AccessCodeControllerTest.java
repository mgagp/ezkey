/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AccessCodeControllerTest
 * Description: /t/{code} behaviour — generic error, 303, session slot id only, rate limit.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccessCodeControllerTest {

  private static final String VALID_CODE = "0123456789abcdef0123456789abcdef";

  private AccessCodeService accessCodeService;
  private DemoApiKeyConfigService demoApiKeyConfigService;
  private DemoRateLimitService demoRateLimitService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accessCodeService = mock(AccessCodeService.class);
    demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    demoRateLimitService = mock(DemoRateLimitService.class);
    when(demoRateLimitService.checkAccessLink(any(), nullable(String.class)))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    AccessCodeController controller =
        new AccessCodeController(accessCodeService, demoApiKeyConfigService, demoRateLimitService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void shouldRenderLoginWithGenericErrorForUnknownCode() throws Exception {
    when(accessCodeService.findSlotIdByCode(VALID_CODE)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/t/{code}", VALID_CODE))
        .andExpect(status().isOk())
        .andExpect(view().name("login"))
        .andExpect(model().attribute("hasError", true))
        .andExpect(model().attribute("error", DemoAuthMessages.GENERIC_SIGN_IN_FAILED))
        .andExpect(model().attribute("showSelfServiceChrome", false))
        .andExpect(model().attribute("showLoginForm", false))
        .andExpect(model().attribute("recoveryHint", DemoAuthMessages.LINK_HINT_CHECK_OR_ASK));
  }

  @Test
  void shouldSeeOtherRedirectAndStoreOnlySlotIdForKnownCode() throws Exception {
    when(accessCodeService.findSlotIdByCode(VALID_CODE)).thenReturn(Optional.of("northwind"));
    when(accessCodeService.getLabel("northwind")).thenReturn("Northwind Portal");

    MockHttpSession prior = new MockHttpSession();
    prior.setAttribute(DemoApiKeyConfigService.SESSION_INTEGRATION_KEY, "old-ikey");
    prior.setAttribute(DemoApiKeyConfigService.SESSION_SECRET_KEY, "old-skey");

    MvcResult result =
        mockMvc
            .perform(get("/t/{code}", VALID_CODE).session(prior))
            .andExpect(status().isSeeOther())
            .andExpect(redirectedUrl("/login"))
            .andReturn();

    assertThat(prior.isInvalid()).isTrue();
    MockHttpSession newSession = (MockHttpSession) result.getRequest().getSession(false);
    assertThat(newSession).isNotNull();
    verify(demoApiKeyConfigService).activateAccessCodeSlot(newSession, "northwind");
  }

  @Test
  void shouldUseAccessLinkRateLimitBucket() throws Exception {
    when(accessCodeService.findSlotIdByCode(VALID_CODE)).thenReturn(Optional.of("northwind"));
    when(demoRateLimitService.checkAccessLink(any(), eq("northwind")))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(false, 30, "203.0.113.10"));

    mockMvc
        .perform(get("/t/{code}", VALID_CODE))
        .andExpect(status().isOk())
        .andExpect(view().name("login"))
        .andExpect(model().attribute("error", DemoAuthMessages.RATE_LIMIT_LOGIN))
        .andExpect(model().attribute("showSelfServiceChrome", false))
        .andExpect(model().attribute("showLoginForm", false))
        .andExpect(model().attribute("recoveryHint", DemoAuthMessages.LINK_HINT_WAIT_AND_REOPEN));

    verify(demoRateLimitService).checkAccessLink(any(), eq("northwind"));
  }

  @Test
  void shouldRateLimitUnknownCodeViaIpOnlyPath() throws Exception {
    when(accessCodeService.findSlotIdByCode(VALID_CODE)).thenReturn(Optional.empty());
    when(demoRateLimitService.checkAccessLink(any(), isNull()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(false, 30, "203.0.113.10"));

    mockMvc
        .perform(get("/t/{code}", VALID_CODE))
        .andExpect(status().isOk())
        .andExpect(view().name("login"))
        .andExpect(model().attribute("error", DemoAuthMessages.RATE_LIMIT_LOGIN))
        .andExpect(model().attribute("recoveryHint", DemoAuthMessages.LINK_HINT_WAIT_AND_REOPEN));

    verify(demoRateLimitService).checkAccessLink(any(), isNull());
  }

  @Test
  void rateLimitedValidAndInvalidAccessLinksHaveIdenticalResponses() throws Exception {
    String invalidCode = "ffffffffffffffffffffffffffffffff";
    DemoRateLimitService.RateLimitDecision denied =
        new DemoRateLimitService.RateLimitDecision(false, 30, "203.0.113.10");

    when(accessCodeService.findSlotIdByCode(VALID_CODE)).thenReturn(Optional.of("northwind"));
    when(accessCodeService.findSlotIdByCode(invalidCode)).thenReturn(Optional.empty());
    when(demoRateLimitService.checkAccessLink(any(), eq("northwind"))).thenReturn(denied);
    when(demoRateLimitService.checkAccessLink(any(), isNull())).thenReturn(denied);

    MvcResult validResult = mockMvc.perform(get("/t/{code}", VALID_CODE)).andReturn();
    MvcResult invalidResult = mockMvc.perform(get("/t/{code}", invalidCode)).andReturn();

    assertThat(validResult.getResponse().getStatus())
        .isEqualTo(invalidResult.getResponse().getStatus());
    assertThat(validResult.getModelAndView()).isNotNull();
    assertThat(invalidResult.getModelAndView()).isNotNull();
    assertThat(validResult.getModelAndView().getViewName())
        .isEqualTo(invalidResult.getModelAndView().getViewName());
    assertThat(validResult.getModelAndView().getModel())
        .containsAllEntriesOf(invalidResult.getModelAndView().getModel());
    assertThat(invalidResult.getModelAndView().getModel())
        .containsAllEntriesOf(validResult.getModelAndView().getModel());
    assertThat(validResult.getResponse().getHeaderNames())
        .containsExactlyInAnyOrderElementsOf(invalidResult.getResponse().getHeaderNames());
    for (String header : validResult.getResponse().getHeaderNames()) {
      assertThat(validResult.getResponse().getHeaders(header))
          .as("header %s", header)
          .isEqualTo(invalidResult.getResponse().getHeaders(header));
    }
  }
}
