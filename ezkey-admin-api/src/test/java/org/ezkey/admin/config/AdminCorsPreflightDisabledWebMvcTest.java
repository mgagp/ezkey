/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminCorsPreflightDisabledWebMvcTest
 * Description: Verifies no CORS headers when {@code ezkey.admin.cors.allowed-origins} is unset.
 */

package org.ezkey.admin.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import org.ezkey.admin.controller.PublicInstanceInfoController;
import org.ezkey.instance.service.PublicInstanceInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Slice test: CORS disabled by default (empty allowed origins). */
@WebMvcTest(controllers = PublicInstanceInfoController.class)
@Import({
  SecurityConfig.class,
  AdminCorsConfig.class,
  AdminCorsTestFilterBeans.class,
  TrustedProxyConfig.class
})
class AdminCorsPreflightDisabledWebMvcTest {

  @MockitoBean private PublicInstanceInfoService publicInstanceInfoService;

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("OPTIONS preflight does not emit Access-Control-Allow-Origin when CORS is off")
  void optionsPreflight_noCorsHeaders() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                options("/api/v1/public/instance-info")
                    .header("Origin", "https://pages.dev")
                    .header("Access-Control-Request-Method", "GET"))
            .andReturn();
    assertNull(result.getResponse().getHeader("Access-Control-Allow-Origin"));
  }
}
