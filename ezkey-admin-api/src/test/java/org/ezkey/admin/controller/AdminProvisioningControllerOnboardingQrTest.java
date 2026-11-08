/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminProvisioningService.OnboardingCredentialsResult;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The admin onboarding QR follows the proof token rule of the enrollment QR (#1266). */
@ExtendWith(MockitoExtension.class)
class AdminProvisioningControllerOnboardingQrTest {

  private static final Integer ADMIN_ID = 2;
  private static final Integer ENROLLMENT_ID = 40;

  @Mock private AdminProvisioningService provisioningService;
  @Mock private QrCodeGeneratorService qrCodeGeneratorService;
  @Mock private QrCodePayloadService qrCodePayloadService;
  @Mock private AuditLogService auditLogService;
  @Mock private HttpServletRequest httpRequest;

  private AdminProvisioningController controller;
  private Authentication authentication;

  @BeforeEach
  void setUp() {
    controller =
        new AdminProvisioningController(
            provisioningService, qrCodeGeneratorService, qrCodePayloadService, auditLogService);
    authentication =
        new UsernamePasswordAuthenticationToken(
            new AdminPrincipal(1, AdminType.GLOBAL_ADMIN, null, null),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  @Test
  @DisplayName("QR while CREATED returns a PNG")
  void qrWhileCreated_returnsPng() throws Exception {
    byte[] png = new byte[] {1, 2, 3};
    when(provisioningService.getAdminOnboarding(any(), any()))
        .thenReturn(
            new OnboardingCredentialsResult(
                ENROLLMENT_ID, "proof-token", 654321, null, EnrollmentStatus.CREATED));
    when(qrCodePayloadService.composePayload(ENROLLMENT_ID, "proof-token")).thenReturn("{}");
    when(qrCodeGeneratorService.generateQrCodeImage("{}", 300, 300)).thenReturn(png);

    ResponseEntity<?> response =
        controller.getAdminOnboardingQrCode(ADMIN_ID, authentication, httpRequest);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertArrayEquals(png, (byte[]) response.getBody());
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(value = EnrollmentStatus.class, names = "CREATED", mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("QR outside CREATED returns 409 and never composes the payload")
  void qrOutsideCreated_returnsConflictWithoutComposingPayload(EnrollmentStatus status)
      throws Exception {
    // The token is set on purpose: the status decides, even if a caller left the value in place.
    when(provisioningService.getAdminOnboarding(any(), any()))
        .thenReturn(
            new OnboardingCredentialsResult(ENROLLMENT_ID, "proof-token", 654321, null, status));

    ResponseEntity<?> response =
        controller.getAdminOnboardingQrCode(ADMIN_ID, authentication, httpRequest);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    verify(qrCodePayloadService, never()).composePayload(any(), any());
    verify(qrCodeGeneratorService, never()).generateQrCodeImage(any(), anyInt(), anyInt());
  }
}
