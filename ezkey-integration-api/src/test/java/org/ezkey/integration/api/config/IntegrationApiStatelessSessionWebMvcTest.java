/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationApiStatelessSessionWebMvcTest
 * Description: Anonymous 401 must not emit a servlet session Set-Cookie (#816).
 */

package org.ezkey.integration.api.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ezkey.audit.integrity.AuditChainHeartbeatGuardService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.authattempt.mapper.AuthAttemptIntegrationApiMapper;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.api.controller.IntegrationApiAuthAttemptController;
import org.ezkey.integration.api.security.RateLimitService;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Verifies Integration API servlet session policy: anonymous 401 has no {@code Set-Cookie} (#816).
 *
 * @since 2026
 */
@WebMvcTest(controllers = IntegrationApiAuthAttemptController.class)
@Import({IntegrationApiSecurityConfig.class, IntegrationApiTestFilterBeans.class})
@DisplayName("Integration API stateless servlet sessions (#816)")
class IntegrationApiStatelessSessionWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AuthAttemptService authAttemptService;
  @MockitoBean private AuthAttemptIntegrationApiMapper authAttemptMapper;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private RateLimitService rateLimitService;
  @MockitoBean private EnrollmentRepository enrollmentRepository;
  @MockitoBean private IntegrationRepository integrationRepository;
  @MockitoBean private AuditEntityFkResolver auditEntityFkResolver;
  @MockitoBean private AuditChainHeartbeatGuardService auditChainHeartbeatGuardService;

  @Test
  @DisplayName("Anonymous GET auth-attempts returns 401 without Set-Cookie or servlet session")
  void anonymousAuthAttemptsGet_noSetCookieAndNoSession() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/auth-attempts/1"))
            .andExpect(status().isUnauthorized())
            .andReturn();

    assertNull(result.getResponse().getHeader("Set-Cookie"));
    assertTrue(result.getRequest().getSession(false) == null);
  }
}
