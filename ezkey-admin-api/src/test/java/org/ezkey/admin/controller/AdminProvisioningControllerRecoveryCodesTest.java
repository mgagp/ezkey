/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningControllerRecoveryCodesTest
 * Description: Unit tests for administrator recovery-code regeneration endpoint.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminProvisioningService.RecoveryCodesRegenerationResult;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminProvisioningController recovery-code regeneration tests")
class AdminProvisioningControllerRecoveryCodesTest {

  @Mock private AdminProvisioningService provisioningService;
  @Mock private QrCodeGeneratorService qrCodeGeneratorService;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private AuditLogService auditLogService;
  @Mock private HttpServletRequest httpRequest;
  @Mock private Authentication authentication;

  private AdminProvisioningController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AdminProvisioningController(
            provisioningService, qrCodeGeneratorService, qrCodePayloadService, auditLogService);

    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");
  }

  @Test
  @DisplayName("regenerateRecoveryCodes returns new codes and writes success audit")
  void regenerateRecoveryCodesReturnsNewCodesAndWritesSuccessAudit() {
    AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
    when(authentication.getPrincipal()).thenReturn(principal);

    Tenant tenant = new Tenant();
    tenant.setTenantId(7);

    EzkeyAdmin admin = new EzkeyAdmin("tenant.admin", AdminType.TENANT_ADMIN);
    admin.setAdminId(42);
    admin.setTenant(tenant);
    admin.setActive(true);

    RecoveryCodesRegenerationResult result =
        new RecoveryCodesRegenerationResult(
            admin, List.of("1111-2222-3333-4444-5555-6666-7777-8888"), 2);
    when(provisioningService.regenerateRecoveryCodes(42, principal)).thenReturn(result);

    ResponseEntity<?> response =
        controller.regenerateRecoveryCodes(42, authentication, httpRequest);

    assertEquals(HttpStatus.OK, response.getStatusCode());

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());
    AuditLog logged = captor.getValue();
    assertEquals(EventType.ADMIN_RECOVERY_CODES_REGENERATED, logged.getEventType());
    assertEquals(AdminAuditConstants.RECOVERY_CODES_REGENERATED, logged.getEventAction());
    assertEquals(EventStatus.SUCCESS, logged.getEventStatus());
    assertEquals(1, logged.getAdminId());
    assertEquals(42, logged.getTargetAdminId());
  }

  @Test
  @DisplayName("regenerateRecoveryCodes returns 400 for inactive target administrator")
  void regenerateRecoveryCodesReturnsBadRequestForInactiveTarget() {
    AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
    when(authentication.getPrincipal()).thenReturn(principal);
    when(provisioningService.regenerateRecoveryCodes(42, principal))
        .thenThrow(
            new IllegalStateException(
                "Cannot regenerate recovery codes for an inactive administrator"));

    ResponseEntity<?> response =
        controller.regenerateRecoveryCodes(42, authentication, httpRequest);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());
    AuditLog logged = captor.getValue();
    assertEquals(EventType.ADMIN_RECOVERY_CODES_REGENERATED, logged.getEventType());
    assertEquals(AdminAuditConstants.RECOVERY_CODES_REGENERATION_FAILED, logged.getEventAction());
    assertEquals(EventStatus.FAILURE, logged.getEventStatus());
    assertTrue(logged.getErrorMessage().contains("inactive"));
  }
}
