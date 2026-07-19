/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminEnrollmentControllerSecurityWebMvcTest
 * Description: Regression for missing Authorization header on enrollment reset endpoint.
 */

package org.ezkey.admin.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.admin.security.AdminOperationsRateLimitService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.audit.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression coverage for {@code POST /api/v1/admin/enrollments/reset} authentication contract.
 *
 * @since 2026
 */
@WebMvcTest(controllers = AdminEnrollmentController.class)
@Import({SecurityConfig.class, AdminCorsTestFilterBeans.class, TrustedProxyConfig.class})
@DisplayName("AdminEnrollmentController reset security contract")
class AdminEnrollmentControllerSecurityWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AdminRecoveryService recoveryService;
  @MockitoBean private AdminOperationsRateLimitService adminOpsRateLimitService;
  @MockitoBean private AuditLogService auditLogService;

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
}
