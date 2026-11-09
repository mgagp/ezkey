/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationPendingAttemptPolicyWebMvcTest
 * Description: System integration pending-attempt policy changes return HTTP 403.
 */

package org.ezkey.admin.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.exception.DomainExceptionHandler;
import org.ezkey.integration.domain.PendingAttemptPolicy;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.exception.SystemIntegrationLifecycleException;
import org.ezkey.integration.mapper.IntegrationControllerMapper;
import org.ezkey.integration.service.IntegrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP contract for changing an integration pending-attempt policy.
 *
 * @since 2026
 */
@WebMvcTest(controllers = IntegrationController.class)
@Import({
  SecurityConfig.class,
  AdminCorsTestFilterBeans.class,
  TrustedProxyConfig.class,
  DomainExceptionHandler.class
})
@DisplayName("Integration pending-attempt policy")
class IntegrationPendingAttemptPolicyWebMvcTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private IntegrationService integrationService;
  @MockitoBean private IntegrationControllerMapper integrationControllerMapper;
  @MockitoBean private EzkeyAdminRepository adminRepository;
  @MockitoBean private AccessControlService accessControlService;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private EnrollmentRevocationService enrollmentRevocationService;

  @Test
  @DisplayName("changing the system integration policy returns 403 system-integration-lifecycle")
  void systemIntegrationChangeIsForbidden() throws Exception {
    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setAdminId(1);
    admin.setUsername("global.admin");
    admin.setAdminType(AdminType.GLOBAL_ADMIN);
    when(adminRepository.findByUsername("global.admin")).thenReturn(Optional.of(admin));

    Tenant tenant = new Tenant("Ezkey System", "system");
    tenant.setTenantId(1);
    Integration integration = new Integration();
    integration.setId(3);
    integration.setIsSystemIntegration(true);
    integration.setTenant(tenant);
    when(integrationService.getById(3)).thenReturn(Optional.of(integration));
    when(integrationService.updatePendingAttemptPolicy(3, PendingAttemptPolicy.REFUSE))
        .thenThrow(
            new SystemIntegrationLifecycleException(
                "System integration pending-attempt policy cannot be changed because it is"
                    + " reserved for administrator MFA."));

    mockMvc
        .perform(
            post("/api/v1/integrations/3/pending-attempt-policy")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pendingAttemptPolicy\":\"REFUSE\"}")
                .with(user("global.admin").roles("ADMIN")))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.type")
                .value("https://ezkey.io/problems/domain/system-integration-lifecycle"));
  }
}
