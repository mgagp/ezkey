/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminEnrollmentControllerSecurityWebMvcTest
 * Description: Regression for the enrollment reset authentication contract and its public scope.
 */

package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.AdminOperationsRateLimitProperties;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression coverage for {@code POST /api/v1/admin/enrollments/reset} authentication contract.
 *
 * @since 2026
 */
@WebMvcTest(controllers = AdminEnrollmentController.class)
@Import({
  SecurityConfig.class,
  AdminCorsTestFilterBeans.class,
  TrustedProxyConfig.class,
  AdminEnrollmentControllerSecurityWebMvcTest.NonDefaultRateLimit.class
})
@DisplayName("AdminEnrollmentController reset security contract")
class AdminEnrollmentControllerSecurityWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminRecoveryService recoveryService;
  @MockitoBean private AdminOperationsRateLimitService adminOpsRateLimitService;
  @MockitoBean private AuditLogService auditLogService;

  /** Non-default enrollment reset limit: 7 per 2 minutes (defaults are 3 per 30). */
  @TestConfiguration
  static class NonDefaultRateLimit {
    @Bean
    AdminOperationsRateLimitProperties adminOperationsRateLimitProperties() {
      AdminOperationsRateLimitProperties properties = new AdminOperationsRateLimitProperties();
      properties.getEnrollmentReset().setRequests(7);
      properties.getEnrollmentReset().setWindowMinutes(2);
      return properties;
    }
  }

  @Test
  @DisplayName("429 reports the configured reset limit and derives Retry-After from it (#1315)")
  void rateLimitedResetUsesConfiguredLimitAndRetryAfter() throws Exception {
    when(adminOpsRateLimitService.canResetEnrollment(anyString())).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/admin/enrollments/reset")
                .header("Authorization", "Bearer ezkey_recovery_test-token")
                .contentType(APPLICATION_JSON)
                .content("{\"enrollmentId\":1,\"reason\":\"device lost and rotating enrollment\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "120"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Rate limit exceeded for operation 'ENROLLMENT_RESET': 7/7 operations in"
                        + " 2-minute window"));
  }

  @Test
  @DisplayName("Missing Authorization header returns 401 ProblemDetail")
  void missingAuthorizationHeaderReturns401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/enrollments/reset")
                .contentType(APPLICATION_JSON)
                .content("{\"enrollmentId\":1,\"reason\":\"device lost and rotating enrollment\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            jsonPath("$.type")
                .value("https://ezkey.io/problems/authentication/missing-authorization-header"))
        .andExpect(jsonPath("$.title").value("Authentication Required"))
        .andExpect(jsonPath("$.detail").value("Missing required Authorization header"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.path").value("/api/v1/admin/enrollments/reset"));
  }

  @Test
  @DisplayName(
      "Only the reset path is public: other admin enrollment and MFA paths require a session")
  void onlyResetPathIsPublic() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/enrollments/other").contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(post("/api/v1/admin/mfa/attempt").contentType(APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }
}
