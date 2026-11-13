/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: DemoAcmeSecurityHeadersTest
 * Description: Referrer-Policy no-referrer and Cache-Control no-store on login and /t/{code}.
 */

package org.ezkey.demo.acme.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.demo.acme.DemoAcmeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
class DemoAcmeSecurityHeadersTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ApplicationContext applicationContext;

  @Test
  void shouldNotRegisterDefaultUserDetailsService() {
    assertThat(applicationContext.getBeanNamesForType(UserDetailsService.class)).isEmpty();
  }

  @Test
  void loginShouldSendNoReferrerAndNoStore() throws Exception {
    mockMvc
        .perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Cache-Control", containsString("no-store")));
  }

  @Test
  void knownAccessCodeShouldSeeOtherWithNoReferrerAndNoStore() throws Exception {
    mockMvc
        .perform(get("/t/0123456789abcdef0123456789abcdef"))
        .andExpect(status().isSeeOther())
        .andExpect(header().string("Location", containsString("/login")))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Cache-Control", containsString("no-store")));
  }

  @Test
  void unknownAccessCodeShouldRenderLoginWithNoReferrerAndNoStore() throws Exception {
    mockMvc
        .perform(get("/t/ffffffffffffffffffffffffffffffff"))
        .andExpect(status().isOk())
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Cache-Control", containsString("no-store")));
  }
}
