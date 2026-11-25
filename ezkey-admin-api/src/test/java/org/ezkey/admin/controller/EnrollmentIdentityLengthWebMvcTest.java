/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentIdentityLengthWebMvcTest
 * Description: Too-long enrollment Name or User ID returns a validation 400 before any write.
 */

package org.ezkey.admin.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.ezkey.admin.config.AdminCorsTestFilterBeans;
import org.ezkey.admin.config.SecurityConfig;
import org.ezkey.admin.config.TrustedProxyConfig;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.admin.service.EnrollmentUpdateService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.audit.support.AuditEntityFkResolver;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.enrollment.mapper.EnrollmentAdminMapper;
import org.ezkey.enrollment.service.EnrollmentService;
import org.ezkey.exception.ValidationExceptionHandler;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * HTTP contract for enrollment Name and User ID lengths (#1457): a value wider than its column is a
 * {@code validation-failed} 400 that names the field, on create and on update, and nothing reaches
 * the service or the database.
 *
 * @since 2026
 */
@WebMvcTest(controllers = EnrollmentController.class)
@Import({
  SecurityConfig.class,
  AdminCorsTestFilterBeans.class,
  TrustedProxyConfig.class,
  ValidationExceptionHandler.class
})
@DisplayName("Enrollment Name and User ID length contract (#1457)")
class EnrollmentIdentityLengthWebMvcTest {

  private static final String VALIDATION_FAILED =
      "https://ezkey.io/problems/admin/validation-failed";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private EnrollmentService enrollmentService;
  @MockitoBean private EnrollmentAdminMapper enrollmentMapper;
  @MockitoBean private AuditLogService auditLogService;
  @MockitoBean private QrCodeGeneratorService qrCodeGeneratorService;
  @MockitoBean private QrCodePayloadService qrCodePayloadService;
  @MockitoBean private AccessControlService accessControlService;
  @MockitoBean private EnrollmentRepository enrollmentRepository;
  @MockitoBean private IntegrationRepository integrationRepository;
  @MockitoBean private EzkeyAdminRepository adminRepository;
  @MockitoBean private EnrollmentRevocationService enrollmentRevocationService;
  @MockitoBean private EnrollmentUpdateService enrollmentUpdateService;
  @MockitoBean private AuditEntityFkResolver auditEntityFkResolver;

  private static RequestPostProcessor tenantAdmin() {
    return authentication(
        new UsernamePasswordAuthenticationToken(
            new AdminPrincipal(1, AdminType.TENANT_ADMIN, 1, null),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  @Test
  @DisplayName("create: a 65-character Name returns 400 validation-failed on name")
  void createWithNameOver64IsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"integrationId\":1,\"name\":\"" + "n".repeat(65) + "\"}")
                .with(tenantAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_FAILED))
        .andExpect(jsonPath("$.detail").value("name: Enrollment name must be 1 to 64 characters"));
    verifyNoInteractions(enrollmentService);
  }

  @Test
  @DisplayName("create: a 256-character User ID returns 400 validation-failed on userIdentifier")
  void createWithUserIdentifierOver255IsRejected() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/enrollments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"integrationId\":1,\"name\":\"Alice\",\"userIdentifier\":\""
                        + "u".repeat(256)
                        + "\"}")
                .with(tenantAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_FAILED))
        .andExpect(
            jsonPath("$.detail")
                .value("userIdentifier: User identifier must not exceed 255 characters"));
    verifyNoInteractions(enrollmentService);
  }

  @Test
  @DisplayName("update: a 65-character Name returns 400 validation-failed on enrollmentName")
  void updateWithNameOver64IsRejected() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/enrollments/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enrollmentName\":\"" + "n".repeat(65) + "\"}")
                .with(tenantAdmin()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value(VALIDATION_FAILED))
        .andExpect(
            jsonPath("$.detail")
                .value("enrollmentName: Enrollment name must be 1 to 64 characters"));
    verifyNoInteractions(enrollmentUpdateService);
  }
}
