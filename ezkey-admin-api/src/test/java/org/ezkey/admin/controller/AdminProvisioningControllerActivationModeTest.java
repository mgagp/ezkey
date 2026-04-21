/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminProvisioningControllerActivationModeTest
 * Description: Focused controller tests for activation-code provisioning responses.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.dto.request.AdminCreateRequestDto;
import org.ezkey.admin.dto.response.AdminProvisioningResponseDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class AdminProvisioningControllerActivationModeTest {

  @Mock private AdminProvisioningService provisioningService;
  @Mock private QrCodeGeneratorService qrCodeGeneratorService;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private AuditLogService auditLogService;
  @Mock private HttpServletRequest httpRequest;

  private AdminProvisioningController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AdminProvisioningController(
            provisioningService, qrCodeGeneratorService, qrCodePayloadService, auditLogService);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
  }

  @Test
  @DisplayName("createGlobalAdmin returns activation code metadata in activation mode")
  void createGlobalAdminReturnsActivationCodeMetadata() {
    EzkeyAdmin created = new EzkeyAdmin("pending.global", AdminType.GLOBAL_ADMIN);
    created.setAdminId(77);
    created.setEmail("pending@example.com");
    created.setFirstName("Pending");
    created.setLastName("Global");
    created.setActive(true);
    created.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);
    created.setCreatedAt(OffsetDateTime.parse("2026-04-20T10:15:30Z"));

    AdminProvisioningService.ProvisioningResult result =
        new AdminProvisioningService.ProvisioningResult(
            created,
            null,
            null,
            null,
            null,
            AdminOnboardingMode.ACTIVATION_CODE,
            "ezkey_activation_demo",
            OffsetDateTime.parse("2026-04-27T10:15:30Z"));

    AdminPrincipal principal = new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null);

    when(provisioningService.createGlobalAdmin(
            "pending.global",
            "pending@example.com",
            null,
            "Pending",
            "Global",
            AdminOnboardingMode.ACTIVATION_CODE,
            principal))
        .thenReturn(result);

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_GLOBAL_ADMIN")));

    ResponseEntity<?> response =
        controller.createGlobalAdmin(
            new AdminCreateRequestDto(
                "pending.global",
                "pending@example.com",
                null,
                "Pending",
                "Global",
                null,
                AdminOnboardingMode.ACTIVATION_CODE),
            authentication,
            httpRequest);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    AdminProvisioningResponseDto body =
        assertInstanceOf(AdminProvisioningResponseDto.class, response.getBody());
    assertEquals("PENDING_ACTIVATION", body.lifecycleStatus());
    assertEquals("ACTIVATION_CODE", body.onboardingMode());
    assertEquals("ezkey_activation_demo", body.activationCode());
    assertEquals(null, body.enrollmentId());
    assertEquals(null, body.recoveryCodes());
    verify(auditLogService).log(any());
  }
}
