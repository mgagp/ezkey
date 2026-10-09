/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: LoginCsrfAccessDeniedHandlerTest
 * Description: CSRF failures on POST /login redirect to sessionexpired (+ optional entry=link).
 */

package org.ezkey.demo.acme.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.demo.acme.DemoAcmeApplication;
import org.ezkey.demo.acme.DemoAuthMessages;
import org.ezkey.demo.acme.web.LinkEntryMarker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = DemoAcmeApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "ezkey.rate-limit.enabled=false",
      "ezkey.access-codes.northwind.code=0123456789abcdef0123456789abcdef",
      "ezkey.access-codes.northwind.integration-key=ezkey_ikey_test",
      "ezkey.access-codes.northwind.secret-key=ezkey_skey_test",
      "ezkey.access-codes.northwind.label=Northwind Portal"
    })
class LoginCsrfAccessDeniedHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void staleCsrfPostLoginWithEntryLink_redirectsToSessionExpiredB() throws Exception {
    mockMvc
        .perform(
            post("/login")
                .param("username", "alice")
                .param(LinkEntryMarker.PARAM, LinkEntryMarker.VALUE)
                .param("_csrf", "stale-or-missing-token"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error=sessionexpired&entry=link"));

    MvcResult rendered =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login?error=sessionexpired&entry=link"))
            .andExpect(status().isOk())
            .andReturn();
    String html = rendered.getResponse().getContentAsString();
    assertThat(html).doesNotContain("name=\"username\"");
    assertThat(html).doesNotContain("id=\"api-key-modal\"");
    assertThat(html).doesNotContain("ABOUT THIS DEMO");
    assertThat(html).doesNotContain("class=\"info-card\"");
    assertThat(html).contains(DemoAuthMessages.SESSION_OR_SLOT_LOST.replace("'", "&#39;"));
  }

  @Test
  void staleCsrfPostLoginWithoutMarker_redirectsBareSessionExpiredC() throws Exception {
    mockMvc
        .perform(post("/login").param("username", "alice").param("_csrf", "stale-or-missing-token"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error=sessionexpired"));

    MvcResult rendered =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/login?error=sessionexpired"))
            .andExpect(status().isOk())
            .andReturn();
    String html = rendered.getResponse().getContentAsString();
    assertThat(html).contains("CONFIGURE API KEY");
    assertThat(html).contains("id=\"api-key-modal\"");
  }

  @Test
  void otherCsrfFailureRemainsForbidden() throws Exception {
    // Apply API key is not in the narrow login CSRF handler scope.
    mockMvc
        .perform(
            post("/api/apply-api-key")
                .contentType("application/json")
                .content("{\"integrationKey\":\"ikey\",\"secretKey\":\"skey\"}")
                .header("X-CSRF-TOKEN", "bogus"))
        .andExpect(status().isForbidden());
  }

  @Test
  void missingCsrfOnPostLoginWithEntryLink_redirectsWithMarker() throws Exception {
    MockHttpSession empty = new MockHttpSession();
    mockMvc
        .perform(
            post("/login")
                .session(empty)
                .param("username", "alice")
                .param(LinkEntryMarker.PARAM, LinkEntryMarker.VALUE))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login?error=sessionexpired&entry=link"));
  }
}
