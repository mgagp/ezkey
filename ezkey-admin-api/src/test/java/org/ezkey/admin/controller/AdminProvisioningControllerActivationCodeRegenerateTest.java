/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningControllerActivationCodeRegenerateTest
 * Description: Unit tests for activation-code regeneration (Global Admin recovery path).
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.response.AdminActivationCodeReissueResponseDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminProvisioningService.ActivationCodeReissueResult;
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
@DisplayName("AdminProvisioningController activation-code regeneration tests")
class AdminProvisioningControllerActivationCodeRegenerateTest {

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
  @DisplayName("regenerateActivationCode returns activation payload and writes success audit")
  void regenerateActivationCodeReturnsPayloadAndWritesSuccessAudit() {
    AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
    when(authentication.getPrincipal()).thenReturn(principal);

    Tenant tenant = new Tenant();
    tenant.setTenantId(7);

    EzkeyAdmin admin = new EzkeyAdmin("tenant.admin", AdminType.TENANT_ADMIN);
    admin.setAdminId(42);
    admin.setTenant(tenant);

    String codePrefix = AdminAuditConstants.ACTIVATION_TOKEN_PREFIX;
    String plainCode = codePrefix + UUID.randomUUID().toString().replace("-", "");
    OffsetDateTime expiry = OffsetDateTime.parse("2030-01-01T00:00:00Z");

    ActivationCodeReissueResult result =
        new ActivationCodeReissueResult(admin, plainCode, expiry, true, 1);
    when(provisioningService.reissueActivationCode(42, principal)).thenReturn(result);

    ResponseEntity<?> response =
        controller.regenerateActivationCode(42, authentication, httpRequest);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody() instanceof AdminActivationCodeReissueResponseDto);
    AdminActivationCodeReissueResponseDto body =
        (AdminActivationCodeReissueResponseDto) response.getBody();
    assertEquals(42, body.adminId());
    assertEquals(plainCode, body.activationCode());
    assertEquals(expiry, body.activationCodeExpiresAt());
    assertTrue(body.invalidatedPreviousTokens());

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());
    AuditLog logged = captor.getValue();
    assertEquals(EventType.ADMIN_ACTIVATION_CODE_REISSUED, logged.getEventType());
    assertEquals(AdminAuditConstants.ACTIVATION_CODE_REISSUED, logged.getEventAction());
    assertEquals(EventStatus.SUCCESS, logged.getEventStatus());
    assertEquals(1, logged.getAdminId());
    assertEquals(42, logged.getTargetAdminId());
  }

  @Test
  @DisplayName("regenerateActivationCode returns 400 when provisioning rejects eligibility")
  void regenerateActivationCodeReturnsBadRequestForIneligibleAdministrator() {
    AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);
    when(authentication.getPrincipal()).thenReturn(principal);
    when(provisioningService.reissueActivationCode(42, principal))
        .thenThrow(
            new IllegalStateException(
                "Cannot re-issue activation codes for an inactive administrator"));

    ResponseEntity<?> response =
        controller.regenerateActivationCode(42, authentication, httpRequest);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService, times(1)).log(captor.capture());
    AuditLog logged = captor.getValue();
    assertEquals(EventType.ADMIN_ACTIVATION_CODE_REISSUED, logged.getEventType());
    assertEquals(AdminAuditConstants.ACTIVATION_CODE_REISSUE_FAILED, logged.getEventAction());
    assertEquals(EventStatus.FAILURE, logged.getEventStatus());
    assertTrue(logged.getErrorMessage().contains("inactive"));
  }
}
