/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LoginControllerSecurityTest
 * Description: Verifies CSRF protection for the ACME demo login endpoints.
 */

package org.ezkey.demo.acme.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.demo.acme.config.EzkeyClientProvider;
import org.ezkey.demo.acme.security.DemoRateLimitService;
import org.ezkey.demo.acme.service.DemoApiKeyConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerSecurityTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    EzkeyClientProvider ezkeyClientProvider = mock(EzkeyClientProvider.class);
    DemoApiKeyConfigService demoApiKeyConfigService = mock(DemoApiKeyConfigService.class);
    DemoRateLimitService demoRateLimitService = mock(DemoRateLimitService.class);
    LoginController loginController =
        new LoginController(ezkeyClientProvider, demoApiKeyConfigService, demoRateLimitService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(loginController)
            .addFilters(new CsrfFilter(new HttpSessionCsrfTokenRepository()))
            .build();
  }

  @Test
  void shouldRejectLoginPostWithoutCsrfToken() throws Exception {
    mockMvc.perform(post("/login").param("username", "alice")).andExpect(status().isForbidden());
  }

  @Test
  void shouldRejectApplyApiKeyWithoutCsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/api/apply-api-key")
                .contentType("application/json")
                .content("{\"integrationKey\":\"ikey\",\"secretKey\":\"skey\"}"))
        .andExpect(status().isForbidden());
  }
}
