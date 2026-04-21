/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: IntegrationControllerCreateAuditTest
 * Description: Focused audit classification tests for IntegrationController#create.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.ezkey.admin.security.AccessControlService;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.EnrollmentRevocationService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.exception.SystemTenantNotConfiguredException;
import org.ezkey.integration.domain.IntegrationCreateRequest;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.dto.IntegrationCreateRequestDto;
import org.ezkey.integration.exception.IntegrationCodeAlreadyExistsException;
import org.ezkey.integration.mapper.IntegrationControllerMapper;
import org.ezkey.integration.service.IntegrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntegrationController create audit")
class IntegrationControllerCreateAuditTest {

  @Mock private IntegrationService integrationService;
  @Mock private IntegrationControllerMapper integrationControllerMapper;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AccessControlService accessControlService;
  @Mock private AuditLogService auditLogService;
  @Mock private EnrollmentRevocationService enrollmentRevocationService;

  private IntegrationController controller;

  @BeforeEach
  void setUp() {
    controller =
        new IntegrationController(
            integrationService,
            integrationControllerMapper,
            adminRepository,
            accessControlService,
            auditLogService,
            enrollmentRevocationService);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("create() audits duplicate code as business failure with request details")
  void create_AuditsBusinessFailure_WhenIntegrationCodeAlreadyExists() {
    EzkeyAdmin tenantAdmin = tenantAdmin();
    authenticateAs(tenantAdmin);

    IntegrationCreateRequestDto requestDto =
        new IntegrationCreateRequestDto("portal", "Portal", "Backoffice portal");
    when(adminRepository.findById(tenantAdmin.getAdminId())).thenReturn(Optional.of(tenantAdmin));
    when(integrationControllerMapper.toCreateRequest(requestDto))
        .thenReturn(new IntegrationCreateRequest());
    when(integrationService.createIntegration(
            any(IntegrationCreateRequest.class), any(EzkeyAdmin.class)))
        .thenThrow(new IntegrationCodeAlreadyExistsException("portal", "Acme"));

    HttpServletRequest httpRequest = new MockHttpServletRequest();

    assertThrows(
        IntegrationCodeAlreadyExistsException.class,
        () -> controller.create(requestDto, httpRequest));

    verify(auditLogService)
        .log(
            argThat(
                (AuditLog auditLog) ->
                    auditLog.getEventStatus() == EventStatus.FAILURE
                        && "integration_creation_failed".equals(auditLog.getEventAction())
                        && Integer.valueOf(tenantAdmin.getAdminId()).equals(auditLog.getAdminId())
                        && Integer.valueOf(42).equals(auditLog.getTenantId())
                        && auditLog.getErrorMessage().contains("already exists")
                        && auditLog
                            .getEventDetails()
                            .contains("\"requested_integration_code\":\"portal\"")
                        && auditLog
                            .getEventDetails()
                            .contains("\"requested_integration_name\":\"Portal\"")
                        && auditLog
                            .getEventDetails()
                            .contains("\"conflicting_tenant_name\":\"Acme\"")));
  }

  @Test
  @DisplayName("create() audits missing system tenant as technical error with system scope details")
  void create_AuditsTechnicalError_WhenSystemTenantIsMissing() {
    EzkeyAdmin globalAdmin = globalAdmin();
    authenticateAs(globalAdmin);

    IntegrationCreateRequestDto requestDto =
        new IntegrationCreateRequestDto("system-app", "System App", null);
    when(adminRepository.findById(globalAdmin.getAdminId())).thenReturn(Optional.of(globalAdmin));
    when(integrationControllerMapper.toCreateRequest(requestDto))
        .thenReturn(new IntegrationCreateRequest());
    when(integrationService.createIntegration(
            any(IntegrationCreateRequest.class), any(EzkeyAdmin.class)))
        .thenThrow(
            new SystemTenantNotConfiguredException(
                "System tenant not found. Database may not be properly initialized."));

    HttpServletRequest httpRequest = new MockHttpServletRequest();

    assertThrows(
        SystemTenantNotConfiguredException.class, () -> controller.create(requestDto, httpRequest));

    verify(auditLogService)
        .log(
            argThat(
                (AuditLog auditLog) ->
                    auditLog.getEventStatus() == EventStatus.ERROR
                        && "integration_creation_error".equals(auditLog.getEventAction())
                        && Integer.valueOf(globalAdmin.getAdminId()).equals(auditLog.getAdminId())
                        && auditLog.getTenantId() == null
                        && auditLog
                            .getErrorMessage()
                            .contains(
                                "System tenant not found. Database may not be properly"
                                    + " initialized.")
                        && auditLog
                            .getEventDetails()
                            .contains("\"requested_scope\":\"system_tenant\"")
                        && auditLog
                            .getEventDetails()
                            .contains("\"error_type\":\"SystemTenantNotConfiguredException\"")));
  }

  private EzkeyAdmin tenantAdmin() {
    Tenant tenant = new Tenant("Acme", "acme");
    tenant.setTenantId(42);
    tenant.setActive(true);

    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setAdminId(7);
    admin.setUsername("tenant.admin");
    admin.setAdminType(AdminType.TENANT_ADMIN);
    admin.setTenant(tenant);
    return admin;
  }

  private EzkeyAdmin globalAdmin() {
    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setAdminId(1);
    admin.setUsername("global.admin");
    admin.setAdminType(AdminType.GLOBAL_ADMIN);
    return admin;
  }

  private void authenticateAs(EzkeyAdmin admin) {
    Integer tenantId = admin.getTenant() != null ? admin.getTenant().getTenantId() : null;
    AdminPrincipal principal =
        new AdminPrincipal(admin.getAdminId(), admin.getAdminType(), tenantId, null);
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
