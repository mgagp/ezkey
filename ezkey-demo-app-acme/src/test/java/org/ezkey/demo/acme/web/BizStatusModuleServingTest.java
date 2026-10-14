/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: BizStatusModuleServingTest
 * Description: Proves /js/biz-status.mjs is served as JavaScript and dashboard wires window.* handlers.
 */

package org.ezkey.demo.acme.web;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.demo.acme.DemoAcmeApplication;
import org.ezkey.demo.acme.dto.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Security-review m2 proof for PR #804: ES module static serving + onclick window bindings.
 *
 * @author Ezkey contributors
 * @since 2026
 */
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
class BizStatusModuleServingTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void bizStatusModuleShouldBeServedAsJavaScriptMime() throws Exception {
    mockMvc
        .perform(get("/js/biz-status.mjs"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Content-Type",
                    anyOf(
                        containsString("text/javascript"),
                        containsString("application/javascript"))))
        .andExpect(content().string(containsString("showRowStatusOn")))
        .andExpect(content().string(containsString("export function")));
  }

  @Test
  void dashboardShouldWireModuleImportAndWindowApprovalHandlers() throws Exception {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("user", new AuthenticatedUser("alice", "Alice Demo", 1));

    mockMvc
        .perform(get("/dashboard").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("type=\"module\"")))
        .andExpect(content().string(containsString("/js/biz-status.mjs")))
        .andExpect(content().string(containsString("window.requestApproval = requestApproval")))
        .andExpect(
            content().string(containsString("window.checkApprovalStatus = checkApprovalStatus")))
        .andExpect(content().string(containsString("onclick=\"requestApproval('PAYMENT')\"")))
        .andExpect(content().string(containsString("onclick=\"checkApprovalStatus('PAYMENT')\"")));
  }
}
