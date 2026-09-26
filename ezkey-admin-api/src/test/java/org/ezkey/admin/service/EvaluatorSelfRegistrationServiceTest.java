/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EvaluatorSelfRegistrationServiceTest
 * Description: Unit tests for anonymous evaluator self-registration service.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.ezkey.admin.config.EvaluatorSelfRegistrationProperties;
import org.ezkey.admin.domain.AdminOnboardingMode;
import org.ezkey.admin.exception.EvaluatorOnboardingUnavailableException;
import org.ezkey.admin.security.AdminPrincipal;
import org.ezkey.admin.service.AdminAuthService.TokenIssueResult;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EvaluatorSelfRegistrationService")
class EvaluatorSelfRegistrationServiceTest {

  @Mock private EvaluatorSelfRegistrationRateLimiter rateLimiter;
  @Mock private AdminProvisioningService provisioningService;
  @Mock private AdminAuthService authService;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private TenantRepository tenantRepository;

  private EvaluatorSelfRegistrationProperties properties;
  private EvaluatorSelfRegistrationService service;

  @BeforeEach
  void setUp() {
    properties = new EvaluatorSelfRegistrationProperties();
    properties.setEnabled(true);
    properties.setAdminUiUrl("https://admin-ui.example.local");
    properties.setGuidedTourUrl("https://ezkey.org/community-guided-tour.html");
    properties.setBootstrapSessionTtlHours(8);
    service =
        new EvaluatorSelfRegistrationService(
            properties,
            rateLimiter,
            provisioningService,
            authService,
            adminRepository,
            tenantRepository);
  }

  @Test
  @DisplayName("validateTenantLabel rejects email-like input")
  void validateTenantLabel_rejectsEmail() {
    assertThrows(
        IllegalArgumentException.class,
        () -> EvaluatorSelfRegistrationService.validateTenantLabel("team@example.com"));
  }

  @Test
  @DisplayName("register provisions tenant, activation code, and BOOTSTRAP session with 8h TTL")
  void register_mintsBootstrapSession_eightHourAbsoluteTtl() {
    EzkeyAdmin globalAdmin = new EzkeyAdmin("bootstrap", AdminType.GLOBAL_ADMIN);
    globalAdmin.setAdminId(1);
    when(adminRepository.findByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN))
        .thenReturn(List.of(globalAdmin));
    when(tenantRepository.existsByTenantName(any())).thenReturn(false);

    Tenant tenant = new Tenant("eval-deadbeef", "desc");
    tenant.setTenantId(42);
    when(provisioningService.createTenant(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(AdminPrincipal.class)))
        .thenReturn(tenant);

    OffsetDateTime activationExpires = OffsetDateTime.now().plusDays(7);
    EzkeyAdmin pendingAdmin = new EzkeyAdmin("eval-admin-deadbeef", AdminType.TENANT_ADMIN);
    pendingAdmin.setAdminId(99);
    pendingAdmin.setTenant(tenant);
    AdminProvisioningService.ProvisioningResult provisioningResult =
        new AdminProvisioningService.ProvisioningResult(
            pendingAdmin,
            null,
            null,
            null,
            null,
            AdminOnboardingMode.ACTIVATION_CODE,
            "ABCD-1234",
            activationExpires);
    when(provisioningService.createTenantAdmin(
            any(),
            eq(null),
            eq(null),
            any(),
            any(),
            eq(42),
            eq(AdminOnboardingMode.ACTIVATION_CODE),
            any(AdminPrincipal.class)))
        .thenReturn(provisioningResult);

    OffsetDateTime bootstrapExpires = OffsetDateTime.now().plusHours(8);
    AdminToken bootstrapToken =
        new AdminToken(
            "hash",
            pendingAdmin,
            AdminType.TENANT_ADMIN.name(),
            bootstrapExpires,
            AdminTokenPurpose.BOOTSTRAP);
    when(authService.issueBootstrapSession(eq(pendingAdmin), eq(8)))
        .thenReturn(new TokenIssueResult(bootstrapToken, "ezkey_bootstrap_plain"));

    var response = service.register("My lab", "203.0.113.5");

    assertEquals("ABCD-1234", response.activationCode());
    assertEquals(activationExpires, response.activationCodeExpiresAt());
    assertEquals("ezkey_bootstrap_plain", response.sessionToken());
    assertEquals(bootstrapExpires, response.sessionExpiresAt());
    assertEquals("eval-admin-deadbeef", response.username());
    assertTrue(response.tenantLabel().startsWith("eval-"));
    verify(rateLimiter).verifyAndRecordSuccess("203.0.113.5");
    verify(authService).issueBootstrapSession(pendingAdmin, 8);

    long hoursBetween =
        ChronoUnit.HOURS.between(OffsetDateTime.now().minusMinutes(1), response.sessionExpiresAt());
    assertTrue(hoursBetween >= 7 && hoursBetween <= 8);
  }

  @Test
  @DisplayName("register forces 8h TTL even when property is misconfigured")
  void register_ignoresMisconfiguredTtlProperty() {
    properties.setBootstrapSessionTtlHours(12);

    EzkeyAdmin globalAdmin = new EzkeyAdmin("bootstrap", AdminType.GLOBAL_ADMIN);
    globalAdmin.setAdminId(1);
    when(adminRepository.findByAdminTypeAndActiveTrue(AdminType.GLOBAL_ADMIN))
        .thenReturn(List.of(globalAdmin));
    when(tenantRepository.existsByTenantName(any())).thenReturn(false);

    Tenant tenant = new Tenant("eval-aabbccdd", "desc");
    tenant.setTenantId(7);
    when(provisioningService.createTenant(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(AdminPrincipal.class)))
        .thenReturn(tenant);

    EzkeyAdmin pendingAdmin = new EzkeyAdmin("eval-admin-aabbccdd", AdminType.TENANT_ADMIN);
    pendingAdmin.setTenant(tenant);
    when(provisioningService.createTenantAdmin(
            any(),
            eq(null),
            eq(null),
            any(),
            any(),
            eq(7),
            eq(AdminOnboardingMode.ACTIVATION_CODE),
            any(AdminPrincipal.class)))
        .thenReturn(
            new AdminProvisioningService.ProvisioningResult(
                pendingAdmin,
                null,
                null,
                null,
                null,
                AdminOnboardingMode.ACTIVATION_CODE,
                "CODE",
                OffsetDateTime.now().plusDays(7)));

    OffsetDateTime expires = OffsetDateTime.now().plusHours(8);
    AdminToken token =
        new AdminToken(
            "h", pendingAdmin, AdminType.TENANT_ADMIN.name(), expires, AdminTokenPurpose.BOOTSTRAP);
    when(authService.issueBootstrapSession(any(), anyInt()))
        .thenReturn(new TokenIssueResult(token, "ezkey_bootstrap_x"));

    service.register(null, "127.0.0.1");

    ArgumentCaptor<Integer> ttlCaptor = ArgumentCaptor.forClass(Integer.class);
    verify(authService).issueBootstrapSession(eq(pendingAdmin), ttlCaptor.capture());
    assertEquals(8, ttlCaptor.getValue());
  }

  @Test
  @DisplayName("reissueOnboarding returns new activation + BOOTSTRAP while pending activation")
  void reissue_pendingActivation_returnsActivationAndBootstrap() {
    Tenant tenant = new Tenant("eval-deadbeef", "desc");
    tenant.setTenantId(42);
    tenant.setActive(true);
    EzkeyAdmin pendingAdmin = new EzkeyAdmin("eval-admin-deadbeef", AdminType.TENANT_ADMIN);
    pendingAdmin.setAdminId(99);
    pendingAdmin.setActive(true);
    pendingAdmin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);
    pendingAdmin.setTenant(tenant);
    pendingAdmin.setEnrollment(null);

    when(adminRepository.findByUsernameWithEnrollment("eval-admin-deadbeef"))
        .thenReturn(java.util.Optional.of(pendingAdmin));
    OffsetDateTime activationExpires = OffsetDateTime.now().plusDays(7);
    when(provisioningService.reissueActivationCodeForEvaluatorResume(pendingAdmin))
        .thenReturn(
            new AdminProvisioningService.ActivationCodeReissueResult(
                pendingAdmin, "ezkey_activation_new", activationExpires, true, 1));
    OffsetDateTime bootstrapExpires = OffsetDateTime.now().plusHours(8);
    AdminToken bootstrapToken =
        new AdminToken(
            "hash",
            pendingAdmin,
            AdminType.TENANT_ADMIN.name(),
            bootstrapExpires,
            AdminTokenPurpose.BOOTSTRAP);
    when(authService.issueBootstrapSession(eq(pendingAdmin), eq(8)))
        .thenReturn(new TokenIssueResult(bootstrapToken, "ezkey_bootstrap_reissue"));

    var response = service.reissueOnboarding("eval-admin-deadbeef", "203.0.113.9");

    assertEquals(EvaluatorSelfRegistrationService.PHASE_PENDING_ACTIVATION, response.phase());
    assertEquals("ezkey_activation_new", response.activationCode());
    assertEquals("ezkey_bootstrap_reissue", response.sessionToken());
    assertNull(response.enrollmentProofToken());
    verify(rateLimiter).verifyAndRecordReissue("203.0.113.9", "eval-admin-deadbeef");
  }

  @Test
  @DisplayName("reissueOnboarding rotates QR proof + BOOTSTRAP for ACTIVE CREATED enrollment")
  void reissue_deviceBind_returnsRotatedProofAndBootstrap() {
    Tenant tenant = new Tenant("eval-cafebabe", "desc");
    tenant.setTenantId(5);
    tenant.setActive(true);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(77);
    enrollment.setStatus(EnrollmentStatus.CREATED);
    enrollment.setActive(true);
    EzkeyAdmin admin = new EzkeyAdmin("eval-admin-cafebabe", AdminType.TENANT_ADMIN);
    admin.setAdminId(11);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    admin.setTenant(tenant);
    admin.setEnrollment(enrollment);

    when(adminRepository.findByUsernameWithEnrollment("eval-admin-cafebabe"))
        .thenReturn(java.util.Optional.of(admin));
    when(provisioningService.rotateIncompleteEnrollmentProofForEvaluatorResume(admin))
        .thenReturn(
            new AdminProvisioningService.OnboardingCredentialsResult(
                77, "ezkey_proof_rotated", 654321, null));
    OffsetDateTime bootstrapExpires = OffsetDateTime.now().plusHours(8);
    AdminToken bootstrapToken =
        new AdminToken(
            "hash",
            admin,
            AdminType.TENANT_ADMIN.name(),
            bootstrapExpires,
            AdminTokenPurpose.BOOTSTRAP);
    when(authService.issueBootstrapSession(eq(admin), eq(8)))
        .thenReturn(new TokenIssueResult(bootstrapToken, "ezkey_bootstrap_bind"));

    var response = service.reissueOnboarding("eval-admin-cafebabe", "203.0.113.10");

    assertEquals(EvaluatorSelfRegistrationService.PHASE_DEVICE_BIND, response.phase());
    assertEquals(77, response.enrollmentId());
    assertEquals("ezkey_proof_rotated", response.enrollmentProofToken());
    assertEquals(654321, response.enrollmentChallenge());
    assertNull(response.activationCode());
    assertEquals("ezkey_bootstrap_bind", response.sessionToken());
  }

  @Test
  @DisplayName("reissueOnboarding rejects verified enrollment (device already bound)")
  void reissue_rejectsVerifiedEnrollment() {
    Tenant tenant = new Tenant("eval-done", "desc");
    tenant.setActive(true);
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(1);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setActive(true);
    EzkeyAdmin admin = new EzkeyAdmin("eval-admin-done", AdminType.TENANT_ADMIN);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);
    admin.setTenant(tenant);
    admin.setEnrollment(enrollment);
    when(adminRepository.findByUsernameWithEnrollment("eval-admin-done"))
        .thenReturn(java.util.Optional.of(admin));

    assertThrows(
        EvaluatorOnboardingUnavailableException.class,
        () -> service.reissueOnboarding("eval-admin-done", "203.0.113.11"));
    verify(authService, never()).issueBootstrapSession(any(), anyInt());
  }

  @Test
  @DisplayName("reissueOnboarding rejects non-evaluator username prefix")
  void reissue_rejectsNonEvaluatorUsername() {
    assertThrows(
        EvaluatorOnboardingUnavailableException.class,
        () -> service.reissueOnboarding("ops.admin", "203.0.113.12"));
    verify(adminRepository, never()).findByUsernameWithEnrollment(any());
  }
}
