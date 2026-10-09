/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LoginTenantLinkLayoutTest
 * Description: Tenant-link login layouts A/B/C — chrome hidden server-side, entry=link marker.
 */

package org.ezkey.demo.acme.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.AccessCodeService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.ezkey.demo.acme.web.LinkEntryMarker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders {@code login.html} through Thymeleaf so server-side chrome removal is asserted in HTML.
 */
class LoginTenantLinkLayoutTest {

  private EzkeyClientProvider ezkeyClientProvider;
  private DemoApiKeyConfigService demoApiKeyConfigService;
  private DemoRateLimitService demoRateLimitService;
  private AccessCodeService accessCodeService;
  private LoginController loginController;
  private MockMvc loginMvc;
  private MockMvc accessCodeMvc;

  @BeforeEach
  void setUp() {
    ezkeyClientProvider = mock(EzkeyClientProvider.class);
    demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    demoRateLimitService = mock(DemoRateLimitService.class);
    accessCodeService = mock(AccessCodeService.class);
    when(demoRateLimitService.checkLogin(any()))
        .thenReturn(new DemoRateLimitService.RateLimitDecision(true, 0, "127.0.0.1"));
    when(ezkeyClientProvider.getClient(any())).thenReturn(null);

    loginController =
        new LoginController(
            ezkeyClientProvider, demoApiKeyConfigService, demoRateLimitService, accessCodeService);
    HandlerInterceptor csrfModel =
        new HandlerInterceptor() {
          @Override
          public void postHandle(
              jakarta.servlet.http.HttpServletRequest request,
              jakarta.servlet.http.HttpServletResponse response,
              Object handler,
              org.springframework.web.servlet.ModelAndView modelAndView) {
            if (modelAndView != null) {
              modelAndView.addObject(
                  "_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-csrf-token"));
            }
          }
        };

    loginMvc =
        MockMvcBuilders.standaloneSetup(loginController)
            .setViewResolvers(thymeleafViewResolver())
            .addInterceptors(csrfModel)
            .build();

    AccessCodeController accessCodeController =
        new AccessCodeController(accessCodeService, demoApiKeyConfigService, demoRateLimitService);
    accessCodeMvc =
        MockMvcBuilders.standaloneSetup(accessCodeController)
            .setViewResolvers(thymeleafViewResolver())
            .addInterceptors(csrfModel)
            .build();
  }

  @Test
  void stateA_nominal_rendersFormWithoutSelfServiceChrome() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn("northwind");
    when(accessCodeService.getLabel("northwind")).thenReturn("Northwind Portal");

    String html = renderLogin(session, null, null, null);

    assertThat(html).contains("Sign in to Northwind Portal");
    assertThat(html).contains("name=\"username\"");
    assertThat(html).contains("name=\"entry\" value=\"link\"");
    assertThat(html).doesNotContain("api-key-modal");
    assertThat(html).doesNotContain("ABOUT THIS DEMO");
    assertThat(html).doesNotContain("CONFIGURE API KEY");
  }

  @Test
  void stateA_expiredAndRejected_keepFormWithoutChrome() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn("northwind");
    when(accessCodeService.getLabel("northwind")).thenReturn("Northwind Portal");

    String expired = renderLogin(session, "expired", null, null);
    assertThat(expired).contains("name=\"username\"");
    assertThat(expired).contains("Authentication request expired");
    assertThat(expired).doesNotContain("api-key-modal");
    assertThat(expired).doesNotContain("ABOUT THIS DEMO");

    String rejected = renderLogin(session, "rejected", null, null);
    assertThat(rejected).contains("name=\"username\"");
    assertThat(rejected).contains("Authentication rejected by user");
    assertThat(rejected).doesNotContain("api-key-modal");
  }

  @Test
  void stateB_sessionexpiredWithMarker_singleMessageNoFormNoChrome() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn(null);

    String html = renderLogin(session, "sessionexpired", null, LinkEntryMarker.VALUE);

    assertThat(html).contains(htmlEscaped(DemoAuthMessages.SESSION_OR_SLOT_LOST));
    assertThat(html).doesNotContain("name=\"username\"");
    assertThat(html).doesNotContain("api-key-modal");
    assertThat(html).doesNotContain("ABOUT THIS DEMO");
    assertThat(html).doesNotContain(htmlEscaped(DemoAuthMessages.LINK_RECOVERY_REOPEN));
    assertThat(html).doesNotContain("data-testid=\"link-recovery-hint\"");
  }

  @Test
  void stateB_logoutFromSlotMode_logoutMessagePlusRecovery() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn(null);

    String html = renderLogin(session, null, "true", LinkEntryMarker.VALUE);

    assertThat(html).contains("You have been logged out.");
    assertThat(html).contains(htmlEscaped(DemoAuthMessages.LINK_RECOVERY_REOPEN));
    assertThat(html).doesNotContain("name=\"username\"");
    assertThat(html).doesNotContain("api-key-modal");
  }

  @Test
  void stateB_unknownAccessCode_genericPlusHintNoChrome() throws Exception {
    when(accessCodeService.findSlotIdByCode("boguscode000000000000000000000000"))
        .thenReturn(Optional.empty());

    MvcResult result =
        accessCodeMvc
            .perform(get("/t/{code}", "boguscode000000000000000000000000"))
            .andExpect(status().isOk())
            .andReturn();

    String html = result.getResponse().getContentAsString();
    assertThat(html).contains(DemoAuthMessages.GENERIC_SIGN_IN_FAILED);
    assertThat(html).contains(htmlEscaped(DemoAuthMessages.LINK_HINT_CHECK_OR_ASK));
    assertThat(html).doesNotContain("name=\"username\"");
    assertThat(html).doesNotContain("api-key-modal");
    assertThat(html).doesNotContain("ABOUT THIS DEMO");
  }

  @Test
  void staleTabPostWithMarkerAndNoSlot_sessionOrSlotLostStateB() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn(null);
    when(ezkeyClientProvider.getClient(session)).thenReturn(null);

    loginMvc
        .perform(
            post("/login")
                .session(session)
                .param("username", "alice")
                .param(LinkEntryMarker.PARAM, LinkEntryMarker.VALUE))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error=sessionexpired&entry=link"));

    ExtendedModelMap model = new ExtendedModelMap();
    loginController.loginPage("sessionexpired", null, LinkEntryMarker.VALUE, session, model);
    assertThat(model.get("error")).isEqualTo(DemoAuthMessages.SESSION_OR_SLOT_LOST);
    assertThat(model.get("showLoginForm")).isEqualTo(false);
    assertThat(model.get("showSelfServiceChrome")).isEqualTo(false);
    assertThat(model.get("recoveryHint")).isNull();
  }

  @Test
  void stateC_plainLogin_unchangedChrome() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn(null);

    String html = renderLogin(session, null, null, null);

    assertThat(html).contains("CONFIGURE API KEY");
    assertThat(html).contains("api-key-modal");
    assertThat(html).contains("ABOUT THIS DEMO");
    assertThat(html).contains("name=\"username\"");
    assertThat(html).doesNotContain("name=\"entry\" value=\"link\"");
  }

  @Test
  void entryLinkWithSlotStillActive_isStateA() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn("northwind");
    when(accessCodeService.getLabel("northwind")).thenReturn("Northwind Portal");

    String html = renderLogin(session, null, null, LinkEntryMarker.VALUE);

    assertThat(html).contains("Sign in to Northwind Portal");
    assertThat(html).contains("name=\"username\"");
    assertThat(html).doesNotContain("api-key-modal");
    assertThat(html).doesNotContain(DemoAuthMessages.LINK_RECOVERY_REOPEN);
  }

  @Test
  void authStatusSessionLostInSlotMode_redirectCarriesEntryLink() throws Exception {
    MockHttpSession session = new MockHttpSession();
    when(demoApiKeyConfigService.getActiveSlotId(session)).thenReturn("northwind");

    loginMvc
        .perform(get("/api/auth-status").session(session))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.redirectUrl")
                .value("/login?error=sessionexpired&entry=link"));
  }

  private String renderLogin(MockHttpSession session, String error, String logout, String entry)
      throws Exception {
    var builder = get("/login").session(session);
    if (error != null) {
      builder = builder.param("error", error);
    }
    if (logout != null) {
      builder = builder.param("logout", logout);
    }
    if (entry != null) {
      builder = builder.param(LinkEntryMarker.PARAM, entry);
    }
    MvcResult result = loginMvc.perform(builder).andExpect(status().isOk()).andReturn();
    return result.getResponse().getContentAsString();
  }

  private static ThymeleafViewResolver thymeleafViewResolver() {
    ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
    templateResolver.setPrefix("templates/");
    templateResolver.setSuffix(".html");
    templateResolver.setTemplateMode(TemplateMode.HTML);
    templateResolver.setCharacterEncoding("UTF-8");

    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(templateResolver);

    ThymeleafViewResolver viewResolver = new ThymeleafViewResolver();
    viewResolver.setTemplateEngine(engine);
    viewResolver.setCharacterEncoding("UTF-8");
    return viewResolver;
  }

  /** Thymeleaf HTML-escapes {@code '} as {@code &#39;}. */
  private static String htmlEscaped(String raw) {
    return raw.replace("'", "&#39;");
  }
}
