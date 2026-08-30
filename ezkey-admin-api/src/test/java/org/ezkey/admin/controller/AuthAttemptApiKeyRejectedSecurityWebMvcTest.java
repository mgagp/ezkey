/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptApiKeyRejectedSecurityWebMvcTest
 * Description: Admin API does not authenticate HTTP Basic API-key credentials (401).
 */

package org.ezkey.admin.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.mapper.AuthAttemptAdminApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Filter-chain contract: HTTP Basic API-key credentials do not authenticate on Admin API.
 *
 * <p>Uses a raw {@code Authorization: Basic} header (not {@code httpBasic()} or a mocked {@code
 * ROLE_API_KEY} user). Those post-processors would inject an authenticated principal and produce
 * 403 instead of the live 401 contract.
 *
 * @since 2026
 */
@WebMvcTest(controllers = AuthAttemptController.class)
@Import({SecurityConfig.class, AdminCorsTestFilterBeans.class, TrustedProxyConfig.class})
@DisplayName("AuthAttemptController API-key credentials are unauthenticated")
class AuthAttemptApiKeyRejectedSecurityWebMvcTest {

  private static final String CREATE_PATH = "/api/v1/auth-attempts";
  private static final String CREATE_BODY = "{\"enrollmentId\":1,\"challengeRequested\":false}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthAttemptService authAttemptService;
  @MockitoBean private AuthAttemptAdminApiMapper authAttemptMapper;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private EnrollmentRepository enrollmentRepository;
  @MockitoBean private AccessControlService accessControlService;
  @MockitoBean private IntegrationRepository integrationRepository;
  @MockitoBean private AuditEntityFkResolver auditEntityFkResolver;

  @Test
  @DisplayName("HTTP Basic API-key credentials on create return 401")
  void apiKeyBasicCreateUnauthorized() throws Exception {
    mockMvc
        .perform(
            post(CREATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY)
                .header("Authorization", basic("ezkey_ikey_test", "ezkey_skey_test")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Admin role reaches create after the security layer")
  void adminCreateIsAuthenticated() throws Exception {
    mockMvc
        .perform(
            post(CREATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(CREATE_BODY)
                .with(user("admin").roles("ADMIN")))
        .andExpect(status().isForbidden());
  }

  private static String basic(String username, String password) {
    String raw = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
