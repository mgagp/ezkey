/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminCorsPreflightEnabledWebMvcTest
 * Description: Verifies CORS preflight when {@code ezkey.admin.cors.allowed-origins} is configured.
 */

package org.ezkey.admin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import org.ezkey.admin.controller.PublicInstanceInfoController;
import org.ezkey.instance.service.PublicInstanceInfoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Slice test: CORS enabled for a configured browser origin. */
@WebMvcTest(controllers = PublicInstanceInfoController.class)
@Import({
  SecurityConfig.class,
  AdminCorsConfig.class,
  AdminCorsTestFilterBeans.class,
  TrustedProxyConfig.class
})
@TestPropertySource(
    properties = {
      "ezkey.admin.cors.allowed-origins=https://ui.example",
    })
class AdminCorsPreflightEnabledWebMvcTest {

  @MockitoBean private PublicInstanceInfoService publicInstanceInfoService;

  @MockitoBean private EvaluatorSelfRegistrationProperties evaluatorSelfRegistrationProperties;

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("OPTIONS preflight returns Access-Control-Allow-Origin for allowed browser origin")
  void optionsPreflight_reflectsOrigin() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                options("/api/v1/public/instance-info")
                    .header("Origin", "https://ui.example")
                    .header("Access-Control-Request-Method", "GET"))
            .andReturn();
    assertEquals(
        "https://ui.example", result.getResponse().getHeader("Access-Control-Allow-Origin"));
    assertNotNull(result.getResponse().getHeader("Access-Control-Allow-Methods"));
  }
}
