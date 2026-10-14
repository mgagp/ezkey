/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminPasswordlessCancelThenRetryMockMvcTest
 * Description: MockMvc proof that Cancel then immediate login retry is not HTTP 429 (#831).
 */

package org.ezkey.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.config.AdminRateLimitProperties;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.config.TrustedProxyProperties;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.admin.service.EvaluatorTempSessionService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc integration: login → passwordless-wait → passwordless-cancel → login again must not hit
 * HTTP 429 when login and wait use separate buckets (#831).
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Admin passwordless Cancel then retry (#831)")
class AdminPasswordlessCancelThenRetryMockMvcTest {

  @Mock private AdminAuthService authService;
  @Mock private AdminProvisioningService provisioningService;
  @Mock private AdminRecoveryService recoveryService;
  @Mock private AuditLogService auditLogService;
  @Mock private AdminRecoveryProperties recoveryProperties;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminBrowserSessionCookieProperties browserSessionCookieProperties;
  @Mock private AdminSessionCookieService sessionCookieService;
  @Mock private AdminCsrfTokenService csrfTokenService;
  @Mock private EvaluatorTempSessionService evaluatorTempSessionService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AdminRateLimitProperties properties = new AdminRateLimitProperties();
    properties.setEnabled(true);
    properties.getLogin().setRequests(2);
    properties.getLogin().setWindowMinutes(1);
    properties.getPasswordlessWait().setRequests(2);
    properties.getPasswordlessWait().setWindowMinutes(1);
    properties.getBackstop().setEnabled(false);

    AdminRateLimitFilter rateLimitFilter =
        new AdminRateLimitFilter(
            properties, new TrustedProxyProperties(), new SimpleMeterRegistry());

    AdminAuthController controller =
        new AdminAuthController(
            authService,
            provisioningService,
            recoveryService,
            auditLogService,
            rateLimitFilter,
            recoveryProperties,
            adminRepository,
            browserSessionCookieProperties,
            sessionCookieService,
            csrfTokenService,
            evaluatorTempSessionService);

    when(browserSessionCookieProperties.isBrowserSessionCookieEnabled()).thenReturn(false);
    when(adminRepository.findByUsername(anyString())).thenReturn(java.util.Optional.empty());

    AdminLoginResponseDto pending =
        AdminLoginResponseDto.pendingPasswordless(
            55,
            12,
            "admin.community",
            "GLOBAL_ADMIN",
            "waiting",
            OffsetDateTime.now().plusMinutes(2),
            "waiter-secret-xyz");
    when(authService.authenticate(any(AdminLoginRequestDto.class))).thenReturn(pending);
    doNothing().when(authService).cancelPasswordlessAuth(eq(55), anyString());
    when(authService.waitForPasswordlessAuth(anyInt(), any(), anyString()))
        .thenReturn(pending);

    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .addFilters(rateLimitFilter)
            .build();
  }

  @Test
  @DisplayName("Cancel then immediate login retry is not rate-limited (separate buckets)")
  void cancelThenRetryLogin_notRateLimited() throws Exception {
    String loginBody =
        "{\"username\":\"admin.community\",\"challengeRequested\":true,\"nonBlocking\":true}";

    mockMvc
        .perform(
            post("/api/v1/admin/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody)
                .with(request -> {
                  request.setRemoteAddr("203.0.113.40");
                  return request;
                }))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/admin/auth/passwordless-wait")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"authAttemptId\":55,\"challengeCode\":12,\"waiterSecret\":\"waiter-secret-xyz\"}")
                .with(request -> {
                  request.setRemoteAddr("203.0.113.40");
                  return request;
                }))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/admin/auth/passwordless-cancel")
                .contentType(APPLICATION_JSON)
                .content("{\"authAttemptId\":55,\"waiterSecret\":\"waiter-secret-xyz\"}")
                .with(request -> {
                  request.setRemoteAddr("203.0.113.40");
                  return request;
                }))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/admin/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody)
                .with(request -> {
                  request.setRemoteAddr("203.0.113.40");
                  return request;
                }))
        .andExpect(status().isOk());
  }
}
