/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.dto.response.AdminOnboardingResponseDto;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.AdminProvisioningService.OnboardingCredentialsResult;
import org.ezkey.admin.service.QrCodeGeneratorService;
import org.ezkey.admin.service.QrCodePayloadService;
import org.ezkey.audit.domain.EventStatus;
import org.ezkey.audit.domain.EventType;
import org.ezkey.audit.domain.entity.AuditLog;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Revealing an administrator's bind material through {@code /admins/{id}/onboarding*} writes one
 * audit row per response that carries it, and never the material itself (#1313, test T9).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Admin onboarding bind material reveal audit (#1313)")
class AdminProvisioningControllerBindMaterialAuditTest {

  private static final Integer CALLER_ID = 1;
  private static final Integer TARGET_ADMIN_ID = 2;
  private static final Integer ENROLLMENT_ID = 40;
  private static final Integer TENANT_ID = 9;
  private static final String TOKEN = "proof-token-secret";
  private static final Integer CHALLENGE = 654321;

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
            new AdminPrincipal(CALLER_ID, AdminType.GLOBAL_ADMIN, null, null),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
  }

  private static OnboardingCredentialsResult result(
      String token, Integer challenge, EnrollmentStatus status) {
    return new OnboardingCredentialsResult(
        ENROLLMENT_ID, token, challenge, null, status, TENANT_ID);
  }

  private AuditLog capturedRow() {
    ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
    verify(auditLogService).log(captor.capture());
    return captor.getValue();
  }

  private static void assertRevealRow(AuditLog row, String channel) {
    assertThat(row.getEventType()).isEqualTo(EventType.ADMIN_BIND_MATERIAL_REVEALED);
    assertThat(row.getEventAction()).isEqualTo(AdminAuditConstants.ADMIN_BIND_MATERIAL_REVEALED);
    assertThat(row.getEventStatus()).isEqualTo(EventStatus.SUCCESS);
    assertThat(row.getAdminId()).isEqualTo(CALLER_ID);
    assertThat(row.getTargetAdminId()).isEqualTo(TARGET_ADMIN_ID);
    assertThat(row.getEnrollmentId()).isEqualTo(ENROLLMENT_ID);
    assertThat(row.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(row.getEventDetails()).contains("\"channel\":\"" + channel + "\"");
    assertThat(row.getEventDetails())
        .doesNotContain(TOKEN)
        .doesNotContain(String.valueOf(CHALLENGE));
    assertThat(row.getErrorMessage()).isNull();
  }

  @Test
  @DisplayName("JSON at CREATED writes one reveal row without the token or the challenge")
  void json_atCreated_writesOneRow() {
    when(provisioningService.getAdminOnboarding(any(), any()))
        .thenReturn(result(TOKEN, CHALLENGE, EnrollmentStatus.CREATED));
    String code = "{\"enrollmentProofToken\":\"" + TOKEN + "\"}";
    when(qrCodePayloadService.composeEnrollmentCode(TOKEN)).thenReturn(code);

    ResponseEntity<?> response =
        controller.getAdminOnboarding(TARGET_ADMIN_ID, authentication, httpRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    // #1494: the enrollment code rides the same audited reveal as the token.
    assertThat(((AdminOnboardingResponseDto) response.getBody()).enrollmentCode()).isEqualTo(code);
    assertRevealRow(capturedRow(), "json");
  }

  @Test
  @DisplayName("JSON at BOUND (challenge only) still writes one reveal row")
  void json_atBound_writesOneRow() {
    when(provisioningService.getAdminOnboarding(any(), any()))
        .thenReturn(result(null, CHALLENGE, EnrollmentStatus.BOUND));

    ResponseEntity<?> response =
        controller.getAdminOnboarding(TARGET_ADMIN_ID, authentication, httpRequest);

    assertThat(((AdminOnboardingResponseDto) response.getBody()).enrollmentCode()).isNull();
    assertRevealRow(capturedRow(), "json");
  }

  @Test
  @DisplayName("JSON after VERIFIED carries no material and writes no row")
  void json_afterVerified_writesNoRow() {
    when(provisioningService.getAdminOnboarding(any(), any()))
        .thenReturn(result(null, null, EnrollmentStatus.VERIFIED));

    ResponseEntity<?> response =
        controller.getAdminOnboarding(TARGET_ADMIN_ID, authentication, httpRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    verify(auditLogService, never()).log(any());
  }

  @Test
  @DisplayName("QR at CREATED writes one reveal row")
  void qr_atCreated_writesOneRow() throws Exception {
    when(provisioningService.getAdminOnboarding(any(), any()))
        .thenReturn(result(TOKEN, CHALLENGE, EnrollmentStatus.CREATED));
    when(qrCodePayloadService.composePayload(TOKEN)).thenReturn("{}");
    when(qrCodeGeneratorService.generateQrCodeImage("{}", 300, 300)).thenReturn(new byte[] {1});

    ResponseEntity<?> response =
        controller.getAdminOnboardingQrCode(TARGET_ADMIN_ID, authentication, httpRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertRevealRow(capturedRow(), "qr");
  }

  @Test
  @DisplayName("QR refused at BOUND shows nothing and writes no row")
  void qr_refusedAtBound_writesNoRow() {
    when(provisioningService.getAdminOnboarding(any(), any()))
        .thenReturn(result(null, CHALLENGE, EnrollmentStatus.BOUND));

    ResponseEntity<?> response =
        controller.getAdminOnboardingQrCode(TARGET_ADMIN_ID, authentication, httpRequest);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    verify(auditLogService, never()).log(any());
  }
}
