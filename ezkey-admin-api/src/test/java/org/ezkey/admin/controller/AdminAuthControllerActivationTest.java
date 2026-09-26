/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthControllerActivationTest
 * Description: Focused controller tests for public activation endpoint.
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
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.config.AdminRecoveryProperties;
import org.ezkey.admin.constants.AdminAuditConstants;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.dto.request.AdminActivationRequestDto;
import org.ezkey.admin.dto.response.AdminActivationResponseDto;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.security.AdminCsrfTokenService;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.security.AdminSessionCookieService;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.admin.service.AdminProvisioningService;
import org.ezkey.admin.service.EvaluatorSelfRegistrationService;
import org.ezkey.audit.service.AuditLogService;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminAuthControllerActivationTest {

  @Mock private AdminAuthService authService;
  @Mock private AdminProvisioningService provisioningService;
  @Mock private org.ezkey.admin.service.AdminRecoveryService recoveryService;
  @Mock private EvaluatorSelfRegistrationService evaluatorSelfRegistrationService;
  @Mock private AuditLogService auditLogService;
  @Mock private AdminRateLimitFilter rateLimitFilter;
  @Mock private AdminRecoveryProperties recoveryProperties;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminBrowserSessionCookieProperties browserSessionCookieProperties;
  @Mock private AdminSessionCookieService sessionCookieService;
  @Mock private AdminCsrfTokenService csrfTokenService;
  @Mock private HttpServletRequest httpRequest;

  private AdminAuthController controller;

  @BeforeEach
  void setUp() {
    controller =
        new AdminAuthController(
            authService,
            provisioningService,
            recoveryService,
            evaluatorSelfRegistrationService,
            auditLogService,
            rateLimitFilter,
            recoveryProperties,
            adminRepository,
            browserSessionCookieProperties,
            sessionCookieService,
            csrfTokenService);
    when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
  }

  @Test
  @DisplayName("activate returns first-time enrollment credentials")
  void activateReturnsFirstTimeEnrollmentCredentials() {
    EzkeyAdmin admin = new EzkeyAdmin("pending.admin", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(9);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);

    org.ezkey.enrollment.domain.entity.Enrollment enrollment =
        new org.ezkey.enrollment.domain.entity.Enrollment();
    enrollment.setEnrollmentId(123);

    when(provisioningService.activatePendingAdmin(
            AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "demo123"))
        .thenReturn(
            new AdminProvisioningService.ProvisioningResult(
                admin,
                enrollment,
                "ezkey_proof_demo",
                654321,
                List.of("1111-2222-3333-4444-5555-6666-7777-8888"),
                AdminOnboardingMode.ACTIVATION_CODE,
                null,
                null));

    ResponseEntity<AdminActivationResponseDto> response =
        controller.activate(
            new AdminActivationRequestDto(AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "demo123"),
            httpRequest);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    AdminActivationResponseDto body =
        assertInstanceOf(AdminActivationResponseDto.class, response.getBody());
    assertEquals(true, body.success());
    assertEquals("pending.admin", body.username());
    assertEquals(123, body.enrollmentId());
    assertEquals("ezkey_proof_demo", body.enrollmentProofToken());
    assertEquals(null, body.recoveryCodes());
    assertEquals(null, body.onboardingResumeSecret());
    verify(auditLogService).log(any());
  }

  @Test
  @DisplayName("activate mints onboarding-resume secret when evaluator self-reg enabled")
  void activateMintsOnboardingResumeWhenSelfRegEnabled() {
    when(evaluatorSelfRegistrationService.isEnabled()).thenReturn(true);
    EzkeyAdmin admin = new EzkeyAdmin("eval-admin-x", AdminType.TENANT_ADMIN);
    admin.setAdminId(9);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    org.ezkey.enrollment.domain.entity.Enrollment enrollment =
        new org.ezkey.enrollment.domain.entity.Enrollment();
    enrollment.setEnrollmentId(123);
    when(provisioningService.activatePendingAdmin(
            AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "demo123"))
        .thenReturn(
            new AdminProvisioningService.ProvisioningResult(
                admin,
                enrollment,
                "ezkey_proof_demo",
                654321,
                null,
                AdminOnboardingMode.ACTIVATION_CODE,
                null,
                null));
    OffsetDateTime resumeExpires = OffsetDateTime.now().plusHours(8);
    AdminToken resumeToken =
        new AdminToken(
            "hash",
            admin,
            AdminType.TENANT_ADMIN.name(),
            resumeExpires,
            org.ezkey.integration.domain.AdminTokenPurpose.ONBOARDING_RESUME);
    when(authService.issueOnboardingResumeSecret(admin))
        .thenReturn(
            new AdminAuthService.TokenIssueResult(resumeToken, "ezkey_onboarding_resume_abc"));

    ResponseEntity<AdminActivationResponseDto> response =
        controller.activate(
            new AdminActivationRequestDto(AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "demo123"),
            httpRequest);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("ezkey_onboarding_resume_abc", response.getBody().onboardingResumeSecret());
    assertEquals(resumeExpires, response.getBody().onboardingResumeExpiresAt());
  }

  @Test
  @DisplayName("activate returns 403 on invalid activation code")
  void activateReturnsForbiddenOnInvalidCode() {
    when(provisioningService.activatePendingAdmin(
            AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "bad123"))
        .thenThrow(new AuthenticationException("Invalid activation code"));

    ResponseEntity<AdminActivationResponseDto> response =
        controller.activate(
            new AdminActivationRequestDto(AdminAuditConstants.ACTIVATION_TOKEN_PREFIX + "bad123"),
            httpRequest);

    assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    assertEquals(false, response.getBody().success());
    assertEquals("Invalid activation code", response.getBody().message());
  }
}
