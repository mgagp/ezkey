/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptPendingCountSecurityWebMvcTest
 * Description: SEC-025 — pending-count is Admin-only (API-key Basic → 401).
 */

package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.mapper.AuthAttemptAdminApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SEC-025 regression: {@code GET /api/v1/auth-attempts/pending-count} requires {@code ROLE_ADMIN}.
 *
 * <p>This endpoint is operator dashboard telemetry. HTTP Basic API-key credentials do not
 * authenticate on Admin API (401), so they cannot observe instance-wide pending MFA volume.
 *
 * @since 2026
 */
@WebMvcTest(controllers = AuthAttemptController.class)
@Import({SecurityConfig.class, AdminCorsTestFilterBeans.class, TrustedProxyConfig.class})
@DisplayName("SEC-025 AuthAttemptController pending-count authorization")
class AuthAttemptPendingCountSecurityWebMvcTest {

  private static final String PENDING_COUNT_PATH = "/api/v1/auth-attempts/pending-count";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthAttemptService authAttemptService;
  @MockitoBean private AuthAttemptAdminApiMapper authAttemptMapper;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private EnrollmentRepository enrollmentRepository;
  @MockitoBean private AccessControlService accessControlService;
  @MockitoBean private IntegrationRepository integrationRepository;
  @MockitoBean private AuditEntityFkResolver auditEntityFkResolver;

  @Test
  @DisplayName("HTTP Basic API-key credentials receive 401 on pending-count")
  void apiKeyBasicUnauthorizedOnPendingCount() throws Exception {
    String basic =
        "Basic "
            + java.util.Base64.getEncoder()
                .encodeToString("ezkey_ikey_test:ezkey_skey_test".getBytes());
    mockMvc
        .perform(get(PENDING_COUNT_PATH).header("Authorization", basic))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {"ADMIN", "TENANT_ADMIN"})
  @DisplayName("Tenant Admin can read pending-count (200)")
  void tenantAdminCanReadPendingCount() throws Exception {
    stubPendingCount(3L);

    mockMvc
        .perform(get(PENDING_COUNT_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(3));
  }

  @Test
  @WithMockUser(roles = {"ADMIN", "GLOBAL_ADMIN"})
  @DisplayName("Global Admin can read pending-count (200)")
  void globalAdminCanReadPendingCount() throws Exception {
    stubPendingCount(7L);

    mockMvc
        .perform(get(PENDING_COUNT_PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(7));
  }

  private void stubPendingCount(long totalElements) {
    when(authAttemptService.findByFilters(
            eq(AuthAttemptStatus.PENDING),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.<AuthAttempt>of(), Pageable.ofSize(1), totalElements));
  }
}
